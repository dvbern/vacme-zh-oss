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
 * DTO welches zum selecten von Tagesstatistikdaten fuer 1. Termine benutzt wird. Geht nicht auf den Client
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Setter
public class ImpfzentrumDayStatTermin1DataRow implements Comparator<ImpfzentrumDayStatTermin1DataRow>, Serializable {


	static final long serialVersionUID = -2383509281396573578L;

	private @NonNull String registrierungsnummer;
	private @NonNull RegistrierungStatus status;
	private @NonNull Impfslot slot1;

	private @NonNull LocalDateTime termin1Datum;
	private @NonNull OrtDerImpfung termin1Odi;


	private @Nullable LocalDateTime impfung1Datum;
	private @Nullable Impfstoff impfung1Impfstoff;


	@SuppressWarnings("unused") // Wirdbenutzt (Projections.constructor)
	public ImpfzentrumDayStatTermin1DataRow(
		@NonNull Registrierung registrierung,
		@NonNull Impftermin termin1,
		@NonNull Impfslot slot1,
		@Nullable Impfung impfung1

	) {
		this.readAttributesOfRegistration(registrierung);
		this.readAttributesOfTermin1(termin1);
		this.slot1 = slot1;
		this.readAttributesOfImpfung1(impfung1);

	}



	private void readAttributesOfRegistration(@NonNull Registrierung registrierung) {
		this.registrierungsnummer = registrierung.getRegistrierungsnummer();
		this.status = registrierung.getRegistrierungStatus();

	}

	private void readAttributesOfTermin1(@Nullable Impftermin termin1) {
		if (termin1 != null) {
			this.termin1Datum = termin1.getImpfslot().getZeitfenster().getVon();
			this.termin1Odi = termin1.getImpfslot().getOrtDerImpfung();
		}
	}


	private void readAttributesOfImpfung1(@Nullable Impfung impfung1) {
		if (impfung1 != null) {
			this.impfung1Datum = impfung1.getTimestampImpfung();
			this.impfung1Impfstoff = impfung1.getImpfstoff();
		}
	}


	@Override
	public int compare(ImpfzentrumDayStatTermin1DataRow a, ImpfzentrumDayStatTermin1DataRow b) {
		return a.termin1Datum.compareTo(b.termin1Datum);
	}

}
