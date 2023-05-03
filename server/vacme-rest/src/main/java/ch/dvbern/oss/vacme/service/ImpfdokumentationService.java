/*
 * Copyright (C) 2022 DV Bern AG, Switzerland
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.dvbern.oss.vacme.service;

import java.time.LocalDateTime;
import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.validation.constraints.NotEmpty;

import ch.dvbern.oss.vacme.entities.UserPrincipal;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.impfen.ImpfungkontrolleTermin;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import ch.dvbern.oss.vacme.entities.types.BenutzerRolle;
import ch.dvbern.oss.vacme.i18n.ServerMessageUtil;
import ch.dvbern.oss.vacme.repo.FragebogenRepo;
import ch.dvbern.oss.vacme.repo.ImpfterminRepo;
import ch.dvbern.oss.vacme.repo.ImpfungRepo;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_1_DURCHGEFUEHRT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_1_KONTROLLIERT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_2_DURCHGEFUEHRT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_2_KONTROLLIERT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.KONTROLLIERT_BOOSTER;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ImpfdokumentationService {

	private final ImpfungRepo impfungRepo;
	private final ImpfterminRepo impfterminRepo;
	private final FragebogenService fragebogenService;
	private final DokumentService dokumentService;
	private final TerminbuchungService terminbuchungService;
	private final ImpfinformationenService impfinformationenService;
	private final ExternesZertifikatService externesZertifikatService;
	private final UserPrincipal userPrincipal;
	private final FragebogenRepo fragebogenRepo;

	@ConfigProperty(name = "vacme.validation.impfung.disallow.sameday", defaultValue = "true")
	protected Boolean validateSameDayImpfungen;

	@NonNull
	public Impfung createImpfung(
		@NonNull Registrierung registrierung,
		@NonNull OrtDerImpfung ortDerImpfung,
		@NonNull Impffolge impffolge,
		@NonNull Impfung impfung,
		boolean nachtrag,
		@NonNull LocalDateTime timestampOfImpfung
	) {
		switch (impffolge) {
		case ERSTE_IMPFUNG:
			ValidationUtil.validateStatus(registrierung, IMPFUNG_1_KONTROLLIERT);
			break;
		case ZWEITE_IMPFUNG:
			ValidationUtil.validateStatus(registrierung, IMPFUNG_2_KONTROLLIERT);
			break;
		case BOOSTER_IMPFUNG:
			ValidationUtil.validateStatusOneOf(registrierung, KONTROLLIERT_BOOSTER);
			break;
		}

		ImpfinformationDto impfinformationen =
			this.impfinformationenService.getImpfinformationenAndEnsureStatusIsAktuell(registrierung.getRegistrierungsnummer());

		// Grundimmunisierungs-Flag: kann nur true sein, wenn die vorherige Impfung auch true hatte
		Impfung previousImpfung = ImpfinformationenService.getNewestVacmeImpfung(impfinformationen);
		boolean isExternGrundimmunisiert = externesZertifikatService.isConfirmedExternGrundimmunisiert(registrierung);
		ValidationUtil.validateGrundimmunisierung(impfung, impffolge, previousImpfung, isExternGrundimmunisiert);

		// Den Termin je nach Impffolge holen bzw. einen Ad-hoc Termin erstellen, wenn keiner vorhanden ist oder dieser
		// nicht heute oder nicht in diesem odi ist,
		// in diesem Fall wird der bestehende Termin freigegben
		Impftermin termin = getOrCreateImpftermin(impfinformationen, impffolge, ortDerImpfung, timestampOfImpfung);

		// Sicherstellen, dass nicht zuviel geimpft wurde
		ValidationUtil.validateAnzahlImpfungen(impffolge, impfinformationen);

		// Bevor wir eventuelle Abstaende pruefen: Falls der Impfstoff nur einen Termin erfordert, den zweiten gleich loeschen
		// Damit verhindern wir, dass es einen Fehler gibt, nur weil der zweite (nicht mehr benoetigte) Termin in einem anderen ODI ist.
		if (impffolge == Impffolge.ERSTE_IMPFUNG && ImpfinformationenService.willBeGrundimmunisiertAfterErstimpfung(impfung, impfinformationen)) {
			impfterminRepo.termin2Freigeben(registrierung);
		}

		// Egal, ob es ein Ad-Hoc oder ein geplanter Termin war: Abstaende pruefen
		switch (impffolge) {
		case ERSTE_IMPFUNG:
			// Der Abstand wird nicht mehr geprueft
			// Es ist nicht mehr noetig, dass beide Termine zum gleichen Odi gehoeren
			break;
		case ZWEITE_IMPFUNG:
			// Beim zweiten Termin wird der ODI nicht mehr geprueft!
			// Beim zweiten Termin wird auch der Abstand nicht mehr validiert!
			// Es wird nur geprueft, ob nicht am selben Tag bereits geimpft wurde
			validateImpfungEinTagNachBisherige(registrierung, timestampOfImpfung);
			break;
		case BOOSTER_IMPFUNG:
			ValidationUtil.validateBoosterImpfungEinTagNachBisherige(impfinformationen, timestampOfImpfung, validateSameDayImpfungen);
			break;
		}

		// Und dass der Termin am richtigen Datum ist
		ValidationUtil.validateCorrectDatum(termin, timestampOfImpfung);
		ValidationUtil.validateNotAfterTomorrow(termin);
		ValidationUtil.validateImpfungMinDate(termin);
		if (!userPrincipal.isCallerInRole(BenutzerRolle.OI_IMPFVERANTWORTUNG)) {
			ValidationUtil.validateOdiNotDeactivated(ortDerImpfung);
		}

		// Der Impfstoff muss zugelassen sein, oder wenn es extern ist reicht auch extern_zugelassen
		ValidationUtil.validateImpfstoffZulassung(impfung.getImpfstoff(), impfung.isExtern());

		impfung.setTimestampImpfung(timestampOfImpfung);
		impfung.setTermin(termin);
		impfungRepo.create(impfung);
		LOG.info("VACME-IMPFUNG: Impfung fuer die Registrierung {} erfasst, Impffolge {}", registrierung.getRegistrierungsnummer(), impffolge);
		impfinformationen = this.impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer()); // need to reload because new
		// impfungen were created
		setNextStatus(impfinformationen, impffolge, impfung, nachtrag);

		// Wir setzen den ODI hier als gewuenschterODI. Damit faellt die Person aus der Pendenzenliste
		// ihres vorherigen gewuneschten ODI heraus!
		final boolean grundimmunisiert = RegistrierungStatus.getMindestensGrundimmunisiertOrAbgeschlossen()
			.contains(impfinformationen.getRegistrierung().getRegistrierungStatus());
		if (!grundimmunisiert) {
			// Urspruengliche Wahl soll fuer die zweite Impfung immer noch gelten.
			// Wenn noch nichts ausgewaehlt war, die Angaben der ersten Impfung nehmen
			if (registrierung.getGewuenschterOdi() == null && !registrierung.isNichtVerwalteterOdiSelected()) {
				// Es gab noch keine Wahl, weder fuer einen verwalteten noch fuer einen nicht verwalteten ODI
				registrierung.setGewuenschterOdi(ortDerImpfung);
				registrierung.setNichtVerwalteterOdiSelected(false);
			}
		} else {
			// ODI muss nur fuer die 1-2 Grundimpfungen gleich sein. Die sind jetzt vorbei, wir setzen daher den gewuenschten ODI fuer
			// die naechste Impfung zurueck
			registrierung.setGewuenschterOdi(null);
			registrierung.setNichtVerwalteterOdiSelected(false);
		}

		// Das Selbstzahler-Flag jetzt zuruecksetzen, es muss fuer eine eventuelle naechste Impfung wieder neu gesetzt werden
		registrierung.setSelbstzahler(false);

		// Die Dokumentation erstellen und speichern
		// Falls es bereits eine Doku gibt, muss es zuerst geloescht werden
		if (Impffolge.ERSTE_IMPFUNG != impffolge) {
			dokumentService.deleteImpfdokumentationPdf(registrierung);
		}
		dokumentService.createAndSaveImpfdokumentationPdf(impfinformationen);

		return impfung;
	}

	public void cleanupTemporarySelbstzahlendeFlagOnImpfkontrolleTermin(
		@NotEmpty @NonNull String registrierungsnummer) {
		ImpfinformationDto infos = impfinformationenService.getImpfinformationenAndEnsureStatusIsAktuell(registrierungsnummer);
		Fragebogen fragebogen = fragebogenService.findFragebogenByRegistrierungsnummer(registrierungsnummer);

		ImpfungkontrolleTermin impfungkontrolleTermin =
			ImpfinformationenService.getNewestImpfkontrolleTermin(infos, fragebogen);
		// Wir haben soeben erfolgreich geimpft -> Daher der Termin existiert garantiert.
		Objects.requireNonNull(impfungkontrolleTermin);

		impfungkontrolleTermin.setSelbstzahlende(null);

		fragebogenRepo.update(fragebogen);
	}

	/**
	 * Gibt den Impftermin zurueck. Es ist sichergestellt, dass der Termin
	 * - am richtigen Datum
	 * - am richtigen ODI ist
	 * Falls dies nicht der Fall ist, wird der Termin geloescht und ein neuer AdHoc-Termin erstellt.
	 * Falls noch gar kein Termin vorhanden ist, wird ein neuer AdHoc-Termin erstellt
	 */
	@NonNull
	private Impftermin getOrCreateImpftermin(
		@NonNull ImpfinformationDto impfinformationen,
		@NonNull Impffolge impffolge,
		@NonNull OrtDerImpfung ortDerImpfung,
		@NonNull LocalDateTime timeOfTermin
	) {
		Registrierung registrierung = impfinformationen.getRegistrierung();
		Integer impffolgeNr = ImpfinformationenService.getNumberOfImpfung(impfinformationen) + 1;
		Impftermin termin = ImpfinformationenService.getImpftermin(impfinformationen, impffolge, impffolgeNr);
		if (termin != null) {
			boolean correctOrtDerImpfung = ValidationUtil.isCorrectOrtDerImpfung(ortDerImpfung, termin);
			boolean correctDatum = ValidationUtil.isCorrectDatum(termin, timeOfTermin);
			if (!correctOrtDerImpfung || !correctDatum) {
				// Es gibt bereits einen Termin, aber entweder der Ort oder das Datum ist falsch
				// Der Kontrolleur hat aber entschieden, trotzdem zu impfen
				logDiffToActualTermin(correctOrtDerImpfung, correctDatum, impffolge, registrierung, termin, ortDerImpfung);
				// Wir erstellen einen neuen Ad-Hoc Termin im richtigen ODI zum richtigen zeitpunkt
				termin = terminbuchungService.createOnDemandImpftermin(impffolge, ortDerImpfung, timeOfTermin);
				switch (impffolge) {
				case ERSTE_IMPFUNG:
					// Es handelt sich um die erste Impfung: Der bestehende Termin wird freigegeben und der neue AdHoc Termin gebucht
					impfterminRepo.termin1Freigeben(registrierung);
					impfterminRepo.termin1Speichern(registrierung, termin);
					break;
				case ZWEITE_IMPFUNG:
					// Es ist die zweite Impfung: Der bestehende Termin wird freigegeben und der neue AdHoc Termin gebucht
					impfterminRepo.termin2Freigeben(registrierung);
					impfterminRepo.termin2Speichern(registrierung, termin);
					break;
				case BOOSTER_IMPFUNG:
					if (impfinformationen.getImpfdossier() != null) {
						Impfdossiereintrag eintrag =
							impfinformationen.getImpfdossier().getEintragForImpffolgeNr(impffolgeNr);
						impfterminRepo.boosterTerminFreigeben(eintrag);
						impfterminRepo.boosterTerminSpeichern(registrierung, eintrag, termin);
					}
				}
			}
		} else {
			// Es bestand noch kein Termin (MobilesImpfteam, OdiOhneTermine, AdHocImpfling)
			termin = terminbuchungService.createOnDemandImpftermin(impffolge, ortDerImpfung, timeOfTermin);
			switch (impffolge) {
			case ERSTE_IMPFUNG:
				impfterminRepo.termin1Speichern(registrierung, termin);
				break;
			case ZWEITE_IMPFUNG:
				impfterminRepo.termin2Speichern(registrierung, termin);
				break;
			case BOOSTER_IMPFUNG:
				// Der Eintrag muss schon existieren, denn impfen kann man nur, wenn der ImpfkontrolleTermin existiert.
				Impfdossiereintrag impfdossiereintrag = impfinformationenService.getExistingLatestImpfdossierEintrag(impfinformationen, impffolgeNr);
				impfterminRepo.boosterTerminSpeichern(registrierung, impfdossiereintrag, termin);
				break;
			}
		}
		return termin;
	}

	private void logDiffToActualTermin(boolean correctOrtDerImpfung, boolean correctDatum, Impffolge impffolge, Registrierung registrierung, Impftermin termin
		, OrtDerImpfung ortDerImpfung) {
		if (!correctDatum) {
			LOG.info("VACME-INFO: Es wurde fuer die Registrierung {} bei der {} am  Ort {}  zu einem  "
					+ " anderen als dem im Termin vereinbarten Datum {} geimpft."
					+ "", registrierung.getRegistrierungsnummer(), impffolge, ortDerImpfung.getName(),
				termin.getImpfslot().getZeitfenster().getVon().toLocalDate());
		}
		if (!correctOrtDerImpfung) {
			LOG.info("VACME-INFO: Es wurde fuer die Registrierung {} bei der {} am  Ort {}  zu einem  "
				+ " anderen als dem im Termin vereinbarten Ort {} geimpft."
				+ "", registrierung.getRegistrierungsnummer(), impffolge, ortDerImpfung.getName(), termin.getImpfslot().getOrtDerImpfung().getName());
		}

	}

	public void setNextStatus(@NonNull ImpfinformationDto regInfo, @NonNull Impffolge impffolge, @NonNull Impfung impfung, boolean nachtrag) {
		Registrierung registrierung = regInfo.getRegistrierung();
		final int anzahlDosenBenoetigt = impfung.getImpfstoff().getAnzahlDosenBenoetigt();
		switch (impffolge) {
		case ERSTE_IMPFUNG:
			if (ImpfinformationenService.willBeGrundimmunisiertAfterErstimpfung(impfung, regInfo)) {
				registrierung.setStatusToAbgeschlossenAfterErstimpfung(regInfo, impfung);
				return;
			}
			if (nachtrag) {
				// Wenn es ein Nachtrag der ersten Impfung war, muss der Status direkt auf IMPFUNG_2_KONTROLLIERT gesetzt
				// werden, damit mit der Doku der zweiten Impfung weitergefahren werden kann
				simulateKontrolle2(registrierung);
				registrierung.setStatusToNichtAbgeschlossenStatus(IMPFUNG_2_KONTROLLIERT, regInfo.getImpfung1());
			} else {
				registrierung.setStatusToNichtAbgeschlossenStatus(IMPFUNG_1_DURCHGEFUEHRT, regInfo.getImpfung1());
			}
			return;
		case ZWEITE_IMPFUNG:
			if (anzahlDosenBenoetigt <= 2) {
				registrierung.setStatusToAbgeschlossen(regInfo, impfung);
			} else {
				registrierung.setStatusToNichtAbgeschlossenStatus(IMPFUNG_2_DURCHGEFUEHRT, regInfo.getImpfung1());
			}
			return;
		case BOOSTER_IMPFUNG:
			registrierung.setStatusToImmunisiertAfterBooster(regInfo, impfung);
			return;
		default:
			throw new IllegalStateException("Unexpected value: " + impffolge);
		}
	}

	/**
	 * Bei einem Nachtrag der 1. Impfung wird die 2. Kontrolle nicht physisch gemacht daher wird
	 * dies direkt hier auf dem Server "simuliert"
	 */
	private void simulateKontrolle2(@NonNull Registrierung registrierung) {
		// Wenn es ein Nachtrag der ersten Impfung war, muss der Status direkt auf IMPFUNG_2_KONTROLLIERT gesetzt
		// werden, damit mit der Doku der zweiten Impfung weitergefahren werden kann
		final Fragebogen fragebogen = fragebogenService.findFragebogenByRegistrierungsnummer(registrierung.getRegistrierungsnummer());
		ImpfungkontrolleTermin kontrolleTermin = fragebogen.getPersonenkontrolle().getKontrolleTermin2();
		if (kontrolleTermin == null) {
			kontrolleTermin = new ImpfungkontrolleTermin();
			fragebogen.getPersonenkontrolle().setKontrolleTermin2(kontrolleTermin);
		}
		kontrolleTermin.setTimestampKontrolle(LocalDateTime.now());
		kontrolleTermin.setBemerkung(ServerMessageUtil.getMessageAllConfiguredLanguages(
			"impfdok_nachtrag_impfung_1_bemerkung_kontrolle_2", " / ", 1, 2)); // TODO generisch
	}

	private void validateImpfungEinTagNachBisherige(@NonNull Registrierung registrierung, @NonNull LocalDateTime timestampOfImpfung) {
		Objects.requireNonNull(registrierung.getImpftermin1(),
			"Bei der zweiten Impfung muss zwingend eine Impfung1 vorhanden sein");
		final Impfung impfung1 = impfungRepo.getByImpftermin(registrierung.getImpftermin1())
			.orElseThrow(() -> AppFailureException.entityNotFound(Impfung.class, registrierung.getImpftermin1()));
		if (validateSameDayImpfungen) {
			ValidationUtil.validateSecondImpfungOnSameDay(impfung1, timestampOfImpfung);
		}
		ValidationUtil.validateSecondImpfungBeforeFirst(impfung1, timestampOfImpfung);
	}
}
