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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ch.dvbern.oss.vacme.entities.UserPrincipal;
import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.benutzer.Benutzer;
import ch.dvbern.oss.vacme.entities.embeddables.DateTimeRange;
import ch.dvbern.oss.vacme.entities.externeimpfinfos.ExternesZertifikat;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossier;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.Impfstoff;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impfslot;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.helper.TestImpfstoff;
import ch.dvbern.oss.vacme.jax.korrektur.ImpfungDatumKorrekturJax;
import ch.dvbern.oss.vacme.jax.korrektur.ImpfungKorrekturJax;
import ch.dvbern.oss.vacme.repo.BenutzerRepo;
import ch.dvbern.oss.vacme.repo.ImpfterminRepo;
import ch.dvbern.oss.vacme.repo.ImpfungRepo;
import ch.dvbern.oss.vacme.repo.MigrationRepo;
import ch.dvbern.oss.vacme.service.booster.BoosterService;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationBuilder;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.shared.mappers.TestUtil;
import ch.dvbern.oss.vacme.util.TestdataCreationUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

class KorrekturServiceTest {

	private KorrekturService korrekturservice;
	private ImpfungRepo impfungRepoMock;
	private ImpfstoffService impfstoffService;
	private UserPrincipal userPrincipal;
	private TerminbuchungService terminbuchungServiceMock;
	private ImpfterminRepo impfterminRepoMock;

	@BeforeEach
	void setUp() {
		System.setProperty("vacme.mandant", "BE");

		impfungRepoMock = Mockito.mock(ImpfungRepo.class);
		impfstoffService = Mockito.mock(ImpfstoffService.class);
		userPrincipal = Mockito.mock(UserPrincipal.class);
		terminbuchungServiceMock = Mockito.mock(TerminbuchungService.class);
		impfterminRepoMock = Mockito.mock(ImpfterminRepo.class);

		korrekturservice = new KorrekturService(
			impfungRepoMock,
			impfterminRepoMock,
			userPrincipal,
			terminbuchungServiceMock,
			Mockito.mock(OrtDerImpfungService.class),
			Mockito.mock(RegistrierungService.class),
			Mockito.mock(DokumentService.class),
			Mockito.mock(PdfArchivierungService.class),
			impfstoffService,
			Mockito.mock(ImpfdokumentationService.class),
			Mockito.mock(VMDLService.class),
			Mockito.mock(MigrationRepo.class),
			Mockito.mock(ZertifikatService.class),
			Mockito.mock(ImpfinformationenService.class),
			Mockito.mock(KeyCloakRegService.class),
			Mockito.mock(BenutzerRepo.class),
			Mockito.mock(ApplicationPropertyService.class),
			Mockito.mock(BoosterService.class)
		);

	}

	@CsvSource({
		//"Zertifikat, Impffolge, vollst, Status                            , impfstoffAlt , impfstoffNeu  , hasBooster , Exception"

		"false , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG , TEST_MODERNA , TEST_MODERNA  , false , null" ,
		"true  , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG , TEST_MODERNA , TEST_PFIZER   , false , null" ,
		"true  , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG , TEST_PFIZER  , TEST_JOHNSON  , false , null" ,
		"false , ERSTE_IMPFUNG  , false , ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG , TEST_MODERNA , TEST_MODERNA  , false , null" ,
		"false , ERSTE_IMPFUNG  , false , ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG , TEST_MODERNA , TEST_PFIZER   , false , null" ,
		"true  , ERSTE_IMPFUNG  , false , ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG , TEST_PFIZER  , TEST_JOHNSON  , false , null" ,

		"false , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN 					, TEST_JOHNSON , TEST_JOHNSON  , false , null" ,
		"false , ERSTE_IMPFUNG  , false , ABGESCHLOSSEN 					, TEST_JOHNSON , TEST_PFIZER   , false , null" , // hat eigentlich nie ein zert gehabt daher kein reovke noetig
		"false , ERSTE_IMPFUNG  , false , ABGESCHLOSSEN 					, TEST_JOHNSON , TEST_JOHNSON  , false , null" , // hat eigentlich nie ein zert gehabt daher kein reovke noetig
		"false , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN                     , TEST_MODERNA , TEST_MODERNA  , false , null" ,
		"false , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN                     , TEST_MODERNA , TEST_PFIZER   , false , null" ,
		"false , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN                     , TEST_JOHNSON , TEST_PFIZER   , false , null" ,
		"true  , ZWEITE_IMPFUNG , true  , ABGESCHLOSSEN                     , TEST_MODERNA , TEST_PFIZER   , false , null" ,
		"false , ZWEITE_IMPFUNG , true  , ABGESCHLOSSEN                     , TEST_MODERNA , TEST_MODERNA  , false , null" ,
		"true  , ZWEITE_IMPFUNG , true  , ABGESCHLOSSEN                    	, TEST_PFIZER  , TEST_JOHNSON  , false , null" , // Zweitimpfung von 2-Dosen-Stoff auf 1-Dosen-Stoff -> erlaubt

		// Wechsel der Impfstoffe der Grundimpfungen, die nicht erlaubt sind:
		"false , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN                     , TEST_PFIZER  , TEST_JOHNSON  , false , IMPFUNG_ZUVIELE_DOSEN_KORREKTUR" , // Erstimpfung von 2-Dosen-Stoff auf 1-Dosen-Stoff, aber es gibt schon Zweitimpfung -> Exception
		"false , ERSTE_IMPFUNG  , true  , ABGESCHLOSSEN                     , TEST_JOHNSON , TEST_PFIZER   , true , IMPFUNG_ZUVIELE_DOSEN_KORREKTUR" , // Erstimpfung von 1-Dosen nach 2-Dose, aber es gibt schon Booster -> Exception

	})
	@ParameterizedTest
	void impfungKorrigieren(
		boolean expectCertCreation,
		Impffolge correctedFolge,
		boolean vollstaendigGeimpft,
		RegistrierungStatus status,
		TestImpfstoff impfstoffAlt,
		TestImpfstoff impfstoffNeu,
		boolean hasBooster,
		String expectedValidationErrorKey
	) {
		Impfstoff impfstoffFalsch = TestImpfstoff.createImpfstoffForTest(impfstoffAlt);
		Impfstoff impfstoffChanged = TestImpfstoff.createImpfstoffForTest(impfstoffNeu);

		@NonNull Impffolge folge = correctedFolge;
		@NonNull String lot = RandomStringUtils.random(10); // irelevant
		@NonNull BigDecimal menge= new BigDecimal("0.1");
		ImpfungKorrekturJax korrekturJax = new ImpfungKorrekturJax(folge, impfstoffChanged.getId(), lot, menge, null);
		boolean genesen = vollstaendigGeimpft && RegistrierungStatus.ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG == status;

		ImpfinformationBuilder helper = new ImpfinformationBuilder();
		helper.create().withImpfung1(LocalDate.now(), impfstoffFalsch);
		if (genesen) {
			helper.withCoronaTest(LocalDate.now().minusMonths(1));
		}
		if (vollstaendigGeimpft && status == RegistrierungStatus.ABGESCHLOSSEN) {
			helper.withImpfung2(LocalDate.now(), impfstoffFalsch);
		}
		if (hasBooster) {
			helper.withBooster(LocalDate.now(), impfstoffFalsch);
		}

		ImpfinformationDto infos = helper.getInfos();
		Impftermin impftermin = new Impftermin();
		impftermin.setImpffolge(folge);
		Impfung impfungToCorrect = folge == Impffolge.ERSTE_IMPFUNG ? infos.getImpfung1() : infos.getImpfung2();
		Assertions.assertNotNull(impfungToCorrect);
		impfungToCorrect.setTermin(impftermin);
		impfungToCorrect.setGenerateZertifikat(false);

		Mockito.when(impfungRepoMock.getByImpftermin(Mockito.any())).thenReturn(Optional.of(impfungToCorrect));
		Mockito.when(impfstoffService.findById(Mockito.any())).thenAnswer(invocation -> {
			ID<Impfstoff> argument = invocation.getArgument(0, ID.class);
			return TestImpfstoff.getImpfstoffById(argument.getId());
		});

		Benutzer benutzer = new Benutzer(UUID.randomUUID());
		benutzer.setBenutzername("testuser");
		Mockito.when(userPrincipal.getBenutzerOrThrowException()).thenReturn(benutzer);

		// Korrektur durchfuehren: mit oder ohne erwartete AppValidationException
		if (expectedValidationErrorKey.equals("null")) {
			this.korrekturservice.impfungKorrigieren(korrekturJax, infos, impftermin);
		} else {
			TestUtil.assertThrowsAppvalidation(
				AppValidationMessage.valueOf(expectedValidationErrorKey),
				null,
				() -> {
					this.korrekturservice.impfungKorrigieren(korrekturJax, infos, impftermin);
				});
		}

		Assertions.assertEquals(expectCertCreation, impfungToCorrect.isGenerateZertifikat());
	}

	@Test
	public void testSonderfall_zweiteImpfungAufJanssonKorrigieren() {
		// Dieser Fall ist in der Applikation nicht moeglich zu erfassen, wird aber im Code bei Korrektur abgehandelt
		ImpfinformationBuilder helper = new ImpfinformationBuilder();
		final Impfstoff janssen = TestdataCreationUtil.createImpfstoffJanssen();
		final Impfstoff pfizer = TestdataCreationUtil.createImpfstoffPfizer();
		helper
			.create()
			.withImpfung1(LocalDate.now(), janssen)
			.withImpfung2(LocalDate.now(), pfizer);
		final ImpfinformationDto infos = helper.getInfos();

		Assertions.assertNotNull(infos);
		Assertions.assertNotNull(infos.getImpfung2());

		ImpfungKorrekturJax korrekturJax = new ImpfungKorrekturJax(Impffolge.ZWEITE_IMPFUNG, janssen.getId(), "lot", BigDecimal.ONE, 2);

		Mockito.when(impfungRepoMock.getByImpftermin(Mockito.any())).thenReturn(Optional.of(infos.getImpfung2()));
		Mockito.when(impfstoffService.findById(Mockito.any())).thenAnswer(invocation -> {
			return janssen;
		});

		TestUtil.assertThrowsAppvalidation(
			AppValidationMessage.valueOf(AppValidationMessage.IMPFUNG_ZUVIELE_DOSEN.name()),
			null,
			() -> {

				this.korrekturservice.impfungKorrigieren(korrekturJax, infos, infos.getImpfung2().getTermin());
			});
	}

	@CsvSource({
		//"DatumExternesZertifikat, DatumImpfungBooster1, ImpffolgeToCorrect, ImpffolgeNrToCorrect, datumNew, expectedValidationErrorKey"
		"2021-05-01, 2021-11-01, 2021-12-01, BOOSTER_IMPFUNG, 3, 2021-11-02, null",
		"2021-05-01, 2021-11-01, 2021-12-01, BOOSTER_IMPFUNG, 3, 2021-12-02, IMPFTERMIN_IMPFUNG_BETWEEN_OTHERS",
		"2021-05-01, 2021-11-01, 2021-12-01, BOOSTER_IMPFUNG, 3, 2999-12-31, IMPFTERMIN_INVALID_CORRECTION", // Bei Korrektur in Zukunft
		"2021-05-01, 2021-11-01, 2021-12-01, BOOSTER_IMPFUNG, 3, 2021-01-01, IMPFTERMIN_IMPFUNG_BETWEEN_OTHERS"
	})
	@ParameterizedTest
	void impfdatumKorrigierenMitExternesZertifikat(
		@NonNull LocalDate dateExternesZertifikat,
		@NonNull LocalDate dateBooster1,
		@NonNull LocalDate dateBooster2,
		@NonNull Impffolge impffolgeToCorrect,
		@NonNull Integer impffolgeNrToCorrect,
		@NonNull LocalDate dateNew,
		@NonNull String expectedValidationErrorKey
	) {
		final Fragebogen fragebogen = TestdataCreationUtil.createFragebogen();
		Impfstoff impfstoff = TestdataCreationUtil.createImpfstoffModerna();

		ExternesZertifikat externesZertifikat =
			TestdataCreationUtil.createExternesZertifikat(fragebogen.getRegistrierung(), impfstoff, 2, dateExternesZertifikat);

		Impfdossier dossier = new Impfdossier();
		dossier.setRegistrierung(fragebogen.getRegistrierung());

		final Impfung booster1 = TestdataCreationUtil.addBoosterToDossier(dossier, dateBooster1.atStartOfDay(), 3);
		final Impfung booster2 = TestdataCreationUtil.addBoosterToDossier(dossier, dateBooster2.atStartOfDay(), 4);
		List<Impfung> boosterImpfungen = new ArrayList<>();
		boosterImpfungen.add(booster1);
		boosterImpfungen.add(booster2);

		ImpfinformationDto infos = TestdataCreationUtil.createImpfinformationen(
			fragebogen.getRegistrierung(),
			null,
			null,
			dossier,
			externesZertifikat,
			boosterImpfungen
		);

		testDatumKorrigieren(impffolgeToCorrect, impffolgeNrToCorrect, dateNew, expectedValidationErrorKey, infos);
	}

	@CsvSource({
		//"DateImpfung1, DateImpfung2, DatumImpfungBooster1, ImpffolgeToCorrect, ImpffolgeNrToCorrect, datumNew, expectedValidationErrorKey"
		"2021-01-01, 2021-02-01, 2021-11-01, 2021-12-01, BOOSTER_IMPFUNG, 3, 2021-11-02, null",
		"2021-01-01, 2021-02-01, 2021-11-01, 2021-12-01, BOOSTER_IMPFUNG, 3, 2021-12-02, IMPFTERMIN_IMPFUNG_BETWEEN_OTHERS",
		"2021-01-01, 2021-02-01, 2021-11-01, 2021-12-01, BOOSTER_IMPFUNG, 3, 2999-12-31, IMPFTERMIN_INVALID_CORRECTION", // Bei Korrektur in Zukunft
		"2021-01-01, 2021-02-01, 2021-11-01, 2021-12-01, BOOSTER_IMPFUNG, 3, 2021-01-02, IMPFTERMIN_IMPFUNG_BETWEEN_OTHERS",
	})
	@ParameterizedTest
	void impfdatumKorrigierenOhneExternesZertifikat(
		@NonNull LocalDate dateImpfung1,
		@NonNull LocalDate dateImpfung2,
		@NonNull LocalDate dateBooster1,
		@NonNull LocalDate dateBooster2,
		@NonNull Impffolge impffolgeToCorrect,
		@NonNull Integer impffolgeNrToCorrect,
		@NonNull LocalDate dateNew,
		@NonNull String expectedValidationErrorKey
	) {
		final Fragebogen fragebogen = TestdataCreationUtil.createFragebogen();

		Impfung impfung1 = TestdataCreationUtil.createImpfungWithImpftermin(dateImpfung1.atStartOfDay(), Impffolge.ERSTE_IMPFUNG);
		Impfung impfung2 = TestdataCreationUtil.createImpfungWithImpftermin(dateImpfung2.atStartOfDay(), Impffolge.ZWEITE_IMPFUNG);

		Impfdossier dossier = new Impfdossier();
		dossier.setRegistrierung(fragebogen.getRegistrierung());

		final Impfung booster1 = TestdataCreationUtil.addBoosterToDossier(dossier, dateBooster1.atStartOfDay(), 3);
		final Impfung booster2 = TestdataCreationUtil.addBoosterToDossier(dossier, dateBooster2.atStartOfDay(), 4);
		List<Impfung> boosterImpfungen = new ArrayList<>();
		boosterImpfungen.add(booster1);
		boosterImpfungen.add(booster2);

		ImpfinformationDto infos = TestdataCreationUtil.createImpfinformationen(
			fragebogen.getRegistrierung(),
			impfung1,
			impfung2,
			dossier,
			null,
			boosterImpfungen
		);

		testDatumKorrigieren(impffolgeToCorrect, impffolgeNrToCorrect, dateNew, expectedValidationErrorKey, infos);
	}

	private void testDatumKorrigieren(
		@NonNull Impffolge impffolgeToCorrect,
		@NonNull Integer impffolgeNrToCorrect,
		@NonNull LocalDate dateNew,
		@NonNull String expectedValidationErrorKey,
		@NonNull ImpfinformationDto infos
	) {
		final Impfdossiereintrag eintragToCorrect = ImpfinformationenService.getDossiereintragForNr(infos, impffolgeNrToCorrect);
		Assertions.assertNotNull(eintragToCorrect);
		final Impftermin terminToCorrect = eintragToCorrect.getImpftermin();
		Assertions.assertNotNull(terminToCorrect);
		final Impfung impfungToCorrect = ImpfinformationenService.readImpfungForImpffolgeNr(infos, impffolgeToCorrect, impffolgeNrToCorrect);
		Assertions.assertNotNull(impfungToCorrect);

		ImpfungDatumKorrekturJax jax = new ImpfungDatumKorrekturJax(
			impffolgeToCorrect, dateNew.atStartOfDay(), impffolgeNrToCorrect
		);

		// Mockerei
		Benutzer benutzer = new Benutzer(UUID.randomUUID());
		benutzer.setBenutzername("testuser");
		Mockito.when(userPrincipal.getBenutzerOrThrowException()).thenReturn(benutzer);
		Mockito.when(impfungRepoMock.getByImpftermin(Mockito.any())).thenReturn(Optional.of(impfungToCorrect));
		Mockito.when(terminbuchungServiceMock.createOnDemandImpftermin(Mockito.any(), Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
			Impfslot slot = new Impfslot();
			slot.setOrtDerImpfung(terminToCorrect.getImpfslot().getOrtDerImpfung());
			slot.setZeitfenster(DateTimeRange.of(dateNew.atStartOfDay(), dateNew.atStartOfDay()));
			Impftermin adHocTermin = new Impftermin();
			adHocTermin.setImpffolge(Impffolge.BOOSTER_IMPFUNG);
			adHocTermin.setImpfslot(slot);
			eintragToCorrect.setImpfterminFromImpfterminRepo(adHocTermin);
			impfungToCorrect.setTermin(adHocTermin);
			return adHocTermin;
		});
		Mockito.doAnswer(invocation -> {
			Impftermin termin = invocation.getArgument(2, Impftermin.class);
			eintragToCorrect.setImpfterminFromImpfterminRepo(termin);
			termin.setGebuchtFromImpfterminRepo(true);
			return null;
		}).when(impfterminRepoMock).boosterTerminSpeichern(Mockito.any(), Mockito.any(), Mockito.any());

		// Korrektur durchfuehren: mit oder ohne erwartete AppValidationException
		if (expectedValidationErrorKey.equals("null")) {
			korrekturservice.impfungDatumKorrigieren(jax, terminToCorrect, infos);
		} else {
			TestUtil.assertThrowsAppvalidation(
				AppValidationMessage.valueOf(expectedValidationErrorKey),
				null,
				() -> {
					korrekturservice.impfungDatumKorrigieren(jax, terminToCorrect, infos);
				});
		}
	}


}
