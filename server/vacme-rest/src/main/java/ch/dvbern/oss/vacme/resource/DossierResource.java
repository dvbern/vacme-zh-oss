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
import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.benutzer.Benutzer;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFileTyp;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.entities.zertifikat.Zertifikat;
import ch.dvbern.oss.vacme.i18n.ServerMessageUtil;
import ch.dvbern.oss.vacme.jax.registration.DashboardJax;
import ch.dvbern.oss.vacme.jax.registration.FileInfoJax;
import ch.dvbern.oss.vacme.jax.registration.ZertifikatJax;
import ch.dvbern.oss.vacme.rest.auth.Authorizer;
import ch.dvbern.oss.vacme.service.ApplicationPropertyCacheService;
import ch.dvbern.oss.vacme.service.BenutzerService;
import ch.dvbern.oss.vacme.service.DokumentService;
import ch.dvbern.oss.vacme.service.ExternesZertifikatService;
import ch.dvbern.oss.vacme.service.FragebogenService;
import ch.dvbern.oss.vacme.service.PdfService;
import ch.dvbern.oss.vacme.service.RegistrierungService;
import ch.dvbern.oss.vacme.service.ZertifikatRunnerService;
import ch.dvbern.oss.vacme.service.ZertifikatRunnerService.ZertifikatResultDto;
import ch.dvbern.oss.vacme.service.ZertifikatService;
import ch.dvbern.oss.vacme.service.covidcertificate.CovidCertBatchType;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.shared.util.MimeType;
import ch.dvbern.oss.vacme.shared.util.OpenApiConst;
import ch.dvbern.oss.vacme.util.QRCodeUtil;
import ch.dvbern.oss.vacme.util.RestUtil;
import com.google.zxing.WriterException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.tags.Tags;
import org.jetbrains.annotations.Nullable;

import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.AS_BENUTZER_VERWALTER;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.KT_IMPFDOKUMENTATION;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.KT_MEDIZINISCHE_NACHDOKUMENTATION;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.KT_NACHDOKUMENTATION;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.OI_DOKUMENTATION;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.OI_IMPFVERANTWORTUNG;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.OI_KONTROLLE;
import static ch.dvbern.oss.vacme.rest.auth.ClientApplications.VACME_WEB;
import static ch.dvbern.oss.vacme.util.ZertifikatDownloadUtil.zertifikatBlobToDownloadResponse;

@ApplicationScoped
@Transactional
@Slf4j
@Tags(@Tag(name = OpenApiConst.TAG_DOSSIER))
@Path(VACME_WEB + "/dossier")
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DossierResource {

	private final Authorizer authorizer;
	private final RegistrierungService registrierungService;
	private final DokumentService dokumentService;
	private final PdfService pdfService;
	private final FragebogenService fragebogenService;
	private final ZertifikatService zertifikatService;
	private final ApplicationPropertyCacheService propertyCacheService;
	private final ZertifikatRunnerService zertifikatRunnerService;
	private final ImpfinformationenService impfinformationenService;
	private final ExternesZertifikatService externeImpfinfoService;
	private final BenutzerService benutzerService;

	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/dashboard/{registrierungsnummer}")
	@RolesAllowed({ OI_KONTROLLE, OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, AS_BENUTZER_VERWALTER,
		KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION, KT_IMPFDOKUMENTATION })
	public DashboardJax getDashboardRegistrierung(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {

		ImpfinformationDto impfinformationen = this.impfinformationenService.getImpfinformationenAndEnsureStatusIsAktuell(registrierungsnummer);
		Fragebogen fragebogen = this.fragebogenService.findFragebogenByRegistrierungsnummer(registrierungsnummer);
		Registrierung registrierung = impfinformationen.getRegistrierung();
		authorizer.checkReadAuthorization(registrierung);

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
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/dashboard/kvk-nummer/{kvkNummer}")
	@RolesAllowed({ OI_KONTROLLE, OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG,
		KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION, KT_IMPFDOKUMENTATION })
	public List<DashboardJax> searchDashboardRegistrierung(
		@NonNull @NotNull @PathParam("kvkNummer") String kvkNummer
	) {
		return registrierungService.searchRegistrierungByKvKNummer(kvkNummer)
			.stream()
			.map(registrierung -> {
				ImpfinformationDto impfinformationen = this.impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
				Fragebogen fragebogen = this.fragebogenService.findFragebogenByRegistrierungsnummer(registrierung.getRegistrierungsnummer());
				return new DashboardJax(impfinformationen, fragebogen);
			})
			.collect(Collectors.toList());
	}

	@GET()
	@Produces({ "image/gif" })
	@Path("/qr-code/{code}")
	@PermitAll
	public Response getQrCode(@NotNull @PathParam("code") String code) {
		final Config config = ConfigProvider.getConfig();
		String baseUrl = config.getValue("vacme.server.base.url", String.class);
		String url = baseUrl + "/dossier/" + code;
		Response.ResponseBuilder responseBuilder = null;
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
	@APIResponse(content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM, schema = @Schema(type = SchemaType.STRING, format = "binary")), description = "OK", responseCode = "200")	@Path("download/impfdokumentation/{registrierungsnummer}")
	@RolesAllowed({ OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG,
		KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION, KT_IMPFDOKUMENTATION })
	public Response downloadImpfdokumentation(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		authorizer.checkReadAuthorization(registrierung);

		final byte[] content = dokumentService.getOrCreateImpfdokumentationPdf(registrierung);
		return createDownloadResponse(RegistrierungFileTyp.IMPF_DOKUMENTATION, registrierungsnummer, content);
	}

	@GET
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("{registrierungsnummer}/fileinfo")
	@RolesAllowed({ OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, OI_KONTROLLE,
		KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION, KT_IMPFDOKUMENTATION })
	@Operation(description = "Gibt die Metainfos der hochgeladenen Files zurueck")
	public List<FileInfoJax> getFileInfo(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);
		authorizer.checkReadAuthorization(registrierung);

		return dokumentService.getUploadedDocInfos(registrierung);
	}

	@Nullable
	@GET
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.TEXT_PLAIN)
	@Path("{registrierungsnummer}/username")
	@RolesAllowed({ KT_NACHDOKUMENTATION })
	@Operation(description = "Gibt die benutzername fuer registrierung zurueck")
	public String getUsername(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);
		authorizer.checkReadAuthorization(registrierung);
		if (registrierung.getBenutzerId() == null) {
			return null;
		}
		return benutzerService.getById(Benutzer.toId(registrierung.getBenutzerId()))
			.map(Benutzer::getBenutzername)
			.orElse(null);
	}

	@POST
	@Consumes(MediaType.WILDCARD)
	@APIResponse(content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM, schema = @Schema(type = SchemaType.STRING, format = "binary")), description = "OK", responseCode = "200")
	@Path("download/registrierungsbestaetigung/{registrierungsnummer}")
	@RolesAllowed({ OI_KONTROLLE, OI_IMPFVERANTWORTUNG, KT_IMPFDOKUMENTATION })
	public Response downloadRegistrierungsbestaetigung(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		authorizer.checkReadAuthorization(registrierung);

		final byte[] content = pdfService.createRegistrationsbestaetigung(registrierung);
		return createDownloadResponse(RegistrierungFileTyp.REGISTRIERUNG_BESTAETIGUNG, registrierungsnummer, content);
	}

	@GET
	@Consumes(MediaType.WILDCARD)
	@APIResponse(content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM, schema = @Schema(type = SchemaType.STRING, format = "binary")), description = "OK", responseCode = "200")
	@Path("download/zertifikat/{registrierungsnummer}")
	@RolesAllowed({ OI_KONTROLLE, OI_IMPFVERANTWORTUNG,
		KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION, KT_IMPFDOKUMENTATION })
	public Response downloadZertifikat(
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
	@RolesAllowed({ OI_KONTROLLE, OI_IMPFVERANTWORTUNG,
		KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION, KT_IMPFDOKUMENTATION })
	public Response downloadZertifikatWithId(
		@NonNull @NotNull @PathParam("zertifikatid") UUID zertifikatId,
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);
		authorizer.checkReadAuthorization(registrierung);

		Zertifikat zertifikat = zertifikatService.getZertifikatById(new ID(zertifikatId, Zertifikat.class));
		return zertifikatBlobToDownloadResponse(this.zertifikatService.getZertifikatPdf(zertifikat));
	}


	@GET
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("list/zertifikate/{registrierungsnummer}")
	@RolesAllowed({ OI_KONTROLLE, OI_IMPFVERANTWORTUNG,
		KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION, KT_IMPFDOKUMENTATION })
	public List<ZertifikatJax> getAllZertifikate(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer) {

		ImpfinformationDto infos = impfinformationenService.getImpfinformationenNoCheck(registrierungsnummer);
		authorizer.checkReadAuthorization(infos.getRegistrierung());

		List<Zertifikat> zertifikatList = zertifikatService.getAllZertifikateRegardlessOfRevocation(infos.getRegistrierung());

		return zertifikatService.mapToZertifikatJax(zertifikatList);

	}

	@POST
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("create/zertifikat/{registrierungsnummer}")
	@RolesAllowed({ OI_KONTROLLE, OI_IMPFVERANTWORTUNG,
		KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION, KT_IMPFDOKUMENTATION })
	@Schema(type = SchemaType.STRING, format = "binary")
	public Response createAndDownload(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) throws Exception {
		final ImpfinformationDto infos = impfinformationenService.getImpfinformationenNoCheck(registrierungsnummer);
		authorizer.checkUpdateAuthorization(infos.getRegistrierung());
		final Impfung newestImpfung = ImpfinformationenService.getNewestVacmeImpfung(infos);
		final boolean zertifikatPending = newestImpfung != null && newestImpfung.isGenerateZertifikat();
		if (zertifikatPending) {
			// In diesen Fall versuchen wir, fuer die neueste Impfung ein Zertifikat zu erstellen

			Objects.requireNonNull(newestImpfung);
			final ID<Impfung> idOfNewestImpfung = Impfung.toId(newestImpfung.getId());
			ZertifikatResultDto result = zertifikatRunnerService.createZertifikatForRegistrierung(
				registrierungsnummer,
				idOfNewestImpfung,
				CovidCertBatchType.ONLINE);
			if (!result.success) {
				throw result.exception != null ? result.exception : AppValidationMessage.ZERTIFIKAT_GENERIERUNG_FEHLER.create();
			}
		}
		return Response.ok().build();
	}

	@NonNull
	private Response createDownloadResponse(
		@NonNull RegistrierungFileTyp fileTyp,
		@NonNull String registrierungsnummer,
		byte[] content) {
		return RestUtil.createDownloadResponse(
			ServerMessageUtil.translateEnumValue(fileTyp, Locale.GERMAN, registrierungsnummer),
			content,
			MimeType.APPLICATION_PDF
		);
	}

	@DELETE
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("deleteRegistrierung/{registrierungsnummer}")
	@RolesAllowed({ AS_BENUTZER_VERWALTER, KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION })
	public Response deleteRegistrierung(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Objects.requireNonNull(registrierungsnummer);

		ImpfinformationDto impfinfos =
			impfinformationenService.getImpfinformationenAndEnsureStatusIsAktuell(registrierungsnummer);
		final Fragebogen fragebogen = fragebogenService.findFragebogenByRegistrierungsnummer(registrierungsnummer);

		registrierungService.deleteRegistrierung(impfinfos, fragebogen);
		return Response.ok().build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("isZertifikatEnabled")
	@RolesAllowed({ OI_KONTROLLE, OI_IMPFVERANTWORTUNG,
		KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION, KT_IMPFDOKUMENTATION })
	public boolean isZertifikatEnabled() {
		return propertyCacheService.isZertifikatEnabled();
	}
}

