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

package ch.dvbern.oss.vacme.rest_client.vmdl;
import ch.dvbern.oss.vacme.entities.types.BenutzerRolleName;
import ch.dvbern.oss.vacme.jax.vmdl.VMDLDeleteJax;
import ch.dvbern.oss.vacme.rest_client.RestClientLoggingFilter;
import ch.dvbern.oss.vacme.jax.vmdl.VMDLUploadJax;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/v2")
@RegisterRestClient(configKey="vmdl-api")
@RegisterProvider(RestClientLoggingFilter.class)
@RegisterClientHeaders(VMDLRequestAuthTokenFactory.class)
@RolesAllowed(BenutzerRolleName.SYSTEM_INTERNAL_ADMIN)
public interface VMDLRestClientService {

    @POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/vaccinationData")
	@RolesAllowed(BenutzerRolleName.SYSTEM_INTERNAL_ADMIN)
    void uploadData(@NonNull @NotNull @Parameter(description = "Data to upload to VMDL Interface")
						List<VMDLUploadJax> data);

	@DELETE
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/vaccinationData")
	@RolesAllowed(BenutzerRolleName.SYSTEM_INTERNAL_ADMIN)
	void deleteData(@NonNull @NotNull @Parameter(description = "Data to be deleted in VMDL Interface")
						List<VMDLDeleteJax> data);
}
