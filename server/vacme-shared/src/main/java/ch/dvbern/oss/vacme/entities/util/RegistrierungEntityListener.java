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

package ch.dvbern.oss.vacme.entities.util;

import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Dieser Listener stellt sicher, dass der Impftermin als gebucht markiert ist
 */
@Slf4j
public class RegistrierungEntityListener {

	@PrePersist
	protected void prePersist(@NonNull Registrierung registrierung) {
		validate(registrierung);
	}

	@PreUpdate
	public void preUpdate(@NonNull Registrierung registrierung) {
		validate(registrierung);
	}

	void validate(@NonNull Registrierung registrierung) {
		if (registrierung.getImpftermin1() != null) {
			if (!registrierung.getImpftermin1().isGebucht()) {
				LOG.error("VACME-ERROR: Impftermin 1 wurde nicht korrekt gebucht. Registrierung {}", registrierung.getRegistrierungsnummer());
				registrierung.getImpftermin1().setGebuchtFromImpfterminRepo(true);
			}
		}
		if (registrierung.getImpftermin2() != null) {
			if (!registrierung.getImpftermin2().isGebucht()) {
				LOG.error("VACME-ERROR: Impftermin 2 wurde nicht korrekt gebucht. Registrierung {}", registrierung.getRegistrierungsnummer());
				registrierung.getImpftermin2().setGebuchtFromImpfterminRepo(true);
			}
		}
	}
}
