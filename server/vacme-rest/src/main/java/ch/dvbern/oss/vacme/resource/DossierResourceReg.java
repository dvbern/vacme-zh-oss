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

package ch.dvbern.oss.vacme.resource;

import java.io.IOException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import ch.dvbern.oss.vacme.entities.UserPrincipal;
import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.benutzer.Benutzer;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.onboarding.Onboarding;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFileTyp;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impfslot;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import ch.dvbern.oss.vacme.entities.zertifikat.Zertifikat;
import ch.dvbern.oss.vacme.i18n.ServerMessageUtil;
import ch.dvbern.oss.vacme.jax.registration.DashboardJax;
import ch.dvbern.oss.vacme.jax.registration.ErkrankungJax;
import ch.dvbern.oss.vacme.jax.registration.ImpfslotJax;
import ch.dvbern.oss.vacme.jax.registration.NextFreierTerminJax;
import ch.dvbern.oss.vacme.jax.registration.OrtDerImpfungBuchungJax;
import ch.dvbern.oss.vacme.jax.registration.OrtDerImpfungDisplayNameExtendedJax;
import ch.dvbern.oss.vacme.jax.registration.OrtDerImpfungDisplayNameJax;
import ch.dvbern.oss.vacme.jax.registration.RegistrierungsCodeJax;
import ch.dvbern.oss.vacme.jax.registration.SelectOrtDerImpfungJax;
import ch.dvbern.oss.vacme.jax.registration.TerminbuchungJax;
import ch.dvbern.oss.vacme.jax.registration.ZertifikatJax;
import ch.dvbern.oss.vacme.repo.RegistrierungRepo;
import ch.dvbern.oss.vacme.rest.auth.Authorizer;
import ch.dvbern.oss.vacme.service.ApplicationPropertyCacheService;
import ch.dvbern.oss.vacme.service.DokumentService;
import ch.dvbern.oss.vacme.service.DossierService;
import ch.dvbern.oss.vacme.service.FragebogenService;
import ch.dvbern.oss.vacme.service.NextTerminCacheService;
import ch.dvbern.oss.vacme.service.OrtDerImpfungService;
import ch.dvbern.oss.vacme.service.PdfService;
import ch.dvbern.oss.vacme.service.RegistrierungService;
import ch.dvbern.oss.vacme.service.TerminbuchungService;
import ch.dvbern.oss.vacme.service.ZertifikatRunnerService;
import ch.dvbern.oss.vacme.service.ZertifikatService;
import ch.dvbern.oss.vacme.service.booster.BoosterService;
import ch.dvbern.oss.vacme.service.covidcertificate.CovidCertBatchType;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.service.onboarding.OnboardingService;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.shared.util.MimeType;
import ch.dvbern.oss.vacme.shared.util.OpenApiConst;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.util.QRCodeUtil;
import ch.dvbern.oss.vacme.util.RestUtil;
import ch.dvbern.oss.vacme.util.ValidationUtil;
import com.google.zxing.WriterException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.tags.Tags;
import org.hibernate.exception.ConstraintViolationException;

import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.FREIGEGEBEN;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.FREIGEGEBEN_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.GEBUCHT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.GEBUCHT_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_1_DURCHGEFUEHRT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_1_KONTROLLIERT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_2_KONTROLLIERT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.KONTROLLIERT_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ODI_GEWAEHLT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ODI_GEWAEHLT_BOOSTER;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.CC_AGENT;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.CC_BENUTZER_VERWALTER;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.IMPFTERMINCLIENT;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.IMPFWILLIGER;
import static ch.dvbern.oss.vacme.rest.auth.ClientApplications.VACME_INITIALREG;
import static ch.dvbern.oss.vacme.util.ZertifikatDownloadUtil.zertifikatBlobToDownloadResponse;

@ApplicationScoped
@Transactional
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Tags(@Tag(name = OpenApiConst.TAG_DOSSIER))
@Path(VACME_INITIALREG + "/dossier")
public class DossierResourceReg {

	@ConfigProperty(name = "vacme.terminreservation.enabled", defaultValue = "true")
	protected boolean terminReservationEnabled;

	private final Authorizer authorizer;
	private final DossierService dossierService;
	private final RegistrierungService registrierungService;
	private final RegistrierungRepo registrierungRepo;
	private final OrtDerImpfungService ortDerImpfungService;
	private final PdfService pdfService;
	private final DokumentService dokumentService;
	private final TerminbuchungService terminbuchungService;
	private final FragebogenService fragebogenService;
	private final NextTerminCacheService nextTerminCacheService;
	private final UserPrincipal userPrincipal;
	private final ZertifikatService zertifikatService;
	private final ZertifikatRunnerService zertifikatRunnerService;
	private final ApplicationPropertyCacheService propertyCacheService;
	private final ImpfinformationenService impfinformationenService;
	private final OnboardingService onboardingService;
	private final TransactionManager tm;
	private final BoosterService boosterService;

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/termine/frei/{ortDerImpfungId}/{impffolge}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT, IMPFTERMINCLIENT })
	public List<ImpfslotJax> getFreieImpftermine(
		@NonNull @NotNull @PathParam("ortDerImpfungId") UUID ortDerImpfungId,
		@NonNull @NotNull @PathParam("impffolge") Impffolge impffolge,
		@NonNull @NotNull
		@Parameter(description = "Datum zu dem der erste passende Gegenstuecktermin gefunden werden soll")
			NextFreierTerminJax date
	) {
		return ortDerImpfungService
			.getFreieImpftermine(OrtDerImpfung.toId(ortDerImpfungId), impffolge, date.getNextDate().toLocalDate())
			.stream()
			.map(ImpfslotJax::new)
			.collect(Collectors.toList());
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/termine/nextfrei/{ortDerImpfungId}/{impffolge}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT, IMPFTERMINCLIENT })
	@Schema(format = OpenApiConst.Format.DATE_TIME)
	@Nullable
	public NextFreierTerminJax getNextFreierImpftermin(
		@NonNull @NotNull @PathParam("ortDerImpfungId") UUID ortDerImpfungId,
		@NonNull @NotNull @PathParam("impffolge") Impffolge impffolge,
		@Nullable @Parameter(description = "Datum zu dem der erste passende Gegenstuecktermin gefunden werden soll")
			NextFreierTerminJax otherTerminDate
	) {
		LocalDateTime nextDate = otherTerminDate != null ? otherTerminDate.getNextDate() : null;
		LocalDateTime nextFreierImpftermin;

		// erstimpfungen with no other Termindate and Boostertermine are cached
		nextFreierImpftermin =
			nextTerminCacheService.getNextFreierImpfterminThroughCache(
				OrtDerImpfung.toId(ortDerImpfungId),
				impffolge,
				nextDate,
				nextDate != null); // if the otherTerminDate is null then the last argument does not really matter but cache
		// expects it to be false
		if (nextFreierImpftermin == null) {
			return null;
		}
		return new NextFreierTerminJax(nextFreierImpftermin);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/termine/nextfrei/{ortDerImpfungId}/umbuchung")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	@Schema(format = OpenApiConst.Format.DATE_TIME)
	@Nullable
	public NextFreierTerminJax getNextFreierImpfterminUmbuchung(
		@NonNull @NotNull @PathParam("ortDerImpfungId") UUID ortDerImpfungId,
		@Nullable @Parameter(description = "Datum zu dem der erste passende Gegenstuecktermin gefunden werden soll")
			NextFreierTerminJax otherTerminDate
	) {
		LocalDateTime nextDate = otherTerminDate != null ? otherTerminDate.getNextDate() : null;
		LocalDateTime nextFreierImpftermin = ortDerImpfungService.getNextFreierImpftermin(
			OrtDerImpfung.toId(ortDerImpfungId), Impffolge.ZWEITE_IMPFUNG, nextDate, false);
		if (nextFreierImpftermin == null) {
			return null;
		}
		return new NextFreierTerminJax(nextFreierImpftermin);
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.WILDCARD)
	@Path("/buchen")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public Response termineBuchen(
		@NonNull @NotNull @Valid TerminbuchungJax termin
	) {
		String registrierungsnummer = termin.getRegistrierungsnummer();

		final Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		authorizer.checkUpdateAuthorization(registrierung);

		// Wenn wir hier hin kommen, hat sich der Benutzer - wenn er nicht sowieso schon mobil war - als immobiler
		// nachtraeglich fuer ein stationaeres ODI entschieden
		registrierung.setImmobil(false);

		try {
			if (RegistrierungStatus.getAnyStatusOfGrundimmunisiert().contains(registrierung.getRegistrierungStatus())) {
				// Wir sind schon im booster
				dossierService.termineBuchenBooster(
					registrierung,
					Impfslot.toId(termin.getSlotNId()));
			} else {
				dossierService.termineBuchenGrundimmunisierung(
					registrierung,
					Impfslot.toId(termin.getSlot1Id()),
					Impfslot.toId(termin.getSlot2Id()));
			}
		} catch (Exception exception) {
			Throwable rootCause = ExceptionUtils.getRootCause(exception);
			if (rootCause != null) {

				if (rootCause.getClass().equals(SQLIntegrityConstraintViolationException.class) ||
					ConstraintViolationException.class.equals(rootCause.getClass())
				) {
					throw AppValidationMessage.IMPFTERMIN_BESETZT.create("");
				}

			}
			throw exception;
		}
		return Response.ok().build();
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.WILDCARD)
	@Path("/selectOdi")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public Response selectOrtDerImpfung(
		@NonNull @NotNull @Valid SelectOrtDerImpfungJax selectJax
	) {
		String registrierungsnummer = selectJax.getRegistrierungsnummer();
		final Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		authorizer.checkUpdateAuthorization(registrierung);

		dossierService.selectOrtDerImpfung(
			registrierung,
			OrtDerImpfung.toId(selectJax.getOdiId()));
		return Response.ok().build();
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.WILDCARD)
	@Path("/selectNichtVerwalteterOdi/{registrierungsnummer}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public Response selectNichtVerwalteterOrtDerImpfung(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		final Registrierung registrierung = registrierungRepo
			.getByRegistrierungnummer(registrierungsnummer)
			.orElseThrow(() -> AppValidationMessage.UNKNOWN_REGISTRIERUNGSNUMMER.create(registrierungsnummer));

		authorizer.checkUpdateAuthorization(registrierung);

		dossierService.selectNichtVerwalteterOrtDerImpfung(registrierung);

		return Response.ok().build();
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("reservieren/{registrierungsnummer}/{impfslotId}/{impffolge}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public Response reservieren(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer,
		@NonNull @NotNull @PathParam("impfslotId") UUID impfslotId,
		@NonNull @NotNull @PathParam("impffolge") Impffolge impffolge
	) {
		// Falls die Reservation ausgeschaltet ist, wollen wir direkt abbrechen
		if (!terminReservationEnabled) {
			return Response.ok().build();
		}

		Objects.requireNonNull(registrierungsnummer);
		Objects.requireNonNull(impfslotId);
		Objects.requireNonNull(impffolge);

		final Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		authorizer.checkUpdateAuthorization(registrierung);

		terminbuchungService.reservieren(registrierung, Impfslot.toId(impfslotId), impffolge);
		return Response.ok().build();
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("umbuchen")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public Response umbuchen(
		@NonNull @NotNull @Valid TerminbuchungJax termin
	) {
		String registrierungsnummer = termin.getRegistrierungsnummer();

		final Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		authorizer.checkUpdateAuthorization(registrierung);

		if (RegistrierungStatus.getAnyStatusOfGrundimmunisiert().contains(registrierung.getRegistrierungStatus())) {
			// Booster
			// muss min. freigebeben_booster sein zum selber buchen!
			ValidationUtil.validateStatusOneOf(
				registrierung,
				FREIGEGEBEN_BOOSTER, ODI_GEWAEHLT_BOOSTER, GEBUCHT_BOOSTER, KONTROLLIERT_BOOSTER);

			terminbuchungService.umbuchenBooster(
				registrierung,
				Impfslot.toId(termin.getSlotNId()));
		} else {
			// Impfung 1/2
			// muss mind. freigegeben sein zum selber buchen!
			ValidationUtil.validateStatusOneOf(
				registrierung,
				FREIGEGEBEN,
				ODI_GEWAEHLT,
				GEBUCHT,
				IMPFUNG_1_KONTROLLIERT,
				IMPFUNG_1_DURCHGEFUEHRT,
				IMPFUNG_2_KONTROLLIERT);

			terminbuchungService.umbuchenGrundimmunisierung(
				registrierung,
				Impfslot.toId(termin.getSlot1Id()),
				Impfslot.toId(termin.getSlot2Id()));
		}

		return Response.ok().build();
	}

	/**
	 * Cancel the selected ODI and additionally cancel any booked termin
	 *
	 * @param registrierungsnummer no-doc
	 * @return status code
	 */
	@DELETE
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.WILDCARD)
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	@Path("/cancel/{registrierungsnummer}")
	public Response odiAndTermineAbsagen(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		final Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		authorizer.checkUpdateAuthorization(registrierung);

		dossierService.odiAndTermineAbsagen(registrierung);

		return Response.ok().build();
	}

	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/dashboard/{registrierungsnummer}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public DashboardJax getDashboardRegistrierung(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {

		ImpfinformationDto impfinformationen =
			this.impfinformationenService.getImpfinformationenAndEnsureStatusIsAktuell(registrierungsnummer);
		Registrierung registrierung = impfinformationen.getRegistrierung();
		authorizer.checkReadAuthorization(registrierung);
		Fragebogen fragebogen = this.fragebogenService.findFragebogenByRegistrierungsnummer(registrierungsnummer);
		boolean hasCovidZertifikat = false;
		LocalDateTime timestampLetzterPostversand = null;
		if (RegistrierungStatus.getStatusWithPossibleZertifikat().contains(registrierung.getRegistrierungStatus())) {
			hasCovidZertifikat = zertifikatService.hasCovidZertifikat(registrierungsnummer);
			timestampLetzterPostversand = zertifikatRunnerService.getTimestampOfLastPostversand(registrierung);
		}

		return new DashboardJax(
			impfinformationen,
			fragebogen,
			hasCovidZertifikat,
			timestampLetzterPostversand
		);

	}

	@GET
	@Nullable
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/onboarding/dashboard/{onboardingCode}")
	@RolesAllowed({ CC_AGENT, CC_BENUTZER_VERWALTER })
	public DashboardJax getDashboardRegistrierungByOnboardingCode(
		@NonNull @NotNull @PathParam("onboardingCode") String onboardingCode
	) {
		Optional<Onboarding> onboardingByCode = this.onboardingService.findOnboardingByCode(onboardingCode);
		if (onboardingByCode.isPresent()) {
			Onboarding onboarding = onboardingByCode.get();
			Registrierung registrierung = onboarding.getRegistrierung();
			authorizer.checkReadAuthorization(registrierung);
			boolean hasCovidZertifikat = false;

			LocalDateTime timestampLetzterPostversand = null;
			if (RegistrierungStatus.getStatusWithPossibleZertifikat()
				.contains(registrierung.getRegistrierungStatus())) {
				hasCovidZertifikat = zertifikatService.hasCovidZertifikat(registrierung.getRegistrierungsnummer());
				timestampLetzterPostversand = zertifikatRunnerService.getTimestampOfLastPostversand(registrierung);
			}

			ImpfinformationDto impfinformationen =
				this.impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
			Fragebogen fragebogen =
				this.fragebogenService.findFragebogenByRegistrierungsnummer(registrierung.getRegistrierungsnummer());

			return new DashboardJax(impfinformationen, fragebogen, hasCovidZertifikat, timestampLetzterPostversand);
		}
		return null;
	}

	@GET
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/odi/all/{registrierungsnummer}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT, IMPFTERMINCLIENT })
	public List<OrtDerImpfungDisplayNameExtendedJax> getAllOrteDerImpfungDisplayName(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Fragebogen fragebogen = fragebogenService.findFragebogenByRegistrierungsnummer(registrierungsnummer);
		authorizer.checkReadAuthorization(fragebogen.getRegistrierung());
		return ortDerImpfungService.findAllActivePublicFiltered(fragebogen)
			.stream()
			.map(ortDerImpfung -> {
				OrtDerImpfungDisplayNameExtendedJax odiJax = new OrtDerImpfungDisplayNameExtendedJax(ortDerImpfung);
				if (!ortDerImpfung.isNoFreieTermine1()) {
					LocalDateTime nextTermin1 = nextTerminCacheService.getNextFreierImpfterminThroughCache(
						ortDerImpfung.toId(),
						Impffolge.ERSTE_IMPFUNG,
						null,
						false);
					odiJax.setNextTermin1Date(nextTermin1);
				}

				if (!ortDerImpfung.isNoFreieTermine2()) {
					LocalDateTime nextTermin2 = nextTerminCacheService.getNextFreierImpfterminThroughCache(
						ortDerImpfung.toId(),
						Impffolge.ZWEITE_IMPFUNG,
						null,
						false);
					odiJax.setNextTermin2Date(nextTermin2);
				}

				if (!ortDerImpfung.isNoFreieTermineN()) {
					LocalDateTime nextTerminN = nextTerminCacheService.getNextFreierImpfterminThroughCache(
						ortDerImpfung.toId(),
						Impffolge.BOOSTER_IMPFUNG,
						null,
						false);
					odiJax.setNextTerminNDate(nextTerminN);
				}

				return odiJax;
			})
			.collect(Collectors.toList());
	}

	@GET
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/impfterminclient/odi")
	@RolesAllowed({ IMPFTERMINCLIENT })
	/**
	 * Wird von uns nicht direkt verwendet, aber von Rimpfli (externe api) (apiV1RegDossierImpfterminclientOdiGet)
	 */
	public List<OrtDerImpfungDisplayNameJax> getAllOrteDerImpfungForImpfterminclient() {
		return ortDerImpfungService.findAllActivePublic()
			.stream()
			.map(OrtDerImpfungDisplayNameJax::new)
			.collect(Collectors.toList());
	}

	@GET()
	@Produces({ "image/gif" })
	@Path("/qr-code/{code}")
	@PermitAll
	public Response getQrCode(@NotNull @PathParam("code") String code) {
		// todo team, das koennte man jetzt einschraenken da wir den qr code link nicht vermailen
		final Config config = ConfigProvider.getConfig();
		String baseUrl = config.getValue("vacme.server.base.url", String.class);
		String url = baseUrl + "/dossier/" + code;
		Response.ResponseBuilder responseBuilder;
		try {
			responseBuilder = Response.ok(QRCodeUtil.createQRImage(url, 300));
			responseBuilder.header("Content-Disposition", "attachment; filename=\"qrcode.gif\"");
			return responseBuilder.build();
		} catch (WriterException | IOException e) {
			LOG.error("Could not download qr-code", e);
			return Response.serverError().build();
		}
	}

	@POST
	@Consumes(MediaType.WILDCARD)
	@APIResponse(content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM, schema = @Schema(type = SchemaType.STRING, format = "binary")), description = "OK", responseCode = "200")
	@Path("download/registrierungsbestaetigung/{registrierungsnummer}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public Response downloadRegistrierungsbestaetigung(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		authorizer.checkReadAuthorization(registrierung);

		final byte[] content = pdfService.createRegistrationsbestaetigung(registrierung);
		return createDownloadResponse(RegistrierungFileTyp.REGISTRIERUNG_BESTAETIGUNG, registrierungsnummer, content);
	}

	@POST
	@Consumes(MediaType.WILDCARD)
	@APIResponse(content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM, schema = @Schema(type = SchemaType.STRING, format = "binary")), description = "OK", responseCode = "200")
	@Path("download/terminbestaetigung/{registrierungsnummer}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public Response downloadTerminbestaetigung(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		authorizer.checkReadAuthorization(registrierung);
		if (!registrierung.isNichtVerwalteterOdiSelected()
			&& registrierung.getImpftermin1() == null
			&& registrierung.getGewuenschterOdi() == null) {
			throw AppValidationMessage.ILLEGAL_STATE.create("Kein Impftermin oder Gewuenschter ODI");
		}

		Impftermin boosterTerminOrNull = findBoosterTerminOrNull(registrierung);
		final byte[] content = pdfService.createTerminbestaetigung(registrierung, boosterTerminOrNull);
		return createDownloadResponse(RegistrierungFileTyp.TERMIN_BESTAETIGUNG, registrierungsnummer, content);
	}

	@Nullable
	private Impftermin findBoosterTerminOrNull(@NonNull @NotNull Registrierung registrierung) {
		Impftermin boosterTerminOrNull = null;
		if (RegistrierungStatus.getAnyStatusOfGrundimmunisiert().contains(registrierung.getRegistrierungStatus())) {
			final ImpfinformationDto infos =
				impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
			final Optional<Impftermin> pendingBoosterTermin = ImpfinformationenService.getPendingBoosterTermin(infos);
			if (pendingBoosterTermin.isPresent()) {
				boosterTerminOrNull = pendingBoosterTermin.get();
			}
		}
		return boosterTerminOrNull;
	}

	@POST
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("terminbestaetigung/erneutsenden/{registrierungsnummer}")
	@RolesAllowed({ CC_AGENT })
	public RegistrierungsCodeJax terminbestaetigungErneutSenden(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);
		Impftermin boosterTerminOrNull = findBoosterTerminOrNull(registrierung);
		terminbuchungService.terminbestaetigungErneutSenden(registrierung, boosterTerminOrNull);

		return RegistrierungsCodeJax.from(registrierung);
	}

	@GET
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/odibuchung/{odiId}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public OrtDerImpfungBuchungJax getAllOrteDerImpfungBuchung(
		@NonNull @NotNull @PathParam("odiId") UUID odiId
	) {
		OrtDerImpfung odi = ortDerImpfungService.getById(OrtDerImpfung.toId(odiId));
		return OrtDerImpfungBuchungJax.from(odi);

	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@APIResponse(content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM, schema = @Schema(type = SchemaType.STRING, format = "binary")), description = "OK", responseCode = "200")
	@Path("download/impfdokumentation/{registrierungsnummer}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public Response downloadImpfdokumentation(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		authorizer.checkReadAuthorization(registrierung);

		final byte[] content = dokumentService.getOrCreateImpfdokumentationPdf(registrierung);
		return createDownloadResponse(RegistrierungFileTyp.IMPF_DOKUMENTATION, registrierungsnummer, content);
	}

	@POST
	@Consumes(MediaType.WILDCARD)
	@APIResponse(content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM, schema = @Schema(type = SchemaType.STRING, format = "binary")), description = "OK", responseCode = "200")
	@Path("download/zertifikat/{registrierungsnummer}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	/* Das Callcenter soll eigentlich das Zertifikat nicht sehen, ausser es ist eh schon auf der Overview.
		 Aber wenn das Zertifikat ueber die Reg-UUID erreichbar ist, kann das CC jede Person in der Adressaenderung
		 suchen,
		 dann das Zertifikat per RegID herunterladen, dort die UVCI lesen und die Registrierung in der UVCI-Suche
		 komplett oeffnen.
		  DESHALB: per RegNr herunterladen, nicht per RegID */
	public Response downloadBestZertifikatForRegistrierung(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		authorizer.checkReadAuthorization(registrierung);
		Optional<Zertifikat> zertifikatOpt = zertifikatService.getBestMatchingZertifikat(registrierung);
		if (zertifikatOpt.isPresent()) {
			return zertifikatBlobToDownloadResponse(zertifikatService.getZertifikatPdf(zertifikatOpt.get()));
		} else {
			throw AppValidationMessage.NO_ZERTIFIKAT_PDF.create(registrierungsnummer);
		}
	}

	@GET
	@Consumes(MediaType.WILDCARD)
	@APIResponse(content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM, schema = @Schema(type = SchemaType.STRING, format = "binary")), description = "OK", responseCode = "200")
	@Path("download/zertifikatwithid/{zertifikatid}/{registrierungsnummer}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public Response downloadZertifikatWithId(
		@NonNull @NotNull @PathParam("zertifikatid") UUID zertifikatId,
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);
		authorizer.checkReadAuthorization(registrierung);
		Zertifikat zertifikat = zertifikatService.getZertifikatById(new ID(zertifikatId, Zertifikat.class));
		return zertifikatBlobToDownloadResponse(this.zertifikatService.getZertifikatPdf(zertifikat));
	}

	@NonNull
	private Response createDownloadResponse(
		@NonNull RegistrierungFileTyp fileTyp,
		@NonNull String registrierungsnummer,
		byte[] content) {

		return RestUtil.createDownloadResponse(ServerMessageUtil.translateEnumValue(fileTyp, Locale.GERMAN,
			registrierungsnummer), content, MimeType.APPLICATION_PDF);

	}

	@DELETE
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("deleteRegistrierung/{registrierungsnummer}")
	@RolesAllowed(IMPFWILLIGER)
	public Response deleteRegistrierung(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Objects.requireNonNull(registrierungsnummer);

		ImpfinformationDto impfinfos =
			impfinformationenService.getImpfinformationenAndEnsureStatusIsAktuell(registrierungsnummer);
		final Fragebogen fragebogen = fragebogenService.findFragebogenByRegistrierungsnummer(registrierungsnummer);

		// Auf Seite Registrierung darf nur der Benutzer sich selber loeschen
		authorizer.checkUpdateAuthorization(fragebogen.getRegistrierung());

		registrierungService.deleteRegistrierung(impfinfos, fragebogen);
		return Response.ok().build();
	}

	@DELETE
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("deleteBenutzer")
	@RolesAllowed(IMPFWILLIGER)
	public Response deleteBenutzer(
	) {
		final Benutzer loggedInBenutzer = userPrincipal.getBenutzerOrThrowException();
		registrierungService.deleteBenutzer(loggedInBenutzer);
		return Response.ok().build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("isZertifikatEnabled")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public boolean isZertifikatEnabled() {
		return propertyCacheService.isZertifikatEnabled();
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{registrierungId}/zertifikat/resend")
	@RolesAllowed(CC_AGENT)
	public Response recreatePerPost(
		@NonNull @NotNull @PathParam("registrierungId") UUID registrierungId
	) throws Exception {

		Registrierung registrierung = registrierungService.findRegistrierungById(registrierungId)
			.orElseThrow(() -> AppFailureException.entityNotFound(Registrierung.class, registrierungId.toString()));
		authorizer.checkUpdateAuthorization(registrierung);

		final ImpfinformationDto infos =
			impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
		// In diesen Fall versuchen wir, fuer die neueste Impfung ein Zertifikat zu erstellen
		ID<Impfung> idOfNewestImpfung = ImpfinformationenService.getNewestVacmeImpfungId(infos);

		this.zertifikatRunnerService.createZertifikatForRegistrierung(registrierung, idOfNewestImpfung, CovidCertBatchType.POST);
		return Response.ok().build();
	}

	@GET
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("list/zertifikate/{registrierungsnummer}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public List<ZertifikatJax> getAllZertifikate(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer) {

		ImpfinformationDto infos = impfinformationenService.getImpfinformationenNoCheck(registrierungsnummer);
		authorizer.checkReadAuthorization(infos.getRegistrierung());

		List<Zertifikat> zertifikatList =
			zertifikatService.getAllZertifikateRegardlessOfRevocation(infos.getRegistrierung());

		return zertifikatService.mapToZertifikatJax(zertifikatList);

	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("erkrankungen/{registrierungsnummer}/{rollback}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public DashboardJax updateErkrankungen(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer,
		@NonNull @NotNull @PathParam("rollback") boolean rollback,
		@Nullable @Parameter List<ErkrankungJax> erkrankungJaxList
	) throws SystemException {
		Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);
		authorizer.checkUpdateAuthorization(registrierung);

		// Hierhin sollten wir nur aus einem BoosterStatus kommen
		if (!RegistrierungStatus.getAnyStatusOfGrundimmunisiert().contains(registrierung.getRegistrierungStatus())) {
			throw AppValidationMessage.ILLEGAL_STATE.create(
				"Erkrankungen (Selbstdeklaration) darf man nur im Booster-Status bearbeiten");
		}
		// Im Status Booster-Kontrolliert darf man die Erkrankung auch nicht bearbeiten
		if (KONTROLLIERT_BOOSTER == registrierung.getRegistrierungStatus()) {
			throw AppValidationMessage.ILLEGAL_STATE.create("Erkrankungen (Selbstdeklaration) darf man nach der Kontrolle nicht bearbeiten");
		}

		assert erkrankungJaxList != null;
		dossierService.updateErkrankungen(registrierung, erkrankungJaxList);

		if (rollback) {
			// Mit rollback=true kann man ausprobieren, was passieren wuerde, wenn man die Erkrankungen speichern wuerde
			tm.setRollbackOnly();
		}

		// Reload
		ImpfinformationDto impfinformationen =
			this.impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
		Fragebogen fragebogen = this.fragebogenService.findFragebogenByRegistrierungsnummer(registrierungsnummer);
		return new DashboardJax(impfinformationen, fragebogen);
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.WILDCARD)
	@Path("/mobil/{registrierungsnummer}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public Response changeToMobil(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		final Registrierung registrierung = registrierungRepo
			.getByRegistrierungnummer(registrierungsnummer)
			.orElseThrow(() -> AppValidationMessage.UNKNOWN_REGISTRIERUNGSNUMMER.create(registrierungsnummer));

		authorizer.checkUpdateAuthorization(registrierung);

		registrierung.setImmobil(false);

		boosterService.recalculateImpfschutzAndStatusmovesForSingleReg(registrierung);

		return Response.ok().build();
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.WILDCARD)
	@Path("/selbstzahler/{registrierungsnummer}/{selbstzahler}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public Response changeSelbstzahler(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer,
		@PathParam("selbstzahler") boolean selbstzahler
	) {
		final Registrierung registrierung = registrierungRepo
			.getByRegistrierungnummer(registrierungsnummer)
			.orElseThrow(() -> AppValidationMessage.UNKNOWN_REGISTRIERUNGSNUMMER.create(registrierungsnummer));

		authorizer.checkUpdateAuthorization(registrierung);

		registrierung.setSelbstzahler(selbstzahler);
		return Response.ok().build();
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.WILDCARD)
	@Path("/phoneNumberUpdate/{registrierungsnummer}")
	@RolesAllowed({ IMPFWILLIGER, CC_AGENT })
	public Response setTimestampPhonenumberUpdate(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		final Registrierung registrierung = registrierungRepo
			.getByRegistrierungnummer(registrierungsnummer)
			.orElseThrow(() -> AppValidationMessage.UNKNOWN_REGISTRIERUNGSNUMMER.create(registrierungsnummer));

		registrierung.setTimestampPhonenumberUpdate(LocalDateTime.now());
		return Response.ok().build();
	}
}

