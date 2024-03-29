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

import ch.dvbern.oss.vacme.entities.terminbuchung.Impfslot;
import ch.dvbern.oss.vacme.jax.base.AbstractUUIDEntityJax;
import ch.dvbern.oss.vacme.jax.impfslot.DateTimeRangeJax;
import lombok.Getter;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;

@Getter
@Setter
public class ImpfslotJax extends AbstractUUIDEntityJax {

	private OrtDerImpfungDisplayNameJax ortDerImpfung;

	private DateTimeRangeJax zeitfenster;

	private int kapazitaetErsteImpfung;

	private int kapazitaetZweiteImpfung;


	public ImpfslotJax(@NonNull Impfslot slotEntity) {
		super(slotEntity);
		this.ortDerImpfung = new OrtDerImpfungDisplayNameJax(slotEntity.getOrtDerImpfung());
		this.zeitfenster = new DateTimeRangeJax(slotEntity.getZeitfenster().getVon(), slotEntity.getZeitfenster().getBis());
		this.kapazitaetErsteImpfung = slotEntity.getKapazitaetErsteImpfung();
		this.kapazitaetZweiteImpfung = slotEntity.getKapazitaetZweiteImpfung();
	}
}
