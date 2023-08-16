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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.PersistenceException;

import ch.dvbern.oss.vacme.entities.UserPrincipal;
import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.benutzer.Benutzer;
import ch.dvbern.oss.vacme.entities.embeddables.Adresse;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossier;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.ImpfungkontrolleTermin;
import ch.dvbern.oss.vacme.entities.massenimport.Massenimport;
import ch.dvbern.oss.vacme.entities.onboarding.Onboarding;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Personenkontrolle;
import ch.dvbern.oss.vacme.entities.registration.Prioritaet;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungsEingang;
import ch.dvbern.oss.vacme.entities.types.daterange.DateUtil;
import ch.dvbern.oss.vacme.entities.umfrage.Umfrage;
import ch.dvbern.oss.vacme.entities.zertifikat.Zertifikat;
import ch.dvbern.oss.vacme.jax.base.AdresseJax;
import ch.dvbern.oss.vacme.jax.korrektur.PersonendatenKorrekturJax;
import ch.dvbern.oss.vacme.jax.registration.SelfserviceEditJax;
import ch.dvbern.oss.vacme.repo.AudHelperRepo;
import ch.dvbern.oss.vacme.repo.DokumentRepo;
import ch.dvbern.oss.vacme.repo.ExternesZertifikatRepo;
import ch.dvbern.oss.vacme.repo.FragebogenRepo;
import ch.dvbern.oss.vacme.repo.ImpfdossierRepo;
import ch.dvbern.oss.vacme.repo.ImpfterminRepo;
import ch.dvbern.oss.vacme.repo.MassenimportRepo;
import ch.dvbern.oss.vacme.repo.OnboardingRepo;
import ch.dvbern.oss.vacme.repo.PersonenkontrolleRepo;
import ch.dvbern.oss.vacme.repo.RegistrierungRepo;
import ch.dvbern.oss.vacme.repo.UmfrageRepo;
import ch.dvbern.oss.vacme.scheduler.SystemAdminRunnerService;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.shared.util.Constants;
import ch.dvbern.oss.vacme.util.EnumUtil;
import ch.dvbern.oss.vacme.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.exception.ConstraintViolationException;
import org.jetbrains.annotations.NotNull;

import static ch.dvbern.oss.vacme.entities.registration.RegistrierungFileTyp.REGISTRIERUNG_BESTAETIGUNG;
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
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolle.IMPFWILLIGER;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RegistrierungService {

	private final UserPrincipal userPrincipal;
	private final RegistrierungRepo registrierungRepo;
	private final PersonenkontrolleRepo personenkontrolleRepo;
	private final FragebogenRepo fragebogenRepo;
	private final ImpfterminRepo impfterminRepo;
	private final StammdatenService stammdatenService;
	private final ConfirmationService confirmationService;
	private final KeyCloakRegService keyCloakRegService;
	private final BenutzerService benutzerService;
	private final DokumentRepo dokumentRepo;
	private final SmsService smsService;
	private final SystemAdminRunnerService systemAdminRunnerService;
	private final KorrekturService korrekturService;
	private final ImpfinformationenService impfinformationenService;
	private final ImpfdossierRepo impfdossierRepo;
	private final AudHelperRepo audHelperRepo;
	private final ExternesZertifikatRepo externesZertifikatRepo;
	private final ZertifikatService zertifikatService;
	private final MassenimportRepo massenimportRepo;
	private final OnboardingRepo onboardingRepo;
	private final UmfrageRepo umfrageRepo;
	private final ImpfdossierService impfdossierService;

	@ConfigProperty(name = "vacme.kontrolle.gueltigkeit.hours", defaultValue = "4")
	protected Double kontrolleGueltigkeit; // Double zum Testen -> 0.1h moeglich

	@ConfigProperty(name = "vacme.minalter.impfung", defaultValue = "5")
	protected int minAlterImpfung;


	@NonNull
	public Registrierung createRegistrierung(@NonNull Fragebogen fragebogen) {
		Registrierung registrierung = fragebogen.getRegistrierung();
		if (!registrierung.isNew()) {
			throw AppValidationMessage.EXISTING_REGISTRIERUNG.create(registrierung.getRegistrierungsnummer());
		}
		// Berechnete Attribute setzen
		final Benutzer currentBenutzer = userPrincipal.getBenutzerOrThrowException();
		if (userPrincipal.isCallerInRole(IMPFWILLIGER)) {
			registrierung.setRegistrierungsEingang(RegistrierungsEingang.ONLINE_REGISTRATION);
			registrierung.setBenutzerId(currentBenutzer.getId());
		} else {
			// Alle anderen sind Callcenter. Fuer die Registrierung vor Ort (am ODI) gibt es einen separaten Service
			registrierung.setRegistrierungsEingang(RegistrierungsEingang.CALLCENTER_REGISTRATION);
			registrierung.setBenutzerId(null);
		}
		registrierung.setRegistrierungsnummer(stammdatenService.createUniqueRegistrierungsnummer());
		registrierung.setPrioritaet(stammdatenService.calculatePrioritaet(fragebogen));
		registrierung.setRegistrationTimestamp(LocalDateTime.now());
		registrierung.setRegistrierungStatus(REGISTRIERT);
		setStatusAccordingToPrioritaetFreischaltung(registrierung);

		// Speichern
		try {
			fragebogenRepo.create(fragebogen);
		} catch (PersistenceException e) {
			if (e.getCause() instanceof ConstraintViolationException) {
				LOG.warn("VACME-WARNING: Es wurde versucht, eine Registrierung zweimal zu speichern von Benutzer: {}", registrierung.getBenutzerId());
				// we catch this exception because it happens frequently in production
				throw AppValidationMessage.REGISTRIERUNG_DOES_ALREADY_EXIST.create();
			}
			throw e;
		}
		impfdossierService.createImpfdossier(registrierung); // covid Dossier gibts immer

		// Registrierung per SMS oder Post senden
		confirmationService.sendConfirmationNoBoosterTermin(REGISTRIERUNG_BESTAETIGUNG, registrierung);

		return registrierung;
	}

	public void registrierungErneutSenden(@NonNull Registrierung registrierung) {
		confirmationService.resendConfirmation(REGISTRIERUNG_BESTAETIGUNG, registrierung, null);
	}

	@NonNull
	public Registrierung findRegistrierung(@NonNull String registrierungsnummer) {
		return findRegistrierungAndEnsureStatusIsAktuell(registrierungsnummer);
	}

	@NonNull
	private Registrierung findRegistrierungAndEnsureStatusIsAktuell(@NonNull String registrierungsnummer) {
		Registrierung registrierung = findRegistrierungNoCheck(registrierungsnummer);
		ensureStatusIsAktuell(registrierung);
		return registrierung;
	}

	public void ensureStatusIsAktuell(@NonNull Registrierung registrierung) {
		// Falls unterdessen meine Prioritaet freigeschaltet wurde, muss mein Status angepasst werden
		setStatusAccordingToPrioritaetFreischaltung(registrierung);
		// Falls die Registrierung in einem "KONTROLLIERT"-Status ist, und die Gueltigkeit der Kontrolle
		// abgelaufen ist, muss evtl. der Status zurueckgesetzt werden
		handleGueltigkeitKontrolleAbgelaufen(registrierung);

		// Es muss sichergestellt dass das selbstzahler Flag entfernt wird wenn man regulaer freigegeben ist
		handleResetSelbstzahlerFlagIfRegulaerFreigegeben(registrierung);
	}

	private void handleResetSelbstzahlerFlagIfRegulaerFreigegeben(Registrierung registrierung) {
		if (registrierung.isSelbstzahler()) {
			ImpfinformationDto infos =
				impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
			if (infos.getImpfdossier() != null
				&& infos.getImpfdossier().getImpfschutz() != null
				&& infos.getImpfdossier().getImpfschutz().getFreigegebenNaechsteImpfungAb() != null
				&& infos.getImpfdossier().getImpfschutz().getFreigegebenNaechsteImpfungAb().isBefore(LocalDateTime.now())
			){
				registrierung.setSelbstzahler(false);
			}
		}
	}

	@NonNull
	public Registrierung findRegistrierungNoCheck(@NonNull String registrierungsnummer) {
		Registrierung registrierung = registrierungRepo.getByRegistrierungnummer(registrierungsnummer).orElseThrow(
			() -> AppValidationMessage.UNKNOWN_REGISTRIERUNGSNUMMER.create(registrierungsnummer));
		return registrierung;
	}

	public void acceptElektronischerImpfausweis(@NonNull Registrierung registrierung) {
		registrierung.setAbgleichElektronischerImpfausweis(true);
		registrierungRepo.update(registrierung);
	}

	@Nullable
	public Registrierung findRegistrierungByUser(UUID userId) {
		Registrierung registrierung = registrierungRepo.getByUserId(userId).orElse(null);
		return registrierung;
	}

	@NonNull
	public Optional<Registrierung> findRegistrierungById(UUID registrierungId) {
		return registrierungRepo.getById(Registrierung.toId(registrierungId));
	}

	public void updatePersonalien(Fragebogen fragebogen, @NonNull ImpfinformationDto impfinformationen, AdresseJax updateJax) {
		Registrierung registrierung = fragebogen.getRegistrierung();

		Adresse update = updateJax.toEntity();
		if (!Objects.equals(registrierung.getAdresse(), update)) {
			PersonendatenKorrekturJax korrekturJax = PersonendatenKorrekturJax.from(fragebogen);
			korrekturJax.setAdresse(updateJax);
			korrekturService.personendatenKorrigieren(fragebogen, impfinformationen, korrekturJax);
		}
	}

	public void updateSelfserviceData(Fragebogen fragebogen, @NonNull ImpfinformationDto impfinformationen, SelfserviceEditJax updateJax) {
		Registrierung registrierung = impfinformationen.getRegistrierung();


		// Adressaenderung ohne Konsequenzen
		Adresse adresse = registrierung.getAdresse();
		adresse.setAdresse1(updateJax.getAdresse().getAdresse1());
		adresse.setAdresse2(updateJax.getAdresse().getAdresse2());
		adresse.setPlz(updateJax.getAdresse().getPlz());
		adresse.setOrt(updateJax.getAdresse().getOrt());

		// Fragebogen Update ohne Konsequenzen
		assert updateJax.getChronischeKrankheiten() != null;
		fragebogen.setChronischeKrankheiten(updateJax.getChronischeKrankheiten());
		assert updateJax.getLebensumstaende() != null;
		fragebogen.setLebensumstaende(updateJax.getLebensumstaende());
		assert updateJax.getBeruflicheTaetigkeit() != null;
		fragebogen.setBeruflicheTaetigkeit(updateJax.getBeruflicheTaetigkeit());

		registrierung.setBemerkung(updateJax.getBemerkung());

		// Krankenkassennummer Update mit Archivierung der Nummer
		registrierung.setKrankenkasse(updateJax.getKrankenkasse());
		registrierung.setKrankenkasseKartenNrAndArchive(updateJax.getKrankenkasseKartenNr()); // setter macht auch archivierung
		registrierung.setAuslandArt(updateJax.getAuslandArt());

		registrierung.setKeinKontakt(updateJax.getKeinKontakt());

		registrierung.setTimestampInfoUpdate(updateJax.getTimestampInfoUpdate());
	}


	private void setStatusAccordingToPrioritaetFreischaltung(@NonNull @NotNull Registrierung registrierung) {
		int alter = (int) DateUtil.getAge(registrierung.getGeburtsdatum());
		moveFromRegistriertToFreigegebenIfMinAgeReachedAndPrioritaetIsFreigeschaltet(registrierung, alter);
		moveFromFreigegebenToRegistriertIfMinAgeNotReached(registrierung, alter);
	}

	private void moveFromFreigegebenToRegistriertIfMinAgeNotReached(@NonNull @NotNull Registrierung registrierung, int alter) {
		// Dies machen wir nur, weil Personen die das Mindestalter noch nicht erreicht haben, schon freigegeben wurden
		if (FREIGEGEBEN == registrierung.getRegistrierungStatus() && alter < minAlterImpfung) {
			registrierung.setRegistrierungStatus(REGISTRIERT);
			LOG.info("Setting Registrierung to REGISTRIERT {} (min. Alter nicht erreicht)", registrierung.getRegistrierungsnummer());
		}
	}

	private void moveFromRegistriertToFreigegebenIfMinAgeReachedAndPrioritaetIsFreigeschaltet(@NonNull @NotNull Registrierung registrierung, int alter) {
		if (REGISTRIERT == registrierung.getRegistrierungStatus() && alter >= minAlterImpfung && stammdatenService.istPrioritaetFreigeschaltet(registrierung.getPrioritaet())) {
			registrierung.setRegistrierungStatus(FREIGEGEBEN);
			LOG.info("Setting Registrierung to FREIGEGEBEN {}", registrierung.getRegistrierungsnummer());
		}
	}

	public void impfungVerweigert(Registrierung registrierung) {

		final Fragebogen fragebogen = fragebogenRepo.getByRegistrierung(registrierung)
			.orElseThrow(() -> AppValidationMessage.UNKNOWN_REGISTRIERUNGSNUMMER.create(registrierung.getRegistrierungsnummer()));

		switch (registrierung.getRegistrierungStatus()) {
		case IMPFUNG_1_KONTROLLIERT:
			ImpfungkontrolleTermin kontrolleTermin1 = fragebogen.getPersonenkontrolle().getKontrolleTermin1();
			RegistrierungStatus lastStatus = ermittleLetztenStatusVorKontrolle1(registrierung);
			LOG.info(
				"VACME-INFO: Impfwillige Person '{}' konnte nicht geimpft werden. Setze Status zurueck auf {}",
				registrierung.getRegistrierungsnummer(),
				lastStatus);
			registrierung.setRegistrierungStatus(lastStatus);
			resetSelbstzahlendeFlag(kontrolleTermin1);
			break;
		case IMPFUNG_2_KONTROLLIERT:
			ImpfungkontrolleTermin kontrolleTermin2 = fragebogen.getPersonenkontrolle().getKontrolleTermin2();
			registrierung.setRegistrierungStatus(IMPFUNG_1_DURCHGEFUEHRT);
			resetSelbstzahlendeFlag(kontrolleTermin2);
			break;
		case KONTROLLIERT_BOOSTER:
			var infos = impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
			var eintrag = ImpfinformationenService.getImpfdossierEintragForKontrolle(infos).orElseThrow();
			ImpfungkontrolleTermin impfungkontrolleTermin = eintrag.getImpfungkontrolleTermin();
			RegistrierungStatus lastStatusBooster = ermittleLetztenStatusVorKontrolleBooster(registrierung, infos.getImpfdossier(), eintrag);
			LOG.info(
				"VACME-INFO: Impfwillige Person '{}' konnte nicht geimpft werden. Setze Status von KONTROLLIERT_BOOSTER zurueck auf {}",
				registrierung.getRegistrierungsnummer(),
				lastStatusBooster);
			registrierung.setRegistrierungStatus(lastStatusBooster);
			resetSelbstzahlendeFlag(impfungkontrolleTermin);
			break;

		default:
			ValidationUtil.validateStatusOneOf(registrierung, IMPFUNG_1_KONTROLLIERT, IMPFUNG_2_KONTROLLIERT, KONTROLLIERT_BOOSTER);
		}
		registrierungRepo.update(registrierung);
	}

	public void handleGueltigkeitKontrolleAbgelaufen(@NonNull Registrierung registrierung) {
		if (EnumUtil.isNoneOf(registrierung.getRegistrierungStatus(), IMPFUNG_1_KONTROLLIERT, IMPFUNG_2_KONTROLLIERT, KONTROLLIERT_BOOSTER)) {
			return;
		}
		// Fuer Prioritaet X (Massenimport durch ODI) soll die Kontrolle nicht ablaufen (VACME-503). Das gilt aber nur fuer Impfung 1&2!
		if (EnumUtil.isOneOf(registrierung.getPrioritaet(), Prioritaet.X)
			&& EnumUtil.isOneOf(registrierung.getRegistrierungStatus(), IMPFUNG_1_KONTROLLIERT, IMPFUNG_2_KONTROLLIERT)) {
			return;
		}
		final Fragebogen fragebogen = fragebogenRepo.getByRegistrierung(registrierung)
			.orElseThrow(() -> AppValidationMessage.UNKNOWN_REGISTRIERUNGSNUMMER.create(registrierung.getRegistrierungsnummer()));

		// Bei ablauf in jedem fall, das selbstzahler flag resetten.
		registrierung.setSelbstzahler(false);

		switch (registrierung.getRegistrierungStatus()) {
			case IMPFUNG_1_KONTROLLIERT:
				ImpfungkontrolleTermin kontrolleTermin1 = fragebogen.getPersonenkontrolle().getKontrolleTermin1();
				if (isKontrolleAbgelaufen(kontrolleTermin1)) {
					final RegistrierungStatus lastStatus = ermittleLetztenStatusVorKontrolle1(registrierung);
					registrierung.setRegistrierungStatus(lastStatus);
					resetSelbstzahlendeFlag(kontrolleTermin1);
					LOG.info("VACME-INFO: Kontrolle 1 abgelaufen. Setze Status zurueck auf {}, Registrierung {}",
						lastStatus, registrierung.getRegistrierungsnummer());
					registrierungRepo.update(registrierung);
				}
				break;
			case IMPFUNG_2_KONTROLLIERT:
				ImpfungkontrolleTermin kontrolleTermin2 = fragebogen.getPersonenkontrolle().getKontrolleTermin2();
				if (isKontrolleAbgelaufen(kontrolleTermin2)) {
					registrierung.setRegistrierungStatus(IMPFUNG_1_DURCHGEFUEHRT);
					resetSelbstzahlendeFlag(kontrolleTermin2);
					LOG.info("VACME-INFO: Kontrolle 2 abgelaufen. Setze Status zurueck auf getPersonenkontrolle, Registrierung {}",
						registrierung.getRegistrierungsnummer());
					registrierungRepo.update(registrierung);
				}
				break;
			case KONTROLLIERT_BOOSTER:
				var infos = impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
				var eintrag = ImpfinformationenService.getImpfdossierEintragForKontrolle(infos).orElseThrow();
				ImpfungkontrolleTermin impfungkontrolleTermin = eintrag.getImpfungkontrolleTermin();
				if (isKontrolleAbgelaufen(impfungkontrolleTermin)) {
					final RegistrierungStatus lastStatus = ermittleLetztenStatusVorKontrolleBooster(registrierung, infos.getImpfdossier(), eintrag);
					registrierung.setRegistrierungStatus(lastStatus);
					resetSelbstzahlendeFlag(impfungkontrolleTermin);
					LOG.info("VACME-INFO: Kontrolle N abgelaufen. Setze Status zurueck auf {}, Registrierung {}",
						lastStatus, registrierung.getRegistrierungsnummer());
					registrierungRepo.update(registrierung);
				}
				break;
			default:
				throw AppValidationMessage.REGISTRIERUNG_WRONG_STATUS.create(registrierung.getRegistrierungsnummer(),
					"IMPFUNG_1_KONTROLLIERT, IMPFUNG_2_KONTROLLIERT, KONTROLLIERT_BOOSTER");

		}
	}

	private static void resetSelbstzahlendeFlag(@Nullable ImpfungkontrolleTermin impfungkontrolleTermin) {
		if (impfungkontrolleTermin != null) {
			impfungkontrolleTermin.setSelbstzahlende(null);
		}
	}

	private boolean isKontrolleAbgelaufen(@Nullable ImpfungkontrolleTermin impfungkontrolleTermin) {
		if (impfungkontrolleTermin != null) {
			final LocalDateTime timestampKontrolle = impfungkontrolleTermin.getTimestampKontrolle();
			long kontrolleSince = DateUtil.getMinutesBetween(timestampKontrolle, LocalDateTime.now());
			// KontrolleGueltigkeit ist in Stunden
			final boolean kontrolleAbgelaufen = kontrolleSince > kontrolleGueltigkeit * 60;
			return kontrolleAbgelaufen;
		}
		// Wenn es keine Kontrolle-Objekt gibt, ist die Kontrolle ebenfalls abgelaufen
		return true;
	}

	@NonNull
	public RegistrierungStatus ermittleLetztenStatusVorKontrolle1(@NonNull Registrierung registrierung) {
		RegistrierungStatus lastStatus = null;
		if (registrierung.getImpftermin1() != null && registrierung.getImpftermin2() != null) {
			// Beide Termine vorhanden -> Status gebucht
			lastStatus = GEBUCHT;
		} else {
			// Keine Termine vorhanden. War die Prio ueberhaupt schon freigegeben?
			if (!stammdatenService.istPrioritaetFreigeschaltet(registrierung.getPrioritaet())) {
				lastStatus = REGISTRIERT;
			} else if (registrierung.getGewuenschterOdi() != null
				&& (!registrierung.getGewuenschterOdi().isTerminverwaltung()
				|| registrierung.getGewuenschterOdi().isMobilerOrtDerImpfung())) {
				// ODI ausgewaehlt, aber keine Termine (moeglich)
				lastStatus = ODI_GEWAEHLT;
			} else {
				lastStatus = FREIGEGEBEN;
			}
		}
		return lastStatus;
	}

	@NonNull
	public RegistrierungStatus ermittleLetztenStatusVorKontrolleBooster(
		@NonNull Registrierung registrierung,
		@Nullable Impfdossier dossier,
		@Nullable Impfdossiereintrag eintrag
	) {
		// Termin vorhanden -> gebucht
		if (eintrag != null && eintrag.getImpftermin() != null) {
			return GEBUCHT_BOOSTER;
		}
		// es war eine ad hoc Kontrolle
		// war die Reg schon freigegen fuer booster?
		if (dossier != null && !ImpfinformationenService.hasFreigegebenenImpfschutz(dossier)) {
			return IMMUNISIERT;
		}
		// ODI gewaehlt fuer Booster (derjenige von Impfung 1/2 wurde beim Schieben nach Immunisiert auf null gesetzt)
		if (registrierung.getGewuenschterOdi() != null
			&& (!registrierung.getGewuenschterOdi().isTerminverwaltung()
			|| registrierung.getGewuenschterOdi().isMobilerOrtDerImpfung())) {
			return ODI_GEWAEHLT_BOOSTER;
		}
		return FREIGEGEBEN_BOOSTER;
	}

	@NonNull
	public List<Registrierung> searchRegistrierungByKvKNummer(@NonNull String kvkNummer) {
		return registrierungRepo.searchRegistrierungByKvKNummer(kvkNummer);
	}

	public void deleteRegistrierung(ImpfinformationDto impfinfos, Fragebogen fragebogen) {
		// Darf nur VOR der ersten Impfung gemacht werden
		final Registrierung registrierung = impfinfos.getRegistrierung();
		if (impfinformationenService.hasVacmeImpfungen(impfinfos)){
			throw AppValidationMessage.ILLEGAL_STATE.create("Registrierung " + registrierung.getRegistrierungsnummer() + " hat schon  VACME-Imfpungen");
		}
		final List<Zertifikat> allZertifikate = zertifikatService.getAllZertifikateRegardlessOfRevocation(registrierung);
		if (!allZertifikate.isEmpty()) {
			throw AppValidationMessage.DELETE_NOT_POSSIBLE_BECAUSE_ZERTIFIKAT.create(registrierung.getRegistrierungsnummer());
		}
		// Massenimport loschen
		final Optional<Massenimport> massenimportOptional = massenimportRepo.getByRegistrierung(registrierung);
		massenimportOptional.ifPresent(massenimport -> massenimportRepo.removeRegistrierungFromMassenimport(massenimport, registrierung));
		// Onboarding
		final List<Onboarding> onboardingList = onboardingRepo.findByRegistrierung(registrierung);
		for (Onboarding onboarding : onboardingList) {
			onboardingRepo.delete(onboarding);
			audHelperRepo.deleteOnboardingDataInAuditTables(onboarding);
		}

		// Evtl. vorhandene Termine freigeben
		impfterminRepo.termine1Und2Freigeben(registrierung);
		// da wir nur loeschen, wenn wir noch keine Impfung haben koennen wir nur einen 1. Boostertermin haben (ie. ext. Zertifikat erfasst)
		firstAndOnlyBoosterterminFreigebenIfExists(impfinfos.getImpfdossier());
		// Loeschungen immer loggen
		LOG.info("VACME-INFO: Fragebogen {} geloescht durch {}. RegistrierungsID {}",
			registrierung.getRegistrierungsnummer(),
			userPrincipal.getBenutzerOrThrowException().getBenutzername(),
			registrierung.getId());

		// Falls es einen Benutzer gibt (Online Anmeldung) diesen auch loeschen
		final UUID idOfOnlineBenutzer = registrierung.getBenutzerId();
		if (idOfOnlineBenutzer != null) {
			final Optional<Benutzer> benutzerOptional = benutzerService.getById(Benutzer.toId(idOfOnlineBenutzer));
			benutzerOptional.ifPresent(this::deleteBenutzerIfExists);
		}
		// Alle Dokumente loeschen, die zu dieser Reg gehoeren
		dokumentRepo.deleteAllRegistrierungFilesForReg(registrierung);
		// Die Audittabellen loeschen
		audHelperRepo.deleteFragebogenDataInAuditTables(fragebogen);
		// Fragebogen inkl. aller angehaengten Objekte loeschen (e.g. Impfungkontrolle fuer 1/2)
		Personenkontrolle personenkontrolle = fragebogen.getPersonenkontrolle();
		fragebogenRepo.delete(fragebogen.toId());

		// Impfdossier loeschen wenn vorhanden und keine Impfungen dran waren
		impfdossierRepo.findImpfdossierForReg(registrierung)
			.ifPresent(impfdossier -> {
				audHelperRepo.deleteImpfdossierDataInAuditTables(impfdossier);
				impfdossierRepo.delete(impfdossier.toId());
				LOG.info("... Impfdossier geloescht ({})", registrierung.getRegistrierungsnummer());
			});
		// externes Zert loeschen wenn vorhanden
		externesZertifikatRepo.findExternesZertifikatForReg(registrierung)
			.ifPresent(externesZertifikat -> {
				audHelperRepo.deleteExternesZertifikatInAuditTables(externesZertifikat);
				externesZertifikatRepo.remove(externesZertifikat);
				LOG.info("... externes Zertifikat geloescht ({})", registrierung.getRegistrierungsnummer());
			});
		// Snapshots
		registrierungRepo.deleteSnapshot(registrierung);
		// Umfrage
		final List<Umfrage> umfrageList = umfrageRepo.getUmfrageByRegistrierung(registrierung);
		for (Umfrage umfrage : umfrageList) {
			umfrageRepo.delete(umfrage.toId());
		}

		registrierungRepo.delete(registrierung.toId());
		personenkontrolleRepo.delete(personenkontrolle.toId());
	}

	private void firstAndOnlyBoosterterminFreigebenIfExists(@Nullable Impfdossier impfdossier) {
		if (impfdossier != null) {
			validateOnlyOneEintragwithTermin(impfdossier);

			impfdossier.getOrderedEintraege().stream().
				findFirst()
				.ifPresent(impfterminRepo::boosterTerminFreigeben);
		}
	}

	private void validateOnlyOneEintragwithTermin(@NonNull Impfdossier impfdossier) {
		long eintraegeWithTermin = impfdossier.getOrderedEintraege().stream()
			.filter(impfdossiereintrag -> impfdossiereintrag.getImpftermin() != null)
			.count();
		if (eintraegeWithTermin > 1) {
			throw AppValidationMessage.ILLEGAL_STATE.create("Dossier " + impfdossier.getRegistrierung().getRegistrierungsnummer()
				+ " hatte mehr als einen Dossiereintrag mit Impftermin. Dies ist bei der Accountloeschung nicht "
				+ "erwartet");
		}
	}

	public void deleteBenutzer(@NonNull Benutzer benutzer) {
		deleteBenutzerInVacmeAndAudit(benutzer);
		// Die ID in KeyCloak entspricht der ID des Vacme-Benutzers!
		keyCloakRegService.removeUser(benutzer.getId().toString());
	}

	public void deleteBenutzerIfExists(@NonNull Benutzer benutzer) {
		deleteBenutzerInVacmeAndAudit(benutzer);
		// Die ID in KeyCloak entspricht der ID des Vacme-Benutzers!
		keyCloakRegService.removeUserIfExists(benutzer.getId().toString());
	}

	private void deleteBenutzerInVacmeAndAudit(@NonNull Benutzer benutzer) {
		// Die Audittabellen loeschen
		audHelperRepo.deleteBenutzerDataInAuditTables(benutzer);
		// Den Benutzer in VacMe ebenfalls loeschen
		benutzerService.delete(benutzer.toId());
	}

	public void sendBenutzernameForRegistrierung(@NonNull String registrierungsnummer) {
		// Wir geben explizit keine Fehlermeldungen zurueck, wenn z.B. keine Registrierung gefunden wird,
		// diese eine falsche Eingangsart hat, oder der Benutzer nicht gefunden wird, um keine Informationen
		// nach aussen preiszugeben (Dieser Service ist public aufrufbar!)
		final Optional<Registrierung> registrierungOptional = registrierungRepo.getByRegistrierungnummer(registrierungsnummer);
		if (registrierungOptional.isPresent()) {
			final Registrierung registrierung = registrierungOptional.get();
			if (RegistrierungsEingang.ONLINE_REGISTRATION == registrierung.getRegistrierungsEingang()) {
				if (registrierung.getBenutzerId() != null) {
					final Optional<Benutzer> benutzerOptional = benutzerService.getById(Benutzer.toId(registrierung.getBenutzerId()));
					if (benutzerOptional.isPresent()) {
						Benutzer benutzer = benutzerOptional.get();
						if (benutzer.getBenutzernameGesendetTimestamp() != null) {
							// Pruefen, ob er schon wieder darf
							long between = DateUtil.getMinutesBetween(benutzer.getBenutzernameGesendetTimestamp(), LocalDateTime.now());
							if (between < Constants.MIN_ABSTAND_ZWISCHEN_BENUTZERNAME_ABFRAGE_IN_MINUTEN) {
								LOG.info("VACME-INFO: Es wurde versucht, den Benutzernamen fuer {} neu anzufordern, obwohl die letzte Abfrage erst "
									+ "{} Minuten her war", registrierungsnummer, between);
								return;
							}
						}
						systemAdminRunnerService.setBenutzernameGesendetTimestamp(benutzer);
						smsService.sendBenutzername(benutzer, registrierung.getLocale());
					}
				}
			}
		}
	}

	public Optional<LocalDateTime> getLastAbgeschlossenTimestampFromSnapshot(@NonNull ID<Registrierung> id) {
		return registrierungRepo.getLastAbgeschlossenTimestampFromSnapshot(id);
	}

	public void runPriorityUpdateForGrowingChildren() {
		runPriorityUpgradeToGroupForAge(12, Prioritaet.Q, Prioritaet.P);
		runPriorityUpgradeToGroupForAge(12, Prioritaet.T, Prioritaet.S);

	}

	private void runPriorityUpgradeToGroupForAge(int age, Prioritaet prioritaetFrom, Prioritaet prioritaetTo){
		List<String> foundRegs = registrierungRepo.getRegnumsOfGroupWithAgeGreaterOrEq(prioritaetFrom, age);

		for (String regnum : foundRegs) {
			Registrierung registrierung =
				registrierungRepo.getByRegistrierungnummer(regnum).orElseThrow(() -> AppFailureException.entityNotFound(Registrierung.class, "regnum:" + regnum));
			registrierung.setPrioritaet(prioritaetTo);
		}
		LOG.info("VACME-PRIORITY-UPDATE updated {} Regs that turned older than {} to Prio {} from {}", foundRegs.size(), age, prioritaetTo, prioritaetFrom);
	}

}
