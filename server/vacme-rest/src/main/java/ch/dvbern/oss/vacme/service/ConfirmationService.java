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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import ch.dvbern.oss.vacme.entities.UserPrincipal;
import ch.dvbern.oss.vacme.entities.benutzer.Benutzer;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossier;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFile;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFileTyp;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungsEingang;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.zertifikat.Zertifikat;
import ch.dvbern.oss.vacme.repo.BenutzerRepo;
import ch.dvbern.oss.vacme.service.boosterprioritaet.BoosterPrioUtil;
import ch.dvbern.oss.vacme.service.covidcertificate.CovidCertBatchType;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_BOOSTER_FREIGABE_NOTIFICATION_DISABLED;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_BOOSTER_FREIGABE_NOTIFICATION_TERMIN_N_DISABLED;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolle.IMPFWILLIGER;
import static ch.dvbern.oss.vacme.shared.errors.AppValidationMessage.UNHANDLED_CONFIRMATION_TYPE;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@ApplicationScoped
public class ConfirmationService {

	private final UserPrincipal userPrincipal;
	private final BenutzerRepo benutzerRepo;
	private final SmsService smsService;
	private final PdfService pdfService;
	private final DokumentService dokumentService;
	private final ImpfinformationenService impfinformationenService;
	private final FTPRetryClientService ftpRetryClientService;
	private final ApplicationPropertyService applicationPropertyService;

	/**
	 * Sends a confirmation indicated by a specific {@link RegistrierungFileTyp} to the correct target
	 *
	 * @param typ confirmation type, either for {@link RegistrierungFileTyp#REGISTRIERUNG_BESTAETIGUNG}
	 * or {@link RegistrierungFileTyp#TERMIN_BESTAETIGUNG}
	 * @param registrierung the target registration
	 */
	public void sendConfirmationNoBoosterTermin(@NonNull RegistrierungFileTyp typ, @NonNull Registrierung registrierung) {
		sendConfirmation(typ, registrierung, null);
	}

	/**
	 * Sends a confirmation indicated by a specific {@link RegistrierungFileTyp} to the correct target
	 *
	 * @param typ confirmation type, either for {@link RegistrierungFileTyp#REGISTRIERUNG_BESTAETIGUNG}
	 * or {@link RegistrierungFileTyp#TERMIN_BESTAETIGUNG}
	 * @param registrierung the target registration
	 */
	public void sendConfirmation(@NonNull RegistrierungFileTyp typ, @NonNull Registrierung registrierung, @Nullable Impftermin boosterTerminOrNull) {
		if (registrierung.getRegistrierungsEingang() == RegistrierungsEingang.ONLINE_REGISTRATION) {
			sendConfirmationSMS(typ, registrierung, boosterTerminOrNull);
		} else { // bei callcenter und allen anderen machen wir das file neu so dass es neu gesendet wird
			sendConfirmationDocument(typ, registrierung, boosterTerminOrNull);
		}
	}

	/**
	 * Resends a confirmation by deleting and sending it again.
	 *
	 * @param typ confirmation type, either for {@link RegistrierungFileTyp#REGISTRIERUNG_BESTAETIGUNG}
	 * or {@link RegistrierungFileTyp#TERMIN_BESTAETIGUNG}
	 * @param registrierung the target registration
	 */
	public void resendConfirmation(@NonNull RegistrierungFileTyp typ, @NonNull Registrierung registrierung, @Nullable Impftermin boosterTerminOrNull) {
		switch (typ) {
		case REGISTRIERUNG_BESTAETIGUNG:
			dokumentService.deleteRegistrierungbestaetigung(registrierung);
			break;
		case TERMIN_BESTAETIGUNG:
			dokumentService.deleteTerminbestaetigung(registrierung);
			break;
		default:
			// do nothing in all other cases
			LOG.info("Attempted to delete confirmation for unhandled type {}", typ.name());
			break;
		}

		sendConfirmation(typ, registrierung, boosterTerminOrNull);
	}

	/**
	 * Sends a confirmation indicated by a specific {@link RegistrierungFileTyp} by SMS to the correct target
	 *
	 * @param typ confirmation type, either for {@link RegistrierungFileTyp#REGISTRIERUNG_BESTAETIGUNG}
	 * or {@link RegistrierungFileTyp#TERMIN_BESTAETIGUNG}
	 * @param registrierung the target registration
	 */
	private void sendConfirmationSMS(@NonNull RegistrierungFileTyp typ, @NonNull Registrierung registrierung, @Nullable Impftermin boosterTerminOrNull) {
		final Benutzer targetBenutzer = getSMSTargetBenutzer(registrierung);

		if (targetBenutzer.hasNonEmptyAndValidatedMobileNumber()) {

			String number = targetBenutzer.getMobiltelefon();
			Objects.requireNonNull(number);

			switch (typ) {
			case REGISTRIERUNG_BESTAETIGUNG:
				smsService.sendOnlineRegistrierungsSMS(registrierung, number, targetBenutzer);
				break;
			case TERMIN_BESTAETIGUNG:
				smsService.sendTerminbestaetigungSMS(registrierung, boosterTerminOrNull, number);
				break;
			case FREIGABE_BOOSTER_INFO:
				ImpfinformationDto infos =
					this.impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());

				if (doNotSendFreigabeBoosterInfo(infos)) {
					// Es soll keine Benachrichtigung geschickt werden. Wir brechen hier ab
					return;
				}

				final Impfdossier impfdossier = infos.getImpfdossier();
				Objects.requireNonNull(
					impfdossier,
					() -> "Kein Impfdossier gefunden fuer " + infos.getRegistrierung().getRegistrierungsnummer());
				Objects.requireNonNull(
					impfdossier.getImpfschutz(),
					() -> "Kein Impfschutz gefunden " + infos.getRegistrierung().getRegistrierungsnummer());
				if (!impfdossier.getImpfschutz().isBenachrichtigungBeiFreigabe()) {
					// Es soll keine Benachrichtigung geschickt werden. Wir brechen hier ab
					return;
				}

				Objects.requireNonNull(infos.getRegistrierung().getBenutzerId());
				String benutzerName =
					benutzerRepo.getById(Benutzer.toId(infos.getRegistrierung().getBenutzerId()))
						.map(Benutzer::getBenutzername)
						.orElse(null);
				smsService.sendFreigabeBoosterSMS(infos, benutzerName, number);
				break;
			default:
				throwUnexpectedConfirmation(typ, registrierung);
			}
		}
	}

	/**
	 * Sends a confirmation indicated by a specific {@link RegistrierungFileTyp} by Document (e.g. using postal service)
	 * to the correct target
	 *
	 * @param typ confirmation type, either for {@link RegistrierungFileTyp#REGISTRIERUNG_BESTAETIGUNG}
	 * or {@link RegistrierungFileTyp#TERMIN_BESTAETIGUNG}
	 * @param registrierung the target registration
	 */
	private void sendConfirmationDocument(@NonNull RegistrierungFileTyp typ, @NonNull Registrierung registrierung, @Nullable Impftermin boosterTerminOrNull) {

		byte[] fileContent;
		switch (typ) {
		case REGISTRIERUNG_BESTAETIGUNG:
			if(isKeinKontaktAndLog(registrierung)){
				return;
			}
			fileContent = pdfService.createRegistrationsbestaetigung(registrierung);
			break;
		case TERMIN_BESTAETIGUNG:
			fileContent = pdfService.createTerminbestaetigung(registrierung, boosterTerminOrNull);
			break;
		case FREIGABE_BOOSTER_INFO:
			// Wir muessen noch rausfinden wann die neuste Impfung war da das in den Brief kommt
			ImpfinformationDto infos =
				this.impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());

			if (doNotSendFreigabeBoosterInfo(infos)) {
				// Es soll keine Benachrichtigung geschickt werden. Wir brechen hier ab
				return;
			}

			Objects.requireNonNull(infos.getImpfdossier(), "Kein Impfdossier gefunden");
			Objects.requireNonNull(infos.getImpfdossier().getImpfschutz(), "Kein Impfschutz gefunden");
			if (!infos.getImpfdossier().getImpfschutz().isBenachrichtigungBeiFreigabe()) {
				// Es soll keine Benachrichtigung geschickt werden. Wir brechen hier ab
				return;
			}

			LocalDate latestImpfung = BoosterPrioUtil.getDateOfNewestImpfung(infos);
			if (latestImpfung == null) {
				throw AppValidationMessage.IMPFUNG_DATE_NOT_AVAILABLE.create(registrierung.getRegistrierungsnummer());
			}

			fileContent = pdfService.createFreigabeBoosterBrief(infos.getRegistrierung(), latestImpfung);
			break;
		default:
			throwUnexpectedConfirmation(typ, registrierung);
			return;
		}

		saveAndSendLetter(registrierung, typ, fileContent);
	}

	public void sendTerminabsage(@NonNull Registrierung registrierung, @NonNull Impftermin termin,
		@NonNull String terminEffectiveStartBeforeOffsetReset) {
		if (isVerstorbenAndLog(registrierung)) {
			return;
		}
		if (registrierung.getRegistrierungsEingang() == RegistrierungsEingang.ONLINE_REGISTRATION) {
			final Benutzer targetBenutzer = getSMSTargetBenutzer(registrierung);
			String number = targetBenutzer.getMobiltelefon();
			Objects.requireNonNull(number);
			smsService.sendTerminabsage(registrierung, termin, number, terminEffectiveStartBeforeOffsetReset);
		} else { // bei callcenter und allen anderen generieren wir das file neu damit es neu gesendet wird
			byte[] fileContent = pdfService.createTerminabsage(registrierung, termin, terminEffectiveStartBeforeOffsetReset);
			saveAndSendLetter(registrierung, RegistrierungFileTyp.TERMIN_ABSAGE, fileContent);
		}
	}

	public void sendTerminabsageBeideTermine(@NonNull Registrierung registrierung, @NonNull Impftermin termin1, @NonNull Impftermin termin2,
		@NonNull String terminEffectiveStart, @NonNull String termin2EffectiveStart) {
		if (isVerstorbenAndLog(registrierung)) {
			return;
		}
		if (registrierung.getRegistrierungsEingang() == RegistrierungsEingang.ONLINE_REGISTRATION) {
			final Benutzer targetBenutzer = getSMSTargetBenutzer(registrierung);
			String number = targetBenutzer.getMobiltelefon();
			Objects.requireNonNull(number);
			smsService.sendTerminabsageBeideTermine(registrierung, number, terminEffectiveStart, termin2EffectiveStart);
		} else { // bei callcenter und allen anderen generieren wir das file neu damit es neu gesendet wird
			sendBriefTerminabsagenBeideTermine(registrierung, termin1, termin2, terminEffectiveStart, termin2EffectiveStart);
		}
	}

	private void sendBriefTerminabsagenBeideTermine(@NotNull Registrierung registrierung, @NotNull Impftermin termin1, @NotNull Impftermin termin2,
		@NotNull String terminEffectiveStart, @NotNull String termin2EffectiveStart) {
		byte[] fileContent = pdfService.sendTerminabsageBeideTermine(registrierung, termin1, termin2, terminEffectiveStart, termin2EffectiveStart);
		saveAndSendLetter(registrierung, RegistrierungFileTyp.TERMIN_ABSAGE, fileContent);
	}

	private @NonNull Benutzer getSMSTargetBenutzer(@NonNull Registrierung registrierung) {
		// check if it's a user or call center agent
		if (userPrincipal.isCallerInRole(IMPFWILLIGER)) {

			// therefore we know that the current user matches the user tied to the registration

			return userPrincipal.getBenutzerOrThrowException();

		}

		// otherwise we retrieve the user referenced in the registration
		return retrieveBenutzerOwningReg(registrierung);
	}

	private Benutzer retrieveBenutzerOwningReg(@NonNull Registrierung registrierung) {
		UUID benutzerId = registrierung.getBenutzerId();

		Objects.requireNonNull(benutzerId, "Bei OnlineRegistrierungen ist die BenutzerId zwingend");

		return this.benutzerRepo.getById(Benutzer.toId(benutzerId))
			.orElseThrow(() -> AppFailureException.entityNotFound(Benutzer.class, benutzerId));
	}

	private @NonNull RegistrierungFileTyp getFTPFailTypeByBaseType(@NonNull RegistrierungFileTyp fileTyp) {
		switch (fileTyp) {
		case REGISTRIERUNG_BESTAETIGUNG:
			return RegistrierungFileTyp.REGISTRIERUNG_BESTAETIGUNG_FTP_FAIL;
		case TERMIN_BESTAETIGUNG:
			return RegistrierungFileTyp.TERMIN_BESTAETIGUNG_FTP_FAIL;
		case TERMIN_ABSAGE:
			return RegistrierungFileTyp.TERMIN_ABSAGE_FTP_FAIL;
		case TERMIN_ZERTIFIKAT_STORNIERUNG:
			return RegistrierungFileTyp.TERMIN_ZERTIFIKAT_STORNIERUNG_FTP_FAIL;
		case FREIGABE_BOOSTER_INFO:
			return RegistrierungFileTyp.FREIGABE_BOOSTER_INFO_FTP_FAIL;
		case ZERTIFIKAT_COUNTER_RECALCULATION:
			return RegistrierungFileTyp.ZERTIFIKAT_COUNTER_RECALCULATION_FAIL;
		case ONBOARDING_LETTER:
			return RegistrierungFileTyp.ONBOARDING_LETTER_FTP_FAIL;
		default:
			throw new IllegalArgumentException("No FTP fail type for file type " + fileTyp.name());
		}
	}

	private void throwUnexpectedConfirmation(RegistrierungFileTyp typ, Registrierung registrierung) {
		LOG.info("Encountered unexpected confirmation file type {} on registration type {}", typ.name(), registrierung.getRegistrierungsEingang().name());
		throw UNHANDLED_CONFIRMATION_TYPE.create(typ, registrierung.getRegistrierungsEingang());
	}

	private boolean boosterNotificationDisabled(ImpfinformationDto infos) {
		final boolean notificationDisabled = applicationPropertyService
			.getByKey(VACME_BOOSTER_FREIGABE_NOTIFICATION_DISABLED).getValueAsBoolean();
		final boolean notificationDisabledTerminN = applicationPropertyService
			.getByKey(VACME_BOOSTER_FREIGABE_NOTIFICATION_TERMIN_N_DISABLED).getValueAsBoolean();

		return notificationDisabled
			|| (notificationDisabledTerminN
				&& infos.getBoosterImpfungen() != null
				&& !infos.getBoosterImpfungen().isEmpty());
	}

	@Transactional(TxType.REQUIRES_NEW)
	public void sendZertifikatsbenachrichtigung(
		@NonNull Registrierung registrierung,
		@NonNull Zertifikat zertifikat,
		@NonNull CovidCertBatchType batchType
	) {
		if (isVerstorbenAndLog(registrierung)) {
			return;
		}
		if (registrierung.getRegistrierungsEingang() == RegistrierungsEingang.ONLINE_REGISTRATION) {
			final Benutzer targetBenutzer = retrieveBenutzerOwningReg(registrierung);
			String number = targetBenutzer.getMobiltelefon();
			Objects.requireNonNull(number);
			smsService.sendZertifikatsbenachrichtigung(registrierung, number, batchType);
		} else {
			LOG.warn("VACME-ZERTIFIKAT: Es wird keine Benachrichtigung generiert fuer das generierte Zertifikat"
					+ " {} der  Registrierung {} weil "
					+ "es sich nicht um eine Online Registrierung handelt ",
				zertifikat.getUvci(),
				registrierung.getRegistrierungsnummer()
			);
		}
	}

	@Transactional(TxType.REQUIRES_NEW)
	public void sendZertifikatRevocationBenachrichtigung(
		@NonNull Zertifikat zertifikat,
		@NonNull CovidCertBatchType batchType
	) {
		Validate.isTrue(
			CovidCertBatchType.REVOCATION_ONLINE == batchType
				|| CovidCertBatchType.REVOCATION_POST == batchType, "Batch Typ muss REVOCATION_ONLINE oder REVOCATION_POST sein");
		Registrierung registrierung = zertifikat.getRegistrierung();
		if (isVerstorbenAndLog(registrierung)) {
			return;
		}
		if (batchType == CovidCertBatchType.REVOCATION_ONLINE) {
			final Benutzer targetBenutzer = retrieveBenutzerOwningReg(registrierung);
			String number = targetBenutzer.getMobiltelefon();
			Objects.requireNonNull(number);
			smsService.sendZertifikatRevocationBenachrichtigung(zertifikat, number);
		} else {
			byte[] fileContent = pdfService.createZertifikatStornierung(registrierung, zertifikat);
			saveAndSendLetter(registrierung, RegistrierungFileTyp.TERMIN_ZERTIFIKAT_STORNIERUNG, fileContent);
		}
	}

	private boolean isVerstorbenAndLog(@NonNull Registrierung registrierung) {
		if (Boolean.TRUE.equals(registrierung.getVerstorben())) {
			LOG.warn("Versand abgebrochen, Registrierung ist als verstorben markiert");
			return true;
		}
		return false;
	}

	private boolean isKeinKontaktAndLog(@NonNull Registrierung registrierung) {
		if (Boolean.TRUE.equals(registrierung.getKeinKontakt())) {
			LOG.warn("Versand abgebrochen, Registrierung ist als keinKontakt markiert");
			return true;
		}
		return false;
	}

	public RegistrierungFile saveAndSendLetter(@NotNull Registrierung registrierung, @NonNull RegistrierungFileTyp type, byte[] fileContent) {
		var filename = pdfService.createFilename(type, Locale.GERMAN, registrierung.getRegistrierungsnummer());
		RegistrierungFile file = dokumentService.createAndSave(fileContent, type, registrierung);
		ftpRetryClientService.tryToPutFilesToFtp(filename, null, registrierung, fileContent, getFTPFailTypeByBaseType(type));
		return file;
	}

	private boolean doNotSendFreigabeBoosterInfo(@NonNull ImpfinformationDto infos) {
		return boosterNotificationDisabled(infos)
			|| isKeinKontaktAndLog(infos.getRegistrierung())
			|| infos.getRegistrierung().isImmobil();
	}
}
