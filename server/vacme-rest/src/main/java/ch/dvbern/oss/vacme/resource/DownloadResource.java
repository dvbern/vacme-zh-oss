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

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import ch.dvbern.oss.vacme.entities.embeddables.FileBytes;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFileTyp;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.fhir.FhirService;
import ch.dvbern.oss.vacme.i18n.ServerMessageUtil;
import ch.dvbern.oss.vacme.service.DokumentService;
import ch.dvbern.oss.vacme.service.PdfService;
import ch.dvbern.oss.vacme.service.RegistrierungService;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.util.CleanFileName;
import ch.dvbern.oss.vacme.shared.util.MimeType;
import ch.dvbern.oss.vacme.shared.util.OpenApiConst;
import ch.dvbern.oss.vacme.util.RestUtil;
import com.github.HonoluluHenk.httpcontentdisposition.Disposition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.tags.Tags;

import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.*;
import static ch.dvbern.oss.vacme.rest.auth.ClientApplications.VACME_WEB;

@ApplicationScoped
@Transactional
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Tags(@Tag(name = OpenApiConst.TAG_DOWNLOAD))
@Path(VACME_WEB + "/download")
public class DownloadResource {

	private static final String DOCUMENTS = "documents";

	private final RegistrierungService registrierungService;
	private final PdfService pdfService;
	private final DokumentService dokumentService;
	private final ImpfinformationenService impfinformationenService;
	private final FhirService fhirService;


	@GET
	@Consumes(MediaType.WILDCARD)
	@APIResponse(content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM, schema = @Schema(type = SchemaType.STRING, format = "binary")), description = "OK", responseCode = "200")
	@Path(DOCUMENTS + "/registrierungsbestaetigung/{registrierungsnummer}")
	@RolesAllowed({OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, OI_KONTROLLE, KT_IMPFDOKUMENTATION  })
	public Response downloadRegistrierungsBestaetigung(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		final byte[] content = pdfService.createRegistrationsbestaetigung(registrierung);
		return createDocumentResponse(RegistrierungFileTyp.REGISTRIERUNG_BESTAETIGUNG, registrierungsnummer, content);
	}

	@GET
	@Consumes(MediaType.WILDCARD)
	@APIResponse(content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM, schema = @Schema(type = SchemaType.STRING, format = "binary")), description = "OK", responseCode = "200")
	@Path(DOCUMENTS + "/terminbestaetigung/{registrierungsnummer}")
	@RolesAllowed({OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, OI_KONTROLLE, KT_IMPFDOKUMENTATION  })
	public Response downloadTerminBestaetigung(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		Impftermin boosterTerminOrNull = null;
		if (RegistrierungStatus.getAnyStatusOfGrundimmunisiert().contains(registrierung.getRegistrierungStatus())) {
			final ImpfinformationDto infos = impfinformationenService.getImpfinformationenNoCheck(registrierungsnummer);
			final Optional<Impftermin> pendingBoosterTermin = ImpfinformationenService.getPendingBoosterTermin(infos);
			if (pendingBoosterTermin.isPresent()) {
				boosterTerminOrNull = pendingBoosterTermin.get();
			}
		}

		final byte[] content = pdfService.createTerminbestaetigung(registrierung, boosterTerminOrNull);
		return createDocumentResponse(RegistrierungFileTyp.REGISTRIERUNG_BESTAETIGUNG, registrierungsnummer, content);
	}

	@GET
	@Consumes(MediaType.WILDCARD)
	@APIResponse(content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM, schema = @Schema(type = SchemaType.STRING, format = "binary")), description = "OK", responseCode = "200")
	@Path(DOCUMENTS + "/impfdokumentation/{registrierungsnummer}")
	@RolesAllowed({OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG,
		KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION, KT_IMPFDOKUMENTATION})
	public Response downloadImpfdokumentation(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		final byte[] content = dokumentService.getOrCreateImpfdokumentationPdf(registrierung);
		return createDocumentResponse(RegistrierungFileTyp.IMPF_DOKUMENTATION, registrierungsnummer, content);
	}

	@GET
	@Consumes(MediaType.WILDCARD)
	@APIResponse(content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM, schema = @Schema(type = SchemaType.STRING, format = "binary")), description = "OK", responseCode = "200")
	@Path(DOCUMENTS + "/fhirimpfdokumentation/{registrierungsnummer}")
	@RolesAllowed({KT_MEDIZINISCHER_REPORTER})
	public Response downloadFhirImpfdokumentation(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		final ImpfinformationDto impfinformationen =
			impfinformationenService.getImpfinformationenNoCheck(registrierungsnummer);

		// FHIR Impfdokumentation wird nur erstellt, wenn mind. 1 VacMe-Impfung existiert
		if (!impfinformationenService.hasVacmeImpfungen(impfinformationen)) {
			throw AppValidationMessage.NO_VACME_IMPFUNG.create();
		}

		final byte[] content = fhirService.createFhirImpfdokumentationXML(impfinformationen);
		return createXMLDocumentResponse("fhirImpfdokumentation", registrierungsnummer, content);
	}


	private Response createDocumentResponse(RegistrierungFileTyp typ, String registrierungsnummer, byte[] content) {

		String translatedFileName = ServerMessageUtil.translateEnumValue(typ, Locale.GERMAN, registrierungsnummer);
		CleanFileName fileName = new CleanFileName(translatedFileName);

		final FileBytes downloadFile = FileBytes.of(
			fileName,
			MimeType.APPLICATION_PDF,
			content,
			LocalDateTime.now()
		);

		return RestUtil.buildFileResponse(Disposition.ATTACHMENT, downloadFile);
	}

	private Response createXMLDocumentResponse(String filename, String registrierungsnummer, byte[] content) {

		CleanFileName fileName = new CleanFileName(filename + "_"+ registrierungsnummer);

		final FileBytes downloadFile = FileBytes.of(
			fileName,
			MimeType.APPLICATION_XML,
			content,
			LocalDateTime.now()
		);

		return RestUtil.buildFileResponse(Disposition.ATTACHMENT, downloadFile);
	}

}
