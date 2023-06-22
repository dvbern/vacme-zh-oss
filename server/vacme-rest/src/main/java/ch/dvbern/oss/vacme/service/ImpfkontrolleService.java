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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;

import ch.dvbern.oss.vacme.entities.externeimpfinfos.ExternesZertifikat;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossier;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.impfen.ImpfungkontrolleTermin;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungsEingang;
import ch.dvbern.oss.vacme.entities.types.VollstaendigerImpfschutzTyp;
import ch.dvbern.oss.vacme.jax.registration.ExternGeimpftJax;
import ch.dvbern.oss.vacme.jax.registration.ImpfkontrolleJax;
import ch.dvbern.oss.vacme.jax.registration.ImpfkontrolleTerminJax;
import ch.dvbern.oss.vacme.repo.FragebogenRepo;
import ch.dvbern.oss.vacme.repo.ImpfdossierRepo;
import ch.dvbern.oss.vacme.repo.ImpfterminRepo;
import ch.dvbern.oss.vacme.repo.ImpfungRepo;
import ch.dvbern.oss.vacme.repo.RegistrierungRepo;
import ch.dvbern.oss.vacme.service.booster.BoosterService;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.util.PhoneNumberUtil;
import ch.dvbern.oss.vacme.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ABGESCHLOSSEN;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.FREIGEGEBEN;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.FREIGEGEBEN_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.GEBUCHT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.GEBUCHT_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMMUNISIERT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_1_DURCHGEFUEHRT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_1_KONTROLLIERT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_2_KONTROLLIERT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.KONTROLLIERT_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ODI_GEWAEHLT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ODI_GEWAEHLT_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.REGISTRIERT;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ImpfkontrolleService {

	private final FragebogenRepo fragebogenRepo;
	private final RegistrierungRepo registrierungRepo;
	private final StammdatenService stammdatenService;
	private final ImpfungRepo impfungRepo;
	private final SmsService smsService;
	private final DokumentService dokumentService;
	private final PdfArchivierungService pdfArchivierungService;
	private final ImpfterminRepo impfterminRepo;
	private final ImpfdossierRepo impfdossierRepo;
	private final ZertifikatService zertifikatService;
	private final ImpfinformationenService impfinformationenService;
	private final ExternesZertifikatService externesZertifikatService;
	private final RegistrierungService registrierungService;
	private final BoosterService boosterService;
	private final ImpfdossierService impfdossierService;

	@ConfigProperty(name = "vacme.validation.kontrolle.disallow.sameday", defaultValue = "true")
	protected Boolean validateSameDayKontrolle;

	@NonNull
	public Registrierung createRegistrierung(
		@NonNull Fragebogen fragebogen,
		@NonNull ImpfkontrolleJax impfkontrolleJax) {
		Registrierung registrierung = fragebogen.getRegistrierung();
		if (!registrierung.isNew()) {
			throw AppValidationMessage.EXISTING_REGISTRIERUNG.create(registrierung.getRegistrierungsnummer());
		}

		// registrierung die direkt in kontrolle erfasst wird hat keinen zugeordneten user
		registrierung.setBenutzerId(null);
		// Wenn eine Registrierung ueber die Impfkontrolle erfasst wird, hat sie immer den Eingang ORT_DER_IMPFUNG
		registrierung.setRegistrierungsEingang(RegistrierungsEingang.ORT_DER_IMPFUNG);
		registrierung.setRegistrierungsnummer(stammdatenService.createUniqueRegistrierungsnummer());
		registrierung.setPrioritaet(stammdatenService.calculatePrioritaet(fragebogen));
		registrierung.setRegistrationTimestamp(LocalDateTime.now());

		// Speichern, sonst kann man das externe Zertifikat nicht erstellen
		fragebogenRepo.create(fragebogen);
		impfdossierService.createImpfdossier(registrierung);

		Objects.requireNonNull(impfkontrolleJax.getExternGeimpft());
		if (impfkontrolleJax.getExternGeimpft().isExternGeimpft()) {
			ExternesZertifikat externesZertifikat =
				externesZertifikatService.createExternGeimpft(registrierung, impfkontrolleJax.getExternGeimpft(), true);

			// Impfinfos erst lese, nachdem das externe Zertifikat erstellt wurde
			ImpfinformationDto infos =
				impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());

			if (externesZertifikatService.isExternesZertGrundimmunisiertUndKontrolliert(externesZertifikat)) {
				// Extern grundimmunisiert -> direkt Boosterkontrolle machen, Impfdossiereintrag erstellen
				registrierung.setStatusToImmunisiertWithExternZertifikat(externesZertifikat);
				registrierung.setRegistrierungStatus(KONTROLLIERT_BOOSTER);
				// Kontrolltermin NEU erstellen
				impfkontrolleTerminErstellen(infos, fragebogen, Impffolge.BOOSTER_IMPFUNG, impfkontrolleJax);
			} else {
				registrierung.setRegistrierungStatus(IMPFUNG_1_KONTROLLIERT);
				// Kontrolltermin NEU erstellen
				impfkontrolleTerminErstellen(infos, fragebogen, Impffolge.ERSTE_IMPFUNG, impfkontrolleJax);
			}

			// bei ad-hoc wird ExternesZertifikat nie entfernt also muessen wir nur neu rechnen wenn wir eiens haben
			boosterService.recalculateImpfschutzAndStatusmovesForSingleReg(registrierung);
		} else {
			// Registrierung normal auf erste impfung inizialisieren
			registrierung.setRegistrierungStatus(IMPFUNG_1_KONTROLLIERT);
			ImpfinformationDto infos =
				impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
			impfkontrolleTerminErstellen(infos, fragebogen, Impffolge.ERSTE_IMPFUNG, impfkontrolleJax);
		}

		// Auch bei Registrierung im ODI soll ein SMS geschicket werden, sofern es sich um eine Mobile Nummer handelt
		if (PhoneNumberUtil.isMobileNumber(registrierung.getTelefon())) {
			Objects.requireNonNull(registrierung.getTelefon());
			smsService.sendOdiRegistrierungsSMS(registrierung, registrierung.getTelefon());
		}

		return registrierung;
	}

	private void impfkontrolleTerminErstellen(
		@NonNull ImpfinformationDto infos,
		@NonNull Fragebogen fragebogen,
		@NonNull Impffolge impffolge,
		@NonNull ImpfkontrolleJax impfkontrolleJax
	) {
		// Kontrolltermin NEU erstellen
		int currentKontrolleNr = ImpfinformationenService.getCurrentKontrolleNr(infos);
		ImpfungkontrolleTermin impfungkontrolleTermin = getOrCreateImpfkontrolleTermin(
			fragebogen, infos, impffolge, currentKontrolleNr, null);
		Objects.requireNonNull(impfungkontrolleTermin);

		ImpfkontrolleTerminJax impfungkontrolleTerminJax = impfkontrolleJax.getImpfungkontrolleTermin();
		Objects.requireNonNull(impfungkontrolleTerminJax);

		impfungkontrolleTerminJax.apply(impfungkontrolleTermin);
	}

	public void kontrolleOkForOnboarding(
		@NonNull Fragebogen fragebogen,
		@NonNull Impffolge impffolge,
		@Nullable LocalDateTime impfDate) {
		ExternGeimpftJax dummyExternGeimpftJax = new ExternGeimpftJax();
		dummyExternGeimpftJax.setExternGeimpft(false);
		kontrolleOk(fragebogen, impffolge, impfDate, null, dummyExternGeimpftJax);
	}

	public void kontrolleOk(
		@NonNull Fragebogen fragebogen,
		@NonNull Impffolge impffolge,
		@Nullable LocalDateTime kontrolleTimeParam,
		@Nullable ImpfkontrolleJax impfkontrolleJax,
		@NonNull @NotNull ExternGeimpftJax externGeimpftJax
	) {
		Registrierung registrierung = fragebogen.getRegistrierung();

		ExternesZertifikat externesZertifikatOrNull =
			externesZertifikatService.createUpdateOrRemoveExternGeimpft(registrierung, externGeimpftJax, true);
		// Infos erst nach dem Erstellen der externen Zertifikat laden, dann hat es das externe Zertifikat schon drin
		ImpfinformationDto infos =
			impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
		int currentKontrolleNr = ImpfinformationenService.getCurrentKontrolleNr(infos);

		Impffolge impffolgeEffektiv =
			preKontrolleJumpImpffolgeForExternesZertifikat(impffolge, registrierung, externesZertifikatOrNull, infos);
		kontrolleOkBasic(
			fragebogen,
			impffolgeEffektiv,
			currentKontrolleNr,
			kontrolleTimeParam,
			impfkontrolleJax,
			registrierung,
			externesZertifikatOrNull);
	}

	private void kontrolleOkBasic(
		@NonNull Fragebogen fragebogen,
		@NonNull Impffolge impffolge,
		@NonNull Integer impffolgeNr,
		@Nullable LocalDateTime kontrolleTime,
		@Nullable ImpfkontrolleJax impfkontrolleJax,
		@NonNull Registrierung registrierung,
		@Nullable ExternesZertifikat externesZertOrNull
	) {
		switch (impffolge) {
		case ERSTE_IMPFUNG:
			ValidationUtil.validateStatusOneOf(registrierung, REGISTRIERT, FREIGEGEBEN, ODI_GEWAEHLT, GEBUCHT);

			// Normalfall -> kontrolliert 1
			registrierung.setRegistrierungStatus(IMPFUNG_1_KONTROLLIERT);
			break;
		case ZWEITE_IMPFUNG:
			ValidationUtil.validateStatus(registrierung, IMPFUNG_1_DURCHGEFUEHRT);

			validateKontrolle2HasErstImpfungVorhanden(registrierung);
			validateNichtAmGleichenTagSchonKontrolliert(registrierung);
			registrierung.setRegistrierungStatus(IMPFUNG_2_KONTROLLIERT);
			break;
		case BOOSTER_IMPFUNG:
			ValidationUtil.validateStatusOneOf(registrierung,
				GEBUCHT_BOOSTER, ODI_GEWAEHLT_BOOSTER,  // bei gebuchten Boosterimpfungen
				IMMUNISIERT, FREIGEGEBEN_BOOSTER, // bei ad hoc Impfungen
				ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG, ABGESCHLOSSEN);
			Objects.requireNonNull(impffolgeNr);

			boolean hasGrundimmunisierung =
				isGrundimmunisiertVacMeOrExternKontrolliert(registrierung, externesZertOrNull);

			if (!hasGrundimmunisierung) {
				throw AppValidationMessage.ILLEGAL_STATE.create(
					"Fuer Booster muss eine Grundimmunisierung vorhanden sein");
			}
			// Normalfall: vollstaendiger Impfschutz aus Vacme oder externem Zertifikat
			validateBoosterHasGrundimmunisierung(registrierung, externesZertOrNull);
			validateNichtAmGleichenTagSchonKontrolliert(registrierung);
			registrierung.setRegistrierungStatus(KONTROLLIERT_BOOSTER);
			break;
		default:
			throw new IllegalStateException("Unexpected value: " + impffolge);
		}

		kontrollTerminErmittelnUndUpdaten(
			fragebogen,
			impffolge,
			impffolgeNr,
			kontrolleTime,
			impfkontrolleJax,
			registrierung);
	}

	private void eventuellVorhandenenBoosterTerminFreigeben(@NonNull ImpfinformationDto infos) {
		if (infos.getImpfdossier() != null) {
			final List<Impfdossiereintrag> eintraege = infos.getImpfdossier().getImpfdossierEintraege();
			eintraege.forEach(impfterminRepo::boosterTerminFreigeben);
		}
	}

	private ImpfungkontrolleTermin kontrollTerminErmittelnUndUpdaten(
		@NotNull Fragebogen fragebogen,
		@NotNull Impffolge impffolge,
		@NotNull Integer impffolgeNr,
		@Nullable LocalDateTime kontrolleTime,
		@Nullable ImpfkontrolleJax impfkontrolleJax,
		@NotNull Registrierung registrierung
	) {
		ImpfinformationDto infos =
			impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());

		// KontrolleTermin nehmen, der im jax ist, oder ad hoc einen erstellen
		UUID originalDossiereintragIDOrNull =
			impfkontrolleJax != null ? impfkontrolleJax.getImpfdossierEintragId() : null;
		ValidationUtil.validateCurrentKontrolleHasNoImpfungYet(infos, impffolge, originalDossiereintragIDOrNull);
		ImpfungkontrolleTermin impfungkontrolleTermin =
			getOrCreateImpfkontrolleTermin(fragebogen, infos, impffolge, impffolgeNr,
				originalDossiereintragIDOrNull);

		// Werte uebertragen von Jax zu Fragebogen und KontrolleTermin
		Objects.requireNonNull(
			impfungkontrolleTermin,
			"ImpfungkontrolleTermin wurde nicht gefunden fuer DE" + originalDossiereintragIDOrNull);
		if (impfkontrolleJax != null) {
			impfkontrolleJax.apply(fragebogen, impfungkontrolleTermin);
		}

		// Timestamp Kontrolle
		LocalDateTime kontrolleTimeOrNow = kontrolleTime == null ? LocalDateTime.now() : kontrolleTime;
		impfungkontrolleTermin.setTimestampKontrolle(kontrolleTimeOrNow);

		// Speichern
		if (impffolge.equals(Impffolge.BOOSTER_IMPFUNG)) {
			// kann erst Impfschutz neu berechnen wenn die changes auch den fragebogen applied wurden
			boosterService.recalculateImpfschutzAndStatusmovesForSingleReg(registrierung);
		}
		fragebogenRepo.update(fragebogen);
		return impfungkontrolleTermin;
	}

	// Macht den Jump bei der Kontrolle wegen dem extenen Zertifikat (wenn das Zertifikat bei der Kontrolle nochmals
	// geaendert wird)
	private Impffolge preKontrolleJumpImpffolgeForExternesZertifikat(
		@NonNull Impffolge impffolge,
		@NonNull Registrierung registrierung,
		@Nullable ExternesZertifikat externesZertOrNull,
		@NonNull ImpfinformationDto infos
	) {
		switch (impffolge) {
		case ERSTE_IMPFUNG:
			ValidationUtil.validateStatusOneOf(registrierung, REGISTRIERT, FREIGEGEBEN, ODI_GEWAEHLT, GEBUCHT);
			if (externesZertifikatService.isExternesZertGrundimmunisiertUndKontrolliert(externesZertOrNull)) {
				// extern immunisiert -> direkt boostern
				registrierung.setStatusToImmunisiertWithExternZertifikat(Objects.requireNonNull(externesZertOrNull));
				// Eventuell noch vorhandene (nicht wahrgenommene) Termine 1 und 2 freigeben
				// Mit externem Zertifikat kann es keinen Termin 1 und 2 geben
				impfterminRepo.termine1Und2Freigeben(registrierung);

				return Impffolge.BOOSTER_IMPFUNG;
			}
			return Impffolge.ERSTE_IMPFUNG;
		case ZWEITE_IMPFUNG:
			ValidationUtil.validateStatus(registrierung, IMPFUNG_1_DURCHGEFUEHRT);
			if (externesZertifikatService.isExternesZertGrundimmunisiertUndKontrolliert(externesZertOrNull)) {
				// extern immunisiert -> direkt boostern
				registrierung.setStatusToImmunisiertWithExternZertifikat(Objects.requireNonNull(externesZertOrNull));
				// Eventuell noch vorhandene (nicht wahrgenommene) Termine 1 und 2 freigeben
				// Mit externem Zertifikat kann es keinen Termin 1 und 2 geben
				impfterminRepo.termine1Und2Freigeben(registrierung);

				return Impffolge.BOOSTER_IMPFUNG;
			}
			return Impffolge.ZWEITE_IMPFUNG;
		case BOOSTER_IMPFUNG:
			ValidationUtil.validateStatusOneOf(registrierung,
				GEBUCHT_BOOSTER, ODI_GEWAEHLT_BOOSTER,  // bei gebuchten Boosterimpfungen
				IMMUNISIERT, FREIGEGEBEN_BOOSTER, // bei ad hoc Impfungen
				ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG, ABGESCHLOSSEN);

			boolean hasGrundimmunisierung =
				isGrundimmunisiertVacMeOrExternKontrolliert(registrierung, externesZertOrNull);

			if (!hasGrundimmunisierung) {
				// nicht grundimmunisiert -> zurueck zu kontrolliert 1 springen, aber es darf noch keine
				// VacMe-Impfungen haben.
				if (impfinformationenService.hasVacmeImpfungen(registrierung)) {
					throw AppValidationMessage.EXISTING_VACME_IMPFUNGEN_CANNOT_REMOVE_GRUNDIMMUN.create();
				}

				registrierung.setStatusToNichtAbgeschlossenStatus(registrierungService.ermittleLetztenStatusVorKontrolle1(
					registrierung), null);
				// Einen eventuell vorhandenen Booster-Termin freigeben
				eventuellVorhandenenBoosterTerminFreigeben(infos);
				return Impffolge.ERSTE_IMPFUNG;
			}

			return Impffolge.BOOSTER_IMPFUNG;
		default:
			throw new IllegalStateException("Unexpected value: " + impffolge);
		}

	}

	@NonNull
	public ImpfungkontrolleTermin getOrCreateImpfkontrolleTermin(
		@NonNull Fragebogen fragebogen,
		@NonNull ImpfinformationDto impfinformationen,
		@NonNull Impffolge impffolge,
		@NonNull Integer impffolgeNr,
		@Nullable UUID dossiereintragID
	) {
		switch (impffolge) {
		case ERSTE_IMPFUNG:
			ImpfungkontrolleTermin kontrolleTermin1 = fragebogen.getPersonenkontrolle().getKontrolleTermin1();
			if (kontrolleTermin1 == null) {
				fragebogen.getPersonenkontrolle().setKontrolleTermin1(new ImpfungkontrolleTermin());
			}
			return fragebogen.getPersonenkontrolle().getKontrolleTermin1();

		case ZWEITE_IMPFUNG:
			ImpfungkontrolleTermin kontrolleTermin2 = fragebogen.getPersonenkontrolle().getKontrolleTermin2();
			if (kontrolleTermin2 == null) {
				fragebogen.getPersonenkontrolle().setKontrolleTermin2(new ImpfungkontrolleTermin());
			}
			return fragebogen.getPersonenkontrolle().getKontrolleTermin2();

		case BOOSTER_IMPFUNG:
			// Bestehenden Kontrolltermin per ID suchen, falls gegeben, und passe die ImpffolgeNr an
			Optional<ImpfungkontrolleTermin> existingKontrolleTerminOpt =
				getExistingBoosterKontrolleTerminAndUpdateImpffolgeNr(
					impfinformationen, impffolgeNr, dossiereintragID);

			if (existingKontrolleTerminOpt.isPresent()) {
				// KontrolleTermin existiert schon
				return existingKontrolleTerminOpt.get();
			} else {
				// Sonst: Eintrag mit der gewuenschten ImpffolgeNr suchen oder neu erstellen
				Impfdossiereintrag dossierEintragAltOrNeu =
					impfinformationenService.getOrCreateLatestImpfdossierEintrag(impfinformationen, impffolgeNr);
				// Der gefundene Eintrag hat in der Regel noch keinen Kontrolltermin haben, aber es kann trotzdem sein:
				// Wenn man Boosterkontrolle macht, das externeZertifikat entfernt, erneut die Kontrolle macht, das
				// externeZertifikat hinzufuegt
				if (dossierEintragAltOrNeu.getImpfungkontrolleTermin() != null) {
					return dossierEintragAltOrNeu.getImpfungkontrolleTermin();
				} else {
					// Normal: Neuen Kontrolltermin fuer den Eintrag erstelltn
					ImpfungkontrolleTermin termin = new ImpfungkontrolleTermin();
					dossierEintragAltOrNeu.setImpfungkontrolleTermin(termin);
					return termin;
				}
			}

		default:
			throw new IllegalStateException("Unexpected value: " + impffolge);
		}
	}

	// todo team: evtl splitten in 2 methoden?
	private Optional<ImpfungkontrolleTermin> getExistingBoosterKontrolleTerminAndUpdateImpffolgeNr(
		@NonNull ImpfinformationDto impfinformationen,
		@NonNull Integer impffolgeNr,
		@Nullable UUID eintragId) {
		if (eintragId != null) {
			Optional<Impfdossiereintrag> existingDossiereintragOptional =
				ImpfinformationenService.getImpfdossierEintragWithID(impfinformationen, eintragId);
			if (existingDossiereintragOptional.isPresent()) {
				Impfdossiereintrag eintrag = existingDossiereintragOptional.get();

				// ImpffolgeNr geaendert?
				if (!eintrag.getImpffolgeNr().equals(impffolgeNr)) {
					// ImpffolgeNr muss fuer diesen Eintrag geaendert werden - wenn moeglich! Andere Eintraege muss
					// man gegebenenfalls loeschen.
					changeImpffolgeNrAndDeleteOtherEintraege(impfinformationen, impffolgeNr, eintrag);
				}

				// Kontrolletermin
				if (eintrag.getImpfungkontrolleTermin() != null) {
					return Optional.of(eintrag.getImpfungkontrolleTermin());
				}
			}
		}
		return Optional.empty();
	}

	private void changeImpffolgeNrAndDeleteOtherEintraege(
		@NonNull ImpfinformationDto impfinformationen,
		@NonNull Integer impffolgeNr,
		@NonNull Impfdossiereintrag eintrag) {
		Impfdossier dossier = impfinformationen.getImpfdossier();
		Validate.notNull(dossier, "Impfdossier kann nicht null sein, wir haben ja soeben einen Eintrag daraus "
			+ "gelesen");

		// Die Impffolgenummer kann nur beim allerersten Booster angepasst werden wenn man ein externes Zertifikat hat
		// In diesem Fall entfernen wir alle Termine die potentiell sonst noch an anderen Eintraegen haengen und
		// entfernen die anderen
		// Eintraege.
		// Alle anderen Eintraege loeschen. Wenn Impfung dranhaengt: Fehler, wenn Termin dranhaengt: freigeben.
		Collection<Impfdossiereintrag> otherEintraege = dossier.getImpfdossierEintraege().stream()
			.filter(impfdossiereintrag -> !impfdossiereintrag.getId().equals(eintrag.getId()))
			.collect(Collectors.toList());
		for (Impfdossiereintrag otherEintrag : otherEintraege) {
			// Wenn Termin gebucht ist: freigeben (und Fehler werfen, wenn schon eine Impfung dran haengt)
			impfterminRepo.boosterTerminFreigeben(otherEintrag);
			// Eintrag loeschen
			impfdossierRepo.deleteEintrag(otherEintrag, impfinformationen.getImpfdossier());
		}

		// ImpffolgeNr updaten
		Integer originalImpffolge = eintrag.getImpffolgeNr();
		eintrag.setImpffolgeNr(impffolgeNr);
		LOG.info("ImpffolgeNr fuer Dossiereintrag geaendert von {} zu {} (Reg. {})", originalImpffolge, impffolgeNr,
			impfinformationen.getRegistrierung().getRegistrierungsnummer());
	}

	public void zweiteImpfungVerzichten(
		@NonNull ImpfinformationDto impfInfo,
		boolean vollstaendigerImpfschutz,
		@Nullable String begruendung,
		@Nullable LocalDate positivGetestetDatum
	) {
		Registrierung registrierung = impfInfo.getRegistrierung();
		ValidationUtil.validateStatusOneOf(registrierung, IMPFUNG_2_KONTROLLIERT, IMPFUNG_1_DURCHGEFUEHRT);
		registrierung.setStatusToAbgeschlossenOhneZweiteImpfung(
			impfInfo,
			vollstaendigerImpfschutz,
			begruendung,
			positivGetestetDatum);
		registrierungRepo.update(registrierung);
		// recreate Impfdokumentation
		dokumentService.deleteImpfdokumentationPdf(registrierung);
		dokumentService.createAndSaveImpfdokumentationWithoutBoosterImpfungenPdf(registrierung);
		// Den zweiten Termin loeschen
		impfterminRepo.termin2Freigeben(registrierung);
		// Freigabe Booster neu berechnen
		boosterService.recalculateImpfschutzAndStatusmovesForSingleReg(registrierung);
	}

	public void zweiteImpfungWahrnehmen(@NonNull ImpfinformationDto infos) {

		Registrierung registrierung = infos.getRegistrierung();

		if (infos.getImpfung2() != null) {
			throw new AppFailureException("Ein zweite Impfung ist schon für die registrierung vorhanden");
		}
		if (infos.getBoosterImpfungen() != null && !infos.getBoosterImpfungen().isEmpty()) {
			throw new AppFailureException("Es besteht bereits eine BoosterImpfung fuer diese Registrierung");
		}
		String reqMesg = "Erste Impfung muss existieren wenn man die 2.Impfung nach Verzicht doch noch wahrnehmen "
			+ "will";
		Objects.requireNonNull(registrierung.getImpftermin1(), reqMesg);
		final Impfung impfung1 = impfungRepo.getByImpftermin(registrierung.getImpftermin1()).orElse(null);
		Objects.requireNonNull(impfung1, reqMesg);

		if (registrierung.getTimestampArchiviert() != null) {
			pdfArchivierungService.deleteImpfungArchive(registrierung);
		}

		// Eventuell bereits vorhandene Zertifikate muessen storniert werden
		if (registrierung.abgeschlossenMitVollstaendigemImpfschutz()) {
			// Erst ab 14.0.0 koennen wir sicher sein, dass die Impfung verknuepft ist mit dem Zertifikat
			// Aber diese Funktion wird nur aufgerufen wenn nun doch noch ein 2. Termin wahrgenommen wird. Das heiss
			// wir koennen alle bisherigen Zertifkate revozieren
			LOG.info(
				"VACME-ZERTIFIKAT-REVOCATION: Zertifikat wird revoked fuer Registrierung {}, da die 2. Impfung "
					+ "noch wargenommen wird",
				registrierung.getRegistrierungsnummer());
			zertifikatService.queueForRevocation(registrierung);
		}
		registrierung.setStatusToNichtAbgeschlossenStatus(IMPFUNG_1_DURCHGEFUEHRT, impfung1);
		registrierungRepo.update(registrierung);

		dokumentService.deleteImpfdokumentationPdf(registrierung);
		dokumentService.createAndSaveImpfdokumentationWithoutBoosterImpfungenPdf(registrierung);
	}

	private void validateKontrolle2HasErstImpfungVorhanden(@NonNull Registrierung registrierung) {
		Objects.requireNonNull(
			registrierung.getImpftermin1(),
			"Bei der zweiten Kontrolle muss zwingend eine Impfung1 vorhanden sein");
	}

	private void validateBoosterHasGrundimmunisierung(
		@NonNull Registrierung registrierung,
		@Nullable ExternesZertifikat externesZertifikat) {
		if (registrierung.abgeschlossenMitVollstaendigemImpfschutz()) {
			return;
		}
		if (!externesZertifikatService.isExternesZertGrundimmunisiertUndKontrolliert(externesZertifikat)) {
			throw AppValidationMessage.MISSING_GRUNDIMMUNISIERUNG.create();
		}
	}

	private void validateNichtAmGleichenTagSchonKontrolliert(@NonNull Registrierung registrierung) {
		if (validateSameDayKontrolle) {
			if (registrierung.getImpftermin1() != null) {
				// impfung koennte exsitieren, muss aber nicht (wenn zwar ein Ersttermin existiert, aber bei der
				// Kontrolle ein externes Zertifikat
				// hinzugefuegt wurde)
				final Optional<Impfung> impfung1Opt = impfungRepo.getByImpftermin(registrierung.getImpftermin1());
				impfung1Opt.ifPresent(ValidationUtil::validateSecondKontrolleOnSameDay);
			}
		}

	}

	/**
	 * Updates both {@link ImpfkontrolleTerminJax} on the {@link ImpfkontrolleJax}
	 *
	 * @param fragebogen no-doc
	 * @param impfkontrolleJax no-doc
	 */
	public ImpfungkontrolleTermin updateImpfkontrolleTermine(
		@NonNull Fragebogen fragebogen,
		@NonNull ImpfkontrolleJax impfkontrolleJax,
		@NonNull Impffolge impffolge
	) {
		Objects.requireNonNull(impfkontrolleJax.getExternGeimpft(), "ExternesZertifikat soll vom client immer" +
			" uebermittelt werden. Felder koennen aber null sein");
		Registrierung registrierung = fragebogen.getRegistrierung();

		ExternesZertifikat externesZertifikatOrNull = externesZertifikatService.createUpdateOrRemoveExternGeimpft(
			registrierung, impfkontrolleJax.getExternGeimpft(), true);

		// Infos erst nach dem Erstellen der externen Zertifikat laden, dann hat es das externe Zertifikat schon drin
		ImpfinformationDto infos =
			impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
		int currentKontrolleNr = ImpfinformationenService.getCurrentKontrolleNr(infos);

		Impffolge impffolgeEffektiv =
			preKontrolleJumpImpffolgeForExternesZertifikat(impffolge, registrierung, externesZertifikatOrNull, infos);
		return kontrollTerminErmittelnUndUpdaten(
			fragebogen,
			impffolgeEffektiv,
			currentKontrolleNr,
			null,
			impfkontrolleJax,
			registrierung);
		// hier machen wir nicht das Statusupdate auf Kontrolle 1/2/Booster

	}

	private boolean isGrundimmunisiertVacMeOrExternKontrolliert(
		@NonNull @NotNull Registrierung registrierung,
		@Nullable ExternesZertifikat externesZertOrNull) {

		// vollstaendig dank dem soeben bearbeiteten externesZertifikat?
		if (externesZertifikatService.isExternesZertGrundimmunisiertUndKontrolliert(externesZertOrNull)) {
			return true;
		}

		// vollstaendig dank Vacme, d.h. schon vor dieser Kontrolle? Achtung hier duerfen keine untrusted values zu
		// true fuehren
		// (e.g VOLLSTAENDIG_EXTERNESZERTIFIKAT geht nicht weil das vom Impfwilligen direkt gesettz wird)
		var impfschutzTyp = registrierung.getVollstaendigerImpfschutzTyp();
		return hasVacmeGrundimmunisierung(externesZertOrNull, impfschutzTyp);

	}

	/**
	 * @return true wenn der Impfschutz durch eine Impfung in Vacme erreicht wurde
	 */
	private boolean hasVacmeGrundimmunisierung(
		@Nullable ExternesZertifikat externesZertOrNull,
		@Nullable VollstaendigerImpfschutzTyp impfschutzTyp
	) {
		if (impfschutzTyp != null) {
			switch (impfschutzTyp) {
			case VOLLSTAENDIG_VACME:
			case VOLLSTAENDIG_VACME_GENESEN:
				return true;
			case VOLLSTAENDIG_EXT_PLUS_VACME:
			case VOLLSTAENDIG_EXT_GENESEN_PLUS_VACME:
				Validate.notNull(
					externesZertOrNull,
					"VollstaendigerImpfschutzTyp " + impfschutzTyp + ", aber das ExterneZertifikat ist null");
				return externesZertOrNull.isKontrolliert();
			default:
				// Achtung hier darf VOLLSTAENDIG_EXTERNESZERTIFIKAT nicht drin sein weil wir nur explizit in vacme
				// gemachte Impfungen anschauen
				return false;
			}
		}
		return false;
	}
}
