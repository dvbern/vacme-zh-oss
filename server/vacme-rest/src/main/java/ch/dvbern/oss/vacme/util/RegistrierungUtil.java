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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import ch.dvbern.oss.vacme.entities.impfen.Impfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import org.checkerframework.checker.nullness.qual.NonNull;

public class RegistrierungUtil {

	private RegistrierungUtil() {
		//util
	}

	/**
	 * Gibt ein Optional des Impftermin der angefragten Folge zurueck
	 * @param impfinformationDto Infos ueber Impfung
	 * @param impffolge die Impffolge
	 * @return Optional mit dem Termin
	 */
	public static List<Impftermin> getImpfterminOfReg(
		@NonNull ImpfinformationDto impfinformationDto,
		@NonNull Impffolge impffolge
	) {
		Registrierung registrierung = impfinformationDto.getRegistrierung();
		if (impffolge == Impffolge.ERSTE_IMPFUNG) {
			return registrierung.getImpftermin1() != null ? List.of(registrierung.getImpftermin1()) :
				Collections.emptyList();
		}
		if (impffolge == Impffolge.ZWEITE_IMPFUNG) {
			return registrierung.getImpftermin2() != null ? List.of(registrierung.getImpftermin2()) :
				Collections.emptyList();
		}
		if (impffolge == Impffolge.BOOSTER_IMPFUNG) {
			if (impfinformationDto.getImpfdossier() != null) {
				return impfinformationDto.getImpfdossier()
					.getImpfdossierEintraege()
					.stream()
					.map(Impfdossiereintrag::getImpftermin)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
			} else {
				return Collections.emptyList();
			}

		}
		throw new IllegalArgumentException("Unhandled Impffolge " + impffolge);
	}

	/**
	 * Liste mit allen existierenden Impftermiinen einer Registrierung. Die Liste kann leer sein
	 * @param impfinformationDto informationen zur Registrierung und ihre Impfungen
	 * @return Liste mit den Impfterminen
	 */
	@NonNull
	public static Collection<Impftermin> getExistingImpftermineOfReg(@NonNull ImpfinformationDto impfinformationDto) {
		List<Impftermin> existingTermine = Arrays.stream(Impffolge.values())
			.map(impffolge -> getImpfterminOfReg(impfinformationDto, impffolge))
			.flatMap(Collection::stream)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		return existingTermine;
	}
}
