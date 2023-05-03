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

package ch.dvbern.oss.vacme.service.boosterprioritaet.rules;

import java.util.List;
import java.util.Optional;

import ch.dvbern.oss.vacme.entities.base.ImpfInfo;
import ch.dvbern.oss.vacme.entities.externeimpfinfos.ExternesZertifikat;
import ch.dvbern.oss.vacme.entities.impfen.Erkrankung;
import ch.dvbern.oss.vacme.entities.impfen.Impfschutz;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface IBoosterPrioritaetRule {

	/**
	 * Berechnet aufgrund der Inputs den geltenden Impfschutz
	 * @param fragebogen Fragebogen und damit auch Registrierung
	 * @param orderedImpfInfos Informationen zu den Impfungen (Impfung kann auch als Externes Zertifkat erfasst sein)
	 * @param externesZertifikat
	 * @return
	 */
	@NonNull
	Optional<Impfschutz> calculateImpfschutz(
		@NonNull Fragebogen fragebogen,
		@NonNull List<ImpfInfo> orderedImpfInfos,
		@NonNull List<Erkrankung> orderedErkrankungen,
		@Nullable ExternesZertifikat externesZertifikat
	);

	@Nullable
	Integer getAnzahlMonateBisFreigabe();
}
