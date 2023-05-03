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

package ch.dvbern.oss.vacme.entities.base;

import java.time.LocalDateTime;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import ch.dvbern.oss.vacme.entities.impfen.Impfstoff;
import org.checkerframework.checker.nullness.qual.NonNull;

public interface ImpfInfo {

	@NotNull
	@NonNull
	LocalDateTime getTimestampImpfung();

	@NotNull
	@NonNull
	Impfstoff getImpfstoff();

	@NotNull
	@NonNull
	UUID getId();

	/**
	 * um zu verhinden dass wir nach der 1. Booster Impfung grad wieder eine Freigabe mit einem neuen Termin machen
	 * muessen wir wissen ob eine Impfung eine Grundimmunisierungsimpfung war
	 */
	boolean gehoertZuGrundimmunisierung();

	/**
	 * Gibt zurück ob man die nächste impfung möglicherweise selber zahlen muss.
	 * Bei dokumentierten Impfungen ist das ab Grundimmunisierung + 1 Booster,
	 * also wenn (letzte) Impfung ein booster ist, muss die nächste potenziell selber bezahlt werden.
	 * Bei ExternemZertifikat true sobald durch EZ Grundimmunisierung erreicht ist (resultierende Fehlerquote akzeptiert)
	 */
	boolean isNextImpfungPossiblySelbstzahler();

}
