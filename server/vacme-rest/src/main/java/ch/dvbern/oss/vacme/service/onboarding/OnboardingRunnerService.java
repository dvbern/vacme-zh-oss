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

package ch.dvbern.oss.vacme.service.onboarding;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import ch.dvbern.oss.vacme.entities.base.ApplicationProperty;
import ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.service.ApplicationPropertyService;
import ch.dvbern.oss.vacme.service.RegistrierungService;
import ch.dvbern.oss.vacme.util.TimingUtil;
import ch.dvbern.oss.vacme.util.ValidationUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.StopWatch;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Slf4j
@Transactional(TxType.NOT_SUPPORTED)
public class OnboardingRunnerService {

	@ConfigProperty(name = "vacme.onboarding.batchsize", defaultValue = "1")
	long onboardingBatchSize;

	private final RegistrierungService registrierungService;
	private final ApplicationPropertyService applicationPropertyService;
	private final OnboardingService onboardingService;

	@Inject
	public OnboardingRunnerService(
		@NonNull RegistrierungService registrierungService,
		@NonNull ApplicationPropertyService applicationPropertyService,
		@NonNull OnboardingService onboardingService

	) {

		this.registrierungService = registrierungService;
		this.applicationPropertyService = applicationPropertyService;
		this.onboardingService = onboardingService;
	}

	/**
	 * actual logic to run the batchjob
	 *
	 * @param batchType type to run for
	 */
	public void generateBatchOfOnboardingLetters(OnboardingBatchType batchType) {
		if (isAlreadyInProgress(batchType)) {
			LOG.info("VACME-ONBOARDING: ({}) Batchjob already in progress, aborting.", batchType);
			return;
		}
		aquireBatchJobLock(batchType);
		StopWatch stopWatch = StopWatch.createStarted();
		int successCounter = 0;
		int totalCounter = 0;
		List<String> regsToCreateOnboardingFor = new ArrayList<>();
		try {
			regsToCreateOnboardingFor = onboardingService.findRegistrierungenForOnboarding(getOnboardingLettersBatchSize());

			LOG.info("VACME-ONBOARDING: ({}) Starting to generate a batch of up to {} Onboarding-Letters. Found {}",
				batchType, getOnboardingLettersBatchSize(), regsToCreateOnboardingFor.size());

			for (String regNum : regsToCreateOnboardingFor) {
				try {

					Registrierung registrierung = this.registrierungService.findRegistrierungNoCheck(regNum);
					if (!registrierung.isGenerateOnboardingLetter()) {
						LOG.warn("VACME-ONBOARDING: ({}) Registration {} does not meet criteria for onboarding. Maybe it was already processed?",
							batchType, registrierung.getRegistrierungsnummer());
						continue;
					}

					LOG.info("VACME-ONBOARDING: ({}) Erstelle Onboarding Notification fuer {}", batchType, regNum);
					boolean success = createOnboardingNotification(regNum, batchType);
					if (success) {
						successCounter++;
					}
				} finally {
					totalCounter++;
				}
			}
		} finally {
			stopWatch.stop();
			LOG.info(
				"VACME-ONBOARDING: ({}) Onboardingletter Batchrun beendet. Es wurden {} Briefe von total {} Registrierungen in {}s generiert. {} ms/stk",
				batchType,
				successCounter,
				regsToCreateOnboardingFor.size(),
				stopWatch.getTime(TimeUnit.SECONDS),
				TimingUtil.calculateGenerationSpeed(totalCounter,
				stopWatch.getTime()));
			releaseBatchJobLock(batchType);
		}
	}

	@Transactional(TxType.NOT_SUPPORTED)
	public boolean createOnboardingNotification(@NonNull String regNum, @NonNull OnboardingBatchType batchType) {
		return createOnboardingNotificationForRegistrierung(regNum, batchType);
	}

	@Transactional(TxType.SUPPORTS)
	public boolean createOnboardingNotificationForRegistrierung(@NonNull String regNum, @NonNull OnboardingBatchType batchType) {
		try {

			Registrierung registrierung = this.registrierungService.findRegistrierungNoCheck(regNum);

			// Nochmals validieren, dass die Reg in einem richtigen Status ist
			boolean wrongState = ValidationUtil.invalidOnboardingState(registrierung.getRegistrierungStatus());

			if (wrongState) {
				LOG.error("VACME-ONBOARDING: Registration {} was in wrong state for onboarding {}",
					registrierung.getRegistrierungsnummer(), registrierung.getRegistrierungStatus());
				return false;
			}

			Validate.isTrue(registrierung.isGenerateOnboardingLetter(), "generateOnboardingLetter "
				+ "must be true");

			onboardingService.triggerLetterGeneration(registrierung.getRegistrierungsnummer(), batchType);
			LOG.info("VACME-ONBOARDING: Created Letter for {}", registrierung.getRegistrierungsnummer());

			return true;

		} catch (Exception exception) {
			LOG.error("VACME-ONBOARDING: Error during Letter creation for {} ",
				regNum, exception);
			return false;
		}
	}

	private void aquireBatchJobLock(@NonNull OnboardingBatchType batchType) {
		ApplicationPropertyKey lockKey = OnboardingBatchType.mapBatchTypeToLockKey(batchType);
		this.applicationPropertyService.aquireBatchJobLock(lockKey);
	}

	private void releaseBatchJobLock(@NonNull OnboardingBatchType batchType) {
		ApplicationPropertyKey lockKey = OnboardingBatchType.mapBatchTypeToLockKey(batchType);
		this.applicationPropertyService.releaseBatchJobLock(lockKey);
	}

	private boolean isAlreadyInProgress(@NonNull OnboardingBatchType batchType) {
		ApplicationPropertyKey applicationPropertyKey = OnboardingBatchType.mapBatchTypeToLockKey(batchType);
		ApplicationProperty batchJobLockApplicationProperty =
			this.applicationPropertyService.getByKey(applicationPropertyKey);
		return Boolean.parseBoolean(batchJobLockApplicationProperty.getValue());
	}

	private long getOnboardingLettersBatchSize() {
		Optional<ApplicationProperty> byKeyOptional =
			this.applicationPropertyService.getByKeyOptional(ApplicationPropertyKey.VACME_ONBOARDING_BRIEF_BATCHSIZE);
		return byKeyOptional
			.filter(applicationProperty -> StringUtils.isNumeric(applicationProperty.getValue()))
			.map(applicationProperty -> Long.parseLong(applicationProperty.getValue()))
			.orElseGet(() -> onboardingBatchSize);
	}

	/**
	 * Need to gracefully shutdown  that all locks have been
	 * released before datasource shutdown.
	 *
	 * @param event ignored
	 */
	void destroy(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event) {
		LOG.info("Shutdown triggered, try to release locks for onboarding");
		for (OnboardingBatchType type : OnboardingBatchType.values()) {
			try {
				if (isAlreadyInProgress(type)) {
					LOG.info("Gracefully release the locks for the onboarding batchjob in the db on shutdown {}", type);
					releaseBatchJobLock(type);
				}
			} catch (Exception e) {
				LOG.warn("Could not gracefully release locks for onboarding letter batchjob on shutdown {}", e.getMessage());
			}
		}
	}
}
