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

import javax.annotation.security.PermitAll;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import ch.dvbern.oss.vacme.service.ApplicationPropertyCacheService;
import ch.dvbern.oss.vacme.shared.util.OpenApiConst;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.tags.Tags;

import static ch.dvbern.oss.vacme.rest.auth.ClientApplications.VACME_INITIALREG;

/**
 * Resource fuer ApplicationProperties fuer reg authenticated benutzer
 */
@ApplicationScoped
@Transactional
@Slf4j
@Tags(@Tag(name = OpenApiConst.TAG_PROPERTIES))
@Path(VACME_INITIALREG + "/properties")
public class ApplicationPropertyRegResource {

	private final ApplicationPropertyCacheService applicationPropertyCacheService;

	@Inject
	public ApplicationPropertyRegResource(
		@NonNull ApplicationPropertyCacheService applicationPropertyCacheService
	) {
		this.applicationPropertyCacheService = applicationPropertyCacheService;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("noFreieTermin")
	@PermitAll
	public boolean noFreieTermin() {
		return applicationPropertyCacheService.noFreieTermin();
	}
}
