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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.PersistenceException;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.embeddables.DateTimeRange;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.registration.AbgesagteTermine;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impfslot;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import ch.dvbern.oss.vacme.entities.types.daterange.DateUtil;
import ch.dvbern.oss.vacme.repo.ImpfdossierRepo;
import ch.dvbern.oss.vacme.repo.ImpfterminRepo;
import ch.dvbern.oss.vacme.repo.RegistrierungRepo;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.util.EnumUtil;
import ch.dvbern.oss.vacme.util.ValidationUtil;
import io.quarkus.runtime.util.ExceptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hibernate.StaleObjectStateException;

import static ch.dvbern.oss.vacme.entities.registration.RegistrierungFileTyp.TERMIN_BESTAETIGUNG;
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
public class TerminbuchungService {

	private final ImpfterminRepo impfterminRepo;
	private final ImpfslotService impfslotService;
	private final DokumentService dokumentService;
	private final ConfirmationService confirmationService;
	private final RegistrierungRepo registrierungRepo;
	private final ImpfinformationenService impfinformationenService;
	private final ImpfdossierRepo impfdossierRepo;

	public void createAdHocTermin1AndBucheTermin2(
		@NonNull Registrierung registrierung,
		@NonNull OrtDerImpfung ortDerImpfung,
		@NonNull ID<Impfslot> slotTermin2
	) {
		// Status pruefen
		ValidationUtil.validateStatusOneOf(registrierung, REGISTRIERT, FREIGEGEBEN, ODI_GEWAEHLT, GEBUCHT, IMPFUNG_1_KONTROLLIERT);

		if (registrierung.getImpftermin1() != null || registrierung.getImpftermin2() != null) {
			// Wenn schon Termine da sind, werden diese freigegeben
			impfterminRepo.termine1Und2Freigeben(registrierung);
		}

		// Ad-Hoc Termin fuer Termin 1 erstellen
		final Impftermin termin1 = createOnDemandImpftermin(Impffolge.ERSTE_IMPFUNG, ortDerImpfung, LocalDateTime.now());

		// Freien Termin fuer diesen Slot 2 suchen
		final Impfslot slot2 = impfslotService.getById(slotTermin2);
		final Impftermin termin2 = impfterminRepo.findMeinenReserviertenOrFreienImpftermin(registrierung, slot2, Impffolge.ZWEITE_IMPFUNG);
		if (termin2 == null) {
			throw AppValidationMessage.IMPFTERMIN_BESETZT.create(slot2.toDateMessage());
		}

		// Der Abstand zwischen den Terminen muss hier nicht geprueft werden

		// Es ist nicht mehr noetig, dass beide Termine zum gleichen Odi gehoeren

		// Termine buchen und auf belegt setzen
		impfterminRepo.termineSpeichern(registrierung, termin1, termin2);

		// Der Status muss jetzt falls noetig auf "GEBUCHT" gesetzt werden
		if (EnumUtil.isOneOf(registrierung.getRegistrierungStatus(), REGISTRIERT, FREIGEGEBEN, ODI_GEWAEHLT)) {
			registrierung.setRegistrierungStatus(GEBUCHT);
		}

		// Terminbestaetigung loeschen und neu erstellen
		dokumentService.deleteTerminbestaetigung(registrierung);
		confirmationService.sendConfirmationNoBoosterTermin(TERMIN_BESTAETIGUNG, registrierung); // nur fuer Impfung 1/2

		registrierung.setGewuenschterOdi(termin1.getImpfslot().getOrtDerImpfung());
		registrierung.setNichtVerwalteterOdiSelected(false);
	}

	public void reservieren(
		@NonNull Registrierung registrierung,
		@NonNull ID<Impfslot> slotId,
		@NonNull Impffolge impffolge
	) {
		// Wir machen hier keine Validierungen, sondern loeschen nur die bisherigen Reservationen und setzen die Neuen
		// Pro Reservation darf es immer nur 1 Reservation pro Impffolge geben.

		final Impfslot slot = impfslotService.getById(slotId);
		Impftermin termin = impfterminRepo.findFreienImpftermin(slot, impffolge);
		if (termin == null) {
			throw AppValidationMessage.IMPFTERMIN_BESETZT.create(slot.toDateMessage());
		}

		try {
			impfterminRepo.terminReservieren(registrierung, termin);
		} catch (PersistenceException e) {
			Throwable rootCause = ExceptionUtil.getRootCause(e);
			if (rootCause != null) {
				if (rootCause.getClass().equals(StaleObjectStateException.class)) {
					// we catch this exception because it may happen if two user are reserving the same termin
					// but we want to show a nicer error
					throw AppValidationMessage.IMPFTERMIN_BESETZT.create(slot.toDateMessage());
				}
			}
			throw e;
		}
	}

	public void umbuchenGrundimmunisierung(
		@NonNull Registrierung registrierung,
		@NonNull ID<Impfslot> slotId1,
		@NonNull ID<Impfslot> slotId2
	) {
		// Status
		ValidationUtil.validateStatusOneOf(
			registrierung,
			REGISTRIERT, // NEU: in Fachapp darf man auch nicht freigegebene direkt "umbuchen"
			FREIGEGEBEN, ODI_GEWAEHLT, GEBUCHT, IMPFUNG_1_KONTROLLIERT, IMPFUNG_1_DURCHGEFUEHRT, IMPFUNG_2_KONTROLLIERT);

		final Impftermin termin1Bisher = registrierung.getImpftermin1();
		final Impftermin termin2Bisher = registrierung.getImpftermin2();

		// Falls die erste Impfung schon erfolgt ist, darf der erste Termin nicht mehr verschoben werden
		if (EnumUtil.isOneOf(registrierung.getRegistrierungStatus(), IMPFUNG_1_DURCHGEFUEHRT, IMPFUNG_2_KONTROLLIERT)) {
			Objects.requireNonNull(termin1Bisher, "Nach erfolgter Impfung 1 muss ein Termin 1 bestehen");
			if (!termin1Bisher.getImpfslot().getId().equals(slotId1.getId())) {
				throw AppValidationMessage.IMPFTERMIN_SCHON_WAHRGENOMMEN.create(registrierung.getRegistrierungsnummer());
			}
		}

		// Die neuen (oder alten, falls nicht geaendert) Termine ermitteln
		Impftermin termin1 = getTerminOrFindReserviertenTermin(registrierung, termin1Bisher, slotId1, Impffolge.ERSTE_IMPFUNG);
		Impftermin termin2 = getTerminOrFindReserviertenTermin(registrierung, termin2Bisher, slotId2, Impffolge.ZWEITE_IMPFUNG);

		// Die alten (wenn noetig) freigeben, die neuen buchen
		final boolean termin1Changed = termin1Bisher == null || !termin1Bisher.getId().equals(termin1.getId());
		if (termin1Changed) {
			impfterminRepo.termin1Freigeben(registrierung);
			impfterminRepo.termin1Speichern(registrierung, termin1);
		}
		final boolean termin2Changed = termin2Bisher == null || !termin2Bisher.getId().equals(termin2.getId());
		if (termin2Changed) {
			impfterminRepo.termin2Freigeben(registrierung);
			impfterminRepo.termin2Speichern(registrierung, termin2);
		}

		// Es ist nicht mehr noetig, dass beide Termine zum gleichen Odi gehoeren

		// Die Abstaende zwischen den Terminen muessen nicht mehr validiert werden beim umbuchen

		// Wir validieren dass der 1. Termin vor dem 2. Ist da wir ja den Abstand nicht validieren
		ValidationUtil.validateFirstTerminBeforeSecond(termin1, termin2);

		// Der Status muss jetzt mindestens "GEBUCHT" sein
		if (EnumUtil.isOneOf(registrierung.getRegistrierungStatus(), REGISTRIERT, FREIGEGEBEN, ODI_GEWAEHLT)) {
			registrierung.setRegistrierungStatus(GEBUCHT);
		}

		// Terminbestaetigung loeschen und neu erstellen
		terminbestaetigungErneutSenden(registrierung, null); // nur fuer Impfung 1/2

		if (RegistrierungStatus.isErsteImpfungDoneAndZweitePending().contains(registrierung.getRegistrierungStatus())) {
			registrierung.setGewuenschterOdi(termin2.getImpfslot().getOrtDerImpfung());
		} else {
			registrierung.setGewuenschterOdi(termin1.getImpfslot().getOrtDerImpfung());
		}
		registrierung.setNichtVerwalteterOdiSelected(false); // nun ist ein konkretes ODI bekannt
	}

	public void umbuchenBooster(
		@NonNull Registrierung registrierung,
		@NonNull ID<Impfslot> slotIdN
	) {
		// Status
		ValidationUtil.validateStatusOneOf(
			registrierung,
			IMMUNISIERT, // neu: in Fachapp umbuchen auch moeglich, wenn noch nicht freigegeben_booster
			FREIGEGEBEN_BOOSTER, ODI_GEWAEHLT_BOOSTER, GEBUCHT_BOOSTER, KONTROLLIERT_BOOSTER);

		final ImpfinformationDto impfinfoDTO = impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());

		Integer impffolgeNr = ImpfinformationenService.getNumberOfImpfung(impfinfoDTO) + 1;
		// Dossiereintrag: gegebenenfalls neu erstellen noetig (szenario: cc erstellt reg mit externGeimpft. Kontrolle ohne Termin oeffnen, Termin umbuchen
		Impfdossiereintrag dossierEintrag = impfinformationenService.getOrCreateLatestImpfdossierEintrag(impfinfoDTO, impffolgeNr);

		final Impftermin terminNBisher = dossierEintrag.getImpftermin();

		// Die neuen (oder alten, falls nicht geaendert) Termine ermitteln
		Impftermin terminN = getTerminOrFindReserviertenTermin(registrierung, terminNBisher, slotIdN, Impffolge.BOOSTER_IMPFUNG);

		// Die alten (wenn noetig) freigeben, die neuen buchen
		final boolean terminNChanged = terminNBisher == null || !terminNBisher.getId().equals(terminN.getId());
		if (terminNChanged) {
			impfterminRepo.boosterTerminFreigeben(dossierEintrag);
			impfterminRepo.boosterTerminSpeichern(registrierung, dossierEintrag, terminN);
		}

		// Der Status muss jetzt mindestens "GEBUCHT" sein (KONTROLLIERT_BOOSTER muss man behalten)
		if (EnumUtil.isOneOf(registrierung.getRegistrierungStatus(), IMMUNISIERT, FREIGEGEBEN_BOOSTER, ODI_GEWAEHLT_BOOSTER)) {
			registrierung.setRegistrierungStatus(GEBUCHT_BOOSTER);
		}

		// Terminbestaetigung loeschen und neu erstellen
		terminbestaetigungErneutSenden(registrierung, terminN); // wichtig, TerminN mitgeben

		registrierung.setGewuenschterOdi(terminN.getImpfslot().getOrtDerImpfung());
	}

	public void boosterTerminAbsagen(
		@NonNull Registrierung registrierung
	) {
		// Status
		ValidationUtil.validateStatusOneOf(
			registrierung,
			GEBUCHT_BOOSTER);
		final ImpfinformationDto impfinfoDTO = impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
		Integer impffolgeNr = ImpfinformationenService.getNumberOfImpfung(impfinfoDTO) + 1;
		Impfdossiereintrag dossierEintrag = impfinformationenService.getOrCreateLatestImpfdossierEintrag(impfinfoDTO, impffolgeNr);
		impfterminRepo.boosterTerminFreigeben(dossierEintrag);
		registrierung.setRegistrierungStatus(FREIGEGEBEN_BOOSTER);
	}

	@NonNull
	private Impftermin getTerminOrFindReserviertenTermin(@NonNull Registrierung registrierung, @Nullable Impftermin terminBisher,
		@NonNull ID<Impfslot> slotIdGewuenscht, @NonNull Impffolge impffolge) {
		boolean terminNeu = terminBisher == null;
		boolean terminUmbuchen = !terminNeu && !terminBisher.getImpfslot().getId().equals(slotIdGewuenscht.getId());
		if (terminNeu || terminUmbuchen) {
			final Impfslot slot = impfslotService.getById(slotIdGewuenscht);
			// Reservierten/Freien Termin fuer diesen Slot suchen
			Impftermin termin = impfterminRepo.findMeinenReserviertenOrFreienImpftermin(registrierung, slot, impffolge);
			// locking sollte nicht noetig sein wegen unique constraint
			if (termin == null) {
				throw AppValidationMessage.IMPFTERMIN_BESETZT.create(slot.toDateMessage());
			}
			return termin;
		} else {
			// Termin hat nicht geaendert
			return terminBisher;
		}
	}

	@NonNull
	public Impftermin createOnDemandImpftermin(
		@NonNull Impffolge impffolge,
		@NonNull OrtDerImpfung ortDerImpfung,
		@NonNull LocalDateTime desiredTime
	) {
		Impfslot currentSlot = impfslotService.findImpfslotForTime(ortDerImpfung, desiredTime);
		if (currentSlot == null) {
			// Wir erstellen einen Impfslot fuer genau diesen moment
			currentSlot = createOnDemandImpfslot(ortDerImpfung, desiredTime);
		}
		final Impftermin freierTermin = impfterminRepo.findFreienImpftermin(currentSlot, impffolge);
		if (freierTermin != null) {
			return freierTermin;
		}
		Impftermin onDemandTermin = new Impftermin();
		onDemandTermin.setImpffolge(impffolge);
		onDemandTermin.setImpfslot(currentSlot);
		// Da wir einen neuen Termin in einem evtl. bereits bestehenden (oder mit Kapazitaet 0 erstellten)
		// Impfslot erstellt haben, muss die Kapazitaet des Slots entsprechend erhoeht werden
		switch (impffolge) {
		case ERSTE_IMPFUNG:
			currentSlot.setKapazitaetErsteImpfung(currentSlot.getKapazitaetErsteImpfung() + 1);
			break;
		case ZWEITE_IMPFUNG:
			currentSlot.setKapazitaetZweiteImpfung(currentSlot.getKapazitaetZweiteImpfung() + 1);
			break;
		case BOOSTER_IMPFUNG:
			currentSlot.setKapazitaetBoosterImpfung(currentSlot.getKapazitaetBoosterImpfung() + 1);
			break;
		}
		impfterminRepo.create(onDemandTermin);
		return onDemandTermin;
	}

	@NonNull
	private Impfslot createOnDemandImpfslot(@NonNull OrtDerImpfung ortDerImpfung, @NonNull LocalDateTime desiredTime) {
		Impfslot onDemandImpfslot = Impfslot.of(ortDerImpfung,
			DateTimeRange.of(DateUtil.getLastHalfHour(desiredTime),
				DateUtil.getNextHalfHour(desiredTime)));
		return impfslotService.create(onDemandImpfslot);
	}

	public void terminbestaetigungErneutSenden(@NonNull Registrierung registrierung, @Nullable Impftermin impfterminN) {
		confirmationService.resendConfirmation(TERMIN_BESTAETIGUNG, registrierung, impfterminN);
	}

	@Transactional(TxType.REQUIRES_NEW)
	public void terminAbsagenForOdiAndDatum(@NonNull Impftermin impftermin) {
		try {
			Impftermin termin = impfterminRepo.getById(impftermin.toId())
				.orElseThrow(() -> AppValidationMessage.ILLEGAL_STATE.create("Reg fuer Termin nicht gefunden " + impftermin.getId()));

			Registrierung registrierung = null;
			if (impftermin.getImpffolge() == Impffolge.BOOSTER_IMPFUNG) {
				registrierung = registrierungRepo.getRegistrierungForBoosterImpftermin(termin)
					.orElseThrow(() -> AppValidationMessage.ILLEGAL_STATE.create("Reg fuer Termin nicht gefunden " + termin.getId()));
			} else {
				registrierung = registrierungRepo.getRegistrierungForGrundimpftermin(termin)
					.orElseThrow(() -> AppValidationMessage.ILLEGAL_STATE.create("Reg fuer Termin nicht gefunden " + termin.getId()));
			}


			AbgesagteTermine abgesagteTermine = registrierung.getAbgesagteTermine() != null ? registrierung.getAbgesagteTermine() : new AbgesagteTermine();
			String terminEffectiveStartBeforeOffsetReset = termin.getTerminZeitfensterStartDateAndTimeString();
			switch (termin.getImpffolge()) {
			case ERSTE_IMPFUNG:
				// Wenn der erste Termin abgesagt wird, muss auch der zweite abgesagt werden
				final Impftermin termin2 = registrierung.getImpftermin2();
				if (termin2 != null) {
					String termin2EffectiveStartBeforeOffsetReset =
						termin2.getTerminZeitfensterStartDateAndTimeString();
					abgesagteTermine.setOdiAndImpftermine(termin, termin2);
					terminAbsagenErsttermin(registrierung, termin);
					terminAbsagenZweittermin(registrierung, termin2);
					confirmationService.sendTerminabsageBeideTermine(registrierung, termin, termin2, terminEffectiveStartBeforeOffsetReset, termin2EffectiveStartBeforeOffsetReset);
				} else {
					abgesagteTermine.setOdiAndImpftermin1(termin);
					terminAbsagenErsttermin(registrierung, termin);
					confirmationService.sendTerminabsage(registrierung, termin, terminEffectiveStartBeforeOffsetReset);
				}
				break;
			case ZWEITE_IMPFUNG:
				abgesagteTermine.setOdiAndImpftermin2(termin);
				terminAbsagenZweittermin(registrierung, termin);
				confirmationService.sendTerminabsage(registrierung, termin, terminEffectiveStartBeforeOffsetReset);
				break;
			case BOOSTER_IMPFUNG:
				abgesagteTermine.setOdiAndImpfterminN(termin);
				terminAbsagenBoostertermin(termin);
				confirmationService.sendTerminabsage(registrierung, termin, terminEffectiveStartBeforeOffsetReset);
				break;
			}
			// Den Termin als "abgesagt" merken fuer Darstellung
			registrierung.setAbgesagteTermine(abgesagteTermine);
		} catch (Exception exception) {
			LOG.warn("Abasege did not work for impftermin {}, will continue with next Impftermin",
				impftermin.getId(), exception);
		}
	}

	private void terminAbsagenErsttermin(@NonNull Registrierung registrierung, @NonNull Impftermin termin) {
		impfterminRepo.termin1Freigeben(registrierung);
		termin.getImpfslot().setKapazitaetErsteImpfung(termin.getImpfslot().getKapazitaetErsteImpfung() - 1);
		impfslotService.updateImpfslot(termin.getImpfslot());
		impfterminRepo.delete(Impftermin.toId(termin.getId()));
	}

	private void terminAbsagenZweittermin(@NonNull Registrierung registrierung, @NonNull Impftermin termin) {
		impfterminRepo.termin2Freigeben(registrierung);
		termin.getImpfslot().setKapazitaetZweiteImpfung(termin.getImpfslot().getKapazitaetZweiteImpfung() - 1);
		impfslotService.updateImpfslot(termin.getImpfslot());
		impfterminRepo.delete(Impftermin.toId(termin.getId()));
	}

	private void terminAbsagenBoostertermin(@NonNull Impftermin termin) {
		final Optional<Impfdossiereintrag> eintrag = impfdossierRepo.findImpfdossiereintragForImpftermin(termin);
		if (eintrag.isPresent()) {
			impfterminRepo.boosterTerminFreigeben(eintrag.get());
			termin.getImpfslot().setKapazitaetBoosterImpfung(termin.getImpfslot().getKapazitaetBoosterImpfung() - 1);
			impfslotService.updateImpfslot(termin.getImpfslot());
			impfterminRepo.delete(Impftermin.toId(termin.getId()));
		}
	}

	/**
	 * Loescht alle Termine dieses Ortes und Tages dieser Impffolge. Es duerfen keine Referenzen mehr bestehen
	 *
	 * @param ortDerImpfung ort fuer den die Termine entfernt werden
	 * @param impffolge 1., 2. oder Booster Impfung
	 * @param datum datum fuer die die Termine des Odis entfernt werden
	 */
	@Transactional(TxType.REQUIRES_NEW)
	public void deleteAllTermineOfOdiAndDatum(
		@NonNull OrtDerImpfung ortDerImpfung,
		@NonNull Impffolge impffolge,
		@NonNull LocalDate datum
	) {
		// Geloescht werden nicht nur die gebuchten, sondern ALLE Termine dieses Tages
		final List<Impftermin> zuLoeschendeTermine = impfterminRepo.findAlleTermine(ortDerImpfung, impffolge, datum, datum);
		LOG.info("VACME-INFO: Es werden {} Termine geloscht", zuLoeschendeTermine.size());
		for (Impftermin impftermin : zuLoeschendeTermine) {
			switch (impffolge) {
			case ERSTE_IMPFUNG:
				impftermin.getImpfslot().setKapazitaetErsteImpfung(0);
				break;
			case ZWEITE_IMPFUNG:
				impftermin.getImpfslot().setKapazitaetZweiteImpfung(0);
				break;
			case BOOSTER_IMPFUNG:
				impftermin.getImpfslot().setKapazitaetBoosterImpfung(0);
				break;
			}
			// Termin loeschen
			impfterminRepo.delete(Impftermin.toId(impftermin.getId()));
		}
	}
}
