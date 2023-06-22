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

package ch.dvbern.oss.vacme.service;

import java.time.LocalDate;
import java.util.Objects;

import ch.dvbern.oss.vacme.entities.registration.Sprache;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.util.Gsm0338;
import ch.dvbern.oss.vacme.util.TestdataCreationUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import static org.mockito.Mockito.spy;

class SmsServiceTest {
	@ParameterizedTest
	@CsvSource({
		// originalString,  transformedString,  isGmsOnly
		"ćĆğščžőđĐ’–çÀâÂë, cCgsczodD'-cAaAe, false", // bekannte Replacements
		"ÁĂÀÂáâąãǎČĆćçčĐĎđËÊÈēěễęěėëêğİïıîÍíķĽŁľłňńÓÕÒọőōôóõõŘřŠŞšśșşÚûúűūűýŽŻŻžźżż–—’‘`´ˈ“”„‟, "
			+ "AAAAaaaaaCCcccDDdEEEeeeeeeeegIiiiiikLLllnnOOOoooooooRrSSssssUuuuuuyZZZzzzz--'''''\"\"\"\","
			+ "false", // alle bekannten Replacements (ausser die Leerschlaege, die gehen in der csv source nicht)
		"Đinđić, Dindic, false",
		"èéâÈÉÀćğőđĆ, èéaEÉAcgodC, false",
		"Werwölfe, Werwölfe, true",
		"Peyvənd əladır, Peyv?nd ?ladir, false",
		"Вакцинација је одлична, ??????????? ?? ???????, false",
		"ការចាក់វ៉ាក់សាំងគឺអស្ចារ្យណាស់។, ???????????????????????????????, false",
		// GMS basic (das Komma mussten wir hier rausnehmen, das geht in der csv source nicht!)
		"@ΔSP0¡P¿p£_!1AQaq$Φ\"2BRbr¥Γ#3CScsèΛ¤4DTdtéΩ%5EUeuùΠ&6FVfvìΨ'7GWgwòΣ(8HXhxÇΘ)9IYiyLFΞ*:JZjzØESC+;KÄkäøÆ<LÖlöCRæ-=MÑmñÅß.>NÜnüåÉ/?O§oà, "
			+ "@ΔSP0¡P¿p£_!1AQaq$Φ\"2BRbr¥Γ#3CScsèΛ¤4DTdtéΩ%5EUeuùΠ&6FVfvìΨ'7GWgwòΣ(8HXhxÇΘ)9IYiyLFΞ*:JZjzØESC+;KÄkäøÆ<LÖlöCRæ-=MÑmñÅß.>NÜnüåÉ/?O§oà, "
			+ "true",
		"\f|^{}\\[~]€, \f|^{}\\[~]€, true" // GSM extension
	})
	void removeUnsupportedCharacters(String original, String expected, boolean isGmsOnly) {
		Assertions.assertEquals(isGmsOnly, Gsm0338.isValidGsm0338(original));
		Assertions.assertEquals(expected, Gsm0338.transformToGsm0338String(original));
	}

	@Test
	void testSpecialUnsupportedCharacters() {
		Assertions.assertEquals("     ", Gsm0338.transformToGsm0338String("\u200B\u202A\u202C\u200F\u00A0"));
	}


	@Test
	void testFreigabeBossterSms() {
		System.setProperty("vacme.mandant", "BE");
		SmsService smsService = spy(SmsService.class);
		smsService.link = "https://be.vacme.ch";


		LocalDate latestImpfungDate = LocalDate.of(2021, 6, 30);
		LocalDate latestExterneImfpung = LocalDate.of(2021, 1, 30);

		ImpfinformationDto infos = TestdataCreationUtil.createImpfinformationen(latestImpfungDate,latestExterneImfpung);
		infos.getRegistrierung().setName("Tester");
		infos.getRegistrierung().setVorname("Tim");
		infos.getRegistrierung().setRegistrierungsnummer("AABBCC");
		infos.getRegistrierung().setSprache(Sprache.DE);
		smsService.sendFreigabeBoosterSMS(infos, "testusername", "077 123 45 67");

		String expectedMessage =
			 "Guten Tag Tim Tester\n"
			+ "Sie wurden zuletzt am 30.06.2021 gegen COVID-19 geimpft.\n"
			+ "Zur Gewährleistung des optimalen Impfschutzes wird Ihnen eine Booster-Impfung empfohlen. Unter https://be.vacme.ch "
			+ "können Sie einen Termin für die Booster-Impfung buchen.\n"
			+ "Ihr VacMe-Code: AABBCC\n"
			+ "Ihr Benutzername: testusername";

		Mockito.verify(smsService).sendSMSToRegistrierung("077 123 45 67", expectedMessage, infos.getRegistrierung());

		Objects.requireNonNull(infos.getExternesZertifikat()).setLetzteImpfungDate(LocalDate.of(2021, 7, 1));
		smsService.sendFreigabeBoosterSMS(infos, null, "077 123 45 67");
		String expectedMessage2 =
			"Guten Tag Tim Tester\n"
				+ "Sie wurden zuletzt am 01.07.2021 gegen COVID-19 geimpft.\n"
				+ "Zur Gewährleistung des optimalen Impfschutzes wird Ihnen eine Booster-Impfung empfohlen. Unter https://be.vacme.ch "
				+ "können Sie einen Termin für die Booster-Impfung buchen.\n"
				+ "Ihr VacMe-Code: AABBCC";

		Mockito.verify(smsService).sendSMSToRegistrierung("077 123 45 67", expectedMessage2, infos.getRegistrierung());

	}

	@Test
	void testFreigabeBossterSmsFR() {
		System.setProperty("vacme.mandant", "BE");
		SmsService smsService = spy(SmsService.class);
		smsService.link = "https://be.vacme.ch";

		LocalDate latestImpfungDate = LocalDate.of(2021, 6, 30);
		LocalDate latestExterneImfpung = LocalDate.of(2021, 1, 30);

		ImpfinformationDto infos = TestdataCreationUtil.createImpfinformationen(latestImpfungDate, latestExterneImfpung);
		infos.getRegistrierung().setName("Tester");
		infos.getRegistrierung().setVorname("Tim");
		infos.getRegistrierung().setRegistrierungsnummer("AABBCC");
		smsService.sendFreigabeBoosterSMS(infos, "testusername", "077 123 45 67");

		infos.getRegistrierung().setSprache(Sprache.FR);

		Objects.requireNonNull(infos.getExternesZertifikat()).setLetzteImpfungDate(LocalDate.of(2021, 7, 1));
		smsService.sendFreigabeBoosterSMS(infos, null, "077 123 45 67");
		String expectedMessageFR =
			"Bonjour Tim Tester,\n"
				+ "Vous avez reçu le 01.07.2021 votre dernière dose de vaccin contre le COVID-19. Pour bénéficier "
				+ "d’une protection vaccinale complète, nous vous recommandons de vous faire administrer un rappel. "
				+ "Vous pouvez fixer un rendez-vous sur https://be.vacme.ch.\n"
				+ "Votre code d’accès à VacMe : AABBCC";

		Mockito.verify(smsService).sendSMSToRegistrierung("077 123 45 67", expectedMessageFR, infos.getRegistrierung());
	}
}
