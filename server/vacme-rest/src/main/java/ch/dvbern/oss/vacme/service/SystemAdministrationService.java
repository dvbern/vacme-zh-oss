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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import ch.dvbern.oss.vacme.entities.benutzer.Benutzer;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfungTyp;
import ch.dvbern.oss.vacme.entities.types.FachRolle;
import ch.dvbern.oss.vacme.entities.types.daterange.DateUtil;
import ch.dvbern.oss.vacme.entities.zertifikat.Zertifikat;
import ch.dvbern.oss.vacme.i18n.ServerMessageUtil;
import ch.dvbern.oss.vacme.jax.ForceTerminJax;
import ch.dvbern.oss.vacme.jax.registration.DashboardJax;
import ch.dvbern.oss.vacme.jax.registration.OdiUserDisplayNameJax;
import ch.dvbern.oss.vacme.repo.ImpfterminRepo;
import ch.dvbern.oss.vacme.repo.RegistrierungRepo;
import ch.dvbern.oss.vacme.reports.zweitBoosterMail.MailZweitBoosterServiceBean;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.service.plz.PLZService;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SystemAdministrationService {

	@ConfigProperty(name = "vacme.service.reporting.anzahlerstimpfungen.mail")
	String serviceReportingAnzahlErstimpfungenMail;

	@ConfigProperty(name = "vacme.service.reporting.anzahlzweitbooster.mail")
	String serviceReportingAnzahlZweitBoosterMail;

	private final RegistrierungRepo registrierungRepo;
	private final ImpfterminRepo impfterminRepo;
	private final PLZService plzService;
	private final BenutzerService benutzerService;
	private final KeyCloakService keyCloakService;
	private final TerminbuchungService terminbuchungService;
	private final OrtDerImpfungService ortDerImpfungService;
	private final MailService mailService;
	private final ZertifikatService zertifikatService;
	private final ZertifikatRunnerService zertifikatRunnerService;
	private final ImpfinformationenService impfinformationenService;
	private final FragebogenService fragebogenService;
	private final MailZweitBoosterServiceBean mailZweitBoosterServiceBean;

	public DashboardJax forceTerminbuchung(@NonNull @NotNull @Valid ForceTerminJax forceTerminJax) {

		Registrierung registrierung =
			this.registrierungRepo.getByRegistrierungnummer(forceTerminJax.getRegistrierungsNummer())
				.orElseThrow(() -> AppFailureException.entityNotFound
					(Registrierung.class, forceTerminJax.getRegistrierungsNummer()));

		final OrtDerImpfung ortDerImpfung =
			ortDerImpfungService.getById(OrtDerImpfung.toId(forceTerminJax.getOrtDerImpfungId()));

		// Status muss FREIGEGEBEN sein
		ValidationUtil.validateStatus(registrierung, RegistrierungStatus.FREIGEGEBEN);
		// Der Zeitabstand zwischen den Terminen wird nicht validiert da es eine Adminfunktion ist

		//darf noch keine Termine haben
		ValidationUtil.validateNoTermine(registrierung);

		// forciere 1. Termin
		LocalDateTime termin1Time = forceTerminJax.getTermin1Time();
		Impftermin termin1 = this.terminbuchungService.createOnDemandImpftermin(Impffolge.ERSTE_IMPFUNG,
			ortDerImpfung, termin1Time);
		impfterminRepo.termin1Speichern(registrierung, termin1);
		// forciere 2. Termin
		LocalDateTime termin2Time = forceTerminJax.getTermin2Time();
		Impftermin termin2 = this.terminbuchungService.createOnDemandImpftermin(Impffolge.ZWEITE_IMPFUNG,
			ortDerImpfung, termin2Time);
		impfterminRepo.termin2Speichern(registrierung, termin2);

		registrierung.setGewuenschterOdi(ortDerImpfung);
		registrierung.setNichtVerwalteterOdiSelected(false); // nun ist ein konkretes ODI bekannt
		registrierung.setRegistrierungStatus(RegistrierungStatus.GEBUCHT);

		boolean hasCovidZertifikat = false;

		LocalDateTime timestampLetzterPostversand = null;
		if (RegistrierungStatus.getStatusWithPossibleZertifikat().contains(registrierung.getRegistrierungStatus())) {
			hasCovidZertifikat = zertifikatService.hasCovidZertifikat(registrierung.getRegistrierungsnummer());
			timestampLetzterPostversand = zertifikatRunnerService.getTimestampOfLastPostversand(registrierung);
		}
		ImpfinformationDto impfinformationen =
			this.impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
		Fragebogen fragebogen =
			this.fragebogenService.findFragebogenByRegistrierungsnummer(registrierung.getRegistrierungsnummer());

		return new DashboardJax(impfinformationen, fragebogen, hasCovidZertifikat, timestampLetzterPostversand);

	}

	@NonNull
	public String importPlzKantonIntoDatabase() {
		return this.plzService.importIntoDatabase();
	}

	@NonNull
	public String importPlzMedstatIntoDatabase() {
		return this.plzService.importMedstatIntoDatabase();
	}

	public void importMissingGln() {
		List<OdiUserDisplayNameJax> userInRole = keyCloakService.getUserInRole(FachRolle.FACHVERANTWORTUNG_BAB);
		userInRole.stream()
			.filter(odiUserDisplayNameJax -> odiUserDisplayNameJax.getId() != null)
			.map(odiUserDisplayNameJax -> benutzerService.getById(Benutzer.toId(UUID.fromString(odiUserDisplayNameJax.getId()))))
			.forEach(userInDb -> userInDb.ifPresent(benutzer -> keyCloakService.getGlnNummerOfUser(benutzer.getBenutzername())
				.ifPresent(glnNummer -> {
					LOG.info("update gln nummer of Benutzer {} to {}", benutzer.getBenutzername(), glnNummer);
					benutzer.setGlnNummer(glnNummer);
				})));
	}

	public void runServiceReportingAnzahlErstimpfungenMailTask(@NonNull LocalDate von, @NonNull LocalDate bis) {
		if (StringUtils.isNotEmpty(serviceReportingAnzahlErstimpfungenMail)) {
			final String vonFormatted = DateUtil.formatDate(von, Locale.GERMAN);
			final String bisFormatted = DateUtil.formatDate(bis, Locale.GERMAN);

			final String zeitraum = vonFormatted + " - " + bisFormatted;
			var subject =
				ServerMessageUtil.getMessage("mail_service_reporting_anzahlerstimpfungen_subject", Locale.GERMAN,
					zeitraum);
			var content =
				ServerMessageUtil.getMessage("mail_service_reporting_anzahlerstimpfungen_content", Locale.GERMAN,
					vonFormatted, bisFormatted);
			long totalErst = 0;
			long totalBooster = 0;
			long totalBoosterKalenderjahr =
				0; // Boosterimpfungen ohne vorherige 1. Impfung oder Booster-Impfung im Kalenderjahr
			for (OrtDerImpfungTyp typ : OrtDerImpfungTyp.values()) {
				var anzahlErst = registrierungRepo.getAnzahlErstimpfungen(typ, von, bis);
				totalErst += anzahlErst;
				var anzahlBooster = registrierungRepo.getAnzahlBoosterOrGrundimunisierungGT3(typ, von, bis);
				totalBooster += anzahlBooster;
				var anzahlBoosterKalenderjahr =
					registrierungRepo.getAnzahlBoosterOhneErstimpfungOderBoosterImKalenderjahr(typ, von, bis);
				totalBoosterKalenderjahr += anzahlBoosterKalenderjahr;
				var odiTypTrans = ServerMessageUtil.getMessage("OrtDerImpfungTyp_" + typ.name(), Locale.GERMAN);
				content += String.format(
					"- %s: %d Erstimpfungen, %d Boosterimpfungen, %d Boosterimpfungen ohne vorherige 1. Impfung oder "
						+ "Booster-Impfung im Kalenderjahr\n",
					odiTypTrans,
					anzahlErst,
					anzahlBooster,
					anzahlBoosterKalenderjahr);
			}
			content += String.format(
				"%s: %d Erstimpfungen, %d Boosterimpfungen, %d Boosterimpfungen ohne vorherige 1. Impfung oder "
					+ "Booster-Impfung im Kalenderjahr\nSumme: %d",
				"Total",
				totalErst,
				totalBooster,
				totalBoosterKalenderjahr,
				totalErst + totalBooster);

			mailService.sendTextMail(serviceReportingAnzahlErstimpfungenMail, subject, content, false);
		}
	}

	public void runServiceReportingAnzahlZweitBoosterMailTask() {
		final String subject = "Reporting 2. Booster-Impfung";
		final String content = mailZweitBoosterServiceBean.generateMailContent();

		Arrays.stream(serviceReportingAnzahlZweitBoosterMail.split(";"))
			.forEach(recipient -> mailService.sendHtmlMail(recipient, subject, content, false));
	}

	@NonNull
	public Map<String, List<Zertifikat>> findCertsToRevoke(@NonNull List<String> regNums) {
		Map<String, List<Zertifikat>> revokeMap = new LinkedHashMap<>();
		for (String regNum : regNums) {
			Optional<Registrierung> byRegistrierungnummer = registrierungRepo.getByRegistrierungnummer(regNum);
			if (byRegistrierungnummer.isPresent()) {
				List<Zertifikat> allNonRevokedZertifikate = zertifikatService.getAllNonRevokedZertifikate(regNum);
				allNonRevokedZertifikate.sort(Comparator.comparing(Zertifikat::getTimestampErstellt));
				if (allNonRevokedZertifikate.size() > 1) {
					List<Zertifikat> zertifikateToRevoke = allNonRevokedZertifikate.subList(
						0, allNonRevokedZertifikate.size() - 1);
					zertifikateToRevoke.forEach(zertifikat -> LOG.info(
						"VACME-ZERTIFIKAT-REVOKEPENDING: "
							+ "Registrierungsnummer: {}, Cert UVCI '{}'",
						regNum,
						zertifikat.getUvci()));
					revokeMap.put(regNum, new ArrayList<>(zertifikateToRevoke));
				}
			}
		}
		return revokeMap;
	}

	public void queueRevocationsForZertifikate(@NonNull Map<String, List<Zertifikat>> regNumToCertsToRevoke) {
		regNumToCertsToRevoke.entrySet().stream().forEach(regToZertEntry -> {
			String zertsToRevoke =
				regToZertEntry.getValue().stream().map(Zertifikat::getUvci).collect(Collectors.joining(","));
			LOG.info("VACME-ZERTIFIKAT-REVOKEPENDING: Adding Certificates to revocation queue for {}: "
				+ "queued {} for revocation", regToZertEntry.getKey(), zertsToRevoke);
			regToZertEntry.getValue().forEach(this.zertifikatService::queueForRevocation);
		});
	}
}
