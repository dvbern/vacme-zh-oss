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

package ch.dvbern.oss.vacme.service.boosterprioritaet.rules;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import ch.dvbern.oss.vacme.entities.base.ImpfInfo;
import ch.dvbern.oss.vacme.entities.externeimpfinfos.ExternesZertifikat;
import ch.dvbern.oss.vacme.entities.impfen.Erkrankung;
import ch.dvbern.oss.vacme.entities.impfen.Impfschutz;
import ch.dvbern.oss.vacme.entities.impfen.Impfstoff;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Prioritaet;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.types.daterange.DateUtil;
import ch.dvbern.oss.vacme.enums.Mandant;
import ch.dvbern.oss.vacme.i18n.MandantUtil;
import ch.dvbern.oss.vacme.service.boosterprioritaet.ImpfstoffInfosForRules;
import ch.dvbern.oss.vacme.shared.util.Constants;
import ch.dvbern.oss.vacme.util.EnumUtil;
import ch.dvbern.oss.vacme.util.PrioritaetUtil;
import com.google.common.collect.Iterables;
import org.apache.commons.lang3.Validate;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import static ch.dvbern.oss.vacme.entities.registration.RegistrierungsEingang.DATA_MIGRATION;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungsEingang.MASSENUPLOAD;

public class BoosterAgePunktePrioritaetImpfstoffRule implements IBoosterPrioritaetRule {

	public static final int IMPFSCHUTZ_DAUER_DAYS = 270;
	public static final int JANSSEN_FREIGABE_DAUER_MONTHS = 2;
	@Nullable
	private final Integer alterVon;

	@Nullable
	private final Integer alterBis;

	@Nullable
	private final Integer punkteVon;

	@Nullable
	private final Integer punkteBis;

	@Nullable
	private final Set<Prioritaet> prioritaeten;

	@Nullable
	private final Integer giltAbAnzahlErhalteneBooster;

	@Nullable
	private final Integer giltBisAnzahlErhalteneBooster;

	@Nullable
	private final Integer anzahlMonateBisFreigabe;
	@Nullable
	private final Integer anzahlTageBisFreigabe;

	@Nullable
	private final Integer anzahlMonateBisFreigabeKrankheit;
	@Nullable
	private final Integer anzahlTageBisFreigabeKrankheit;

	@NonNull
	private final ImpfstoffInfosForRules specifiedImpfstoffe;

	private final boolean enablePfizerOnlyForU30;

	private final boolean ruleOnlyValidForSelbstzahlerFreigabe;
	@Nullable
	private final LocalDate cutoffSelbstzahler;

	public BoosterAgePunktePrioritaetImpfstoffRule(
		@NonNull ImpfstoffInfosForRules specifiedImpfstoffe,
		@Nullable Integer alterVon,
		@Nullable Integer alterBis,
		@Nullable Integer punkteVon,
		@Nullable Integer punkteBis,
		@Nullable Set<Prioritaet> prioritaeten,
		@Nullable Integer anzahlMonateBisFreigabe,
		@Nullable Integer anzahlTageBisFreigabe,
		@Nullable Integer anzahlMonateBisFreigabeKrankheit,
		@Nullable Integer anzahlTageBisFreigabeKrankheit,
		@Nullable Integer giltAbAnzahlErhalteneBooster,
		@Nullable Integer giltBisAnzahlErhalteneBooster,
		boolean enablePfizerOnlyForU30,
		boolean ruleOnlyValidForSelbstzahlerFreigabe,
		@Nullable LocalDate cutoffSelbstzahler
	) {
		this.alterVon = alterVon;
		this.alterBis = alterBis;
		this.punkteVon = punkteVon;
		this.punkteBis = punkteBis;
		this.prioritaeten = prioritaeten;
		this.anzahlMonateBisFreigabe = anzahlMonateBisFreigabe;
		this.anzahlTageBisFreigabe = anzahlTageBisFreigabe;
		this.anzahlMonateBisFreigabeKrankheit = anzahlMonateBisFreigabeKrankheit;
		this.anzahlTageBisFreigabeKrankheit = anzahlTageBisFreigabeKrankheit;
		this.giltAbAnzahlErhalteneBooster = giltAbAnzahlErhalteneBooster;
		this.giltBisAnzahlErhalteneBooster = giltBisAnzahlErhalteneBooster;
		this.enablePfizerOnlyForU30 = enablePfizerOnlyForU30;
		this.specifiedImpfstoffe = specifiedImpfstoffe;
		this.ruleOnlyValidForSelbstzahlerFreigabe = ruleOnlyValidForSelbstzahlerFreigabe;
		this.cutoffSelbstzahler = cutoffSelbstzahler;
		validateDaysAndMonthsBothSetOrUnset();
	}

	private void validateDaysAndMonthsBothSetOrUnset() {
		Validate.isTrue((anzahlMonateBisFreigabe == null && anzahlTageBisFreigabe == null)
			|| (anzahlMonateBisFreigabe != null && anzahlTageBisFreigabe != null));

	}

	@Override
	@Nullable
	public Integer getAnzahlMonateBisFreigabe() {
		return anzahlMonateBisFreigabe;
	}

	@Override
	@NonNull
	public Optional<Impfschutz> calculateImpfschutz(
		@NonNull Fragebogen fragebogen,
		@NonNull List<ImpfInfo> orderedImpfInfos,
		@NonNull List<Erkrankung> orderedErkrankungen,
		@Nullable ExternesZertifikat externesZertifikat
	) {
		Registrierung registrierung = fragebogen.getRegistrierung();
		int alter = (int) DateUtil.getAge(registrierung.getGeburtsdatum());
		int anzahlBoosterImpfungen = countNumberOfBoosterImpfungen(orderedImpfInfos);

		ImpfInfo newestImpfInfo = findNewestImpfinfo(orderedImpfInfos); // latest Vacme Impfung or externesZertifikat
		LocalDate latestErkrankungDate =
			findNewestErkrankungDate(registrierung, orderedErkrankungen, externesZertifikat);

		// CHECK IF RULE APPLIES OR RETURN EMPTY
		try {
			if (newestImpfInfo == null) {
				throw new NoImpfschutzPossibleException();
			}
			checkAlter(alter); // beruecksichtigt auch, ob die Person innerhalb eines Jahres das Alter erreicht
			checkPunkte(fragebogen);
			checkPrio(registrierung);
		} catch (NoImpfschutzPossibleException e) {
			return Optional.empty();
		}

		// CALCULATIONS
		LocalDateTime immunisiertBis = calculateImmunisiertBis(newestImpfInfo);
		Set<UUID> erlaubteImpfstoffe = calculateErlaubteImpfstoffe(alter);
		LocalDate stichtagThatWillEnterAltersRange = calculateStichtagThatWillEnterAltersRange(registrierung, alter);

		LocalDateTime freigegebenAb = null;
		if (!this.ruleOnlyValidForSelbstzahlerFreigabe && isNextImpfungPaidThroughVaccinationCampaing(newestImpfInfo)) {
			freigegebenAb = calculateFreigegebenAbEkif(
				newestImpfInfo,
				stichtagThatWillEnterAltersRange,
				erlaubteImpfstoffe,
				latestErkrankungDate,
				anzahlBoosterImpfungen);
		}
		LocalDateTime freigegebenAbSelbstzahler = calculateFreigegebenAbSelbstzahler(
			newestImpfInfo,
			stichtagThatWillEnterAltersRange,
			erlaubteImpfstoffe,
			latestErkrankungDate,
			anzahlBoosterImpfungen);
		boolean benachrichtigungBeiFreigabe = calculateBenachrichtigung(registrierung);

		// IMPFSCHUTZ
		Impfschutz impfschutz = new Impfschutz(
			immunisiertBis,
			freigegebenAb,
			freigegebenAbSelbstzahler,
			erlaubteImpfstoffe,
			benachrichtigungBeiFreigabe);
		return Optional.of(impfschutz);
	}

	private boolean isNextImpfungPaidThroughVaccinationCampaing(@NonNull ImpfInfo newestImpfInfo) {
		if (cutoffSelbstzahler != null) {	// Falls diese regel ein cutoff date definiert hat
			return
				newestImpfInfo.getTimestampImpfung().toLocalDate().isBefore(cutoffSelbstzahler)
				|| !newestImpfInfo.isNextImpfungPossiblySelbstzahler(); // 1. Booster is still paid through VaccCampaing
		}
		// Sonst immer true zurück geben, damit es das if nicht blockiert
		return true;
	}

	private int countNumberOfBoosterImpfungen(@NonNull List<ImpfInfo> orderedImpfInfos) {
		return (int) orderedImpfInfos.stream().filter(i -> i != null && !i.gehoertZuGrundimmunisierung()).count();
	}

	private void checkAlter(int alter) throws NoImpfschutzPossibleException {
		if (alterVon != null) {
			if (alter < alterVon) {
				if (alterVon - alter == 1) {
					// Erreicht die Person das Alter innerhalb eines Jahres, erhaelt sie einen Impfschutz ab dem
					// naechsten Geburtstag
				} else {
					throw new NoImpfschutzPossibleException();
				}
			}
		}
		if (alterBis != null) {
			if (alter > alterBis) {
				throw new NoImpfschutzPossibleException();
			}
		}
	}

	@Nullable
	private LocalDate calculateStichtagThatWillEnterAltersRange(@NonNull Registrierung registrierung, int alter) {
		if (alterVon != null) {
			if (alter < alterVon) {
				if (alterVon - alter == 1) {
					// Erreicht die Person das Alter innerhalb eines Jahres, wird dies beruecksichtigt.
					return registrierung.getGeburtsdatum().plusYears(alterVon);
				}
			}
		}
		return null;
	}

	private void checkPunkte(@NonNull Fragebogen fragebogen) throws NoImpfschutzPossibleException {
		int punkte = PrioritaetUtil.calculatePrioritaetPunkten(fragebogen);
		if (punkteVon != null) {
			if (punkte < punkteVon) {
				throw new NoImpfschutzPossibleException();
			}
		}
		if (punkteBis != null) {
			if (punkte > punkteBis) {
				throw new NoImpfschutzPossibleException();
			}
		}
	}

	private void checkPrio(@NonNull Registrierung registrierung) throws NoImpfschutzPossibleException {
		if (prioritaeten != null && !prioritaeten.isEmpty()) {
			if (!prioritaeten.contains(registrierung.getPrioritaet())) {
				throw new NoImpfschutzPossibleException();
			}
		}
	}

	@Nullable
	private LocalDate findNewestErkrankungDate(
		@NonNull Registrierung registrierung,
		@NonNull List<Erkrankung> erkrankungen,
		@Nullable ExternesZertifikat externesZertifikat
	) {
		Erkrankung latestErkrankung =
			Iterables.getLast(erkrankungen, null); // die Liste ist schon sortiert -> die letzte daraus nehmen
		LocalDate latestErkrankungDate = latestErkrankung == null ? null : latestErkrankung.getDate();

		LocalDate positivGetestDatum = registrierung.getPositivGetestetDatum();
		final LocalDate positivGetestetEZ =
			externesZertifikat != null ? externesZertifikat.getPositivGetestetDatum() : null;

		return DateUtil.getLatestDateOrNull(latestErkrankungDate, positivGetestDatum, positivGetestetEZ);
	}

	private @Nullable ImpfInfo findNewestImpfinfo(List<ImpfInfo> orderedImpfInfos) {
		return Iterables.getLast(orderedImpfInfos, null);
	}

	@NonNull
	private LocalDateTime calculateImmunisiertBis(@NonNull ImpfInfo newestImpfInfo) {
		return newestImpfInfo.getTimestampImpfung()
			.plusDays(IMPFSCHUTZ_DAUER_DAYS)
			.toLocalDate()
			.atStartOfDay(); // aktuell 270 Tage
	}

	@Nullable
	private LocalDateTime calculateFreigegebenAbEkif(
		@NonNull ImpfInfo newestImpfInfo,
		@Nullable LocalDate alterRegelGuelitigAb,
		@NonNull Set<UUID> erlaubteImpfstoffe,
		@Nullable LocalDate latestErkrankungDate,
		int anzahlBoosterImpfungen
	) {
		return calculateFreigegebenAb(
			newestImpfInfo,
			alterRegelGuelitigAb,
			erlaubteImpfstoffe,
			latestErkrankungDate,
			false,
			anzahlBoosterImpfungen
		);
	}

	@Nullable
	private LocalDateTime calculateFreigegebenAbSelbstzahler(
		@NonNull ImpfInfo newestImpfInfo,
		@Nullable LocalDate alterRegelGuelitigAb,
		@NonNull Set<UUID> erlaubteImpfstoffe,
		@Nullable LocalDate latestErkrankungDate,
		int anzahlBoosterImpfungen
	) {
		return calculateFreigegebenAb(
			newestImpfInfo,
			alterRegelGuelitigAb,
			erlaubteImpfstoffe,
			latestErkrankungDate,
			true,
			anzahlBoosterImpfungen
		);
	}

	@Nullable
	private LocalDateTime calculateFreigegebenAb(
		@NonNull ImpfInfo newestImpfInfo,
		@Nullable LocalDate alterRegelGueltigAb,
		@NonNull Set<UUID> erlaubteImpfstoffe,
		@Nullable LocalDate latestErkrankungDate,
		boolean selbstzahlerModus,
		int anzahlBoosterImpfungen
	) {
		// Wenn die erlaubten Impfstoffe leer sind: Freigabedatum = null!
		if (erlaubteImpfstoffe.isEmpty()) {
			return null;
		}

		LocalDateTime freigegebenAb = null;

		// im Selbstzahlermodus wird ein Friegabedatum fuer selbstzahler berechnet auch wenn
		// die Ekif Empfehlung die Regel noch nicht beinhaltet
		boolean regelRelevant = selbstzahlerModus || isRegelForCurrentBooster(anzahlBoosterImpfungen);

		if (regelRelevant) {
			LocalDateTime freigabeBasedOnLtstImpfung = calcFreigegebenAbBasedOnImpfung(newestImpfInfo);
			LocalDateTime freigabeBasedOnLtstKrankheit = calculateFreigegebenAbBasedOnKrankheit(latestErkrankungDate);

			freigegebenAb = DateUtil.getLaterDateTimeOrNull(freigabeBasedOnLtstImpfung, freigabeBasedOnLtstKrankheit);

			// Falls ein alternativer Stichtag fuer die Regeln definiert ist (d.h. wenn ich in naher Zukunft das
			// Regel-Alter erreiche) und dieses nach dem bisher berechneten freigegebenAb liegt, so nehmen wir
			// dieses
			if (alterRegelGueltigAb != null
				&& freigegebenAb != null
				&& alterRegelGueltigAb.isAfter(freigegebenAb.toLocalDate())) {
				freigegebenAb = alterRegelGueltigAb.atStartOfDay();
			}
		}
		return freigegebenAb;
	}

	@Nullable
	private LocalDateTime calcFreigegebenAbBasedOnImpfung(
		@NonNull ImpfInfo newestImpfInfo
	) {
		LocalDateTime freigegebenAbBasedOnImpfung = null;
		if (anzahlMonateBisFreigabe != null && anzahlTageBisFreigabe != null) {
			LocalDateTime impfdatum = newestImpfInfo.getTimestampImpfung();
			Objects.requireNonNull(impfdatum);
			freigegebenAbBasedOnImpfung = impfdatum.plusMonths(anzahlMonateBisFreigabe)
				.plusDays(anzahlTageBisFreigabe)
				.toLocalDate()
				.atStartOfDay();
		}
		return freigegebenAbBasedOnImpfung;
	}

	@Nullable
	private LocalDateTime calculateFreigegebenAbBasedOnKrankheit(@Nullable LocalDate latestErkrankungDate) {
		LocalDateTime freigegebenAbBasedOnKrankheit = null;
		if (anzahlMonateBisFreigabeKrankheit != null && anzahlTageBisFreigabeKrankheit != null
			&& latestErkrankungDate != null
		) {
			freigegebenAbBasedOnKrankheit = latestErkrankungDate
				.plusMonths(anzahlMonateBisFreigabeKrankheit)
				.plusDays(anzahlTageBisFreigabeKrankheit)
				.atStartOfDay();
		}
		return freigegebenAbBasedOnKrankheit;
	}

	private boolean isRegelForCurrentBooster(int anzahlBoosterImpfungen) {
		boolean valid = true;
		if (giltAbAnzahlErhalteneBooster != null) {
			valid = anzahlBoosterImpfungen >= giltAbAnzahlErhalteneBooster;
		}
		if (giltBisAnzahlErhalteneBooster != null) {
			valid = valid && anzahlBoosterImpfungen <= giltBisAnzahlErhalteneBooster;
		}
		return valid;
	}

	@NonNull
	private Set<UUID> calculateErlaubteImpfstoffe(int alter) {
		Set<UUID> erlaubteImpfstoffe = specifiedImpfstoffe.getSpecifiedEmpfohleneBoosterImpfstoffe()
			.stream()
			.map(Impfstoff::getId)
			.collect(Collectors.toSet());

		if (!erlaubteImpfstoffe.isEmpty() && enablePfizerOnlyForU30 && alter < 30) {
			erlaubteImpfstoffe = Set.of(Constants.PFIZER_BIONTECH_UUID);
		}
		return erlaubteImpfstoffe;
	}

	public boolean calculateBenachrichtigung(@NonNull Registrierung registrierung) {
		// BENACHRICHTIGUNG BEI FREIGABE
		boolean benachrichtigungBeiFreigabe;
		if (MandantUtil.getMandant().equals(Mandant.ZH)) {
			// Nicht benachrichtigt, werden anonymisierte oder Migrationsdaten aus Heimen
			benachrichtigungBeiFreigabe = !registrierung.isAnonymisiert()
				&& isNotExternaluserOfHeim(registrierung);
		} else {
			// Bei Massenupload und Datenmigration soll keine Benachrichtigung geschickt werden
			benachrichtigungBeiFreigabe =
				EnumUtil.isNoneOf(registrierung.getRegistrierungsEingang(), MASSENUPLOAD, DATA_MIGRATION);
		}
		return benachrichtigungBeiFreigabe;
	}

	private boolean isNotExternaluserOfHeim(Registrierung registrierung) {
		return registrierung.getExternalId() == null || (!registrierung.getExternalId().startsWith("T_HEIM")
			&& !registrierung.getExternalId().startsWith(
			"HEIM"));
	}

	private static class NoImpfschutzPossibleException extends Exception {
		private static final long serialVersionUID = 1789523350113085417L;
	}

	public static IBoosterPrioritaetRule createMinAgeRule(
		@NonNull ImpfstoffInfosForRules specifiedImpfstoffe,
		int minAge,
		int anzahlMonateBisFreigabe,
		int anzahlTageBisFreigabe,
		@Nullable Integer anzahlMonateBisFreigabeKrankheit,
		@Nullable Integer anzahlTageBisFreigabeKrankheit,
		@Nullable Integer giltAbAnzahlErhalteneBooster,
		@Nullable Integer giltBisAnzahlErhalteneBooster,
		boolean enablePfizerOnlyForU30,
		boolean ruleOnlyValidForSelbstzahlerFreigabe,
		@Nullable LocalDate cutoffSelbstzahler
	) {
		return new BoosterAgePunktePrioritaetImpfstoffRule(
			specifiedImpfstoffe,
			minAge, null,
			null, null,
			null,
			anzahlMonateBisFreigabe, anzahlTageBisFreigabe,
			anzahlMonateBisFreigabeKrankheit, anzahlTageBisFreigabeKrankheit,
			giltAbAnzahlErhalteneBooster, giltBisAnzahlErhalteneBooster,
			enablePfizerOnlyForU30, ruleOnlyValidForSelbstzahlerFreigabe, cutoffSelbstzahler);
	}

	public static IBoosterPrioritaetRule createPrioritaetenRule(
		@NonNull ImpfstoffInfosForRules specifiedImpfstoffe,
		Set<Prioritaet> prioritaeten,
		int anzahlMonateBisFreigabe,
		int anzahlTageBisFreigabe,
		@Nullable Integer anzahlMonateBisFreigabeKrankheit,
		@Nullable Integer anzahlTageBisFreigabeKrankheit,
		@Nullable Integer giltAbAnzahlErhalteneBooster,
		@Nullable Integer giltBisAnzahlErhalteneBooster,
		boolean enablePfizerOnlyForU30,
		boolean ruleOnlyValidForSelbstzahlerFreigabe,
		@Nullable LocalDate cutoffSelbstzahler
	) {
		return new BoosterAgePunktePrioritaetImpfstoffRule(
			specifiedImpfstoffe,
			null, null,
			null, null,
			prioritaeten,
			anzahlMonateBisFreigabe, anzahlTageBisFreigabe,
			anzahlMonateBisFreigabeKrankheit, anzahlTageBisFreigabeKrankheit,
			giltAbAnzahlErhalteneBooster, giltBisAnzahlErhalteneBooster,
			enablePfizerOnlyForU30, ruleOnlyValidForSelbstzahlerFreigabe, cutoffSelbstzahler);
	}

	public static IBoosterPrioritaetRule createOhneFreigabeRule(
		@NonNull ImpfstoffInfosForRules specifiedImpfstoffe,
		boolean enablePfizerOnlyForU30
	) {
		return new BoosterAgePunktePrioritaetImpfstoffRule(
			specifiedImpfstoffe,
			null, null,
			null, null,
			null,
			null, null,
			null, null,
			null, null,
			enablePfizerOnlyForU30, false, null);
	}
}
