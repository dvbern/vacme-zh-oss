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

package ch.dvbern.oss.vacme.scheduler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.security.RunAs;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.ws.rs.core.StreamingOutput;

import ch.dvbern.oss.vacme.entities.base.ApplicationProperty;
import ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey;
import ch.dvbern.oss.vacme.entities.benutzer.Benutzer;
import ch.dvbern.oss.vacme.entities.documentqueue.DocumentQueueStatus;
import ch.dvbern.oss.vacme.entities.documentqueue.IDocumentQueueService;
import ch.dvbern.oss.vacme.entities.documentqueue.SpracheParamJax;
import ch.dvbern.oss.vacme.entities.documentqueue.VonBisSpracheParamJax;
import ch.dvbern.oss.vacme.entities.documentqueue.entities.AbrechnungDocQueue;
import ch.dvbern.oss.vacme.entities.documentqueue.entities.AbrechnungErwachsenDocQueue;
import ch.dvbern.oss.vacme.entities.documentqueue.entities.AbrechnungKindDocQueue;
import ch.dvbern.oss.vacme.entities.documentqueue.entities.AbrechnungZHDocQueue;
import ch.dvbern.oss.vacme.entities.documentqueue.entities.AbrechnungZHKindDocQueue;
import ch.dvbern.oss.vacme.entities.documentqueue.entities.DocumentQueue;
import ch.dvbern.oss.vacme.entities.documentqueue.entities.DocumentQueueResult;
import ch.dvbern.oss.vacme.entities.documentqueue.entities.SpracheabhDocQueue;
import ch.dvbern.oss.vacme.entities.embeddables.Adresse;
import ch.dvbern.oss.vacme.entities.embeddables.FileBlob;
import ch.dvbern.oss.vacme.entities.massenverarbeitung.MassenverarbeitungQueueTyp;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFileTyp;
import ch.dvbern.oss.vacme.entities.types.daterange.DateUtil;
import ch.dvbern.oss.vacme.entities.umfrage.Umfrage;
import ch.dvbern.oss.vacme.entities.util.DBConst;
import ch.dvbern.oss.vacme.i18n.ServerMessageUtil;
import ch.dvbern.oss.vacme.jax.applicationhealth.ResultDTO;
import ch.dvbern.oss.vacme.jax.odiupload.OdiUploadFormData;
import ch.dvbern.oss.vacme.jax.registration.LatLngJax;
import ch.dvbern.oss.vacme.repo.ApplicationHealthRepo;
import ch.dvbern.oss.vacme.repo.ApplicationPropertyRepo;
import ch.dvbern.oss.vacme.repo.BoosterQueueRepo;
import ch.dvbern.oss.vacme.repo.DokumentRepo;
import ch.dvbern.oss.vacme.repo.ImpfterminRepo;
import ch.dvbern.oss.vacme.repo.RegistrierungRepo;
import ch.dvbern.oss.vacme.repo.UmfrageRepo;
import ch.dvbern.oss.vacme.reports.abrechnung.ReportAbrechnungServiceBean;
import ch.dvbern.oss.vacme.reports.abrechnungZH.ReportAbrechnungZHServiceBean;
import ch.dvbern.oss.vacme.reports.reportingImpfungen.ReportingImpfungenReportServiceBean;
import ch.dvbern.oss.vacme.reports.reportingKantonKantonsarzt.ReportingKantonReportServiceBean;
import ch.dvbern.oss.vacme.reports.reportingKantonKantonsarzt.ReportingKantonsarztReportServiceBean;
import ch.dvbern.oss.vacme.reports.reportingOdiImpfungenTerminbuchungen.ReportingOdiImpfungenReportServiceBean;
import ch.dvbern.oss.vacme.reports.reportingOdiImpfungenTerminbuchungen.ReportingOdiTerminbuchungenReportServiceBean;
import ch.dvbern.oss.vacme.reports.reportingOdis.ReportingOdisReportServiceBean;
import ch.dvbern.oss.vacme.reports.reportingTerminslots.ReportingTerminslotsReportServiceBean;
import ch.dvbern.oss.vacme.service.CheckFreieZweittermineService;
import ch.dvbern.oss.vacme.service.DokumentService;
import ch.dvbern.oss.vacme.service.GeocodeService;
import ch.dvbern.oss.vacme.service.ImpfslotService;
import ch.dvbern.oss.vacme.service.MailService;
import ch.dvbern.oss.vacme.service.OrtDerImpfungService;
import ch.dvbern.oss.vacme.service.PdfArchivierungService;
import ch.dvbern.oss.vacme.service.RegistrierungService;
import ch.dvbern.oss.vacme.service.SettingsService;
import ch.dvbern.oss.vacme.service.StatsService;
import ch.dvbern.oss.vacme.service.SystemAdministrationService;
import ch.dvbern.oss.vacme.service.VMDLService;
import ch.dvbern.oss.vacme.service.ZertifikatRunnerService;
import ch.dvbern.oss.vacme.service.benutzer.BenutzerMassenmutationRunnerService;
import ch.dvbern.oss.vacme.service.booster.BoosterRunnerService;
import ch.dvbern.oss.vacme.service.covidcertificate.CovidCertBatchType;
import ch.dvbern.oss.vacme.service.documentqueue.DocumentQueueRunnerService;
import ch.dvbern.oss.vacme.service.massenmutation.MassenverarbeitungRunnerService;
import ch.dvbern.oss.vacme.service.odiimport.OdiImportManagerService;
import ch.dvbern.oss.vacme.service.onboarding.OnboardingBatchType;
import ch.dvbern.oss.vacme.service.onboarding.OnboardingRunnerService;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import ch.dvbern.oss.vacme.shared.util.CleanFileName;
import ch.dvbern.oss.vacme.shared.util.Constants;
import ch.dvbern.oss.vacme.shared.util.MimeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.tuple.Pair;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.GLOBAL_NO_FREIE_TERMINE;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_ASYNC_DOCUMENT_CREATION_DISABLED;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_BOOSTER_FREIGABE_JOB_DISABLED;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_BOOSTER_RULE_ENGINE_JOB_DISABLED;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_BOOSTER_STATUSMOVER_JOB_DISABLED;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_COVIDAPI_ONLINE_PS_BATCHJOB_LOCK;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_COVIDAPI_POST_PS_BATCHJOB_LOCK;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_COVIDAPI_REVOCATION_ONLINE_PS_BATCHJOB_LOCK;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_COVIDAPI_REVOCATION_POST_PS_BATCHJOB_LOCK;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_COVID_CERT_BATCHJOB_DISABLED;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_COVID_CERT_BATCHJOB_POST_DISABLED;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_COVID_CERT_BATCHJOB_REVOC_ONLINE_DISABLED;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_COVID_CERT_BATCHJOB_REVOC_POST_DISABLED;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_DEACTIVATE_UNUSED_USERACCOUNTS_JOB_DISABLED;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_MASSENVERARBEITUNGQUEUE_PROCESSING_JOB_DISABLED;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_ONBOARDING_BRIEF_DISABLED;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_REGISTRIERUNG_AUTO_ABSCHLIESSEN_JOB_DISABLED;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VMDL_CRON_DISABLED;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_CRON_GLOBAL_FREI_TERMINE_DISABLED;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VACME_CRON_ODI_TERMINE_FREI_DISABLED;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.SYSTEM_INTERNAL_ADMIN;
import static java.time.temporal.TemporalAdjusters.firstDayOfMonth;
import static java.time.temporal.TemporalAdjusters.lastDayOfMonth;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@RunAs(SYSTEM_INTERNAL_ADMIN)
public class SystemAdminRunnerService {

	@ConfigProperty(name = "vacme.admin.mail")
	String adminMail;

	@ConfigProperty(name = "vacme.sls.mail")
	String slsMail;

	@ConfigProperty(name = "vacme.mail.reporting.callenter.disabled", defaultValue = "false")
	boolean reportingCallcenterDisabled;

	@ConfigProperty(name = "geocode.api.key")
	Optional<String> apiKeyOpt;

	@ConfigProperty(name = "vacme.mail.reporting.anzahlimpfungen.disabled", defaultValue = "false")
	boolean reportingAnzahlErstimpfungenDisabled;

	@ConfigProperty(name = "vacme.mail.reporting.anzahlzweitbooster.disabled", defaultValue = "false")
	boolean reportingAnzahlZweitBoosterDisabled;

	@ConfigProperty(name = "vmdl.cron.disabled", defaultValue = "true")
	boolean vmdlCronDisabled;

	@ConfigProperty(name = "vacme.service.automatisch.abschliessen.zeit.days", defaultValue = "100")
	int automatischAbschliessenZeitDays;

	@ConfigProperty(name = "vacme.service.archivierung.days", defaultValue = "400")
	int archivierungZeitDays;

	@ConfigProperty(name = "vacme.cron.archivierung.pdf.disabled")
	boolean archivierungPdfDisabled;

	@ConfigProperty(name = "vacme.min.impftermin.for.meldung")
	Integer minImpfterminForMeldung;

	@ConfigProperty(name = "vacme.cron.stat.dbvalidation.disabled", defaultValue = "false")
	boolean dbValidationJobDisabled;

	@ConfigProperty(name = "vacme.cron.global.freie.termine.disabled", defaultValue = "false")
	boolean vacmeCronGlobalFreiTermineDisabled;

	@ConfigProperty(name = "vacme.cron.odi.termine.frei.disabled", defaultValue = "false")
	boolean vacmeCronOdiTermineFreiDisabled;

	private final StatsService statsService;
	private final CurrentIdentityAssociation association;
	private final ApplicationHealthRepo applicationHealthRepo;
	private final MailService mailService;
	private final VMDLService vmdlService;
	private final DokumentRepo dokumentRepo;
	private final ImpfterminRepo impfterminRepo;
	private final RegistrierungRepo registrierungRepo;
	private final PdfArchivierungService pdfArchivierungService;
	private final DokumentService dokumentService;
	private final OdiImportManagerService odiImportService;
	private final CheckFreieZweittermineService checkFreieZweittermineService;
	private final OrtDerImpfungService ortDerImpfungService;
	private final SystemAdministrationService systemAdministrationService;
	private final ImpfslotService impfslotService;
	private final ApplicationPropertyRepo applicationPropertyRepo;
	private final UmfrageRepo umfrageRepo;
	private final ZertifikatRunnerService zertifikatRunnerService;
	private final OnboardingRunnerService onboardingRunnerService;
	private final BoosterRunnerService boosterRunnerService;
	private final BoosterQueueRepo boosterQueueRepo;
	private final RegistrierungService registrierungService;
	private final ReportAbrechnungServiceBean abrechnungServiceBean;
	private final ReportingImpfungenReportServiceBean reportingImpfungenReportServiceBean;
	private final ReportingTerminslotsReportServiceBean reportingTerminslotsReportServiceBean;
	private final ReportAbrechnungZHServiceBean abrechnungZHServiceBean;
	private final ReportingKantonReportServiceBean reportingKantonReportServiceBean;
	private final ReportingKantonsarztReportServiceBean reportingKantonsarztReportServiceBean;
	private final ReportingOdiImpfungenReportServiceBean reportingOdiImpfungenReportServiceBean;
	private final ReportingOdiTerminbuchungenReportServiceBean reportingOdiTerminbuchungenReportServiceBean;
	private final ReportingOdisReportServiceBean reportingOdisReportServiceBean;
	private final IDocumentQueueService documentQueueService;
	private final ObjectMapper objectMapper;
	private final DocumentQueueRunnerService documentqueueRunnerService;
	private final MassenverarbeitungRunnerService massenmutationRunnerService;
	private final BenutzerMassenmutationRunnerService benutzerMassenmutationRunnerService;
	private final GeocodeService geocodingService;
	private final SettingsService settingsService;

	@Transactional
	void runStatTask() {
		// Task soll asl Systembenutzer laufen
		runAsInternalSystemAdmin();

		statsService.takeKennzahlenSnapshot();
	}

	private void runAsInternalSystemAdmin() {
		// Task soll asl Systembenutzer laufen
		Builder builder = new Builder();
		QuarkusSecurityIdentity internalAdmin =
			builder.addRole(SYSTEM_INTERNAL_ADMIN).setPrincipal(() -> SYSTEM_INTERNAL_ADMIN).build();
		association.setIdentity(internalAdmin);
	}

	@Transactional(TxType.NOT_SUPPORTED)
	public void runDbValidationTask() {
		if (dbValidationJobDisabled) {
			LOG.info("VACME-HEALTH: Validation is disabled (vacme.cron.stat.dbvalidation.disabled)");
			return;
		}
		LOG.info("VACME-HEALTH: Starting Validation");
		boolean allSuccessful =
			// meldeResultat(applicationHealthRepo.runSleepQuery()) && todo reviewer of VACME-1933: comment in for testing
			meldeResultat(applicationHealthRepo.getHealthCheckInvalidImpfslots())
				&& meldeResultat(applicationHealthRepo.getHealthCheckVollstaendigerImpfschutzKeineImpfungen())
				&& meldeResultat(applicationHealthRepo.getHealthCheckDoppeltGeimpftOhneVollsaendigerImpfschutz())
				&& meldeResultat(applicationHealthRepo.getHealthCheckAbgeschlossenOhneVollsaendigerImpfschutz())
				&& meldeResultat(applicationHealthRepo.getHealthCheckNichtAbgeschlossenAberVollstaendigerImpfschutz())
				&& meldeResultat(applicationHealthRepo.getHealthCheckAbgeschlossenOhneCoronaAberVollstaendigerImpfschutz())
				&& meldeResultat(applicationHealthRepo.getHealthCheckFailedZertifikatRevocations())
				&& meldeResultat(applicationHealthRepo.getHealthCheckFailedZertifikatRecreations())
				&& meldeResultat(applicationHealthRepo.getHealthCheckGebuchteTermine())
				&& meldeResultat(applicationHealthRepo.getHealthCheckVerwaisteImpfungen())
				&& meldeResultat(applicationHealthRepo.getHealthCheckRegistrierungenMitImpfungNichtAmTermindatum())
				//	TODO improve performance https://support.dvbern.ch/browse/VACME-1403
				//	&& meldeResultat(applicationHealthRepo.getHealthCheckNichtGeschickteRegistrierungFile())
				&& meldeResultat(applicationHealthRepo.getHealthCheckFalschVerknuepfteZertifikate());

		// Abschluss-Mail
		if (adminMail != null) {
			final String subject = "Health-Checks " + (allSuccessful ? "Everything OKAY" : "FAILED");
			mailService.sendTextMail(adminMail, subject, subject, false);
		}

		LOG.info("VACME-HEALTH: Validation finished with result {}", allSuccessful);
	}

	void runHealthCheckZertifikatJobLock() {
		ResultDTO resultDTO = new ResultDTO();
		resultDTO.start("Health-Check Zertifikat-Batchjobs");
		resultDTO.setSuccess(true);

		if (!isBatchJobDisabled(VACME_COVID_CERT_BATCHJOB_DISABLED)) {
			checkIfNotModified(VACME_COVIDAPI_ONLINE_PS_BATCHJOB_LOCK, resultDTO);
		}
		if (!isBatchJobDisabled(VACME_COVID_CERT_BATCHJOB_REVOC_ONLINE_DISABLED)) {
			checkIfNotModified(VACME_COVIDAPI_REVOCATION_ONLINE_PS_BATCHJOB_LOCK, resultDTO);
		}
		if (!isBatchJobDisabled(VACME_COVID_CERT_BATCHJOB_POST_DISABLED)) {
			checkIfNotModified(VACME_COVIDAPI_POST_PS_BATCHJOB_LOCK, resultDTO);
		}
		if (!isBatchJobDisabled(VACME_COVID_CERT_BATCHJOB_REVOC_POST_DISABLED)) {
			checkIfNotModified(VACME_COVIDAPI_REVOCATION_POST_PS_BATCHJOB_LOCK, resultDTO);
		}

		if (resultDTO.isSuccess()) {
			resultDTO.addInfo("All Batchjobs running!");
		}
		resultDTO.finish(resultDTO.isSuccess());
		meldeResultat(resultDTO);
	}

	public void runHealthCheckGeocoding() {

		if (!this.settingsService.getSettings().isGeocodingEnabled()) {
			LOG.info("VACME-HEALTH: Skipping Geocoding Health-Check because Geocoding is disabled");
			return;
		}

		ResultDTO resultDTO = new ResultDTO();
		resultDTO.start("Health-Check Geocoding");
		resultDTO.setSuccess(true);

		if (apiKeyOpt.isEmpty()) {
			resultDTO.setSuccess(false);
			resultDTO.addInfo("No ApiKey set for the Geocoding API, despite Geocoding being enabled in Settings!");
		} else {
			final Adresse adresse = new Adresse();
			adresse.setAdresse1("Nussbaumstrasse 21");
			adresse.setOrt("Bern");
			adresse.setPlz("3000");

			final LatLngJax result = geocodingService.geocodeAdresse(adresse);

			if (result.getLat() == null || result.getLng() == null) {
				resultDTO.setSuccess(false);
				resultDTO.addInfo("Geocoding failed");
			}
		}

		resultDTO.finish(resultDTO.isSuccess());
		meldeResultat(resultDTO);
	}

	private void checkIfNotModified(@NonNull ApplicationPropertyKey keyOfLockProperty, @NonNull ResultDTO resultDTO) {
		final ApplicationProperty property = applicationPropertyRepo.getByKey(keyOfLockProperty)
			.orElseThrow(() -> AppFailureException.entityNotFound(ApplicationProperty.class, keyOfLockProperty));
		final LocalDateTime lastModified = property.getTimestampMutiert();
		if (DateUtil.getMinutesBetween(lastModified, LocalDateTime.now()) > 60) {
			resultDTO.addInfo("Batchjob is locked: " + keyOfLockProperty);
			resultDTO.setSuccess(false);
		}
	}

	private boolean meldeResultat(@NonNull ResultDTO result) {
		if (!result.isSuccess()) {
			if (adminMail != null) {
				mailService.sendTextMail(adminMail, result.getTitle(), result.getInfo(), false);
			} else {
				LOG.warn("VACME-HEALTH: Mail not sent because there is no admin email set");
			}
		}
		return result.isSuccess();
	}

	@Transactional(TxType.NOT_SUPPORTED)
	public void runVMDLUploadTask() {
		if (isBatchJobDisabled(VMDL_CRON_DISABLED, () -> vmdlCronDisabled)) {
			return;
		}
		// Task soll als Systembenutzer laufen
		runAsInternalSystemAdmin();

		vmdlService.uploadVMDLData();
	}

	public void runCallcenterKennzahlenMailTask() {
		if (reportingCallcenterDisabled) {
			return;
		}
		if (StringUtils.isNotEmpty(slsMail)) {
			// Task laeuft morgens um 1: Wir koennen einfach alle des vortages melden
			final LocalDate stichtag = LocalDate.now().minusDays(1);
			final String stichtagFormatted = DateUtil.formatDate(stichtag, Locale.GERMAN);

			final long anzahlRegistrierungen = dokumentRepo.getAnzahlCallcenterDokumente(
				RegistrierungFileTyp.REGISTRIERUNG_BESTAETIGUNG, stichtag);
			final long anzahlTerminbuchungen = dokumentRepo.getAnzahlCallcenterDokumente(
				RegistrierungFileTyp.TERMIN_BESTAETIGUNG, stichtag);

			LocalDateTime mStart = stichtag.with(firstDayOfMonth()).atStartOfDay();
			String mStartString = DateUtil.formatDate(stichtag.with(firstDayOfMonth()), Locale.GERMAN);
			LocalDateTime mEnd = stichtag.with(lastDayOfMonth()).plusDays(1).atStartOfDay();
			String mEndString = DateUtil.formatDate(stichtag.with(lastDayOfMonth()), Locale.GERMAN);

			final long anzahlRegistrierungenMonth = dokumentRepo.getAnzahlCallcenterDokumenteVonBis(
				RegistrierungFileTyp.REGISTRIERUNG_BESTAETIGUNG, mStart, mEnd);
			final long anzahlTerminbuchungenMonh = dokumentRepo.getAnzahlCallcenterDokumenteVonBis(
				RegistrierungFileTyp.TERMIN_BESTAETIGUNG, mStart, mEnd);

			String subject = ServerMessageUtil.getMessage("mail_kennzahlen_callcenter_subject", Locale.GERMAN,
				stichtagFormatted);
			final String content = ServerMessageUtil.getMessage("mail_kennzahlen_callcenter_content", Locale.GERMAN,
				stichtagFormatted, anzahlRegistrierungen, anzahlTerminbuchungen, mStartString,
				mEndString, anzahlRegistrierungenMonth, anzahlTerminbuchungenMonh);

			mailService.sendTextMail(slsMail, subject, content, false);
		}
	}

	public void runServiceReportingAnzahlErstimpfungenMailTask() {
		if (reportingAnzahlErstimpfungenDisabled) {
			return;
		}
		// Task laeuft am 1. Tag jedes Monats morgens um 1:
		// Wir melden alle Erstimpfungen des vergangenen Monats
		var von = LocalDate.now().minusMonths(1).with(firstDayOfMonth());
		var bis = von.with(TemporalAdjusters.lastDayOfMonth());
		systemAdministrationService.runServiceReportingAnzahlErstimpfungenMailTask(von, bis);
	}

	public void runServiceReportingAnzahlZweitBoosterMailTask() {
		if (reportingAnzahlZweitBoosterDisabled) {
			return;
		}
		systemAdministrationService.runServiceReportingAnzahlZweitBoosterMailTask();
	}

	public void setBenutzernameGesendetTimestamp(@NonNull Benutzer benutzer) {
		// Task soll asl Systembenutzer laufen, da wir in diesem Fall nicht eingeloggt sind
		runAsInternalSystemAdmin();

		benutzer.setBenutzernameGesendetTimestamp(LocalDateTime.now());
	}

	public void runImpfterminReservationResetTask() {
		impfterminRepo.abgelaufeneTerminReservationenAufheben();
	}

	@Transactional(TxType.NOT_SUPPORTED)
	public void runCovidCertUploadTask() {
		// Wir machen zuerst alle Stornierungen. Dies damit das Stornierungs-SMS zuerst geschickt wird
		// und erst danach die Info ueber das neu erzeugte Zertifikat
		if (!isBatchJobDisabled(VACME_COVID_CERT_BATCHJOB_REVOC_ONLINE_DISABLED)) {
			// Task soll asl Systembenutzer laufen
			runAsInternalSystemAdmin();
			zertifikatRunnerService.revokeBatchOfCovidCertificates(CovidCertBatchType.REVOCATION_ONLINE);
		}
		if (!isBatchJobDisabled(VACME_COVID_CERT_BATCHJOB_DISABLED)) {
			// Task soll asl Systembenutzer laufen
			runAsInternalSystemAdmin();
			zertifikatRunnerService.generateBatchOfCovidCertificates(CovidCertBatchType.ONLINE);
		}
	}

	@Transactional(TxType.NOT_SUPPORTED)
	public void runCovidCertUploadTaskNonOnline() {
		if (!isBatchJobDisabled(VACME_COVID_CERT_BATCHJOB_REVOC_POST_DISABLED)) {
			// Task soll asl Systembenutzer laufen
			runAsInternalSystemAdmin();
			zertifikatRunnerService.revokeBatchOfCovidCertificates(CovidCertBatchType.REVOCATION_POST);
		}
		if (!isBatchJobDisabled(VACME_COVID_CERT_BATCHJOB_POST_DISABLED)) {
			// Task soll asl Systembenutzer laufen
			runAsInternalSystemAdmin();
			zertifikatRunnerService.generateBatchOfCovidCertificates(CovidCertBatchType.POST);
		}
	}

	@Transactional(TxType.NOT_SUPPORTED)
	public void runOnboardingLetterGenerationTask() {
		if (!isBatchJobDisabled(VACME_ONBOARDING_BRIEF_DISABLED)) {
			// Task soll als Systembenutzer laufen
			runAsInternalSystemAdmin();
			onboardingRunnerService.generateBatchOfOnboardingLetters(OnboardingBatchType.POST);
		}
	}

	@Transactional
	public void runCovidCertClearTokensTask() {
		zertifikatRunnerService.clearCovidCertTokens();
	}

	private boolean isBatchJobDisabled(@NonNull ApplicationPropertyKey keyToCheckIfEnabled) {
		return isBatchJobDisabled(keyToCheckIfEnabled, () -> false);
	}

	private boolean isBatchJobDisabled(@NonNull ApplicationPropertyKey keyToCheckIfEnabled, @NonNull Supplier<Boolean> defaultSupplier) {
		Optional<ApplicationProperty> disabledInDB = applicationPropertyRepo.getByKey(keyToCheckIfEnabled);
		return disabledInDB
			.map(applicationProperty -> Boolean.parseBoolean(applicationProperty.getValue()))
			.orElseGet(defaultSupplier);
	}

	public void runPDFArchivierung() {
		if (archivierungPdfDisabled) {
			return;
		}
		runAsInternalSystemAdmin();
		List<Pair<Fragebogen, Exception>> failures =
			pdfArchivierungService.createPdfArchivesAndGetFailures(archivierungZeitDays);
		if (!failures.isEmpty()) {
			String subject = "Fehler waehrend Archivierung";
			StringBuilder textBuilder = new StringBuilder();
			textBuilder.append("Folgende registrierung archivierung sind fehlgeschlagen :");
			for (Pair<Fragebogen, Exception> failurePair : failures) {
				textBuilder.append("\nRegistrierung nummer ");
				textBuilder.append(failurePair.getLeft().getRegistrierung().getRegistrierungsnummer());
				textBuilder.append("; ");
				textBuilder.append(failurePair.getRight().getMessage());
			}
			mailService.sendTextMail(adminMail, subject, textBuilder.toString(), false);
			LOG.warn(
				"VACME-INFO: Archivierung PDFs generiert. Fuer {} Registrierungen schlug die generierung aber fehl",
				failures.size());
		} else {
			LOG.info("VACME-INFO: Archivierung PDFs wurden ohne Fehler generiert");
		}
	}

	public void runRegistrierungAutomatischAbschliessen() {
		if (isBatchJobDisabled(VACME_REGISTRIERUNG_AUTO_ABSCHLIESSEN_JOB_DISABLED)) {
			return;
		}
		runAsInternalSystemAdmin();

		LocalDateTime pastDate = LocalDate.now().minusDays(automatischAbschliessenZeitDays).atStartOfDay();
		List<Registrierung> regsToAutoclose = registrierungRepo.getErsteImpfungNoZweiteSince(pastDate);
		for (Registrierung registrierung : regsToAutoclose) {
			registrierung.setStatusToAutomatischAbgeschlossen();
			registrierungRepo.update(registrierung);
			// recreate impfdokumentation
			dokumentService.deleteImpfdokumentationPdf(registrierung);
			dokumentService.createAndSaveImpfdokumentationWithoutBoosterImpfungenPdf(registrierung);
			// Der 2. Termin (wenn vorhanden) liegt in der Vergangenheit und wird explizit nicht friegeben damit
			// es noech moeglich ware eine statistische Auswertung ueber no-shows zu machen
		}
	}

	public void runCheckForZweittermine() {
		runAsInternalSystemAdmin();

		checkFreieZweittermineService.analyseFreieZweittermine();
	}

	public void runUpdateOdiNoTermin() {
		if(isBatchJobDisabled(VACME_CRON_ODI_TERMINE_FREI_DISABLED, () -> vacmeCronOdiTermineFreiDisabled)) {
			return;
		}

		runAsInternalSystemAdmin();
		StopWatch stopWatch = StopWatch.createStarted();
		ortDerImpfungService.updateOdiNoTermin();
		stopWatch.stop();
		if (stopWatch.getTime() > Constants.DB_QUERY_SLOW_THRESHOLD_LONG) {
			LOG.warn("VACME-NO-FREIE-TERMINE: Update vom noFreieTermine1, noFreieTermine2 und noFreieTermineN flag im OrtDerImpfung took {}", stopWatch.getTime());
		}
	}

	@Transactional(TxType.REQUIRES_NEW)
	public void runUpdateGlobalNoTermin() {
		if(isBatchJobDisabled(VACME_CRON_GLOBAL_FREI_TERMINE_DISABLED, () -> vacmeCronGlobalFreiTermineDisabled)) {
			return;
		}
		runAsInternalSystemAdmin();
		// Das globale freieTermine betrachtet Termine1 ODER TermineN. Erst wenn weder noch vorhanden sind, wird die Meldung angezeigt
		boolean noFreieTermine = !impfslotService.hasAtLeastFreieImpftermine(minImpfterminForMeldung);
		ApplicationProperty saved = applicationPropertyRepo
			.getByKey(GLOBAL_NO_FREIE_TERMINE)
			.orElse(null);
		if (saved == null) {
			applicationPropertyRepo.create(new ApplicationProperty(
				GLOBAL_NO_FREIE_TERMINE,
				String.valueOf(noFreieTermine)
			));
		} else {
			boolean savedBool = Boolean.parseBoolean(saved.getValue());
			if (noFreieTermine != savedBool) {
				saved.setValue(String.valueOf(noFreieTermine));
				applicationPropertyRepo.update(saved);
			}
		}
	}

	/**
	 * Upload wird als Systemadmin ausgefuehrt da wir es asynchron laufen lassen
	 */
	public void processOdiUpload(@NonNull OdiUploadFormData formData, @NonNull String emailToNotify) {
		runAsInternalSystemAdmin();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try {
			odiImportService.createMassenupload(formData.inputStream, outputStream, emailToNotify);
		} catch (IOException e) {
			LOG.error("Cannot create Massenimport", e);
		}
	}

	@Transactional(TxType.NOT_SUPPORTED)
	public boolean processDocumentQueueItem(Long queueItemId) {
		runAsInternalSystemAdmin();
		DocumentQueue documentQueueItem = documentQueueService.getDocumentQueueItem(queueItemId);
		if (documentQueueItem.getStatus() != DocumentQueueStatus.NEW && documentQueueItem.getStatus()
			!= DocumentQueueStatus.FAILED_RETRY) {
			LOG.info("VACME-DOC-QUEUE: Queue Item mit id {} ist nicht im Status NEW oder FAILED_RETRY sondern im Status {}. Vielleicht ist es schon in Bearbeitung", queueItemId, documentQueueItem.getStatus());
			return false;
		}
		DocumentQueueResult result = null;
		try {
			documentQueueService.markDocumentQueueItemAsInProgress(documentQueueItem);
			documentQueueItem = documentQueueService.getDocumentQueueItem(queueItemId);
			LOG.info("VACME-DOC-QUEUE: Starting Async generation of DocumentQueueItem {}, typ ({}) for user '{}'",
				documentQueueItem.getId(), documentQueueItem.getTyp(),
				documentQueueItem.getBenutzer().getBenutzername());

			byte[] content = triggerGenerationFunctionForDocQueueItem(documentQueueItem);

			storeDocumentQueueItemResultFile(documentQueueItem, content);
			result = documentQueueItem.getDocumentQueueResult();

			LOG.info("VACME-DOC-QUEUE: Finished Async generation of DocumentQueueItem {} in {}ms", documentQueueItem,
				documentQueueItem.calculateProcessingTimeMs());
			// trigger email success
			documentQueueItem.sendFinishedDocumentQueueJobSuccessMail(mailService, objectMapper);
			return true;
		} catch (Exception e) {
			String typ = documentQueueItem.getTyp().toString();
			LOG.error("VACME-DOC-QUEUE: Could not generate Document of type {}", typ, e);
			// trigger email failure
			String errMsgRootcause = ExceptionUtils.getRootCauseMessage(e);
			documentQueueItem.sendFinishedDocumentQueueJobFailureMail(mailService, objectMapper, errMsgRootcause);
			documentQueueItem.markFailed(StringUtils.abbreviate(errMsgRootcause, DBConst.DB_BEMERKUNGEN_MAX_LENGTH));

			return false;
		} finally {
			documentQueueService.saveResult(documentQueueItem, result);

		}
	}

	@Transactional(TxType.NOT_SUPPORTED)
	private byte[] triggerGenerationFunctionForDocQueueItem(DocumentQueue documentQueueItem) {
		switch (documentQueueItem.getTyp()) {

		case ABRECHNUNG:
			AbrechnungDocQueue abrechnungDocQueue = (AbrechnungDocQueue) documentQueueItem;
			VonBisSpracheParamJax vonBisParam = abrechnungDocQueue.getVonBisSpracheParam(objectMapper);
			Locale locale = vonBisParam.getSprache().getLocale();
			byte[] bytes = abrechnungServiceBean.generateExcelReportAbrechnung(locale, vonBisParam.getVon(),
				vonBisParam.getBis());
			return bytes;
		case ABRECHNUNG_ERWACHSEN:
			AbrechnungErwachsenDocQueue abrechnungErwachsenDocQueue = (AbrechnungErwachsenDocQueue) documentQueueItem;
			VonBisSpracheParamJax vonBisErwachsenParam =
				abrechnungErwachsenDocQueue.getVonBisSpracheParam(objectMapper);
			Locale localeErwachsen = vonBisErwachsenParam.getSprache().getLocale();
			return abrechnungServiceBean.generateExcelReportAbrechnungErwachsen(localeErwachsen,
				vonBisErwachsenParam.getVon(),
				vonBisErwachsenParam.getBis());
		case ABRECHNUNG_KIND:
			AbrechnungKindDocQueue abrechnungKindDocQueue = (AbrechnungKindDocQueue) documentQueueItem;
			VonBisSpracheParamJax vonBisKindParam = abrechnungKindDocQueue.getVonBisSpracheParam(objectMapper);
			Locale localeKind = vonBisKindParam.getSprache().getLocale();
			return abrechnungServiceBean.generateExcelReportAbrechnungKind(localeKind, vonBisKindParam.getVon(),
				vonBisKindParam.getBis());

		case ABRECHNUNG_ZH:
			AbrechnungZHDocQueue abrZhDocQuee = (AbrechnungZHDocQueue) documentQueueItem;
			VonBisSpracheParamJax vonBisParamAbrZhParam = abrZhDocQuee.getVonBisSpracheParam(objectMapper);

			return abrechnungZHServiceBean.generateExcelReportAbrechnung(
				vonBisParamAbrZhParam.getSprache().getLocale(),
				vonBisParamAbrZhParam.getVon(),
				vonBisParamAbrZhParam.getBis());
		case ABRECHNUNG_ZH_KIND:
			AbrechnungZHKindDocQueue abrZhKindDocQuee = (AbrechnungZHKindDocQueue) documentQueueItem;
			VonBisSpracheParamJax vonBisParamAbrZhKindParam = abrZhKindDocQuee.getVonBisSpracheParam(objectMapper);

			return abrechnungZHServiceBean.generateExcelReportAbrechnungKind(
				vonBisParamAbrZhKindParam.getSprache().getLocale(),
				vonBisParamAbrZhKindParam.getVon(),
				vonBisParamAbrZhKindParam.getBis());
		case IMPFUNGEN_REPORT_CSV:
			StreamingOutput streamingOutput = reportingImpfungenReportServiceBean.generateStatisticsExport();
			return streamingOutputToByteArray(streamingOutput);

		case IMPFSLOTS_REPORT_CSV:
			return streamingOutputToByteArray(reportingTerminslotsReportServiceBean.generateStatisticsExport());
		case REGISTRIERUNGEN_KANTON_CSV:
			return streamingOutputToByteArray(reportingKantonReportServiceBean.generateStatisticsExport());
		case REGISTRIERUNGEN_KANTONSARZT_CSV:
			return streamingOutputToByteArray(reportingKantonsarztReportServiceBean.generateStatisticsExport());
		case ODI_REPORT_CSV:
			return reportingOdisReportServiceBean.generateStatisticsExport();
		case ODI_IMPFUNGEN:
			SpracheabhDocQueue docQueueWithSprache = (SpracheabhDocQueue) documentQueueItem;
			SpracheParamJax spracheParam = docQueueWithSprache.getSpracheParam(objectMapper);
			return reportingOdiImpfungenReportServiceBean.generateExcelReportOdiImpfungen(
				spracheParam.getSprache().getLocale(), docQueueWithSprache.getBenutzer().toId());
		case ODI_TERMINBUCHUNGEN:
			SpracheabhDocQueue docQueueTerminbuchung = (SpracheabhDocQueue) documentQueueItem;
			SpracheParamJax sprachparamTerminbuchung = docQueueTerminbuchung.getSpracheParam(objectMapper);
			return reportingOdiTerminbuchungenReportServiceBean.generateExcelReportOdiTerminbuchungen(
				sprachparamTerminbuchung.getSprache().getLocale(),
				docQueueTerminbuchung.getBenutzer().toId()
			);
		default:
			throw new AppFailureException("Unhandeled Document Type " + documentQueueItem.getTyp());
		}

	}

	private byte[] streamingOutputToByteArray(StreamingOutput streamingOutput) {

		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			streamingOutput.write(out);
			return out.toByteArray();
		} catch (Exception e) {
			throw new AppFailureException("problem writing to stream ", e);
		}
	}

	private void storeDocumentQueueItemResultFile(DocumentQueue documentQueueItem, byte[] content) {
		DocumentQueueResult documentQueueResult = new DocumentQueueResult();
		String filename = documentQueueItem.calculateFilename(objectMapper);

		CleanFileName cleanFileName = new CleanFileName(filename);
		FileBlob file = FileBlob.of(cleanFileName, MimeType.APPLICATION_OCTET_STREAM, content);
		documentQueueResult.setFileBlob(file);
		documentQueueItem.setDocumentQueueResult(documentQueueResult);
		documentQueueItem.markSuccessful();

	}

	public void completeUmfrage(@NonNull Umfrage umfrage) {
		runAsInternalSystemAdmin();
		umfrage.setTeilgenommen(true);
		umfrageRepo.update(umfrage);
	}

	@Transactional(TxType.NOT_SUPPORTED)
	public void runBoosterImmunisiertStatusImpfschutzService(boolean forcerun) {
		// Batch Job der nach Immunisiert schiebt
		if (!isBatchJobDisabled(VACME_BOOSTER_STATUSMOVER_JOB_DISABLED) || forcerun) {
			List<String> movedToImmunisiertRegnums;
			// Task soll als Systembenutzer laufen
			runAsInternalSystemAdmin();
			movedToImmunisiertRegnums = boosterRunnerService.performMoveOfAbgeschlosseneToImmunisiert();

			// Regs die nach Immunisiert geschoben wuren koennen evtl noch grad weiter.
			// Daher fuer diese Berechnung uber die Rule Engine triggern
			if (!movedToImmunisiertRegnums.isEmpty()) {
				this.boosterQueueRepo.createRegistrierungQueueItems(movedToImmunisiertRegnums);
				LOG.info(
					"VACME-BOOSTER-IMMUNISIERT: Einfuegen von {} QueueItems zur Impfschutzneuberechnung beendet",
					movedToImmunisiertRegnums.size());
			}
		}
	}

	@Transactional(TxType.NOT_SUPPORTED)
	public void runBoosterFreigabeStatusService(boolean forcerun) {
		// Batch job der nach FreigegebenBooster schiebt
		if (!isBatchJobDisabled(VACME_BOOSTER_FREIGABE_JOB_DISABLED) || forcerun) {
			// Task soll als Systembenutzer laufen
			runAsInternalSystemAdmin();
			boosterRunnerService.performMoveOfImmunisiertToFreigegebenBooster();
		}
	}

	@Transactional(TxType.NOT_SUPPORTED)
	public void runBoosterRuleRecalc(boolean forcerun) {
		if (!isBatchJobDisabled(VACME_BOOSTER_RULE_ENGINE_JOB_DISABLED) || forcerun) {
			runAsInternalSystemAdmin();
			boosterRunnerService.performImpfschutzCalculationByQueue();
		}
	}

	@Transactional
	public void runPriorityUpdateForGrowingChildren() {
		runAsInternalSystemAdmin();
		registrierungService.runPriorityUpdateForGrowingChildren();
	}
	@Transactional
	public void runAsyncDocumentCleanup() {
		documentQueueService.cleanupExpiredResults();
	}

	@Transactional(TxType.NOT_SUPPORTED)
	public void runAsyncDocCreation() {
		if (!isBatchJobDisabled(VACME_ASYNC_DOCUMENT_CREATION_DISABLED)) {

			runAsInternalSystemAdmin();
			List<DocumentQueue> unfinishedDocumentQueueItems = documentQueueService.findUnfinishedDocumentQueueItems();

			List<DocumentQueue> inProgressDocs = unfinishedDocumentQueueItems.stream()
				.filter(documentQueue -> DocumentQueueStatus.IN_PROGRESS == documentQueue.getStatus())
				.collect(Collectors.toList());

			LOG.info("VACME-DOC-CREATION: Aktuell sind noch {} Dokumenterstellung(en) im Status {}",
				inProgressDocs.size(), DocumentQueueStatus.IN_PROGRESS);
			unfinishedDocumentQueueItems.removeAll(inProgressDocs);

			documentqueueRunnerService.performDocumentGenerationRun(unfinishedDocumentQueueItems);
		}
	}

	public void runMassenverarbeitungQueueProcessing() {
		if (!isBatchJobDisabled(VACME_MASSENVERARBEITUNGQUEUE_PROCESSING_JOB_DISABLED)) {
			// Task soll als Systembenutzer laufen
			runAsInternalSystemAdmin();
			massenmutationRunnerService.performImpfungActionByQueue(MassenverarbeitungQueueTyp.IMPFUNG_EXTERALIZE);
			massenmutationRunnerService.performImpfungActionByQueue(MassenverarbeitungQueueTyp.IMPFUNG_ODI_MOVE);
			massenmutationRunnerService.performImpfungActionByQueue(MassenverarbeitungQueueTyp.REGISTRIERUNG_DELETE);
			massenmutationRunnerService.performImpfungActionByQueue(MassenverarbeitungQueueTyp.ODI_LAT_LNG_CALCULATE);
			massenmutationRunnerService.performImpfungActionByQueue(MassenverarbeitungQueueTyp.ENSURE_IMPFDOSSIER_PRESENT);
			massenmutationRunnerService.performImpfungActionByQueue(MassenverarbeitungQueueTyp.IMPFUNG_LOESCHEN);
			massenmutationRunnerService.performImpfungActionByQueue(MassenverarbeitungQueueTyp.IMPFUNG_VERANTWORTLICHER_CHANGE);
		}
	}

	public void runInactiveOdiUserDisableTask() {
		if (!isBatchJobDisabled(VACME_DEACTIVATE_UNUSED_USERACCOUNTS_JOB_DISABLED)) {
			// Task soll als Systembenutzer laufen
			runAsInternalSystemAdmin();
			benutzerMassenmutationRunnerService.performBenutzerInactiveOdiUserSperrenTask();
		}
	}
}
