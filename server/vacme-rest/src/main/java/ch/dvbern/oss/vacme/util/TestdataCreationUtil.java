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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import ch.dvbern.oss.vacme.entities.benutzer.Benutzer;
import ch.dvbern.oss.vacme.entities.covidcertificate.CovidCertificateVaccinesNames;
import ch.dvbern.oss.vacme.entities.embeddables.Adresse;
import ch.dvbern.oss.vacme.entities.embeddables.DateTimeRange;
import ch.dvbern.oss.vacme.entities.externeimpfinfos.ExternesZertifikat;
import ch.dvbern.oss.vacme.entities.impfen.AuslandArt;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossier;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.ImpfempfehlungChGrundimmunisierung;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.Impfschutz;
import ch.dvbern.oss.vacme.entities.impfen.Impfstoff;
import ch.dvbern.oss.vacme.entities.impfen.Impfstofftyp;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.impfen.ImpfungkontrolleTermin;
import ch.dvbern.oss.vacme.entities.impfen.Krankenkasse;
import ch.dvbern.oss.vacme.entities.impfen.Verarbreichungsart;
import ch.dvbern.oss.vacme.entities.impfen.Verarbreichungsort;
import ch.dvbern.oss.vacme.entities.impfen.Verarbreichungsseite;
import ch.dvbern.oss.vacme.entities.registration.AmpelColor;
import ch.dvbern.oss.vacme.entities.registration.BeruflicheTaetigkeit;
import ch.dvbern.oss.vacme.entities.registration.ChronischeKrankheiten;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Geschlecht;
import ch.dvbern.oss.vacme.entities.registration.Lebensumstaende;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungsEingang;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impfslot;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfungTyp;
import ch.dvbern.oss.vacme.entities.types.VollstaendigerImpfschutzTyp;
import ch.dvbern.oss.vacme.entities.types.ZulassungsStatus;
import ch.dvbern.oss.vacme.entities.zertifikat.Zertifikat;
import ch.dvbern.oss.vacme.jax.registration.ZertifikatJax;
import ch.dvbern.oss.vacme.service.covidcertificate.CovidCertUtils;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.util.Constants;
import com.github.javafaker.Faker;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class TestdataCreationUtil {

	@NonNull
	private static Registrierung createRegistrierung() {
		Registrierung registrierung = new Registrierung();
		registrierung.setRegistrierungsnummer("ABCDE");
		registrierung.setName(Faker.instance().name().lastName());
		registrierung.setVorname(Faker.instance().name().firstName());
		registrierung.setGeschlecht(Faker.instance().bool().bool() ? Geschlecht.MAENNLICH : Geschlecht.WEIBLICH);
		registrierung.setGeburtsdatum(LocalDate.of(1940, Month.JANUARY, 1));
		registrierung.setKrankenkasse(Krankenkasse.ANDERE);
		registrierung.setKrankenkasseKartenNrAndArchive("00000000000000000000");
		registrierung.setAuslandArt(AuslandArt.GRENZGAENGER);
		registrierung.setAdresse(createAdresse());
		registrierung.setRegistrierungsEingang(RegistrierungsEingang.CALLCENTER_REGISTRATION);
		registrierung.setMail("tim.tester@mailbucket.dvbern.ch");
		return registrierung;
	}

	@NonNull
	public static Fragebogen createFragebogen() {
		Fragebogen fragebogen = new Fragebogen();
		fragebogen.setAmpel(AmpelColor.GREEN);
		fragebogen.setChronischeKrankheiten(ChronischeKrankheiten.SCHWERE_KRANKHEITSVERLAEUFE);
		fragebogen.setBeruflicheTaetigkeit(BeruflicheTaetigkeit.ANDERE);
		fragebogen.setLebensumstaende(Lebensumstaende.ANDERE);
		fragebogen.setRegistrierung(createRegistrierung());
		return fragebogen;
	}

	@NonNull
	public static Adresse createAdresse() {
		Adresse adresse = new Adresse();
		adresse.setAdresse1(Faker.instance().address().streetAddressNumber());
		adresse.setPlz("3000");
		adresse.setOrt("Bern");
		return adresse;
	}

	public static ExternesZertifikat createExternesZertifikat(Registrierung registrierung, Impfstoff impfstoff, int anzahl, LocalDate newestImpfdatum) {
		ExternesZertifikat externesZertifikat = new ExternesZertifikat();
		externesZertifikat.setRegistrierung(registrierung);
		externesZertifikat.setImpfstoff(impfstoff);
		externesZertifikat.setGenesen(false);
		externesZertifikat.setAnzahlImpfungen(anzahl);
		externesZertifikat.setLetzteImpfungDate(newestImpfdatum);
		return externesZertifikat;
	}

	public static Impfstoff createImpfstoffModerna() {
		Impfstoff impfstoff = createImpfstoff(Constants.MODERNA_UUID, "Spikevax", "Moderna", 2,
			CovidCertificateVaccinesNames.MODERNA.getCode(), "30380777700688", ZulassungsStatus.EMPFOHLEN, ZulassungsStatus.EMPFOHLEN, Impfstofftyp.MRNA);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 1, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(2, 0, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffModernaBivalent() {
		Impfstoff impfstoff = createImpfstoff(Constants.MODERNA_BIVALENT_UUID, "Spikevax® Bivalent Original / Omicron", "Moderna", 2,
			CovidCertificateVaccinesNames.MODERNA_BIVALENT.getCode(), "7680690090012", ZulassungsStatus.EMPFOHLEN, ZulassungsStatus.EMPFOHLEN, Impfstofftyp.MRNA);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 1, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(2, 0, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffPfizer() {
		Impfstoff impfstoff = createImpfstoff(Constants.PFIZER_BIONTECH_UUID, "Comirnaty", "Pfizer/BioNTech", 2,
			CovidCertificateVaccinesNames.PFIZER_BIONTECH.getCode(), "04260703260118", ZulassungsStatus.EMPFOHLEN, ZulassungsStatus.EMPFOHLEN, Impfstofftyp.MRNA);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 1, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(2, 0, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffPfizerKinder() {
		Impfstoff impfstoff = createImpfstoff(Constants.PFIZER_BIONTECH_KINDER_UUID, "Comirnaty Kinder", "Pfizer/BioNTech", 2,
			"Comirnaty Kinder", "?pfizer-kinder?", ZulassungsStatus.EMPFOHLEN, ZulassungsStatus.NICHT_ZUGELASSEN, Impfstofftyp.MRNA); // todo team wie wird das eingeschraenkt sobald fuer Booster freigegeben?
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 1, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(2, 0, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffPfizerBivalent() {
		Impfstoff impfstoff = createImpfstoff(Constants.PFIZER_BIONTECH_BIVALENT_UUID, "Comirnaty Bivalent Original/Omicron BA.1®", "Pfizer/BioNTech", 2,
			CovidCertificateVaccinesNames.PFIZER_BIONTECH_BIVALENT.getCode(), "7680690470012", ZulassungsStatus.EMPFOHLEN, ZulassungsStatus.EMPFOHLEN, Impfstofftyp.MRNA);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 1, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(2, 0, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffAstraZeneca() {
		Impfstoff impfstoff = createImpfstoff(Constants.ASTRA_ZENECA_UUID, "VAXZEFRIA", "AstraZeneca", 2,
			CovidCertificateVaccinesNames.EU_1_21_1529.getCode(), "?astrazeneca?", ZulassungsStatus.EXTERN_ZUGELASSEN, ZulassungsStatus.EXTERN_ZUGELASSEN, Impfstofftyp.ANDERE);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 1, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(2, 0, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffJanssen() {
		Impfstoff impfstoff = createImpfstoff(Constants.JANSSEN_UUID, "COVID-19 vaccine", "Johnson & Johnson", 1,
			CovidCertificateVaccinesNames.EU_1_20_1525.getCode(), "?janssen?", ZulassungsStatus.ZUGELASSEN, ZulassungsStatus.ZUGELASSEN, Impfstofftyp.ANDERE);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 0, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffCovaxin() {
		Impfstoff impfstoff = createImpfstoff(Constants.COVAXIN_UUID, "Covaxin", "Bharat Biotech International Ltd", 2,
			CovidCertificateVaccinesNames.Covaxin.getCode(), "?covaxin?", ZulassungsStatus.EXTERN_ZUGELASSEN, ZulassungsStatus.EXTERN_ZUGELASSEN, Impfstofftyp.ANDERE);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 1, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(2, 0, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffSinopharm() {
		Impfstoff impfstoff = createImpfstoff(Constants.SINOPHARM_UUID, "SARS-CoV-2 Vaccine (Vero Cell)", "Sinopharm", 2,
			CovidCertificateVaccinesNames.BBIBP_CorV.getCode(), "?covaxin?", ZulassungsStatus.EXTERN_ZUGELASSEN, ZulassungsStatus.EXTERN_ZUGELASSEN, Impfstofftyp.ANDERE);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 1, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(3, 0, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffSinovac() {
		Impfstoff impfstoff = createImpfstoff(Constants.SINOVAC_UUID, "CoronaVac", "Sinovac", 2,
			CovidCertificateVaccinesNames.CoronaVac.getCode(), "?sinovac?", ZulassungsStatus.EXTERN_ZUGELASSEN, ZulassungsStatus.EXTERN_ZUGELASSEN, Impfstofftyp.ANDERE);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 1, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(3, 0, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffCovishield() {
		Impfstoff impfstoff = createImpfstoff(Constants.COVISHIELD_UUID, "COVISHIELD™", "Serum Institute of India Pvt. Ltd", 2,
			CovidCertificateVaccinesNames.Covishield.getCode(), "?covishield?", ZulassungsStatus.EXTERN_ZUGELASSEN, ZulassungsStatus.EXTERN_ZUGELASSEN, Impfstofftyp.ANDERE);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 1, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(2, 0, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffCOVOVAX() {
		Impfstoff impfstoff = createImpfstoff(Constants.COVOVAX_UUID, "COVOVAX™", "Serum Institute of India Pvt. Ltd", 2,
			CovidCertificateVaccinesNames.COVOVAX.getCode(), "?covovax?", ZulassungsStatus.EXTERN_ZUGELASSEN, ZulassungsStatus.EXTERN_ZUGELASSEN, Impfstofftyp.ANDERE);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 1, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(2, 0, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffNovavax() {
		// hat unterdessen geaendert und wurde direkt im GUI angepasst: Hersteller bisher "Novavax CZ a.s.", Code bisher unbekannt
		Impfstoff impfstoff = createImpfstoff(Constants.NOVAVAX_UUID, "NUVAXOVID™", "Novavax", 2,
			CovidCertificateVaccinesNames.Novavax.getCode(), "00380631000045", ZulassungsStatus.EMPFOHLEN, ZulassungsStatus.EMPFOHLEN, Impfstofftyp.ANDERE);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 1, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(2, 0, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffSputnikLight() {
		Impfstoff impfstoff = createImpfstoff(Constants.SPUTNIK_LIGHT_UUID, "Sputnik Light", "Gamaleya National Centre of epidemiology and Microbiology, "
			+ "Russia", 1, "TODO-sputnik-light", "?sputnik-light?", ZulassungsStatus.NICHT_WHO_ZUGELASSEN, ZulassungsStatus.NICHT_WHO_ZUGELASSEN, Impfstofftyp.ANDERE);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 1, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffSputnikV() {
		Impfstoff impfstoff = createImpfstoff(Constants.SPUTNIK_V_UUID, "Sputnik V", "Gamaleya National Centre of epidemiology and Microbiology, "
			+ "Russia", 2, CovidCertificateVaccinesNames.Sputnik_V.getCode(), "?sputnik-V?", ZulassungsStatus.NICHT_WHO_ZUGELASSEN, ZulassungsStatus.NICHT_WHO_ZUGELASSEN, Impfstofftyp.ANDERE);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 1, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(2, 0, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffConvidecia() {
		Impfstoff impfstoff = createImpfstoff(Constants.SPUTNIK_V_UUID, "Convidecia (Ad5-nCoV)", "Convidecia (Ad5-nCoV)", 1,
			CovidCertificateVaccinesNames.Convidecia.getCode(), "?convidecia?", ZulassungsStatus.NICHT_WHO_ZUGELASSEN, ZulassungsStatus.NICHT_WHO_ZUGELASSEN, Impfstofftyp.ANDERE);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 2, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffKazakhstan() {
		Impfstoff impfstoff = createImpfstoff(Constants.KAZAKHSTAN_RIBSP_UUID, "Kazakhstan RIBSP", "QazCovid-in", 2,
			"TODO-kazakhstan", "?kazakhstan?", ZulassungsStatus.NICHT_WHO_ZUGELASSEN, ZulassungsStatus.NICHT_WHO_ZUGELASSEN, Impfstofftyp.ANDERE);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 2, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(2, 1, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffAbdala() {
		Impfstoff impfstoff = createImpfstoff(Constants.ABADALA_CIGB66_UUID, "Abadala (CIGB-66)", "Center for Genetic Engineering and Biotechnology (CIGB)", 3,
			"TODO-abdala", "?abdala?", ZulassungsStatus.NICHT_WHO_ZUGELASSEN, ZulassungsStatus.NICHT_WHO_ZUGELASSEN, Impfstofftyp.ANDERE);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 2, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(2, 1, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(3, 0, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoffZifivax() {
		Impfstoff impfstoff = createImpfstoff(Constants.RBD_ZIFIVAX_UUID, "RBD-Dimer, Zifivax", "Anhui Zhifei Longcom, China ZF2001", 2,
			"TODO-zifivax", "?zifivax?", ZulassungsStatus.NICHT_WHO_ZUGELASSEN, ZulassungsStatus.NICHT_WHO_ZUGELASSEN, Impfstofftyp.ANDERE);
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(1, 2, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(2, 1, impfstoff));
		impfstoff.getImpfempfehlungenChGrundimmunisierung().add(new ImpfempfehlungChGrundimmunisierung(3, 0, impfstoff));
		return impfstoff;
	}

	public static Impfstoff createImpfstoff(UUID id, String name, String hersteller, int anzahl, String certCode, String code,
		ZulassungsStatus zulassungsStatus, ZulassungsStatus zulassungsStatusBooster, Impfstofftyp impfstofftyp) {
		Impfstoff impfstoff = new Impfstoff();
		impfstoff.setId(id);
		impfstoff.setName(name);
		impfstoff.setHersteller(hersteller);
		impfstoff.setAnzahlDosenBenoetigt(anzahl);
		impfstoff.setCovidCertProdCode(certCode);
		impfstoff.setCode(code);
		impfstoff.setZulassungsStatus(zulassungsStatus);
		impfstoff.setZulassungsStatusBooster(zulassungsStatusBooster);
		impfstoff.setImpfstofftyp(impfstofftyp);
		return impfstoff;
	}

	public static Impfung createImpfung(@NonNull LocalDate date, @NonNull Impfstoff impfstoff) {
		Impfung impfung = new Impfung();
		impfung.setTimestampImpfung(date.atStartOfDay());
		impfung.setImpfstoff(impfstoff);
		impfung.setMenge(BigDecimal.valueOf(0.3d));
		impfung.setLot("123456");
		impfung.setVerarbreichungsart(Verarbreichungsart.SUBKUTAN);
		impfung.setVerarbreichungsort(Verarbreichungsort.OBERARM);
		impfung.setVerarbreichungsseite(Verarbreichungsseite.LINKS);
		impfung.setBenutzerVerantwortlicher(createBenutzer("Doe", "John", "1000"));
		impfung.setBenutzerDurchfuehrend(createBenutzer("Munsterman", "Max", "2000"));
		impfung.setBemerkung("Das ist eine Bemerkung die sehr lang ist und die einen Zeilenumbruch hat damit wir sehen, wie es auf dem pdf ausschaut");
		impfung.setGrundimmunisierung(true);

		impfung.setTermin(createImpftermin(createOrtDerImpfung(), date));
		return impfung;
	}

	public static Impfung createBoosterImpfung(@NonNull LocalDate date, @NonNull Impfstoff impfstoff) {
		Impfung impfung = createImpfung(date, impfstoff);
		impfung.setGrundimmunisierung(false);
		return impfung;
	}

	public static Benutzer createBenutzer(String name, String vorname, String glnNummer) {
		Benutzer benutzer = new Benutzer(UUID.randomUUID());
		benutzer.setName(name);
		benutzer.setVorname(vorname);
		benutzer.setGlnNummer(glnNummer);
		benutzer.setBenutzername(name);
		benutzer.setEmail(vorname + "." + name + "@mailbucket.dvbern.ch");
		benutzer.setIssuer("Test");
		return benutzer;
	}

	public static OrtDerImpfung createOrtDerImpfung() {
		OrtDerImpfung odi = new OrtDerImpfung();
		odi.setName("Bern Wankdorf " + UUID.randomUUID().toString());
		odi.setGlnNummer("1200");
		odi.setTyp(OrtDerImpfungTyp.IMPFZENTRUM);
		return odi;
	}

	public static Impftermin createImpftermin(@NonNull OrtDerImpfung odi, LocalDate date) {
		Impfslot impfslot = new Impfslot();
		impfslot.setOrtDerImpfung(odi);
		impfslot.setZeitfenster(DateTimeRange.of(date.atStartOfDay(), date.atStartOfDay().plusMinutes(30)));
		Impftermin impftermin = new Impftermin();
		impftermin.setImpfslot(impfslot);
		return impftermin;
	}

	public static ImpfinformationDto createImpfinformationen(@Nullable LocalDate latestVacmeImpfung, @Nullable LocalDate latestExternalImpfung) {
		Fragebogen fragebogen = TestdataCreationUtil.createFragebogen();
		Registrierung reg = fragebogen.getRegistrierung();

		Impfstoff impfstoff = createImpfstoffModerna();
		final OrtDerImpfung odi = createOrtDerImpfung();
		int anzahl = 2;
		ExternesZertifikat externeImpfinfo = latestExternalImpfung != null
			? TestdataCreationUtil.createExternesZertifikat(fragebogen.getRegistrierung(), impfstoff, anzahl,
			latestExternalImpfung)
			: null;

		ImpfinformationDto impfinformationDto;
		if (latestVacmeImpfung != null) {
			Impfung impfung1 = createImpfung(LocalDate.of(2021, 1, 1), impfstoff);
			Impfung impfung2 = createImpfung(latestVacmeImpfung, impfstoff);

			impfinformationDto = new ImpfinformationDto(reg, impfung1, impfung2, createDummyImpfdossier(latestVacmeImpfung, impfung1, impfung2),
				externeImpfinfo);

			Impftermin impftermin1 = createImpftermin(odi, impfung1.getTimestampImpfung().toLocalDate());
			impftermin1.setGebuchtFromImpfterminRepo(true);// allowed for unittest
			impfung1.setTermin(impftermin1);
			impfung1.getTermin().setImpffolge(Impffolge.ERSTE_IMPFUNG);
			reg.setImpftermin1FromImpfterminRepo(impftermin1); // allowed for unittest

			Impftermin impftermin2 = createImpftermin(odi, impfung2.getTimestampImpfung().toLocalDate());
			impftermin2.setGebuchtFromImpfterminRepo(true);// allowed for unittest
			impfung2.setTermin(impftermin2);
			impfung2.getTermin().setImpffolge(Impffolge.ZWEITE_IMPFUNG);
			reg.setImpftermin2FromImpfterminRepo(impftermin1);// allowed for unittest

			reg.setStatusToAbgeschlossen(impfinformationDto, impfung2);
		} else {
			impfinformationDto = new ImpfinformationDto(reg, null, null, null, externeImpfinfo);
			if (externeImpfinfo == null) {
				reg.setVollstaendigerImpfschutzFlagAndTyp(null);
			} else {
				reg.setStatusToImmunisiertWithExternZertifikat(externeImpfinfo);
			}
		}
		return impfinformationDto;
	}

	private static Impfdossier createDummyImpfdossier(@NonNull LocalDate latestImpfung, Impfung impfung1,
		Impfung impfung2) {

		Impfdossier impfdossier = new Impfdossier();
		LocalDateTime impfschtutzTime = latestImpfung.atStartOfDay().plusMonths(12);

		Set<UUID> allowedImpfstoffe = new HashSet<>();
		allowedImpfstoffe.add(impfung1.getImpfstoff().getId());
		allowedImpfstoffe.add(impfung2.getImpfstoff().getId());
		Impfschutz impfschutz = new Impfschutz(impfschtutzTime, impfschtutzTime, impfschtutzTime, allowedImpfstoffe, false);
		impfdossier.setImpfschutz(impfschutz);

		return impfdossier;
	}

	@NonNull
	public static ImpfinformationDto createImpfinformationen(
		@NonNull Registrierung registrierung,
		@Nullable Impfung impfung1,
		@Nullable Impfung impfung2,
		@Nullable Impfdossier dossier,
		@Nullable ExternesZertifikat externesZertifikat,
		@Nullable List<Impfung> boosterImpfungen
	) {
		ImpfinformationDto infos = new ImpfinformationDto(
			registrierung,
			impfung1,
			impfung2,
			dossier,
			externesZertifikat
		);
		infos = new ImpfinformationDto(infos, boosterImpfungen);
		return infos;
	}

	public static ImpfinformationDto addBoosterImpfung(@NonNull ImpfinformationDto dto, @NonNull LocalDate dateOfBooster) {
		return addBoosterImpfung(dto, dateOfBooster, createImpfstoffModerna());
	}

	public static ImpfinformationDto addBoosterImpfung(@NonNull ImpfinformationDto dto, @NonNull LocalDate dateOfBooster, @NonNull Impfstoff impfstoff) {
		final int impffolgeNr = ImpfinformationenService.getCurrentKontrolleNr(dto);
		Impfung booster = createBoosterImpfung(dateOfBooster, impfstoff);
		List<Impfung> boosterImpfungen = new ArrayList<>();
		if (dto.getBoosterImpfungen() != null) {
			boosterImpfungen.addAll(dto.getBoosterImpfungen());
		}
		boosterImpfungen.add(booster);

		Impftermin boosterTermin = createImpftermin(createOrtDerImpfung(), booster.getTimestampImpfung().toLocalDate());
		boosterTermin.setImpffolge(Impffolge.BOOSTER_IMPFUNG);
		boosterTermin.setGebuchtFromImpfterminRepo(true);// allowed for unittest
		booster.setTermin(boosterTermin);

		Impfdossier dossier = dto.getImpfdossier() != null ? dto.getImpfdossier() : new Impfdossier();
		dossier.setRegistrierung(dto.getRegistrierung());
		Impfdossiereintrag eintrag = new Impfdossiereintrag();

		eintrag.setImpfdossier(dossier);
		eintrag.setImpffolgeNr(impffolgeNr);
		ImpfungkontrolleTermin kontrolle = new ImpfungkontrolleTermin();
		kontrolle.setBemerkung("Bemerkung fuer Booster " + impffolgeNr);
		eintrag.setImpfungkontrolleTermin(kontrolle);
		eintrag.setImpfterminFromImpfterminRepo(boosterTermin);
		dossier.getImpfdossierEintraege().add(eintrag);

		final ImpfinformationDto dto2 = new ImpfinformationDto(dto.getRegistrierung(), dto.getImpfung1(), dto.getImpfung2(), dossier,
			dto.getExternesZertifikat());
		return new ImpfinformationDto(dto2, boosterImpfungen);
	}

	public static ImpfinformationDto addExternesZertifikat(@NonNull ImpfinformationDto infos, @Nullable ExternesZertifikat externesZertifikat) {

		return TestdataCreationUtil.createImpfinformationen(
			infos.getRegistrierung(),
			null,
			null,
			infos.getImpfdossier(),
			externesZertifikat,
			infos.getBoosterImpfungen()
		);

	}

	/**
	 * Hilfsmethode welche aus dem gensen Flag und Status versucht einen VollstaendigerImpfschutzTyp zu definieren der
	 * zumindest im HappyCase OK ist zum testen
	 */
	@Nullable
	public static VollstaendigerImpfschutzTyp guessVollstaendigerImpfschutzTyp(boolean vollstaendigGeimpft, RegistrierungStatus status) {
		if (status == RegistrierungStatus.ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG) {
			return vollstaendigGeimpft ? VollstaendigerImpfschutzTyp.VOLLSTAENDIG_VACME_GENESEN : null;
		}
		return vollstaendigGeimpft ? VollstaendigerImpfschutzTyp.VOLLSTAENDIG_VACME : null;
	}

	@NonNull
	public static Impfung addBoosterToDossier(@NonNull Impfdossier dossier, @NonNull LocalDateTime timestampImpfung, @NonNull Integer impffolgeNr) {
		Impfung booster = createImpfungWithImpftermin(timestampImpfung, Impffolge.BOOSTER_IMPFUNG);
		Impfdossiereintrag eintrag = new Impfdossiereintrag();
		eintrag.setImpfterminFromImpfterminRepo(booster.getTermin());
		eintrag.setImpfdossier(dossier);
		eintrag.setImpffolgeNr(impffolgeNr);
		dossier.getImpfdossierEintraege().add(eintrag);
		return booster;
	}

	@NonNull
	public static Impfung createImpfungWithImpftermin(@NonNull LocalDateTime timestampImpfung, @NonNull Impffolge impffolge) {
		Impfung impfung = new Impfung();
		impfung.setTimestampImpfung(timestampImpfung);
		impfung.setImpfstoff(createImpfstoffModerna());
		impfung.setGrundimmunisierung(Impffolge.ERSTE_IMPFUNG.equals(impffolge) || Impffolge.ZWEITE_IMPFUNG.equals(impffolge));
		impfung.setMenge(BigDecimal.ONE);
		impfung.setVerarbreichungsart(Verarbreichungsart.SUBKUTAN);
		impfung.setVerarbreichungsort(Verarbreichungsort.OBERARM);
		impfung.setVerarbreichungsseite(Verarbreichungsseite.LINKS);
		impfung.setLot("123456");

		Impftermin impftermin = new Impftermin();
		impftermin.setGebuchtFromImpfterminRepo(true);// allowed for unittest
		impftermin.setImpffolge(impffolge);
		impfung.setTermin(impftermin);

		Impfslot slot = new Impfslot();
		slot.setZeitfenster(DateTimeRange.of(timestampImpfung, timestampImpfung));
		impftermin.setImpfslot(slot);

		OrtDerImpfung odi = new OrtDerImpfung();
		odi.setName("Test Impfzentrum");
		odi.setTyp(OrtDerImpfungTyp.IMPFZENTRUM);
		slot.setOrtDerImpfung(odi);

		return impfung;
	}

	@NonNull
	public static List<ZertifikatJax> createZertifikateForImpfinformationen(@NonNull ImpfinformationDto infos) {
		List<ZertifikatJax> result = new ArrayList<>();

		if (infos.getImpfung2() != null && !infos.getImpfung2().isExtern()) {
			ZertifikatJax zertifikatJax = createZertifikatJax(infos.getImpfung2(), LocalDateTime.now());
			zertifikatJax.setNumberOfDoses(ImpfinformationenService.getImpffolgeNr(infos, infos.getImpfung2()));
			zertifikatJax.setTotalNumberOfDoses(infos.getImpfung2().getImpfstoff().getAnzahlDosenBenoetigt());
			result.add(zertifikatJax);
		}
		if (infos.getBoosterImpfungen() != null) {
			for (Impfung impfung : infos.getBoosterImpfungen()) {
				if (!impfung.isExtern()) {
					Integer impffolgeNr = ImpfinformationenService.getImpffolgeNr(infos, impfung);
					int zahlNachSchraegstrich = CovidCertUtils.calculateZahlNachSchraegstrich(infos, infos.getRegistrierung(), impffolgeNr);
					ZertifikatJax zertifikatJax1 = createZertifikatJax(impfung, LocalDateTime.now().minusMonths(2).minusDays(1));
					zertifikatJax1.setNumberOfDoses(impffolgeNr);
					zertifikatJax1.setTotalNumberOfDoses(zahlNachSchraegstrich);

					ZertifikatJax zertifikatJax2 = createZertifikatJax(impfung, LocalDateTime.now());
					zertifikatJax2.setNumberOfDoses(impffolgeNr);
					zertifikatJax2.setTotalNumberOfDoses(zahlNachSchraegstrich);

					ZertifikatJax zertifikatJax3 = createZertifikatJax(impfung, LocalDateTime.now().minusMonths(6).minusDays(5));
					zertifikatJax3.setNumberOfDoses(impffolgeNr);
					zertifikatJax3.setTotalNumberOfDoses(zahlNachSchraegstrich);

					result.add(zertifikatJax1);
					result.add(zertifikatJax2);
					result.add(zertifikatJax3);
				}
			}
		}
		return result;
	}

	@NonNull
	private static Zertifikat createZertifikat(@NonNull Impfung impfung, @NonNull LocalDateTime timestampErstellt) {
		Zertifikat zertifikat = new Zertifikat();
		zertifikat.setImpfung(impfung);
		zertifikat.setUvci("urn:uvci:01:CH:D42519B1C8292DD04C91A76D");
		zertifikat.setRevoked(false);
		zertifikat.setTimestampErstellt(timestampErstellt);
		return zertifikat;
	}

	@NonNull
	private static ZertifikatJax createZertifikatJax(@NonNull Impfung impfung, @NonNull LocalDateTime timestampErstellt) {
		ZertifikatJax zertifikatJax = new ZertifikatJax(createZertifikat(impfung, timestampErstellt));
		return zertifikatJax;
	}
}
