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

package ch.dvbern.oss.vacme.jax.stats;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Comparator;

import ch.dvbern.oss.vacme.entities.impfen.Impfstoff;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impfslot;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * DTO welches zum selecten von Tagesstatistikdaten fuer N. Termine benutzt wird. Geht nicht auf den Client
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Setter
public class ImpfzentrumDayStatTerminNDataRow implements Comparator<ImpfzentrumDayStatTerminNDataRow>, Serializable {

	private @NonNull String registrierungsnummer;
	private @NonNull RegistrierungStatus status;
	private @NonNull Impfslot slotN;

	private @NonNull LocalDateTime terminNDatum;
	private @NonNull OrtDerImpfung terminNOdi;
	private @NonNull Boolean terminNGebucht;

	private @Nullable LocalDateTime impfungNDatum;
	private @Nullable Impfstoff impfungNImpfstoff;

	@SuppressWarnings("unused") // Wirdbenutzt (Projections.constructor)
	public ImpfzentrumDayStatTerminNDataRow(
		@NonNull Registrierung registrierung,
		@NonNull Impftermin terminN,
		@NonNull Impfslot slotN,
		@Nullable Impfung impfungN
	) {
		this.readAttributesOfRegistration(registrierung);
		this.readAttributesOfTerminN(terminN);
		this.slotN = slotN;

		this.readAttributesOfImpfungN(impfungN);
	}

	private void readAttributesOfRegistration(@NonNull Registrierung registrierung) {
		this.registrierungsnummer = registrierung.getRegistrierungsnummer();
		this.status = registrierung.getRegistrierungStatus();

	}

	private void readAttributesOfTerminN(@Nullable Impftermin terminN) {
		if (terminN != null) {
			this.terminNDatum = terminN.getImpfslot().getZeitfenster().getVon();
			this.terminNOdi = terminN.getImpfslot().getOrtDerImpfung();
			this.terminNGebucht = terminN.isGebucht();
		}
	}

	private void readAttributesOfImpfungN(@Nullable Impfung impfungN) {
		if (impfungN != null) {
			this.impfungNDatum = impfungN.getTimestampImpfung();
			this.impfungNImpfstoff = impfungN.getImpfstoff();
		}
	}

	@Override
	public int compare(ImpfzentrumDayStatTerminNDataRow a, ImpfzentrumDayStatTerminNDataRow b) {
		return a.terminNDatum.compareTo(b.terminNDatum);
	}

}
