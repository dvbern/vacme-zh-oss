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

package ch.dvbern.oss.vacme.jax.registration;

import java.time.LocalDateTime;

import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import lombok.Getter;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@Getter
@Setter
public class KorrekturDashboardJax extends DashboardJax {

	@Nullable
	private String durchfuehrendePerson1;

	@Nullable
	private String durchfuehrendePerson2;

	@Nullable
	private String mail;

	@Nullable
	private String telefon;

	public KorrekturDashboardJax(
		@NonNull ImpfinformationDto infos,
		@NonNull Fragebogen fragebogen,
		@Nullable LocalDateTime timestampLetzterPostversand
	) {
		super(infos, fragebogen, timestampLetzterPostversand);
		Impfung impfung1 = infos.getImpfung1();
		if (impfung1 != null) {
			this.durchfuehrendePerson1 = impfung1.getBenutzerDurchfuehrend().getDisplayName();
		}
		Impfung impfung2 = infos.getImpfung2();
		if (impfung2 != null) {
			this.durchfuehrendePerson2 = impfung2.getBenutzerDurchfuehrend().getDisplayName();
		}
		Registrierung registrierung = infos.getRegistrierung();
		this.mail = registrierung.getMail();
		this.telefon = registrierung.getTelefon();
	}
}
