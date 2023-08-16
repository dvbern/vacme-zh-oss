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

package ch.dvbern.oss.vacme.service.covidcertificate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ch.dvbern.oss.vacme.entities.covidcertificate.CovidCertificateVaccinesNames;
import ch.dvbern.oss.vacme.entities.covidcertificate.VaccinationCertificateCreateDto;
import ch.dvbern.oss.vacme.entities.covidcertificate.VaccinationCertificateDataDto;
import ch.dvbern.oss.vacme.entities.embeddables.Adresse;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossier;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.Impfstoff;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.Sprache;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.service.ZertifikatService;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationBuilder;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.PlzMappingException;
import ch.dvbern.oss.vacme.shared.util.Constants;
import ch.dvbern.oss.vacme.util.ImpfinformationenUtil;
import ch.dvbern.oss.vacme.util.TestdataCreationUtil;
import ch.dvbern.oss.vacme.util.ValidationUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.Validate;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

class CovidCertApiServiceTest {

	private CovidCertApiService serviceUnderTest;

	@BeforeEach
	public void setup() {
		CovidCertRestApiClientService restMock = Mockito.mock(CovidCertRestApiClientService.class);
		SignatureHeaderBean sigHeaderBeanMock = Mockito.mock(SignatureHeaderBean.class);
		CovidCertSignatureService covidCertSigServiceMock = Mockito.mock(CovidCertSignatureService.class);
		ZertifikatService zertServiceMock = Mockito.mock(ZertifikatService.class);
		ObjectMapper jacksonMapperMock = Mockito.mock(ObjectMapper.class);
		ImpfinformationenService impfinformationenServiceMock = Mockito.mock(ImpfinformationenService.class);
		serviceUnderTest = new CovidCertApiService(sigHeaderBeanMock, covidCertSigServiceMock,
			zertServiceMock,
			jacksonMapperMock,
			impfinformationenServiceMock);
		serviceUnderTest.covidCertRestApiClientService = restMock;
	}

	@Test()
	public void testUnvollstImpfschutz() {
		ImpfinformationBuilder builder = new ImpfinformationBuilder();
		final ImpfinformationDto infos = builder
			.create()
			.withImpfung1(LocalDate.now().minusMonths(1), TestdataCreationUtil.createImpfstoffPfizer())
			.getInfos();
		try {
			VaccinationCertificateCreateDto dto = mapRegForCovidCertDto(infos.getRegistrierung(), infos.getImpfung1(), null, null, CovidCertBatchType.ONLINE);
			Assertions.fail("Shold not be able to map Impfungen without vollstaendiger Impfschutz");
		} catch (IllegalArgumentException exception) {
			//expected
		}
	}

	@NonNull
	private VaccinationCertificateCreateDto mapRegForCovidCertDto(
		@NonNull Registrierung reg,
		@Nullable Impfung impfung1,
		@Nullable Impfung impfung2,
		@Nullable List<Impfung> boosterimpfungen,
		@NonNull CovidCertBatchType batchType,
		@NonNull Impfung impfungToCreateCertFor
	) {
		Impfdossier impfdossier = new Impfdossier();
		ImpfinformationDto infos = new ImpfinformationDto(
			new ImpfinformationDto(
				reg,
				impfung1,
				impfung2,
				impfdossier,
				null
			),
			boosterimpfungen
		);
		if (boosterimpfungen != null) {
			int impffolgeNr = 1 + (impfung1 != null ? 1 : 0) + (impfung2 != null ? 1 : 0);
			for (Impfung impfung : boosterimpfungen) {
				Impfdossiereintrag impfdossiereintrag = new Impfdossiereintrag();
				impfdossiereintrag.setImpffolgeNr(impffolgeNr++);
				impfdossiereintrag.setImpfterminFromImpfterminRepo(impfung.getTermin());
				impfdossiereintrag.setImpfdossier(impfdossier);
				impfdossier.getImpfdossierEintraege().add(impfdossiereintrag);
			}

		}

		Objects.requireNonNull(impfungToCreateCertFor);
		Validate.isTrue(ImpfinformationenUtil.getImpfungenOrderedByImpffolgeNr(infos).contains(impfungToCreateCertFor));
		return serviceUnderTest.mapRegForCovidCertDto(infos, impfungToCreateCertFor, batchType);
	}

	@NonNull
	private VaccinationCertificateCreateDto mapRegForCovidCertDto(
		@NonNull Registrierung reg,
		@Nullable Impfung impfung1,
		@Nullable Impfung impfung2,
		@Nullable List<Impfung> boosterimpfungen,
		@NonNull CovidCertBatchType batchType
	) {
		Impfdossier impfdossier = new Impfdossier();
		ImpfinformationDto infos = new ImpfinformationDto(
			new ImpfinformationDto(
				reg,
				impfung1,
				impfung2,
				impfdossier,
				null
			),
			boosterimpfungen
		);
		final Impfung newestVacmeImpfung = ImpfinformationenService.getNewestVacmeImpfung(infos);
		Objects.requireNonNull(newestVacmeImpfung);
		return mapRegForCovidCertDto(reg, impfung1, impfung2, boosterimpfungen, batchType, newestVacmeImpfung);
	}

	@Test
	public void testMappingLongName() {
		Registrierung reg = new Registrierung();
		reg.setName("de Todos los Santos de Borbón y de Grecia de Silvas von und zu Hohenzollern von Rheinach");
		reg.setVorname("Felipe Juan Pablo Alfonso Miguel Imanol Roberto Franco Carlos Martinez Maria Colombo");
		reg.setGeburtsdatum(LocalDate.of(1950, 6, 4));
		reg.setSprache(Sprache.FR);

		Impfstoff impfstoff = new Impfstoff();
		impfstoff.setId(Constants.PFIZER_BIONTECH_UUID);
		impfstoff.setCovidCertProdCode(CovidCertificateVaccinesNames.PFIZER_BIONTECH.getCode());
		impfstoff.setAnzahlDosenBenoetigt(3);

		Impfung impfung = new Impfung();
		impfung.setTimestampImpfung(LocalDate.of(2021, 5, 15).atStartOfDay());
		impfung.setImpfstoff(impfstoff);

		ImpfinformationDto infos = TestdataCreationUtil.createImpfinformationen(reg, impfung, null, null, null, null);

		reg.setStatusToAbgeschlossen(infos, impfung);

		VaccinationCertificateCreateDto dto = mapRegForCovidCertDto(reg, impfung, null, null, CovidCertBatchType.ONLINE);
		Assertions.assertEquals(Constants.MAX_NAME_LENGTH_COVIDCERT, dto.getName().getFamilyName().length());
		Assertions.assertEquals(Constants.MAX_NAME_LENGTH_COVIDCERT, dto.getName().getGivenName().length());
		Assertions.assertEquals("de Todos los Santos de Borbón y de Grecia de Silvas von und zu Hohenzollern v...", dto.getName().getFamilyName());
		Assertions.assertEquals("Felipe Juan Pablo Alfonso Miguel Imanol Roberto Franco Carlos Martinez Maria ...", dto.getName().getGivenName());

	}

	@Test()
	public void testMapping2Doses() {

		Registrierung reg = new Registrierung();
		reg.setName("Federer");
		reg.setVorname("Roger");
		reg.setGeburtsdatum(LocalDate.of(1950, 6, 4));
		reg.setSprache(Sprache.FR);

		Impfstoff impfstoff = TestdataCreationUtil.createImpfstoffPfizer();

		Impfung impfung = TestdataCreationUtil.createImpfung(LocalDate.of(2021, 5, 15), impfstoff);
		Impfung impfung2 = TestdataCreationUtil.createImpfung(LocalDate.of(2021, 6, 17), impfstoff);
		ImpfinformationDto infos = TestdataCreationUtil.createImpfinformationen(reg, impfung, impfung2, null, null, null);

		reg.setStatusToAbgeschlossen(infos, impfung2);
		VaccinationCertificateCreateDto dto = mapRegForCovidCertDto(reg, impfung, impfung2, null, CovidCertBatchType.ONLINE);

		Assertions.assertEquals("Federer", dto.getName().getFamilyName());
		Assertions.assertEquals("Roger", dto.getName().getGivenName());
		Assertions.assertEquals("fr", dto.getLanguage());

		Assertions.assertEquals(LocalDate.of(1950, 6, 4), dto.getDateOfBirth());

		Assertions.assertEquals(1, dto.getVaccinationInfo().size());
		VaccinationCertificateDataDto dataDto = dto.getVaccinationInfo().get(0);
		Assertions.assertEquals(2, dataDto.getTotalNumberOfDoses());
		Assertions.assertEquals(2, dataDto.getNumberOfDoses());
		Assertions.assertEquals("CH", dataDto.getCountryOfVaccination());
		Assertions.assertEquals(CovidCertificateVaccinesNames.PFIZER_BIONTECH.getCode(),
			dataDto.getMedicinalProductCode());
		Assertions.assertEquals(LocalDate.of(2021, 6, 17), dataDto.getVaccinationDate());

	}

	@Test()
	public void testMappingGenesen1PfizerOneAdditionalGrundimmunisierungPfizer() {

		Registrierung reg = new Registrierung();
		reg.setName("Federer");
		reg.setVorname("Roger");
		reg.setGeburtsdatum(LocalDate.of(1950, 6, 4));
		reg.setSprache(Sprache.FR);

		Impfstoff impfstoff = TestdataCreationUtil.createImpfstoffPfizer();

		Impfung impfung = TestdataCreationUtil.createImpfung(LocalDate.of(2021, 5, 15), impfstoff);
		ImpfinformationDto infos =
			TestdataCreationUtil.createImpfinformationen(reg, impfung, null, null, null, null);

		reg.setStatusToAbgeschlossenOhneZweiteImpfung(infos, true, null, LocalDate.of(2021, 2, 2));

		Impfung impfung2 = TestdataCreationUtil.createImpfung(LocalDate.of(2021, 6, 17), impfstoff);
		Impftermin termin = new Impftermin();
		impfung2.setTermin(termin);
		infos =
			TestdataCreationUtil.createImpfinformationen(reg, impfung, impfung2, null, null, null);

		VaccinationCertificateCreateDto dto = mapRegForCovidCertDto(infos.getRegistrierung(), impfung, null, List.of(impfung2), CovidCertBatchType.ONLINE);

		Assertions.assertEquals("Federer", dto.getName().getFamilyName());
		Assertions.assertEquals("Roger", dto.getName().getGivenName());
		Assertions.assertEquals("fr", dto.getLanguage());

		Assertions.assertEquals(LocalDate.of(1950, 6, 4), dto.getDateOfBirth());

		Assertions.assertEquals(1, dto.getVaccinationInfo().size());
		VaccinationCertificateDataDto dataDto = dto.getVaccinationInfo().get(0);
		Assertions.assertEquals(2, dataDto.getTotalNumberOfDoses());
		Assertions.assertEquals(2, dataDto.getNumberOfDoses());
		Assertions.assertEquals("CH", dataDto.getCountryOfVaccination());
		Assertions.assertEquals(CovidCertificateVaccinesNames.PFIZER_BIONTECH.getCode(),
			dataDto.getMedicinalProductCode());
		Assertions.assertEquals(LocalDate.of(2021, 6, 17), dataDto.getVaccinationDate());

	}
	@Test()
	public void testMappingGenesen1PfizerOneBooster() {

		Registrierung reg = new Registrierung();
		reg.setName("Federer");
		reg.setVorname("Roger");
		reg.setGeburtsdatum(LocalDate.of(1950, 6, 4));
		reg.setSprache(Sprache.FR);

		Impfstoff impfstoff = TestdataCreationUtil.createImpfstoffPfizer();

		Impfung impfung = TestdataCreationUtil.createImpfung(LocalDate.of(2021, 5, 15), impfstoff);
		ImpfinformationDto infos =
			TestdataCreationUtil.createImpfinformationen(reg, impfung, null, null, null, null);

		reg.setStatusToAbgeschlossenOhneZweiteImpfung(infos, true, null, LocalDate.of(2021, 2, 2));

		Impfung impfung2 = TestdataCreationUtil.createImpfung(LocalDate.of(2021, 6, 17), impfstoff);
		Impftermin termin = new Impftermin();
		impfung2.setTermin(termin);

		infos =
			TestdataCreationUtil.createImpfinformationen(reg, impfung, impfung2, null, null, null);

		VaccinationCertificateCreateDto dto = mapRegForCovidCertDto(infos.getRegistrierung(), impfung, null, List.of(impfung2), CovidCertBatchType.ONLINE);

		Assertions.assertEquals("Federer", dto.getName().getFamilyName());
		Assertions.assertEquals("Roger", dto.getName().getGivenName());
		Assertions.assertEquals("fr", dto.getLanguage());

		Assertions.assertEquals(LocalDate.of(1950, 6, 4), dto.getDateOfBirth());

		Assertions.assertEquals(1, dto.getVaccinationInfo().size());
		VaccinationCertificateDataDto dataDto = dto.getVaccinationInfo().get(0);
		Assertions.assertEquals(2, dataDto.getTotalNumberOfDoses());
		Assertions.assertEquals(2, dataDto.getNumberOfDoses());
		Assertions.assertEquals("CH", dataDto.getCountryOfVaccination());
		Assertions.assertEquals(CovidCertificateVaccinesNames.PFIZER_BIONTECH.getCode(),
			dataDto.getMedicinalProductCode());
		Assertions.assertEquals(LocalDate.of(2021, 6, 17), dataDto.getVaccinationDate());

	}

	@Test()
	public void testMapping1DoseJanssenOneBooster() {
		Registrierung reg = new Registrierung();
		reg.setName("Federer");
		reg.setVorname("Roger");
		reg.setGeburtsdatum(LocalDate.of(1950, 6, 4));
		reg.setSprache(Sprache.FR);

		Impfstoff impfstoff = TestdataCreationUtil.createImpfstoffJanssen();

		Impfung impfung = TestdataCreationUtil.createImpfung(LocalDate.of(2021, 5, 15), impfstoff);
		ImpfinformationDto infos = TestdataCreationUtil.createImpfinformationen(reg, impfung, null, null, null, null);
		reg.setStatusToAbgeschlossen(infos, impfung);

		Impfung impfung2 = TestdataCreationUtil.createBoosterImpfung(LocalDate.of(2021, 6, 17), TestdataCreationUtil.createImpfstoffPfizer());
		Impftermin termin = new Impftermin();
		impfung2.setTermin(termin);

		VaccinationCertificateCreateDto dto = mapRegForCovidCertDto(reg, impfung, null, List.of(impfung2), CovidCertBatchType.ONLINE);

		Assertions.assertEquals("Federer", dto.getName().getFamilyName());
		Assertions.assertEquals("Roger", dto.getName().getGivenName());
		Assertions.assertEquals("fr", dto.getLanguage());

		Assertions.assertEquals(LocalDate.of(1950, 6, 4), dto.getDateOfBirth());

		Assertions.assertEquals(1, dto.getVaccinationInfo().size());
		VaccinationCertificateDataDto dataDto = dto.getVaccinationInfo().get(0);
		Assertions.assertEquals(1, dataDto.getTotalNumberOfDoses());
		Assertions.assertEquals(2, dataDto.getNumberOfDoses());
		Assertions.assertEquals("CH", dataDto.getCountryOfVaccination());
		Assertions.assertEquals(CovidCertificateVaccinesNames.PFIZER_BIONTECH.getCode(),
			dataDto.getMedicinalProductCode());
		Assertions.assertEquals(LocalDate.of(2021, 6, 17), dataDto.getVaccinationDate());

	}

	@Test
	public void testMappingComplete() {

		Registrierung reg = new Registrierung();
		reg.setName("Muster");
		reg.setVorname("Hans");
		reg.setGeburtsdatum(LocalDate.of(1984, 12, 12));
		reg.setSprache(Sprache.DE);

		Impfstoff impfstoff = TestdataCreationUtil.createImpfstoffModerna();

		Impfung impfung = TestdataCreationUtil.createImpfung(LocalDate.of(2021, 1, 9), impfstoff);
		Impfung impfung2 =  TestdataCreationUtil.createImpfung(LocalDate.of(2021, 2, 6), impfstoff);
		ImpfinformationDto infos = TestdataCreationUtil.createImpfinformationen(reg, impfung, impfung2, null, null, null);

		reg.setStatusToAbgeschlossen(infos, impfung2);

		VaccinationCertificateCreateDto dto = mapRegForCovidCertDto(reg, impfung, impfung2, null,  CovidCertBatchType.ONLINE);
		Assertions.assertEquals("Muster", dto.getName().getFamilyName());
		Assertions.assertEquals("Hans", dto.getName().getGivenName());
		Assertions.assertEquals("de", dto.getLanguage());
		Assertions.assertEquals(LocalDate.of(1984, 12, 12), dto.getDateOfBirth());
		Assertions.assertEquals(1, dto.getVaccinationInfo().size());

		VaccinationCertificateDataDto dataDto = dto.getVaccinationInfo().get(0);
		Assertions.assertEquals(2, dataDto.getTotalNumberOfDoses());
		Assertions.assertEquals(2, dataDto.getNumberOfDoses());
		Assertions.assertEquals("CH", dataDto.getCountryOfVaccination());
		Assertions.assertEquals(CovidCertificateVaccinesNames.MODERNA.getCode(), dataDto.getMedicinalProductCode());
		Assertions.assertEquals(LocalDate.of(2021, 2, 6), dataDto.getVaccinationDate());

	}

	@Test
	public void testMappingcomplete3Impfungen() {

		Registrierung reg = new Registrierung();
		reg.setName("Muster");
		reg.setVorname("Hans");
		reg.setGeburtsdatum(LocalDate.of(1984, 12, 12));
		reg.setSprache(Sprache.DE);

		Impfstoff impfstoff = TestdataCreationUtil.createImpfstoffModerna();

		Impfung impfung =  TestdataCreationUtil.createImpfung(LocalDate.of(2021, 1, 9), impfstoff);
		Impfung impfung2 = TestdataCreationUtil.createImpfung(LocalDate.of(2021, 2, 6), impfstoff);
		ImpfinformationDto infos = TestdataCreationUtil.createImpfinformationen(reg, impfung, impfung2, null, null, null);

		reg.setStatusToAbgeschlossen(infos, impfung2);

		Impfung impfung3  = TestdataCreationUtil.createBoosterImpfung(LocalDate.of(2021, 9, 6), TestdataCreationUtil.createImpfstoffPfizer());
		Impftermin termin = new Impftermin();
		impfung3.setTermin(termin);

		VaccinationCertificateCreateDto dto = mapRegForCovidCertDto(reg, impfung, impfung2, List.of(impfung3),  CovidCertBatchType.ONLINE);
		Assertions.assertEquals("Muster", dto.getName().getFamilyName());
		Assertions.assertEquals("Hans", dto.getName().getGivenName());
		Assertions.assertEquals("de", dto.getLanguage());
		Assertions.assertEquals(LocalDate.of(1984, 12, 12), dto.getDateOfBirth());
		Assertions.assertEquals(1, dto.getVaccinationInfo().size());

		VaccinationCertificateDataDto dataDto = dto.getVaccinationInfo().get(0);
		Assertions.assertEquals(3, dataDto.getTotalNumberOfDoses());
		Assertions.assertEquals(3, dataDto.getNumberOfDoses());
		Assertions.assertEquals("CH", dataDto.getCountryOfVaccination());
		Assertions.assertEquals(CovidCertificateVaccinesNames.PFIZER_BIONTECH.getCode(), dataDto.getMedicinalProductCode());
		Assertions.assertEquals(LocalDate.of(2021, 9, 6), dataDto.getVaccinationDate());

	}

	@ParameterizedTest
	@CsvSource(value = {
		"2",
		"5",
	}, nullValues = "null")
	public void testMappingcompleteNImpfungen(int boosterCount) {

		Registrierung reg = new Registrierung();
		reg.setName("Muster");
		reg.setVorname("Hans");
		reg.setGeburtsdatum(LocalDate.of(1984, 12, 12));
		reg.setSprache(Sprache.DE);


		Impfstoff impfstoff = TestdataCreationUtil.createImpfstoffModerna();

		Impfung impfung =  TestdataCreationUtil.createImpfung(LocalDate.of(2021, 1, 9), impfstoff);
		Impfung impfung2 =  TestdataCreationUtil.createImpfung(LocalDate.of(2021, 2, 6), impfstoff);
		ImpfinformationDto infos = TestdataCreationUtil.createImpfinformationen(reg, impfung, impfung2, null, null, null);

		reg.setStatusToAbgeschlossen(infos, impfung2);

		List<Impfung> boosterImpfungen = new ArrayList<>();
		for (int i = 0; i < boosterCount; i++) {

			Impfung impfungN = new Impfung();
			impfungN.setTimestampImpfung(LocalDate.of(2021, 9, 6).atStartOfDay().plusYears(i + 1));
			impfstoff = TestdataCreationUtil.createImpfstoffPfizer();
			impfungN.setImpfstoff(impfstoff);
			Impftermin termin = new Impftermin();
			impfungN.setTermin(termin);
			impfung.setGrundimmunisierung(false);

			boosterImpfungen.add(impfungN);

		}

		// check mapping of last booster impfung
		VaccinationCertificateCreateDto dto = mapRegForCovidCertDto(reg, impfung, impfung2, boosterImpfungen,
			CovidCertBatchType.ONLINE, boosterImpfungen.get(boosterImpfungen.size()-1));
		Assertions.assertEquals("Muster", dto.getName().getFamilyName());
		Assertions.assertEquals("Hans", dto.getName().getGivenName());
		Assertions.assertEquals("de", dto.getLanguage());
		Assertions.assertEquals(LocalDate.of(1984, 12, 12), dto.getDateOfBirth());
		Assertions.assertEquals(1, dto.getVaccinationInfo().size());

		VaccinationCertificateDataDto dataDto = dto.getVaccinationInfo().get(0);
		Assertions.assertEquals(boosterCount + 2, dataDto.getTotalNumberOfDoses());
		Assertions.assertEquals(boosterCount + 2, dataDto.getNumberOfDoses());
		Assertions.assertEquals("CH", dataDto.getCountryOfVaccination());
		Assertions.assertEquals(CovidCertificateVaccinesNames.PFIZER_BIONTECH.getCode(), dataDto.getMedicinalProductCode());
		Assertions.assertEquals(LocalDate.of(2021, 9, 6).plusYears(boosterCount), dataDto.getVaccinationDate());

		// check mapping of impfung2
		VaccinationCertificateCreateDto dtoImpfung2 = mapRegForCovidCertDto(reg, impfung, impfung2, boosterImpfungen,
			CovidCertBatchType.ONLINE, impfung2);
		Assertions.assertEquals("Muster", dtoImpfung2.getName().getFamilyName());
		Assertions.assertEquals("Hans", dtoImpfung2.getName().getGivenName());
		Assertions.assertEquals("de", dtoImpfung2.getLanguage());
		Assertions.assertEquals(LocalDate.of(1984, 12, 12), dtoImpfung2.getDateOfBirth());
		Assertions.assertEquals(1, dtoImpfung2.getVaccinationInfo().size());

		VaccinationCertificateDataDto vacc2DataDto = dtoImpfung2.getVaccinationInfo().get(0);
		Assertions.assertEquals(2, vacc2DataDto.getTotalNumberOfDoses());
		Assertions.assertEquals(2, vacc2DataDto.getNumberOfDoses());
		Assertions.assertEquals("CH", vacc2DataDto.getCountryOfVaccination());
		Assertions.assertEquals(CovidCertificateVaccinesNames.MODERNA.getCode(), vacc2DataDto.getMedicinalProductCode());
		Assertions.assertEquals(LocalDate.of(2021, 2, 6), vacc2DataDto.getVaccinationDate());


	}

	@Test
	public void testMappingCompleteWithAddress() {

		System.setProperty("vacme.mandant", "BE");
		Registrierung reg = new Registrierung();
		reg.setName("Meier");
		reg.setVorname("Friederich");
		reg.setGeburtsdatum(LocalDate.of(1982, 12, 12));
		reg.setSprache(Sprache.FR);

		Adresse adr = new Adresse();
		adr.setAdresse1("Musterstrasse 12");
		adr.setOrt("Schüpbach");
		adr.setPlz(" 3535");

		reg.setAdresse(adr);

		Impfstoff impfstoff = TestdataCreationUtil.createImpfstoffModerna();

		Impfung impfung =  TestdataCreationUtil.createImpfung(LocalDate.of(2021, 1, 9), impfstoff);
		Impfung impfung2 = TestdataCreationUtil.createImpfung(LocalDate.of(2021, 2, 6), impfstoff);
		ImpfinformationDto infos = TestdataCreationUtil.createImpfinformationen(reg, impfung, impfung2, null, null, null);

		reg.setStatusToAbgeschlossen(infos, impfung2);


		VaccinationCertificateCreateDto dto = mapRegForCovidCertDto(reg, impfung, impfung2, null, CovidCertBatchType.POST);
		Assertions.assertEquals("Meier", dto.getName().getFamilyName());
		Assertions.assertEquals("Friederich", dto.getName().getGivenName());
		Assertions.assertEquals("fr", dto.getLanguage());
		Assertions.assertEquals(LocalDate.of(1982, 12, 12), dto.getDateOfBirth());
		Assertions.assertEquals(1, dto.getVaccinationInfo().size());

		VaccinationCertificateDataDto dataDto = dto.getVaccinationInfo().get(0);
		Assertions.assertEquals(2, dataDto.getTotalNumberOfDoses());
		Assertions.assertEquals(2, dataDto.getNumberOfDoses());
		Assertions.assertEquals("CH", dataDto.getCountryOfVaccination());
		Assertions.assertEquals(CovidCertificateVaccinesNames.MODERNA.getCode(), dataDto.getMedicinalProductCode());
		Assertions.assertEquals(LocalDate.of(2021, 2, 6), dataDto.getVaccinationDate());

		Assertions.assertNotNull(dto.getAddress());
		Assertions.assertEquals("BE", dto.getAddress().getCantonCodeSender());
		Assertions.assertEquals("Schüpbach", dto.getAddress().getCity());
		Assertions.assertEquals("3535", dto.getAddress().getZipCode());
		Assertions.assertEquals("Musterstrasse 12", dto.getAddress().getStreetAndNr());

		Adresse invalidAdr = new Adresse();
		invalidAdr.setAdresse1("Lauchringenstr 16");
		invalidAdr.setOrt("Lauchringen");
		invalidAdr.setPlz("D-79787");
		reg.setAdresse(invalidAdr);
		VaccinationCertificateCreateDto dto2 = mapRegForCovidCertDto(reg, impfung, impfung2, null, CovidCertBatchType.POST);
		Assertions.assertNull(dto2.getAddress());

		Adresse veryLongAddr = new Adresse();
		veryLongAddr.setAdresse1("Lauchringenstr im Ort mit einem sehr langen Namen welcher dann abgeschnitten werden muss weil er sonst einfach zu lang ist "
			+ "und nicht mehr passen wuerde");
		veryLongAddr.setOrt("Lauchringen im Ort mit einem sehr langen Namen welcher dann abgeschnitten werden muss weil er sonst einfach zu lang ist und nicht"
			+ " mehr passen wuerde");
		veryLongAddr.setPlz("3014");
		reg.setAdresse(veryLongAddr);
		VaccinationCertificateCreateDto dto3 = mapRegForCovidCertDto(reg, impfung, impfung2, null, CovidCertBatchType.POST);
		Assertions.assertNotNull(dto3.getAddress());
		Assertions.assertEquals("BE", dto3.getAddress().getCantonCodeSender());
		Assertions.assertEquals("Lauchringen im Ort mit einem sehr langen Namen welcher dann abgeschnitten werden muss weil er sonst einfach zu lang ist und "
			+ "nich", dto3.getAddress().getCity());
		Assertions.assertEquals("Lauchringenstr im Ort mit einem sehr langen Namen welcher dann abgeschnitten werden muss weil er sonst einfach zu lang ist "
			+ "und n", dto3.getAddress().getStreetAndNr());
	}

	@Test
	public void testMappintCovidGenesen() {
		ImpfinformationBuilder builder = new ImpfinformationBuilder();
		ImpfinformationDto infos = builder
			.create()
			.withImpfung1(LocalDate.of(2021, 1, 9), TestdataCreationUtil.createImpfstoffModerna())
			.getInfos();

		Impfung impfung = infos.getImpfung1();
		Assertions.assertNotNull(impfung);

		final Registrierung registrierung = infos.getRegistrierung();
		registrierung.setName("Muster");
		registrierung.setVorname("Hans");
		registrierung.setGeburtsdatum(LocalDate.of(1984, 12, 12));
		registrierung.setStatusToAbgeschlossenOhneZweiteImpfung(infos, true, null, LocalDate.of(2021,1 ,1));

		VaccinationCertificateCreateDto dto = mapRegForCovidCertDto(registrierung, impfung, null, null, CovidCertBatchType.ONLINE);
		Assertions.assertEquals("Muster", dto.getName().getFamilyName());
		Assertions.assertEquals("Hans", dto.getName().getGivenName());
		Assertions.assertEquals("de", dto.getLanguage());
		Assertions.assertEquals(LocalDate.of(1984, 12, 12), dto.getDateOfBirth());
		Assertions.assertEquals(1, dto.getVaccinationInfo().size());

		VaccinationCertificateDataDto dataDto = dto.getVaccinationInfo().get(0);
		Assertions.assertEquals(1, dataDto.getTotalNumberOfDoses());
		Assertions.assertEquals(1, dataDto.getNumberOfDoses());
		Assertions.assertEquals("CH", dataDto.getCountryOfVaccination());
		Assertions.assertEquals(CovidCertificateVaccinesNames.MODERNA.getCode(), dataDto.getMedicinalProductCode());
		Assertions.assertEquals(LocalDate.of(2021, 1, 9), dataDto.getVaccinationDate());
	}

	@Test
	public void testMappingFail() {
		ImpfinformationBuilder builder = new ImpfinformationBuilder();
		ImpfinformationDto infos = builder
			.create()
			.withImpfung1(LocalDate.of(2021, 1, 9), TestdataCreationUtil.createImpfstoffPfizer())
			.getInfos();

		Impfung impfung = infos.getImpfung1();
		Assertions.assertNotNull(impfung);

		infos.getRegistrierung().setStatusToAbgeschlossenOhneZweiteImpfung(infos,false, "nicht vollstaendig", LocalDate.now());

		try {
			VaccinationCertificateCreateDto dto = mapRegForCovidCertDto(infos.getRegistrierung(), impfung, null, null, CovidCertBatchType.ONLINE);
			Assertions.fail("should fail if not complete imfschutz");
		} catch (IllegalArgumentException exception) {
			// expected
		}
		try {
			infos.getRegistrierung().setStatusToAbgeschlossen(infos, impfung);
			mapRegForCovidCertDto(infos.getRegistrierung(), null, null, null, CovidCertBatchType.ONLINE);
			Assertions.fail("should fail if called without at least 1 impfung");
		} catch (NullPointerException exception) {
			//excepted
		}

	}

	/**
	 * this tests validateAndNormalizePlz
	 */
	@ParameterizedTest
	@CsvSource({
		"F-90200, F-90200, true",
		"D - 79576, D - 79576, true",
		"8173 Neerach, 8173, false",
		"CH-3014,3014, false",
		"Münchenbuchsee, , true",
		"123, , true",
		"Postfach 3001, 3001, false",
		"3 5 3 5, 3535, false",
		"D-3535, 3535, false",
		"\t3'20, 320, true",
		"3127¨, 3127, false",
		"8.04, 804, true",
		"8408, 8408, false",
		"SA 0181, 0181, true",
		"0181, 0181, true",
	})
	public void testPlzMapping(String plzInput, String plzResult, boolean shouldThrow) {
		try {
			String output = ValidationUtil.validateAndNormalizePlz(plzInput);
			if (shouldThrow) {
				Assertions.fail("plz " + plzInput + " should not be valid and throw an exception");
			} else {
				Assertions.assertEquals(plzResult, output);
			}
		} catch (PlzMappingException ex) {
			if (!shouldThrow) {
				Assertions.fail("Should not have thrown exception");
			}
		}
	}
}
