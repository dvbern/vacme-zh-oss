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

package ch.dvbern.oss.vacme.jax.migration;

import ch.dvbern.oss.vacme.entities.util.DBConst;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * DTO fuer die Migrationsschnittstelle welches die noetigen Angaben beinhaltet um die PLZ patchen zu koennen
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegistrierungPLZPatchMigrationJax {

	@NonNull @NotNull @NotEmpty
	@JsonProperty
	@Size(max = DBConst.DB_DEFAULT_MAX_LENGTH)
	@Schema(required = true, maxLength = DBConst.DB_DEFAULT_MAX_LENGTH, description = "ExternalID aus dem Client System. Wird benutzt um Aenderungen in bereits gesendete Datensaetze vornehmen zu koennen")
	private String externalId;

	@NonNull @NotNull @NotEmpty
	@JsonProperty
	@Size(max = DBConst.DB_DEFAULT_MAX_LENGTH)
	@Schema(required = true, maxLength = DBConst.DB_DEFAULT_MAX_LENGTH, description = "Plz der Adresse")
	private String plz;
}
