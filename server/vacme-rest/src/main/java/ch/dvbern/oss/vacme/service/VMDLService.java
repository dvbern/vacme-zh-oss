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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import ch.dvbern.oss.vacme.entities.base.ApplicationProperty;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.jax.vmdl.VMDLDeleteJax;
import ch.dvbern.oss.vacme.jax.vmdl.VMDLUploadJax;
import ch.dvbern.oss.vacme.repo.ImpfungRepo;
import ch.dvbern.oss.vacme.rest_client.vmdl.VMDLRestClientService;
import ch.dvbern.oss.vacme.service.plz.PLZCacheService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jetbrains.annotations.NotNull;

import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.VMDL_CRON_3QUERIES;
import static ch.dvbern.oss.vacme.jax.vmdl.VMDLUploadJax.MEDSTAT_AUSLAND;
import static ch.dvbern.oss.vacme.jax.vmdl.VMDLUploadJax.MEDSTAT_UNKNOWN;
import static ch.dvbern.oss.vacme.jax.vmdl.VMDLUploadJax.UNKOWN_KANTON;

@ApplicationScoped
@Slf4j
public class VMDLService {

	private static final String BUESINGEN_KANTON = "DE";
	private static final String CAMPIONE_ITALIA_KANTON = "IT";
	private static final String VMDL_KRZL_D = "D";
	private static final String VMDL_KRZL_I = "I";

	@Inject
	ImpfungRepo impfungRepo;

	@Inject
	PLZCacheService plzCacheService;

	@Inject
	@RestClient
	VMDLRestClientService vmdlRestClientService;

	@Inject
	ApplicationPropertyService applicationPropertyService;

	@ConfigProperty(name = "vmdl.upload.chunk.limit", defaultValue = "100")
	int uploadChunkLimit;

	@ConfigProperty(name = "vmdl.reporting_unit_id")
	String reportingUnitID;

	@Transactional(TxType.NOT_SUPPORTED)
	public void uploadVMDLData() {
		doUploadVMDLDataForBatch();
	}

	@Transactional(TxType.REQUIRES_NEW)
	private void doUploadVMDLDataForBatch() {
		StopWatch stopWatchQuery = StopWatch.createStarted();

		List<VMDLUploadJax> vmdlUploadJaxList = findVMDLPendeneImpfungen();

		LOG.info("VACME-VMDL: Total Query Time: {}ms for {}", stopWatchQuery.getTime(TimeUnit.MILLISECONDS), vmdlUploadJaxList.size());
		if (vmdlUploadJaxList.isEmpty()) {
			return;
		}

		StopWatch stopwWatchMapping = StopWatch.createStarted();
		this.setKantonBasedOnPlzForAllEntries(vmdlUploadJaxList);
		LOG.info("VACME-VMDL: Mapping Time PLZ Time: {}ms for {}", stopwWatchMapping.getTime(TimeUnit.MILLISECONDS), vmdlUploadJaxList.size());
		this.setMedStatBasedOnPlzForAllEntries(vmdlUploadJaxList);
		LOG.info("VACME-VMDL: Mapping Time Medstat Time: {}ms for {}", stopwWatchMapping.getTime(TimeUnit.MILLISECONDS), vmdlUploadJaxList.size());

		StopWatch stopWatch = StopWatch.createStarted();
		LOG.info("VACME-VMDL: START Send next chunk to VMDL upload. Size: {}", vmdlUploadJaxList.size());
		vmdlRestClientService.uploadData(vmdlUploadJaxList);
		stopWatch.stop();
		LOG.info("VACME-VMDL: END Sending chunk to VMDL (took {}ms)", stopWatch.getTime(TimeUnit.MILLISECONDS));

		final LocalDateTime now = LocalDateTime.now();
		for (VMDLUploadJax impfungVmdl : vmdlUploadJaxList) {
			Impfung impfung = impfungVmdl.getImpfung();
			impfung.setTimestampVMDL(now);
			impfungRepo.update(impfung);
		}
	}

	@NotNull
	private List<VMDLUploadJax> findVMDLPendeneImpfungen() {

		if (run3QueriesSettingEnabled()) {
			return impfungRepo.getVMDLPendenteImpfungen3Queries(uploadChunkLimit, reportingUnitID);
		}

		return impfungRepo.getVMDLPendenteImpfungen2Queries(uploadChunkLimit, reportingUnitID);
	}

	private boolean run3QueriesSettingEnabled() {
		Optional<ApplicationProperty> byKeyOptional =
			this.applicationPropertyService.getByKeyOptional(VMDL_CRON_3QUERIES);
		return byKeyOptional
			.map(applicationProperty -> Boolean.parseBoolean(applicationProperty.getValue()))
			.orElse(false);
	}

	private void setKantonBasedOnPlzForAllEntries(List<VMDLUploadJax> vmdlUploadJaxList) {
		vmdlUploadJaxList.forEach(vmdlUploadJax -> {
			String kantonsKrzl = plzCacheService.findBestMatchingKantonFor(vmdlUploadJax.getPlz()).orElse(UNKOWN_KANTON);
			if (BUESINGEN_KANTON.equals(kantonsKrzl)) {
				kantonsKrzl = VMDL_KRZL_D;
			} else if (CAMPIONE_ITALIA_KANTON.equals(kantonsKrzl)) {
				kantonsKrzl = VMDL_KRZL_I;
			}
			vmdlUploadJax.setPersonResidenceCtn(kantonsKrzl);
		});
	}

	/**
	 * - People living in CH = Medstat Code
	 * - People living in Liechtenstein = LIE
	 * - For people living outside CH and LIE = XX99
	 * - Unknown = 0000
	 */
	private void setMedStatBasedOnPlzForAllEntries(@NonNull List<VMDLUploadJax> vmdlUploadJaxList) {
		vmdlUploadJaxList.forEach(vmdlUploadJax -> {
			Optional<String> medStatOptional = plzCacheService.findBestMatchingMedStatFor(vmdlUploadJax.getPlz());
			if (medStatOptional.isPresent()) {
				// Schweiz und Liechtenstein, PLZ bekannt (FL hat den Code LIE drin)
				vmdlUploadJax.setMedstat(medStatOptional.get());
			} else if (vmdlUploadJax.isAusland()) {
				// Ausland
				vmdlUploadJax.setMedstat(MEDSTAT_AUSLAND);
			} else {
				// unbekannt
				vmdlUploadJax.setMedstat(MEDSTAT_UNKNOWN);
			}
		});
	}

	@Transactional(TxType.REQUIRED)
	public void deleteImpfung(Impfung impfung) {
		LOG.info("VACME-VMDL: START Delete Impfung. ID: {}", impfung.getId());
		VMDLDeleteJax deleteJax = new VMDLDeleteJax(impfung, reportingUnitID);
		vmdlRestClientService.deleteData(Collections.singletonList(deleteJax));
		LOG.info("VACME-VMDL: END Delete Impfung. ID: {}", impfung.getId());
	}
}
