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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.impfen.Erkrankung;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossier;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.Impfschutz;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFileTyp;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impfslot;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import ch.dvbern.oss.vacme.jax.registration.ErkrankungJax;
import ch.dvbern.oss.vacme.repo.ImpfdossierRepo;
import ch.dvbern.oss.vacme.repo.ImpfslotRepo;
import ch.dvbern.oss.vacme.repo.ImpfterminRepo;
import ch.dvbern.oss.vacme.repo.OrtDerImpfungRepo;
import ch.dvbern.oss.vacme.service.booster.BoosterService;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.util.EnumUtil;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.FREIGEGEBEN;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.FREIGEGEBEN_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.GEBUCHT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.GEBUCHT_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMMUNISIERT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_1_KONTROLLIERT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_2_KONTROLLIERT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.KONTROLLIERT_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ODI_GEWAEHLT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ODI_GEWAEHLT_BOOSTER;

@ApplicationScoped
@Transactional
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DossierService {

	private final ImpfterminRepo impfterminRepo;
	private final ImpfslotRepo impfslotRepo;
	private final OrtDerImpfungRepo ortDerImpfungRepo;
	private final DokumentService dokumentService;
	private final ConfirmationService confirmationService;
	private final RegistrierungService registrierungService;
	private final SettingsService settingsService;
	private final ImpfinformationenService impfinformationenService;
	private final ImpfdossierRepo impfdossierRepo;
	private final BoosterService boosterService;
	private final TerminbuchungService terminbuchungService;

	public void termineBuchenGrundimmunisierung(
		@NonNull Registrierung registrierung,
		@NonNull ID<Impfslot> slotId1,
		@NonNull ID<Impfslot> slotId2
	) {
		final Impfslot slot1 = impfslotRepo
			.getById(slotId1)
			.orElseThrow(() -> AppValidationMessage.UNKNOWN_IMPFTERMIN.create(slotId1));
		final Impfslot slot2 = impfslotRepo
			.getById(slotId2)
			.orElseThrow(() -> AppValidationMessage.UNKNOWN_IMPFTERMIN.create(slotId2));

		// Status muss FREIGEGEBEN sein
		ValidationUtil.validateStatus(registrierung, FREIGEGEBEN);
		// Es ist nicht mehr noetig, dass beide Termine zum gleichen Odi gehoeren
		// Sicherstellen, dass genuegend Abstand zwischen den beiden Terminen
		final int minimumDaysBetweenImpfungen = this.settingsService.getSettings().getDistanceImpfungenMinimal();
		final int maximumDaysBetweenImpfungen = this.settingsService.getSettings().getDistanceImpfungenMaximal();
		ValidationUtil.validateDaysBetweenImpfungen(slot1.getZeitfenster(), slot2.getZeitfenster(), minimumDaysBetweenImpfungen, maximumDaysBetweenImpfungen);
		// Freien Termin fuer diesen Slot suchen
		final Impftermin termin1 = impfterminRepo.findMeinenReserviertenOrFreienImpftermin(registrierung, slot1, Impffolge.ERSTE_IMPFUNG);
		final Impftermin termin2 = impfterminRepo.findMeinenReserviertenOrFreienImpftermin(registrierung, slot2, Impffolge.ZWEITE_IMPFUNG);
		// locking sollte nicht noetig sein wegen unique constraint
		if (termin1 == null) {
			throw AppValidationMessage.IMPFTERMIN_BESETZT.create(slot1.toDateMessage());
		}
		if (termin2 == null) {
			throw AppValidationMessage.IMPFTERMIN_BESETZT.create(slot2.toDateMessage());
		}

		// Status setzen & speichern
		registrierung.setRegistrierungStatus(GEBUCHT);
		//Wir setzen den gewunschten OdI aus dem slot 1 oder 2 je nach dem ob die 1. Impfung schon durch ist
		if (!RegistrierungStatus.isErsteImpfungDoneAndZweitePending().contains(registrierung.getRegistrierungStatus())) {
			registrierung.setGewuenschterOdi(slot1.getOrtDerImpfung());
		} else {
			registrierung.setGewuenschterOdi(slot2.getOrtDerImpfung());
		}
		registrierung.setNichtVerwalteterOdiSelected(false);

		// Termine anhaengen und auf besetzt setzen
		impfterminRepo.termineSpeichern(registrierung, termin1, termin2);

		// Terminbestaetigung per SMS senden
		confirmationService.sendConfirmationNoBoosterTermin(RegistrierungFileTyp.TERMIN_BESTAETIGUNG, registrierung); // nur impfung 1/2
	}

	public void termineBuchenBooster(
		@NonNull Registrierung registrierung,
		@NonNull ID<Impfslot> slotIdN
	) {
		final Impfslot slotN = impfslotRepo
			.getById(slotIdN)
			.orElseThrow(() -> AppValidationMessage.UNKNOWN_IMPFTERMIN.create(slotIdN));

		if (!registrierung.isSelbstzahler()) {
			// Status muss FREIGEGEBEN_BOOSTER sein
			ValidationUtil.validateStatus(registrierung, FREIGEGEBEN_BOOSTER);
		} else {
			ValidationUtil.validateStatus(registrierung, IMMUNISIERT);
		}

		// Freien Termin fuer diesen Slot suchen
		final Impftermin terminN = impfterminRepo.findMeinenReserviertenOrFreienImpftermin(registrierung, slotN, Impffolge.BOOSTER_IMPFUNG);
		// locking sollte nicht noetig sein wegen unique constraint
		if (terminN == null) {
			throw AppValidationMessage.IMPFTERMIN_BESETZT.create(slotN.toDateMessage());
		}

		// Status setzen & speichern
		registrierung.setRegistrierungStatus(GEBUCHT_BOOSTER);
		registrierung.setGewuenschterOdi(slotN.getOrtDerImpfung());
		registrierung.setNichtVerwalteterOdiSelected(false);

		// Termine anhaengen und auf besetzt setzen
		final ImpfinformationDto impfinfoDTO = impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
		Integer impffolgeNr = ImpfinformationenService.getNumberOfImpfung(impfinfoDTO) + 1;
		final Impfdossiereintrag dossierEintrag = impfinformationenService.getOrCreateLatestImpfdossierEintrag(impfinfoDTO, impffolgeNr);
		impfterminRepo.boosterTerminSpeichern(registrierung, dossierEintrag, terminN);

		// Terminbestaetigung per SMS senden
		confirmationService.sendConfirmation(RegistrierungFileTyp.TERMIN_BESTAETIGUNG, registrierung, terminN);
	}

	/**
	 * Resets the {@link Registrierung#gewuenschterOdi} and cancels booked termine if there are any
	 *
	 * @param registrierung no-doc
	 */
	public void odiAndTermineAbsagen(@NonNull Registrierung registrierung) {
		/*
			Cancel ODI and or all appointments if an ODI has been selected, the appointments have been
			booked or the first control has been made.
			After these states at least one vaccination has been made and therefore we can no longer
			cancel all appointments.
		 */
		ValidationUtil.validateStatusOneOf(registrierung, FREIGEGEBEN, ODI_GEWAEHLT, GEBUCHT, IMPFUNG_1_KONTROLLIERT,
			IMMUNISIERT, FREIGEGEBEN_BOOSTER, ODI_GEWAEHLT_BOOSTER, GEBUCHT_BOOSTER, KONTROLLIERT_BOOSTER);

		// Flags zuerst zuruecksetzen, da aufgrund dessen der letzte Stand vor Kontrolle ermittelt wird
		registrierung.setGewuenschterOdi(null);
		registrierung.setNichtVerwalteterOdiSelected(false);

		if (RegistrierungStatus.getAnyStatusOfGrundimmunisiert().contains(registrierung.getRegistrierungStatus())) {
			ImpfinformationDto impfinfoDTO =
				impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
			// Unabhaengig davon, ob das ODI aktuell ueber eine Terminverwaltung verfuegt: Falls Termine vorhanden
			// sind, diese loeschen, ansonsten nur gewuenschtesODI entfernen
			final Optional<Impfdossiereintrag> pendingDossiereintrag =
				ImpfinformationenService.getPendingDossiereintrag(impfinfoDTO);
			if (pendingDossiereintrag.isPresent()) {
				final Impfdossiereintrag eintrag = pendingDossiereintrag.get();
				impfterminRepo.boosterTerminFreigeben(eintrag);
				resetBoosterStatusToStatusBeforeBuchung(registrierung, eintrag.getImpfdossier(), eintrag);
			} else {
				resetBoosterStatusToStatusBeforeBuchung(registrierung, impfinfoDTO.getImpfdossier(), null);
			}
		} else {
			impfterminRepo.termine1Und2Freigeben(registrierung);
			// Wenn nach der Kontrolle der Termin abgesagt wird, wollen wir im Status Kontrolliert bleiben, damit man
			// ad-hoc impfen kann!
			if (EnumUtil.isNoneOf(registrierung.getRegistrierungStatus(), IMPFUNG_1_KONTROLLIERT, IMPFUNG_2_KONTROLLIERT)) {
				registrierung.setRegistrierungStatus(registrierungService.ermittleLetztenStatusVorKontrolle1(registrierung));
			}
		}

		dokumentService.deleteTerminbestaetigung(registrierung);
	}

	private void resetBoosterStatusToStatusBeforeBuchung(
		@NonNull Registrierung registrierung,
		@Nullable Impfdossier dossier,
		@Nullable Impfdossiereintrag eintrag
	) {
		// Wenn nach der Kontrolle der Termin abgesagt wird, wollen wir im Status Kontrolliert bleiben, damit man
		// ad-hoc impfen kann!
		if (EnumUtil.isNoneOf(registrierung.getRegistrierungStatus(), KONTROLLIERT_BOOSTER)) {
			registrierung.setRegistrierungStatus(registrierungService.ermittleLetztenStatusVorKontrolleBooster(
				registrierung, dossier, eintrag));
		}
	}

	public void selectOrtDerImpfung(
		@NonNull Registrierung registrierung,
		@NonNull ID<OrtDerImpfung> ortDerImpfungID
	) {
		if (!registrierung.isSelbstzahler()) {
			// Status muss FREIGEGEBEN oder FREIGEGEBEN_BOOSTER sein
			ValidationUtil.validateStatusOneOf(registrierung, FREIGEGEBEN, FREIGEGEBEN_BOOSTER);
		} else {
			ValidationUtil.validateStatusOneOf(registrierung, IMMUNISIERT);
		}

		final OrtDerImpfung gewuenschterODI = ortDerImpfungRepo
			.getById(ortDerImpfungID)
			.orElseThrow(() -> AppValidationMessage.UNKNOWN_ODI.create(ortDerImpfungID));

		registrierung.setGewuenschterOdi(gewuenschterODI);
		registrierung.setNichtVerwalteterOdiSelected(false);

		// Falls dieser ODI kein Mobiler ODI ist, scheint der Impfling neu mobil zu sein
		if (!gewuenschterODI.isMobilerOrtDerImpfung()) {
			registrierung.setImmobil(false);
		}

		switch (registrierung.getRegistrierungStatus()) {
		case FREIGEGEBEN:
			registrierung.setRegistrierungStatus(ODI_GEWAEHLT);
			break;
		case FREIGEGEBEN_BOOSTER:
		case IMMUNISIERT:
			registrierung.setRegistrierungStatus(ODI_GEWAEHLT_BOOSTER);
			break;
		default:
			throw AppValidationMessage.REGISTRIERUNG_WRONG_STATUS.create(registrierung.getRegistrierungsnummer(), FREIGEGEBEN, FREIGEGEBEN_BOOSTER);
		}

		// Terminbestaetigung per SMS bzw. Post senden
		// Wir koennen sendConfirmationNoBoosterTermin verwenden, weil fuer ODI-GEWAEHLT kein Termin notwendig ist
		// sondern nur das gewuenschteOdi, welches fuer Normal und Booster das selbe Attribut ist
		confirmationService.sendConfirmationNoBoosterTermin(RegistrierungFileTyp.TERMIN_BESTAETIGUNG, registrierung);
	}

	public void selectNichtVerwalteterOrtDerImpfung(
		@NonNull Registrierung registrierung
	) {
		if (!registrierung.isSelbstzahler()) {
			// Status muss FREIGEGEBEN oder FREIGEGEBEN_BOOSTER sein
			ValidationUtil.validateStatusOneOf(registrierung, FREIGEGEBEN, FREIGEGEBEN_BOOSTER);
		} else {
			ValidationUtil.validateStatusOneOf(registrierung, IMMUNISIERT);
		}

		registrierung.setNichtVerwalteterOdiSelected(true);
		registrierung.setGewuenschterOdi(null);

		// Der Impfling hat eine nicht aufgefuehrte Arztpraxis gewaehlt, scheint also mobil zu sein
		registrierung.setImmobil(false);

		// Terminbestaetigung per SMS bzw. Post senden
		// Wir koennen sendConfirmationNoBoosterTermin verwenden, weil fuer ODI-GEWAEHLT kein Termin notwendig ist
		// sondern nur das gewuenschteOdi, welches fuer Normal und Booster das selbe Attribut ist
		confirmationService.sendConfirmationNoBoosterTermin(RegistrierungFileTyp.TERMIN_BESTAETIGUNG, registrierung);
	}

	public void updateErkrankungen(@NonNull Registrierung registrierung, @NonNull List<ErkrankungJax> erkrankungJaxList) {
		List<Erkrankung> erkrankungen = erkrankungJaxList.stream().map(ErkrankungJax::toEntity).collect(Collectors.toList());
		impfdossierRepo.updateErkrankungen(impfdossierRepo.getOrCreateImpfdossier(registrierung), erkrankungen);
		boosterService.recalculateImpfschutzAndStatusmovesForSingleReg(registrierung);
		freigabestatusUndTermineEntziehenFallsImpfschutzNochNichtFreigegeben(registrierung);
	}

	public void freigabestatusUndTermineEntziehenFallsImpfschutzNochNichtFreigegeben(@NonNull Registrierung registrierung) {
		ImpfinformationDto infosNeu = impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
		Impfschutz impfschutzOrNullNeu = infosNeu.getImpfdossier() != null ? infosNeu.getImpfdossier().getImpfschutz() : null;

		// neu: nicht (mehr) freigegeben. Falls wir bisher freigegeben waren, muessen wir die Freigabe entziehen und Termine absagen
		boolean neuNichtFreigegeben = impfschutzOrNullNeu == null || impfschutzOrNullNeu.getFreigegebenNaechsteImpfungAb() == null || impfschutzOrNullNeu.getFreigegebenNaechsteImpfungAb().isAfter(LocalDateTime.now());

		if (neuNichtFreigegeben) {
			// Termine freigeben / ODI-Wahl loeschen
			switch (registrierung.getRegistrierungStatus()) {
			case GEBUCHT_BOOSTER:
				terminbuchungService.boosterTerminAbsagen(registrierung);
				registrierung.setGewuenschterOdi(null);
				break;
			case ODI_GEWAEHLT_BOOSTER:
				registrierung.setGewuenschterOdi(null);
				break;
			default:
				// nichts tun
			}

			// Selbstzahler zurücksetzen
			registrierung.setSelbstzahler(false);

			// Nicht-verwalteter ODI zuruecksetzen
			registrierung.setNichtVerwalteterOdiSelected(false);

			// Freigabe-Status entziehen
			switch (registrierung.getRegistrierungStatus()) {
			case FREIGEGEBEN_BOOSTER:
			case GEBUCHT_BOOSTER:
			case ODI_GEWAEHLT_BOOSTER:
				registrierung.setRegistrierungStatus(IMMUNISIERT);
				break;
			default:
				// nichts tun
			}
		}
	}
}
