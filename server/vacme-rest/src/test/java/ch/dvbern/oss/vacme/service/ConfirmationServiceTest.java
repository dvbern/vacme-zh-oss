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

import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFile;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFileTyp;
import ch.dvbern.oss.vacme.repo.BenutzerRepo;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.util.TestdataCreationUtil;
import ch.dvbern.oss.vacme.util.VacmeFileUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ConfirmationServiceTest {


	private ConfirmationService serviceUnderTest;

	@BeforeEach
	void setUp() {

		ImpfinformationenService impfinfoService = Mockito.mock(ImpfinformationenService.class);

		BenutzerRepo benutzerRepo = Mockito.mock(BenutzerRepo.class);
		SmsService smsService = Mockito.mock(SmsService.class);

		PdfService pdfService = Mockito.mock(PdfService.class);
		DokumentService dokumentService = Mockito.mock(DokumentService.class);
		ApplicationPropertyService applicationPropertyService = Mockito.mock(ApplicationPropertyService.class);
		Mockito.when(dokumentService.createAndSave(Mockito.any(), Mockito.any(), Mockito.any()))
			.then(invocationOnMock -> {
				byte[] content = invocationOnMock.getArgument(0);
				RegistrierungFileTyp registrierungFileTyp = invocationOnMock.getArgument(1,
					RegistrierungFileTyp.class);
				Registrierung reg = invocationOnMock.getArgument(2, Registrierung.class);
				return VacmeFileUtil.createRegistrierungFile(registrierungFileTyp, reg, content);
			});
		FTPRetryClientService ftpService = Mockito.mock(FTPRetryClientService.class);
		serviceUnderTest = new ConfirmationService(null, benutzerRepo, smsService,
			pdfService, dokumentService, impfinfoService, ftpService, applicationPropertyService);
	}

	@Test
	void saveAndSendLetter() {
		byte[] empty = new byte[0];
		RegistrierungFile registrierungFile;
		registrierungFile =  serviceUnderTest.saveAndSendLetter(TestdataCreationUtil.createFragebogen().getRegistrierung(),	RegistrierungFileTyp.REGISTRIERUNG_BESTAETIGUNG, empty);
		Assertions.assertNotNull(registrierungFile);
		registrierungFile =  serviceUnderTest.saveAndSendLetter(TestdataCreationUtil.createFragebogen().getRegistrierung(),	RegistrierungFileTyp.TERMIN_BESTAETIGUNG, empty);
		Assertions.assertNotNull(registrierungFile);
		registrierungFile =  serviceUnderTest.saveAndSendLetter(TestdataCreationUtil.createFragebogen().getRegistrierung(),	RegistrierungFileTyp.TERMIN_ABSAGE, empty);
		Assertions.assertNotNull(registrierungFile);
		registrierungFile =  serviceUnderTest.saveAndSendLetter(TestdataCreationUtil.createFragebogen().getRegistrierung(),	RegistrierungFileTyp.TERMIN_ZERTIFIKAT_STORNIERUNG, empty);
		Assertions.assertNotNull(registrierungFile);
		registrierungFile =  serviceUnderTest.saveAndSendLetter(TestdataCreationUtil.createFragebogen().getRegistrierung(),	RegistrierungFileTyp.FREIGABE_BOOSTER_INFO, empty);
		Assertions.assertNotNull(registrierungFile);
		registrierungFile =  serviceUnderTest.saveAndSendLetter(TestdataCreationUtil.createFragebogen().getRegistrierung(),	RegistrierungFileTyp.ZERTIFIKAT_COUNTER_RECALCULATION, empty);
		Assertions.assertNotNull(registrierungFile);
		registrierungFile = serviceUnderTest.saveAndSendLetter(TestdataCreationUtil.createFragebogen().getRegistrierung(), RegistrierungFileTyp.ONBOARDING_LETTER, empty);
		Assertions.assertNotNull(registrierungFile);
	}
}
