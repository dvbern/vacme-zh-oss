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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import ch.dvbern.oss.vacme.entities.benutzer.Benutzer;
import ch.dvbern.oss.vacme.entities.impfen.Impfstoff;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.impfen.Verarbreichungsart;
import ch.dvbern.oss.vacme.entities.impfen.Verarbreichungsort;
import ch.dvbern.oss.vacme.entities.impfen.Verarbreichungsseite;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ImpfdokumentationJax {

	private boolean nachtraeglicheErfassung;

	@Nullable
	private LocalDate datumFallsNachtraeglich;

	@NonNull
	private String registrierungsnummer;

	@NonNull
	private UUID verantwortlicherBenutzerId;

	@NonNull
	private UUID durchfuehrenderBenutzerId;

	@NonNull
	private ImpfstoffJax impfstoff;

	@NonNull
	private String lot;

	@Nullable // must be Boolean not boolean because it can be Null
	private Boolean keineBesonderenUmstaende = false;

	private boolean fieber = false;

	private boolean einwilligung = false;

	private boolean neueKrankheit = false;

	@NonNull
	private Verarbreichungsart verarbreichungsart;

	@NonNull
	private Verarbreichungsort verarbreichungsort;

	@NonNull
	private Verarbreichungsseite verarbreichungsseite;

	private BigDecimal menge;

	private String bemerkung;

	private boolean extern = false;

	private boolean grundimmunisierung;

	@Nullable // must be Boolean not boolean because it can be Null
	private Boolean schwanger;

	private boolean selbstzahlende;

	@Nullable // must be Boolean not boolean because it can be Null
	private Boolean immunsupprimiert;

	@NonNull
	public Impfung toEntity(
		@NonNull Benutzer benutzerVerantwortlich,
		@NonNull Benutzer benutzerDurchfuehrend,
		@NonNull Impfstoff impfstoff
	) {
		Impfung impfung = new Impfung();
		impfung.setBenutzerVerantwortlicher(benutzerVerantwortlich);
		impfung.setBenutzerDurchfuehrend(benutzerDurchfuehrend);
		impfung.setImpfstoff(impfstoff);
		impfung.setLot(lot);
		impfung.setFieber(fieber);
		impfung.setKeineBesonderenUmstaende(keineBesonderenUmstaende);
		impfung.setSchwanger(schwanger);
		impfung.setEinwilligung(einwilligung);
		impfung.setBemerkung(bemerkung);
		impfung.setNeueKrankheit(neueKrankheit);
		impfung.setVerarbreichungsart(verarbreichungsart);
		impfung.setVerarbreichungsort(verarbreichungsort);
		impfung.setVerarbreichungsseite(verarbreichungsseite);
		impfung.setMenge(menge);
		impfung.setExtern(extern);
		impfung.setGrundimmunisierung(grundimmunisierung);
		impfung.setSelbstzahlende(selbstzahlende);

		return impfung;
	}
}
