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

package ch.dvbern.oss.vacme.entities.massenimport;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Join Table for {@link Massenimport} and {@link Registrierung}
 */
@Entity
@Data
@EqualsAndHashCode()
@Table(
	name = "MassenimportRegistrierung",
	uniqueConstraints = @UniqueConstraint(
		name = "UC_MassenimportRegistrierung_registrierung",
		columnNames = "registrierung_id"
	)
)
public class MassenimportRegistrierung implements Serializable {

	private static final long serialVersionUID = -9086313352990682600L;

	@NotNull
	@Id
	@ManyToOne(optional = false)
	@JoinColumn(foreignKey = @ForeignKey(name = "FK_MassenimportRegistrierung_massenimport"))
	private Massenimport massenimport;

	@NotNull
	@Id
	@ManyToOne(optional = false)
	@JoinColumn(foreignKey = @ForeignKey(name = "FK_MassenimportRegistrierung_registrierung"))
	private Registrierung registrierung;
}
