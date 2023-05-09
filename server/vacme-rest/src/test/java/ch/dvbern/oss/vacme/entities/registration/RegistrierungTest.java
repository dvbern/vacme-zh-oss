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

package ch.dvbern.oss.vacme.entities.registration;

import java.time.LocalDate;

import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationBuilder;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.util.TestdataCreationUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegistrierungTest {

	private ImpfinformationDto infos;

	@BeforeEach
	void setUp() {
		ImpfinformationBuilder builder = new ImpfinformationBuilder();
		infos = builder
			.create()
			.withImpfung1(LocalDate.now(), TestdataCreationUtil.createImpfstoffPfizer())
			.getInfos();
	}

	@Test
	void setStatusToAbgeschlossenOhneZweiteImpfung() {
		Registrierung registrierung = infos.getRegistrierung();
		Impfung impfung = infos.getImpfung1();
		Assertions.assertNotNull(impfung);
		// erkrankt
		registrierung.setStatusToAbgeschlossenOhneZweiteImpfung(infos,  true, null, LocalDate.now().minusDays(3));
		Assertions.assertNull(registrierung.getZweiteImpfungVerzichtetGrund());
		assertEquals(Boolean.TRUE, registrierung.getVollstaendigerImpfschutz());
		Assertions.assertTrue(registrierung.isGenesen());
		Assertions.assertEquals(LocalDate.now().minusDays(3), registrierung.getPositivGetestetDatum());

		registrierung.setStatusToAbgeschlossenOhneZweiteImpfung(infos, false, "hatte doch keine Lust", null);
		assertEquals(Boolean.FALSE, registrierung.getVollstaendigerImpfschutz());
		Assertions.assertFalse(registrierung.isGenesen());
		Assertions.assertNull(registrierung.getPositivGetestetDatum());
		Assertions.assertEquals("hatte doch keine Lust",registrierung.getZweiteImpfungVerzichtetGrund());
	}

	@Test
	void testSetStatusToNichtAbgeschlossenStatus() {
		Registrierung registrierung = infos.getRegistrierung();
		Impfung impfung = infos.getImpfung1();
		Assertions.assertNotNull(impfung);

		registrierung.setStatusToAbgeschlossenOhneZweiteImpfung(infos, true, null, LocalDate.now().minusDays(3));
		Assertions.assertNull(registrierung.getZweiteImpfungVerzichtetGrund());
		assertEquals(Boolean.TRUE, registrierung.getVollstaendigerImpfschutz());
		Assertions.assertTrue(registrierung.isGenesen());

		registrierung.setStatusToNichtAbgeschlossenStatus(RegistrierungStatus.IMPFUNG_1_DURCHGEFUEHRT,impfung);
		assertEquals(Boolean.FALSE, registrierung.getVollstaendigerImpfschutz());
		Assertions.assertFalse(registrierung.isGenesen());
	}
}
