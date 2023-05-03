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
import java.util.List;

import ch.dvbern.oss.vacme.entities.externeimpfinfos.ExternesZertifikat;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.util.TestdataCreationUtil;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class BoosterPrioUtilTest {

	@Nullable
	private ExternesZertifikat externeImpfinfo;
	private ImpfinformationDto impfinformationDto;
	private static final LocalDate NEWEST_VACME_IMPFUNG = LocalDate.of(2021, 1, 30);
	private static final LocalDate LATEST_EXT_IMPFDATUM = LocalDate.of(2021, 6, 1);

	/**
	 * Hilfsmethode welche Testsdaten aufsetzt
	 */
	public void initTestregistrierung(boolean withVacmeImpfungen, boolean withExternImpfungen) {

		LocalDate latestVacmeImpfung = withVacmeImpfungen ? NEWEST_VACME_IMPFUNG : null;
		LocalDate latestExtImpfung = withExternImpfungen ? LATEST_EXT_IMPFDATUM : null;
		impfinformationDto = TestdataCreationUtil.createImpfinformationen(latestVacmeImpfung,
			latestExtImpfung);
		externeImpfinfo = impfinformationDto.getExternesZertifikat();

	}

	@Test
	void testGetNewestImpfdatum() {

		// noch gar keine Impfung vorhanden
		initTestregistrierung(false, false);
		LocalDate dateOfNewestImpfung = BoosterPrioUtil.getDateOfNewestImpfung(impfinformationDto);
		assertNull(dateOfNewestImpfung);

		// nur externe Impfung vorhanden
		initTestregistrierung(false, true);
		dateOfNewestImpfung = BoosterPrioUtil.getDateOfNewestImpfung(impfinformationDto);
		Assertions.assertNotNull(dateOfNewestImpfung);
		Assertions.assertEquals(LATEST_EXT_IMPFDATUM, dateOfNewestImpfung);

		// externe und interne Impfung vorhanden aber externe ist neuer
		initTestregistrierung(true, true);
		dateOfNewestImpfung = BoosterPrioUtil.getDateOfNewestImpfung(impfinformationDto);
		Assertions.assertEquals(LATEST_EXT_IMPFDATUM, dateOfNewestImpfung);

		// nur interne Impfung vorhanden
		initTestregistrierung(true, false);
		dateOfNewestImpfung = BoosterPrioUtil.getDateOfNewestImpfung(impfinformationDto);
		Assertions.assertEquals(NEWEST_VACME_IMPFUNG, dateOfNewestImpfung);

		// heute intern geimpft
		initTestregistrierung(true, true);
		Impfung impfungToday = new Impfung();
		impfungToday.setImpfstoff(TestdataCreationUtil.createImpfstoffModerna());
		impfungToday.setTimestampImpfung(LocalDateTime.now());
		impfinformationDto = new ImpfinformationDto(impfinformationDto, List.of(impfungToday));

		dateOfNewestImpfung = BoosterPrioUtil.getDateOfNewestImpfung(impfinformationDto);
		Assertions.assertEquals(LocalDate.now(), dateOfNewestImpfung);

		// gestern extern geimpft
		initTestregistrierung(true, true);
		assert externeImpfinfo != null;
		externeImpfinfo.setLetzteImpfungDate(LocalDate.now().minusDays(1));
		externeImpfinfo.setAnzahlImpfungen(3);
		dateOfNewestImpfung = BoosterPrioUtil.getDateOfNewestImpfung(impfinformationDto);
		Assertions.assertEquals(LocalDate.now().minusDays(1), dateOfNewestImpfung);
	}

}
