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

package ch.dvbern.oss.vacme.util;

import java.util.List;

import ch.dvbern.oss.vacme.entities.externeimpfinfos.ExternesZertifikat;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossier;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import lombok.Getter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Volatile object holding relevant data of the Impfungen of a specific Registrierung.
 * For the business logic please use the ImpfinformationenService.java
 */
@Getter
public class ImpfinformationDto {

	@NonNull
	private final Registrierung registrierung;

	@Nullable
	private final Impfung impfung1;

	@Nullable
	private final Impfung impfung2;

	@Nullable
	private final Impfdossier impfdossier;

	@Nullable
	private final List<Impfung> boosterImpfungen;

	@Nullable
	private final ExternesZertifikat externesZertifikat;

	// used for loading from DB
	public ImpfinformationDto(
		@NonNull Registrierung registrierung,
		@Nullable Impfung impfung1,
		@Nullable Impfung impfung2,
		@Nullable Impfdossier impfdossier,
		@Nullable ExternesZertifikat externesZertifikat
	) {
		this.registrierung = registrierung;
		this.impfung1 = impfung1;
		this.impfung2 = impfung2;
		this.impfdossier = impfdossier;
		this.boosterImpfungen = null;
		this.externesZertifikat = externesZertifikat;
	}

	public ImpfinformationDto(@NonNull ImpfinformationDto infos, @Nullable List<Impfung> boosterImpfungen) {
		this.registrierung = infos.registrierung;
		this.impfung1 = infos.impfung1;
		this.impfung2 = infos.impfung2;
		this.impfdossier = infos.impfdossier;
		this.externesZertifikat = infos.externesZertifikat;
		this.boosterImpfungen = boosterImpfungen;
	}

}
