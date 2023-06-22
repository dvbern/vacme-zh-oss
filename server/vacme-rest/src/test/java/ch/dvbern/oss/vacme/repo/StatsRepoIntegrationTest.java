/*
 * Copyright (C) 2022 DV Bern AG, Switzerland
 */

package ch.dvbern.oss.vacme.repo;

import javax.inject.Inject;

import ch.dvbern.oss.vacme.testing.H2DBProfile;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTestResource(H2DatabaseTestResource.class)
@TestProfile(H2DBProfile.class)
@QuarkusTest
public class StatsRepoIntegrationTest {

	@Inject
	StatsRepo statsRepo;

	@Test
	void testTotalStatEmpty() {
		Assertions.assertNotNull(statsRepo);
		long registrierungTotal = statsRepo.getAnzahlRegistrierungenCallcenter();

		Assertions.assertEquals(0, registrierungTotal);

	}
}
