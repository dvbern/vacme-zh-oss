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

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import ch.dvbern.oss.vacme.entities.externeimpfinfos.ExternesZertifikat;
import ch.dvbern.oss.vacme.entities.impfen.AuslandArt;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.impfen.ImpfungkontrolleTermin;
import ch.dvbern.oss.vacme.entities.impfen.Krankenkasse;
import ch.dvbern.oss.vacme.entities.registration.AmpelColor;
import ch.dvbern.oss.vacme.entities.registration.BeruflicheTaetigkeit;
import ch.dvbern.oss.vacme.entities.registration.ChronischeKrankheiten;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Geschlecht;
import ch.dvbern.oss.vacme.entities.registration.Lebensumstaende;
import ch.dvbern.oss.vacme.entities.registration.Personenkontrolle;
import ch.dvbern.oss.vacme.entities.registration.Prioritaet;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungsEingang;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.jax.base.AdresseJax;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImpfkontrolleJax {

	@NonNull
	@NotNull
	private Integer impffolgeNr;

	@Nullable
	private String registrierungsnummer;

	@Nullable
	private UUID impfdossierEintragId; // wichtig in Boosterkontrolle, weil externes Zertifikat die impffolgeNr veraendert

	@Nullable
	private Prioritaet prioritaet;

	// Aufgrund der Status kann auf dem GUI entschieden werden, ob es sich um Termin 1 oder Termin 2 handelt
	@Nullable
	private RegistrierungStatus status = RegistrierungStatus.REGISTRIERT;

	@NonNull
	private Geschlecht geschlecht;

	@NonNull
	private String name;

	@NonNull
	private String vorname;

	@NonNull
	private AdresseJax adresse;

	private boolean immobil;

	private boolean abgleichElektronischerImpfausweis;

	@Nullable
	private Boolean contactTracing;

	@Nullable
	private String mail;

	@Nullable
	private String telefon;

	@NonNull
	private Krankenkasse krankenkasse;

	@NonNull
	private String krankenkasseKartenNr;

	@Nullable
	private AuslandArt auslandArt;

	@NonNull
	private LocalDate geburtsdatum;

	@Nullable
	private Boolean verstorben;

	@Nullable
	private String bemerkung; // dies ist das Bemerkungsfeld fuer die "Registrierungsbemerkung"

	@NonNull
	private AmpelColor ampelColor;

	@NonNull
	private ChronischeKrankheiten chronischeKrankheiten;

	@NonNull
	private Lebensumstaende lebensumstaende;

	@NonNull
	private BeruflicheTaetigkeit beruflicheTaetigkeit;

	@Nullable
	private String identifikationsnummer;

	@Nullable
	private ImpfkontrolleTerminJax impfungkontrolleTermin = new ImpfkontrolleTerminJax();

	@Nullable
	private ImpfterminJax termin1;

	@Nullable
	private ImpfterminJax termin2;

	@Nullable
	private ImpfterminJax terminNPending;

	@Nullable
	private ImpfungJax impfung1; // noetig fuer rechte Seite der Kontrolle 2, wir wollen die Impfung 1 sehen, nicht nur den Termin!

	@Nullable
	private ImpfungJax impfung2;

	@Nullable
	private OrtDerImpfungDisplayNameJax gewuenschterOrtDerImpfung;

	private boolean nichtVerwalteterOdiSelected;

	@Nullable
	private RegistrierungsEingang eingang;

	@Nullable
	private ExternGeimpftJax externGeimpft;

	private Boolean schutzstatus;

	private Boolean keinKontakt;

	@Nullable
	private ImpfschutzJax impfschutzJax;

	@NonNull
	public static ImpfkontrolleJax from(
		@NonNull Integer impffolgeNr,
		@NonNull Fragebogen fragebogen,
		@Nullable ImpfungkontrolleTermin impfungkontrolleTerminOrNull,
		@NonNull ImpfinformationDto infos,
		@Nullable Impftermin currentBoosterterminOrNull,
		@Nullable Impfdossiereintrag impfdossiereintrag
	) {
		Impfung impfung1 = infos.getImpfung1();
		Impfung impfung2 = infos.getImpfung2();
		ExternesZertifikat externesZertifikat = infos.getExternesZertifikat();
		final Registrierung registrierung = fragebogen.getRegistrierung();
		final Personenkontrolle personenkontrolle = fragebogen.getPersonenkontrolle();
		Objects.requireNonNull(personenkontrolle);
		ImpfterminJax termin1 = registrierung.getImpftermin1() != null ? new ImpfterminJax(registrierung.getImpftermin1()) : null;
		ImpfterminJax termin2 = registrierung.getImpftermin2() != null ? new ImpfterminJax(registrierung.getImpftermin2()) : null;
		ImpfterminJax terminN = currentBoosterterminOrNull != null ? new ImpfterminJax(currentBoosterterminOrNull) : null;
		ImpfkontrolleTerminJax impfkontrolleTerminJax = ImpfkontrolleTerminJax.from(impfungkontrolleTerminOrNull);
		boolean hasImpfschutz = infos.getImpfdossier() != null && infos.getImpfdossier().getImpfschutz() != null;
		ImpfschutzJax impfschutzJax = hasImpfschutz ? ImpfschutzJax.from(infos.getImpfdossier().getImpfschutz()) : null;
		return new ImpfkontrolleJax(
			impffolgeNr,
			registrierung.getRegistrierungsnummer(),
			impfdossiereintrag != null ? impfdossiereintrag.getId() : null,
			registrierung.getPrioritaet(),
			registrierung.getRegistrierungStatus(),
			registrierung.getGeschlecht(),
			registrierung.getName(),
			registrierung.getVorname(),
			AdresseJax.from(registrierung.getAdresse()),
			registrierung.isImmobil(),
			registrierung.isAbgleichElektronischerImpfausweis(),
			registrierung.getContactTracing(),
			registrierung.getMail(),
			registrierung.getTelefon(),
			registrierung.getKrankenkasse(),
			registrierung.getKrankenkasseKartenNr(),
			registrierung.getAuslandArt(),
			registrierung.getGeburtsdatum(),
			registrierung.getVerstorben(),
			registrierung.getBemerkung(),
			fragebogen.getAmpel(),
			fragebogen.getChronischeKrankheiten(),
			fragebogen.getLebensumstaende(),
			fragebogen.getBeruflicheTaetigkeit(),
			personenkontrolle.getIdentifikationsnummer(),
			impfkontrolleTerminJax,
			termin1,
			termin2,
			terminN,
			impfung1 != null ? ImpfungJax.from(impfung1, ImpfinformationenService.getImpffolgeNrOfImpfung1Or2(externesZertifikat, Impffolge.ERSTE_IMPFUNG)) : null,
			impfung2 != null ? ImpfungJax.from(impfung2, ImpfinformationenService.getImpffolgeNrOfImpfung1Or2(externesZertifikat, Impffolge.ZWEITE_IMPFUNG)) : null,
			registrierung.getGewuenschterOdi() != null ? new OrtDerImpfungDisplayNameJax(registrierung.getGewuenschterOdi()) : null,
			registrierung.isNichtVerwalteterOdiSelected(),
			registrierung.getRegistrierungsEingang(),
			externesZertifikat != null ? ExternGeimpftJax.from(externesZertifikat) : null,
			registrierung.getSchutzstatus(),
			registrierung.getKeinKontakt(),
			impfschutzJax
		);
	}

	@NonNull
	public Fragebogen toFragebogenForRegistrierung() {
		// Daten zur Registrierung
		Registrierung registrierung = new Registrierung();
		registrierung.setGeschlecht(geschlecht);
		registrierung.setName(name);
		registrierung.setVorname(vorname);
		registrierung.setGeburtsdatum(geburtsdatum);
		registrierung.setVerstorben(verstorben);
		registrierung.setAdresse(adresse.toEntity());
		registrierung.setMail(mail);
		registrierung.setTelefon(telefon);
		registrierung.setBemerkung(bemerkung);
		registrierung.setKrankenkasse(krankenkasse);
		registrierung.setKrankenkasseKartenNrAndArchive(krankenkasseKartenNr);
		registrierung.setAuslandArt(auslandArt);
		registrierung.setImmobil(immobil);
		registrierung.setAbgleichElektronischerImpfausweis(abgleichElektronischerImpfausweis);
		registrierung.setContactTracing(contactTracing);
		registrierung.setSchutzstatus(schutzstatus);
		registrierung.setKeinKontakt(keinKontakt);

		// Gesundheitsfragebogen
		Fragebogen fragebogen = new Fragebogen();
		fragebogen.setAmpel(ampelColor);
		fragebogen.setBeruflicheTaetigkeit(beruflicheTaetigkeit);
		fragebogen.setLebensumstaende(lebensumstaende);
		fragebogen.setChronischeKrankheiten(chronischeKrankheiten);
		// Kontroll-Daten (Kontrolle geschieht gleichzeitig mit Erfassung)
		fragebogen.setPersonenkontrolle(new Personenkontrolle());
		fragebogen.getPersonenkontrolle().setIdentifikationsnummer(identifikationsnummer);

		fragebogen.setRegistrierung(registrierung);
		return fragebogen;
	}

	public void apply(@NonNull Fragebogen fragebogen, @NonNull ImpfungkontrolleTermin impfungkontrolleTerminToApplyTo) {
		// Die Daten aus dem Fragebogen duerfen nicht ueberschrieben werden
		final Registrierung registrierung = fragebogen.getRegistrierung();
		registrierung.setGeschlecht(geschlecht);
		registrierung.setName(name);
		registrierung.setVorname(vorname);
		registrierung.setGeburtsdatum(geburtsdatum);
		registrierung.setVerstorben(verstorben);
		registrierung.setAdresse(adresse.toEntity());
		registrierung.setMail(mail);
		registrierung.setTelefon(telefon);
		registrierung.setBemerkung(bemerkung);
		registrierung.setKrankenkasse(krankenkasse);
		registrierung.setAuslandArt(auslandArt);
		registrierung.setKrankenkasseKartenNrAndArchive(krankenkasseKartenNr);
		registrierung.setImmobil(immobil);
		registrierung.setAbgleichElektronischerImpfausweis(abgleichElektronischerImpfausweis);
		registrierung.setContactTracing(contactTracing);
		registrierung.setSchutzstatus(schutzstatus);
		registrierung.setKeinKontakt(keinKontakt);
		fragebogen.getPersonenkontrolle().setIdentifikationsnummer(identifikationsnummer);
		Objects.requireNonNull(impfungkontrolleTermin);
		impfungkontrolleTermin.apply(impfungkontrolleTerminToApplyTo);
	}
}
