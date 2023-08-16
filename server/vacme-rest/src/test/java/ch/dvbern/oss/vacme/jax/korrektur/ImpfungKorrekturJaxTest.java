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

package ch.dvbern.oss.vacme.jax.korrektur;

import java.math.BigDecimal;

import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.Impfstoff;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.helper.TestImpfstoff;
import ch.dvbern.oss.vacme.util.TestdataCreationUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ImpfungKorrekturJaxTest {



	@CsvSource({
		"false , false , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG , TEST_MODERNA , TEST_MODERNA" ,
		"true  , true  , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG , TEST_MODERNA , TEST_PFIZER"  ,
		"true  , true  , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG , TEST_JOHNSON , TEST_PFIZER"  ,
		"true  , true  , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG , TEST_PFIZER  , TEST_JOHNSON" ,
		"false , false , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG , TEST_JOHNSON , TEST_JOHNSON" ,
		"false , false , ERSTE_IMPFUNG  , false , ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG , TEST_MODERNA , TEST_MODERNA" ,
		"false , false , ERSTE_IMPFUNG  , false , ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG , TEST_MODERNA , TEST_PFIZER"  ,
		"false , false , ERSTE_IMPFUNG  , false , ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG , TEST_JOHNSON , TEST_PFIZER"  , // hat eigentlich nie ein zert gehabt daher kein reovke noetig
		"false , true  , ERSTE_IMPFUNG  , false , ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG , TEST_PFIZER  , TEST_JOHNSON" ,
		"false , false , ERSTE_IMPFUNG  , false , ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG , TEST_JOHNSON , TEST_JOHNSON" , // hat eigentlich nie ein zert gehabt daher kein reovke noetig

		"false , false , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN                     , TEST_MODERNA , TEST_MODERNA" ,
		"false , false , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN                     , TEST_MODERNA , TEST_PFIZER"  ,
		"true  , false , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN                     , TEST_JOHNSON , TEST_PFIZER"  ,
		"true  , true  , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN                     , TEST_PFIZER  , TEST_JOHNSON" ,
		"true  , true  , ZWEITE_IMPFUNG , true  , ABGESCHLOSSEN                     , TEST_MODERNA , TEST_PFIZER"  ,
		"false , false , ZWEITE_IMPFUNG , true  , ABGESCHLOSSEN                     , TEST_MODERNA , TEST_MODERNA" ,
		"true  , true  , ZWEITE_IMPFUNG , true  , ABGESCHLOSSEN                     , TEST_PFIZER  , TEST_JOHNSON" ,

		"true  , true  , BOOSTER_IMPFUNG , true  , IMMUNISIERT                     , TEST_MODERNA , TEST_PFIZER"  ,
		"false , false , BOOSTER_IMPFUNG , true  , IMMUNISIERT                     , TEST_MODERNA , TEST_MODERNA" ,
		"true  , true  , BOOSTER_IMPFUNG , true  , IMMUNISIERT                     , TEST_PFIZER  , TEST_JOHNSON" ,

	})
	@ParameterizedTest
	void testNeedsRevocationAndNeedsNewCert(
		boolean expectRevocation,
		boolean expectCertCreation,
		Impffolge correctedFolge,
		boolean vollstaendigGeimpft,
		RegistrierungStatus status,
		TestImpfstoff impfstoffAlt,
		TestImpfstoff impfstoffNeu
	) {

		Impfstoff impfstoffFalsch = TestImpfstoff.createImpfstoffForTest(impfstoffAlt);
		Impfstoff impfstoffChanged = TestImpfstoff.createImpfstoffForTest(impfstoffNeu);

		@NonNull String lot = RandomStringUtils.random(10); // irelevant
		@NonNull BigDecimal menge= new BigDecimal("0.1");
		ImpfungKorrekturJax korrekturJax = new ImpfungKorrekturJax(correctedFolge, impfstoffChanged.getId(), lot, menge, null);

		Registrierung reg = new Registrierung();
		reg.setVollstaendigerImpfschutzFlagAndTyp(TestdataCreationUtil.guessVollstaendigerImpfschutzTyp(vollstaendigGeimpft, status));
		if (vollstaendigGeimpft && status == RegistrierungStatus.ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG) {
			reg.setGenesen(true);
		}
		reg.setRegistrierungStatus(status);
		boolean createNewZert = ImpfungKorrekturJax.needsNewZertifikat(reg, correctedFolge, impfstoffFalsch, impfstoffChanged, null);
		boolean needsToRevoke = ImpfungKorrekturJax.needToRevoke(reg, correctedFolge, impfstoffFalsch, impfstoffChanged);

		Assertions.assertEquals(expectRevocation, needsToRevoke, "sould " + (expectRevocation ? "" : " not " ) + " revoke  cert");
		Assertions.assertEquals(expectCertCreation, createNewZert, "sould " + (expectCertCreation ? "" : " not " ) + " create new cert");
	}

}
