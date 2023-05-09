package ch.dvbern.oss.vacme.jax.vmdl;

import java.time.LocalDate;

import ch.dvbern.oss.vacme.entities.impfen.Impfstoff;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationBuilder;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.util.TestdataCreationUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VMDLUploadJaxMappingTest {

	private final Impfstoff moderna = TestdataCreationUtil.createImpfstoffModerna();
	private final ImpfinformationBuilder helper = new ImpfinformationBuilder();


	@BeforeAll
	static void setUp() {
		System.setProperty("vacme.mandant", "BE");
	}

	@Test
	void testMappingOfImpfungWithBooster() {
		helper
			.create()
			.withImpfung1(LocalDate.of(2021, 1,1), moderna)
			.withImpfung2(LocalDate.of(2021,6,1), moderna)
			.withBooster(LocalDate.of(2021, 10, 1), moderna)
			.withBooster(LocalDate.of(2022,2,1), moderna)
			.withBooster(LocalDate.of(2022,3,1), moderna);
		ImpfinformationDto infos = helper.getInfos();
		Fragebogen fragebogen = helper.getFragebogen();

		Assertions.assertNotNull(infos.getImpfung1());
		Assertions.assertNotNull(infos.getImpfung2());
		Assertions.assertNotNull(infos.getImpfdossier());
		Assertions.assertNotNull(infos.getBoosterImpfungen());

		VMDLUploadJax vmdlUploadJax1 = new VMDLUploadJax(infos.getImpfung1(), infos.getRegistrierung(), fragebogen,"rputTest");
		VMDLUploadJax vmdlUploadJax2 = new VMDLUploadJax(infos.getImpfung2(), infos.getRegistrierung(), fragebogen,"rputTest");
		VMDLUploadJax vmdlUploadJax3 = new VMDLUploadJax(infos.getBoosterImpfungen().get(0), infos.getRegistrierung(), fragebogen, infos.getImpfdossier().getEintragForImpffolgeNr(3), "rputTest");
		VMDLUploadJax vmdlUploadJax4 = new VMDLUploadJax(infos.getBoosterImpfungen().get(1), infos.getRegistrierung(), fragebogen, infos.getImpfdossier().getEintragForImpffolgeNr(4), ",rputTest");
		VMDLUploadJax vmdlUploadJax5 = new VMDLUploadJax(infos.getBoosterImpfungen().get(2), infos.getRegistrierung(), fragebogen, infos.getImpfdossier().getEintragForImpffolgeNr(5), ",rputTest");

		Assertions.assertEquals(1,vmdlUploadJax1.getSerie());
		Assertions.assertEquals(1,vmdlUploadJax2.getSerie());
		Assertions.assertEquals(2,vmdlUploadJax3.getSerie());
		Assertions.assertEquals(3,vmdlUploadJax4.getSerie());
		Assertions.assertEquals(4,vmdlUploadJax5.getSerie());
	}

	@Test
	void testMappingOfImpfungWithThreeGrundimmunisierungenAndBooster() {
		helper
			.create()
			.withImpfung1(LocalDate.of(2021, 1,1), moderna)
			.withImpfung2(LocalDate.of(2021,6,1), moderna)
			.withBooster(LocalDate.of(2021, 10, 1), moderna, true)
			.withBooster(LocalDate.of(2022,2,1), moderna)
			.withBooster(LocalDate.of(2022,3,1), moderna);
		ImpfinformationDto infos = helper.getInfos();
		Fragebogen fragebogen = helper.getFragebogen();

		Assertions.assertNotNull(infos.getImpfung1());
		Assertions.assertNotNull(infos.getImpfung2());
		Assertions.assertNotNull(infos.getImpfdossier());
		Assertions.assertNotNull(infos.getBoosterImpfungen());

		VMDLUploadJax vmdlUploadJax1 = new VMDLUploadJax(infos.getImpfung1(), infos.getRegistrierung(), fragebogen,"rputTest");
		VMDLUploadJax vmdlUploadJax2 = new VMDLUploadJax(infos.getImpfung2(), infos.getRegistrierung(), fragebogen,"rputTest");
		VMDLUploadJax vmdlUploadJax3 = new VMDLUploadJax(infos.getBoosterImpfungen().get(0), infos.getRegistrierung(), fragebogen, infos.getImpfdossier().getEintragForImpffolgeNr(3), "rputTest");
		VMDLUploadJax vmdlUploadJax4 = new VMDLUploadJax(infos.getBoosterImpfungen().get(1), infos.getRegistrierung(), fragebogen, infos.getImpfdossier().getEintragForImpffolgeNr(4), ",rputTest");
		VMDLUploadJax vmdlUploadJax5 = new VMDLUploadJax(infos.getBoosterImpfungen().get(2), infos.getRegistrierung(), fragebogen, infos.getImpfdossier().getEintragForImpffolgeNr(5), ",rputTest");

		Assertions.assertEquals(1,vmdlUploadJax1.getSerie());
		Assertions.assertEquals(1,vmdlUploadJax2.getSerie());
		Assertions.assertEquals(1,vmdlUploadJax3.getSerie());
		Assertions.assertEquals(3,vmdlUploadJax4.getSerie()); // TODO VACME-1875: sollte 2 sein
		Assertions.assertEquals(4,vmdlUploadJax5.getSerie()); // TODO VACME-1875: sollte 3 sein
	}

	@Test
	void testMappingKrank() {
		helper
			.create()
			.withImpfung1(LocalDate.of(2021, 1,1), moderna)
			.withCoronaTest(LocalDate.of(2021,6,1))
			.withBooster(LocalDate.of(2021, 10, 1), moderna)
			.withBooster(LocalDate.of(2022,2,1), moderna)
			.withBooster(LocalDate.of(2022,3,1), moderna);
		ImpfinformationDto infos = helper.getInfos();
		Fragebogen fragebogen = helper.getFragebogen();

		Assertions.assertNotNull(infos.getImpfung1());
		Assertions.assertNotNull(infos.getImpfdossier());
		Assertions.assertNotNull(infos.getBoosterImpfungen());

		VMDLUploadJax vmdlUploadJax1 = new VMDLUploadJax(infos.getImpfung1(), infos.getRegistrierung(), fragebogen,"rputTest");
		VMDLUploadJax vmdlUploadJax3 = new VMDLUploadJax(infos.getBoosterImpfungen().get(0), infos.getRegistrierung(), fragebogen, infos.getImpfdossier().getEintragForImpffolgeNr(2), "rputTest");
		VMDLUploadJax vmdlUploadJax4 = new VMDLUploadJax(infos.getBoosterImpfungen().get(1), infos.getRegistrierung(), fragebogen, infos.getImpfdossier().getEintragForImpffolgeNr(3), ",rputTest");
		VMDLUploadJax vmdlUploadJax5 = new VMDLUploadJax(infos.getBoosterImpfungen().get(2), infos.getRegistrierung(), fragebogen, infos.getImpfdossier().getEintragForImpffolgeNr(4), ",rputTest");

		Assertions.assertEquals(1,vmdlUploadJax1.getSerie());
		Assertions.assertEquals(2,vmdlUploadJax3.getSerie());
		Assertions.assertEquals(2,vmdlUploadJax4.getSerie()); // TODO VACME-1875: sollte 3 sein
		Assertions.assertEquals(3,vmdlUploadJax5.getSerie()); // TODO VACME-1875: sollte 4 sein
	}

	@Test
	void testMappingKrankWithThreeGrundimmunisierungen() {
		helper
			.create()
			.withImpfung1(LocalDate.of(2021, 1,1), moderna)
			.withCoronaTest(LocalDate.of(2021,6,1))
			.withBooster(LocalDate.of(2021, 10, 1), moderna, true)
			.withBooster(LocalDate.of(2022,2,1), moderna)
			.withBooster(LocalDate.of(2022,3,1), moderna);
		ImpfinformationDto infos = helper.getInfos();
		Fragebogen fragebogen = helper.getFragebogen();

		Assertions.assertNotNull(infos.getImpfung1());
		Assertions.assertNotNull(infos.getImpfdossier());
		Assertions.assertNotNull(infos.getBoosterImpfungen());

		VMDLUploadJax vmdlUploadJax1 = new VMDLUploadJax(infos.getImpfung1(), infos.getRegistrierung(), fragebogen,"rputTest");
		VMDLUploadJax vmdlUploadJax3 = new VMDLUploadJax(infos.getBoosterImpfungen().get(0), infos.getRegistrierung(), fragebogen, infos.getImpfdossier().getEintragForImpffolgeNr(2), "rputTest");
		VMDLUploadJax vmdlUploadJax4 = new VMDLUploadJax(infos.getBoosterImpfungen().get(1), infos.getRegistrierung(), fragebogen, infos.getImpfdossier().getEintragForImpffolgeNr(3), ",rputTest");
		VMDLUploadJax vmdlUploadJax5 = new VMDLUploadJax(infos.getBoosterImpfungen().get(2), infos.getRegistrierung(), fragebogen, infos.getImpfdossier().getEintragForImpffolgeNr(4), ",rputTest");

		Assertions.assertEquals(1,vmdlUploadJax1.getSerie());
		Assertions.assertEquals(1,vmdlUploadJax3.getSerie());
		Assertions.assertEquals(2,vmdlUploadJax4.getSerie());
		Assertions.assertEquals(3,vmdlUploadJax5.getSerie());
	}
}
