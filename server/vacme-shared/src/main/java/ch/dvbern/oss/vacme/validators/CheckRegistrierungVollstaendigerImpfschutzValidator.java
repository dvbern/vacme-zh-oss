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

package ch.dvbern.oss.vacme.validators;

import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Prueft ob das Flag vollstaendigerImpfschutz der Registrierung richtig gesetzt ist
 */
public class CheckRegistrierungVollstaendigerImpfschutzValidator {

	public static boolean isValid(@NonNull Registrierung registrierung, @Nullable Impfung impfung) {
		if (impfung == null) {
			return false;
		}
		// Vollstaendiger Impfschutz darf nur gesetzt sein, wenn ABGESCHLOSSEN oder ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG,
		// *ohne* Bemerkung zweiteImpfungVerzichtetGrund (d.h. mit Corona)
		// Zusaetzlich immer, wenn wir bereits im "Booster-Zyklus" sind
		if ((RegistrierungStatus.getStatusWithPossibleZertifikat().contains(registrierung.getRegistrierungStatus())
			||  registrierung.abgeschlossenMitCorona())
			&& StringUtils.isEmpty(registrierung.getZweiteImpfungVerzichtetGrund()) // der Verzicht grund sollte nie gesetzt sein
		) {
			return registrierung.abgeschlossenMitVollstaendigemImpfschutz();
		}
		// In allen anderen Faellen: Alles andere als TRUE erlaubt (also auch null)
		// Das Flag fuer die zertifikatsgenerierung darf dann nicht true sein
		return !registrierung.abgeschlossenMitVollstaendigemImpfschutz() && !impfung.isGenerateZertifikat();
	}
}
