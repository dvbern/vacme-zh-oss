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

package ch.dvbern.oss.vacme.jax.applicationhealth;

import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
public class TerminAndImpfungJax {

	@NonNull
	private Registrierung registrierung;
	@Nullable
	private Impftermin termin1;
	@Nullable
	private Impftermin termin2;
	@Nullable
	private Impfung impfung1;
	@Nullable
	private Impfung impfung2;

	@SuppressWarnings("unused") // Wird in StatsRepo benutzt (Projections.constructor)
	public TerminAndImpfungJax(
		@NonNull Registrierung registrierung,
		@Nullable Impftermin termin1,
		@Nullable Impftermin termin2,
		@Nullable Impfung impfung1,
		@Nullable Impfung impfung2
	) {
		this.registrierung = registrierung;
		this.termin1 = termin1;
		this.termin2 = termin2;
		this.impfung1 = impfung1;
		this.impfung2 = impfung2;
	}

	@SuppressWarnings("unused") // Wird in StatsRepo benutzt (Projections.constructor)
	public TerminAndImpfungJax(
		@NonNull Registrierung registrierung,
		@Nullable Impftermin termin1,
		@Nullable Impftermin termin2
	) {
		this.registrierung = registrierung;
		this.termin1 = termin1;
		this.termin2 = termin2;
	}
}
