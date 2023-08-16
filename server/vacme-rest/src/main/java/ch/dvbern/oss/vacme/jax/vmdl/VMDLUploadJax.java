
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

package ch.dvbern.oss.vacme.jax.vmdl;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

import javax.validation.constraints.NotNull;

import ch.dvbern.oss.vacme.entities.impfen.Impfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfungTyp;
import ch.dvbern.oss.vacme.entities.types.daterange.DateUtil;
import ch.dvbern.oss.vacme.i18n.MandantUtil;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import ch.dvbern.oss.vacme.shared.util.Constants;
import ch.dvbern.oss.vacme.shared.util.OpenApiConst;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Slf4j
public class VMDLUploadJax {

	private static final int AGE_REASON_LIMIT = 65;
	public static final String UNKOWN_KANTON = "UNK";
	public static final String MEDSTAT_UNKNOWN = "0000";
	public static final String MEDSTAT_AUSLAND = "XX99";

	@JsonIgnore
	private Impfung impfung;

	@JsonProperty("reporting_unit_id")
	@Schema(required = true, maxLength = 8)
	private String reportingUnitID;

	@JsonProperty("vacc_event_id")
	@Schema(required = true, maxLength = 256)
	private String vaccEventID;

	@JsonProperty("person_anonymised_id")
	@Schema(required = true, maxLength = 64)
	private String personAnonymisedID;

	@JsonProperty("person_residence_ctn")
	@Schema(required = true, maxLength = 3)
	private String personResidenceCtn;

	// MedStat: Wohnort nach Gesundheitsversorgungsregion (MedStat)
	@JsonProperty("medstat")
	@Schema(required = true, maxLength = 4)
	private String medstat;

	@JsonProperty("person_age")
	@Schema(required = true, minimum = "0", maximum = "120")
	private int personAge;

	@JsonProperty("person_sex")
	@Schema(required = true, enumeration = { "1", "2", "3" })
	private int personSexe;

	@JsonProperty("vacc_reason_age")
	@Schema(required = true, enumeration = { "0", "1" })
	private int vaccReasonAge;

	@JsonProperty("vacc_reason_chronic_disease")
	@Schema(required = true, enumeration = { "0", "1" })
	private int vaccReasonChronicDisease;

	@JsonProperty("vacc_reason_med_prof")
	@Schema(required = true, enumeration = { "0", "1" })
	private int vaccReasonMedProf;

	@JsonProperty("vacc_reason_contact_vuln")
	@Schema(required = true, enumeration = { "0", "1" })
	private int vaccReasonContactVuln;

	@JsonProperty("vacc_reason_contact_comm")
	@Schema(required = true, enumeration = { "0", "1" })
	private int vaccReasonContactComm;

	@JsonProperty("vacc_reason_othr")
	@Schema(required = true, enumeration = { "0", "1" })
	private int vaccReasonOther;

	// Serie: 0 = gehoert zur Grundimmunisierung, 1 =  Boosterimpfung, 2+ = Boosterimpfung 2 etc.
	@JsonProperty("serie")
	@Schema(required = true,  minimum = "0",  maximum = "9")
	private int serie;

	@JsonProperty("reporting_unit_location_ctn")
	@Schema(required = true, maxLength = 2)
	private String reportingUnitLocationCtn;

	@JsonProperty("reporting_unit_location_id")
	@Schema(required = true, maxLength = 32)
	private String reportingUnitLocationID;

	@JsonProperty("reporting_unit_location_type")
	@Schema(required = true, enumeration = { "1", "2", "3", "4", "5", "6", "99" })
	private int reportingUnitLocationType;

	@JsonProperty("vacc_lot_number")
	@Schema(required = true, maxLength = 32)
	private String vaccLotNumber;

	@JsonProperty("vacc_id")
	@Schema(required = true, maxLength = 32)
	private String vaccID;

	@JsonProperty("vacc_date")
	@Schema(required = true, format = OpenApiConst.Format.DATE, minimum = "2020-12-20")
	private LocalDate vaccDate;

	@JsonProperty("vacc_count")
	@Schema(required = true)
	private int vaccCount;

	@Nullable
	@JsonProperty("person_pregnancy")
	@Schema(enumeration = { "0", "1" })
	private Integer personPregnancy;

	@JsonProperty("person_recovered_from_covid")
	@Schema(enumeration = { "0", "1" })
	private Integer personRecoveredFromCovid;

	@Nullable
	@JsonProperty("pcr_tested_positive_date")
	@Schema(format = OpenApiConst.Format.DATE, minimum = "2020-12-20")
	private LocalDate pcrTestedPositiveDate;

	@JsonIgnore
	private String plz; // noetig fuer Kanton und MedStat

	@JsonIgnore
	private boolean isAusland; // noetig fuer MedStat

	@SuppressWarnings("unused") // Wird in ImpfungRepo benutzt (Projections.constructor)
	public VMDLUploadJax(
		@NonNull Impfung impfung,
		@NonNull Registrierung registrierung,
		@NonNull Fragebogen fragebogen,
		@NonNull String reportingUnitID
	) {
		this(impfung, registrierung, fragebogen, null, reportingUnitID);

	}

	@SuppressWarnings("unused") // Wird in ImpfungRepo benutzt (Projections.constructor)
	public VMDLUploadJax(
		@NonNull Impfung impfung,
		@NonNull Registrierung registrierung,
		@NonNull Fragebogen fragebogen,
		@Nullable Impfdossiereintrag impfdossiereintrag,
		@NonNull String reportingUnitID
	) {
		this.impfung = impfung;
		this.reportingUnitID = reportingUnitID;
		this.vaccEventID = reportingUnitID + "-" + impfung.getId().toString();
		this.personAnonymisedID = registrierung.getId().toString();
		this.plz = registrierung.getAdresse().getPlz();
		this.isAusland = registrierung.getAuslandArt() != null;
		this.personResidenceCtn = UNKOWN_KANTON; // will later be set by mapping plz if possible
		this.medstat = MEDSTAT_UNKNOWN; // will later be set by mapping plz if possible
		this.personAge = calculateAge(registrierung);
		this.personSexe = getSexe(registrierung);
		this.vaccReasonAge = isAgeReason(registrierung);
		this.vaccReasonChronicDisease = isChronicDiseaseReason(fragebogen);
		this.vaccReasonMedProf = isReasonMedProf(fragebogen);
		this.vaccReasonContactVuln = isReasonContactVuln(fragebogen);
		this.vaccReasonContactComm = isReasonContactComm(fragebogen);
		this.vaccReasonOther = isReasonOther(fragebogen);
		this.serie = calculateSerie(impfung, impfdossiereintrag);
		this.reportingUnitLocationCtn = MandantUtil.getMandant().name();
		this.reportingUnitLocationID = getUnitLocationID(impfung.getTermin().getImpfslot().getOrtDerImpfung());
		this.reportingUnitLocationType = getOdiType(impfung.getTermin().getImpfslot().getOrtDerImpfung().getTyp());
		this.vaccLotNumber = impfung.getLot();
		this.vaccID = impfung.getImpfstoff().getCode();
		this.vaccDate = impfung.getTimestampImpfung().toLocalDate();
		this.vaccCount = getVaccCount(impfung.getTermin().getImpffolge(), impfdossiereintrag);
		this.personPregnancy = isPersonPregnant(impfung);
		this.personRecoveredFromCovid = isPersonRecoveredFromCovid(registrierung);
		this.pcrTestedPositiveDate = getPCRTestDate(registrierung);
	}

	private String getUnitLocationID(OrtDerImpfung ortDerImpfung) {
		String odiID = ortDerImpfung.getId().toString();
		odiID = odiID.replace("-", "");
		if (odiID.length() > 32) { // reporting unit location ID is 32 limited string.
			odiID = odiID.substring(0, 32);
		}
		return odiID;
	}

	private int calculateAge(Registrierung registrierung) {
		int realAge = Period.between(registrierung.getGeburtsdatum(), LocalDate.now()).getYears();
		return Math.min(Math.max(0, realAge), 120); // Lebensalter muss zwischen 0 und 120 Jahre sein (Laut Schnittstelle...)
	}

	private int getSexe(Registrierung registrierung) {
		switch (registrierung.getGeschlecht()) {
		case MAENNLICH:
			return 1;
		case WEIBLICH:
			return 2;
		case ANDERE:
		case UNBEKANNT:
			return 3;
		default:
			throw new IllegalArgumentException();
		}
	}

	private int isAgeReason(Registrierung registrierung) {
		return calculateAge(registrierung) >= AGE_REASON_LIMIT ? 1 : 0; // Ab 65 Jahre
	}

	private int isChronicDiseaseReason(Fragebogen fragebogen) {
		switch (fragebogen.getChronischeKrankheiten()) {
		case KRANKHEIT:
		case SCHWERE_KRANKHEITSVERLAEUFE:
			return 1;
		default:
			return 0;
		}
	}

	private int isReasonMedProf(Fragebogen fragebogen) {
		switch (fragebogen.getBeruflicheTaetigkeit()) {
		case GES_PERSONAL_MIT_PAT_KONTAKT_INTENSIV:
		case GES_PERSONAL_MIT_PAT_KONTAKT:
		case GES_PERSONAL_OHNE_PAT_KONTAKT:
			return 1;
		default:
			return 0;
		}
	}

	private int isReasonContactVuln(Fragebogen fragebogen) {
		switch (fragebogen.getLebensumstaende()) {
		case MIT_BESONDERS_GEFAEHRDETEN_PERSON:
			return 1;
		}
		switch (fragebogen.getBeruflicheTaetigkeit()) {
		case BETREUUNG_VON_GEFAERD_PERSON:
			return 1;
		}
		return 0;
	}

	private int isReasonContactComm(Fragebogen fragebogen) {
		switch (fragebogen.getLebensumstaende()) {
		case GEMEINSCHAFTEN:
		case MASSENUNTERKUENFTEN:
			return 1;
		default:
			return 0;
		}
	}

	private int isReasonOther(@NonNull Fragebogen fragebogen) {
		return (
			isAgeReason(fragebogen.getRegistrierung()) +
				isChronicDiseaseReason(fragebogen) +
				isReasonMedProf(fragebogen) +
				isReasonContactVuln(fragebogen) +
				isReasonContactComm(fragebogen)
		) > 0 ? 0 : 1;
	}

	/**
	Alle mit grundimmunisierung=true: 1 (also auch dritte Impfung, wenn sie grundimmunisierung hat)
	nachfolgende Impfungen: hochzaehlen, also 2, 3, 4...
	 */
	private int calculateSerie(@NonNull Impfung impfung, @Nullable Impfdossiereintrag impfdossiereintrag) {
		if (impfung.isGrundimmunisierung()) {
			return 1;
		}
		if (impfdossiereintrag == null) {
			LOG.warn("Fuer Impfung {} war der Impfdossiereintrag null", impfung.getId().toString());
			throw new AppFailureException("VMDL Daten waren Inkonsistent");
		}
		// Wenn man corona hatte oder janssen wuerden wir ohne das max eine 1 schicken beim ersten booster was falsch waere
		return Math.max(impfdossiereintrag.getImpffolgeNr() - 1, 2);
	}

	private int getOdiType(@NotNull @NonNull OrtDerImpfungTyp typ) {
		switch (typ) {
		case IMPFZENTRUM:
		case KINDER_IMPFZENTRUM:
		case MOBIL:
			return 1;
		case ALTERSHEIM:
			return 2;
		case HAUSARZT:
			return 3;
		case APOTHEKE:
			return 4;
		case SPITAL:
			return 6;
		case ANDERE:
			return 99;
		default:
			throw new IllegalArgumentException();
		}
	}

	private int getVaccCount(@NotNull @NonNull Impffolge impffolge, @Nullable Impfdossiereintrag impfdossiereintrag) {
		switch (impffolge) {
		case ERSTE_IMPFUNG:
			return 1; // TODO kann auch anders sein wegen unvollstaendigem externem Zertifikat, was will VMDL?
		case ZWEITE_IMPFUNG:
			return 2; // TODO kann auch anders sein wegen unvollstaendigem externem Zertifikat, was will VMDL?
		case BOOSTER_IMPFUNG:
			Objects.requireNonNull(impfdossiereintrag, "Impfdossiereintrag nicht vorhanden fuer Booster in  VMDL");
			Objects.requireNonNull(impfdossiereintrag.getImpftermin(), "Impfdossiereintrag ohne Termin in VMDL");
			return impfdossiereintrag.getImpffolgeNr();
		default:
			throw new IllegalArgumentException();
		}
	}

	// Weil man im VacMe UI angeben kann, dass man keine Angaben bezueglich Schwangerschaft geben moechte,
	// sollten wir in diesem Fall auch NULL an VMDL schicken
	@Nullable
	private Integer isPersonPregnant(Impfung impfung) {
		if (impfung.getSchwanger() != null) {
			return impfung.getSchwanger() ? Integer.valueOf(1) : Integer.valueOf(0);
		}
		return null;
	}

	private Integer isPersonRecoveredFromCovid(Registrierung registrierung) {
		return registrierung.abgeschlossenMitCorona() ? Integer.valueOf(1) : Integer.valueOf(0);
	}

	// VMDL erlaubt nur Daten ab dem 20.12.2020. Im VacMe dagegen erlauben wir Daten ab dem 01.01.2020
	@Nullable
	private LocalDate getPCRTestDate(Registrierung registrierung) {
		if (registrierung.getPositivGetestetDatum() != null &&
			DateUtil.contains(registrierung.getPositivGetestetDatum(),
				Constants.MIN_DATE_FOR_IMPFUNGEN.toLocalDate(),
				LocalDate.now())) {
			return registrierung.getPositivGetestetDatum();
		}
		return null;
	}
}
