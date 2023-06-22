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

package ch.dvbern.oss.vacme.service.boosterprioritaet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import ch.dvbern.oss.vacme.entities.base.ImpfInfo;
import ch.dvbern.oss.vacme.entities.impfen.Impfschutz;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.util.ImpfinformationenUtil;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMMUNISIERT;

public class BoosterPrioUtil {

	private BoosterPrioUtil() {
	}

	/**
	 * Ermittelt die neuste Impfung unter Betrachtung der internen VacMe Impfungen und der extern erfassten Impfinfos
	 *
	 * @param impfinformationDto interne (bzw. in Vacme dokumenteirte und gepruefte) Impfungen
	 * @return Datum der neusten Impfung
	 */
	@Nullable
	public static LocalDate getDateOfNewestImpfung(@NonNull ImpfinformationDto impfinformationDto) {
		ImpfInfo newestVacmeImpfung = getNewestVacmeImpfung(impfinformationDto);
		ImpfInfo externeImpfInfoOrNull = impfinformationDto.getExternesZertifikat(); // auch unvollstaendige zaehlen
		ImpfInfo impfInfo = getNewerImpfInfo(newestVacmeImpfung, externeImpfInfoOrNull);
		return impfInfo == null ? null : impfInfo.getTimestampImpfung().toLocalDate();
	}

	@NonNull
	public static List<ImpfInfo> getImpfinfosOrderedByTimestampImpfung(@NonNull ImpfinformationDto impfinformationDto) {
		List<ImpfInfo> impfInfos = new ArrayList<>();
		impfInfos.add(impfinformationDto.getExternesZertifikat());
		impfInfos.add(impfinformationDto.getImpfung1());
		impfInfos.add(impfinformationDto.getImpfung2());
		if (impfinformationDto.getBoosterImpfungen() != null) {
			impfInfos.addAll(impfinformationDto.getBoosterImpfungen());
		}
		return impfInfos.stream().filter(Objects::nonNull).sorted(Comparator.comparing(ImpfInfo::getTimestampImpfung)).collect(Collectors.toList());
	}

	@NonNull
	public static List<ImpfInfo> getImpfinfosOrderedByImpffolgeNr(@NonNull ImpfinformationDto impfinformationDto) {
		List<ImpfInfo> impfInfos = new ArrayList<>();
		if (impfinformationDto.getExternesZertifikat() != null) {
			impfInfos.add(impfinformationDto.getExternesZertifikat());
		}

		List<Impfung> impfungenOrderedByImpffolgeNr =
			ImpfinformationenUtil.getImpfungenOrderedByImpffolgeNr(impfinformationDto);
		impfInfos.addAll(impfungenOrderedByImpffolgeNr);

		return impfInfos;
	}

	@Nullable
	private static ImpfInfo getNewerImpfInfo(@Nullable ImpfInfo vacMeImpfInfo, @Nullable ImpfInfo externImpfInfo) {
		if (vacMeImpfInfo == null && externImpfInfo == null) {
			return null;
		}
		if (vacMeImpfInfo == null) {
			return externImpfInfo;
		}
		if (externImpfInfo == null) {
			return vacMeImpfInfo;
		}

		if (externImpfInfo.getTimestampImpfung().isAfter(vacMeImpfInfo.getTimestampImpfung())) {
			return externImpfInfo;
		} else {
			return vacMeImpfInfo;
		}
	}

	@Nullable
	private static Impfung getNewestVacmeImpfung(@NonNull ImpfinformationDto impfinformationDto) {
		return ImpfinformationenService.getNewestVacmeImpfung(impfinformationDto);
	}

	/**
	 * prueft ob die Registrierung die Bedingungen erfuellt um nach FREIGEGEBEN_BOOSTER verschoben zu werden.
	 */
	public static boolean meetsCriteriaForFreigabeBooster(@NonNull Registrierung registrierung, @Nullable Impfschutz impfschutz) {
		return
			(registrierung.getRegistrierungStatus().equals(IMMUNISIERT))
			&& impfschutz != null
			&& impfschutz.getFreigegebenNaechsteImpfungAb() != null
			&& !impfschutz.getFreigegebenNaechsteImpfungAb().isAfter(LocalDateTime.now())
			&& !Boolean.TRUE.equals(registrierung.getVerstorben());
	}
}
