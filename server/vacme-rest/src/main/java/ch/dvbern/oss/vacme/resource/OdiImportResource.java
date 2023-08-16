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

import javax.annotation.Nonnull;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import ch.dvbern.oss.vacme.entities.UserPrincipal;
import ch.dvbern.oss.vacme.entities.benutzer.Benutzer;
import ch.dvbern.oss.vacme.jax.odiupload.OdiUploadFormData;
import ch.dvbern.oss.vacme.scheduler.SystemAdminRunnerService;
import ch.dvbern.oss.vacme.shared.util.OpenApiConst;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.tags.Tags;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;

import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.AS_REGISTRATION_OI;
import static ch.dvbern.oss.vacme.rest.auth.ClientApplications.VACME_WEB;

@ApplicationScoped
@Transactional
@Slf4j
@Tags(@Tag(name = OpenApiConst.TAG_ODI_IMPORT))
@Path(VACME_WEB + "/odiImport")
public class OdiImportResource {

	private SystemAdminRunnerService systemAdminRunnerService;
	private UserPrincipal userPrincipal;

	@Inject
	public OdiImportResource(
		@NonNull SystemAdminRunnerService systemAdminRunnerService,
		@NonNull UserPrincipal userPrincipal
	) {
		this.systemAdminRunnerService = systemAdminRunnerService;
		this.userPrincipal = userPrincipal;
	}

	/**
	 * Endpoint for uploading mass import excel sheets
	 *
	 * @param formData contains the name of the import and the actual excel file as an input stream
	 */
	@POST
	@Path("upload")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_JSON)
	@RolesAllowed(AS_REGISTRATION_OI)
	@Transactional(TxType.NOT_SUPPORTED)
	public Response uploadAsync(@Nonnull @NotNull @MultipartForm OdiUploadFormData formData) {
		final Benutzer currentUser = userPrincipal.getBenutzerOrThrowException();
		LOG.info("VACME-IMPORTODI: user {} started massenimport of odis ", userPrincipal.getBenutzerOrThrowException().getBenutzername());
		Uni.createFrom().item(formData).emitOn(Infrastructure.getDefaultWorkerPool()).subscribe().with(
			item -> {
				systemAdminRunnerService.processOdiUpload(formData, currentUser.getEmail());
			}, throwable -> {
				LOG.error("Massenimport failed", throwable);
			}
		);
		return Response.accepted().build();
	}
}
