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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import ch.dvbern.oss.vacme.entities.base.ApplicationProperty;
import ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey;
import ch.dvbern.oss.vacme.entities.registration.Prioritaet;
import ch.dvbern.oss.vacme.repo.ApplicationPropertyRepo;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;

@ApplicationScoped
@Slf4j
public class ApplicationPropertyService {

	private final ApplicationPropertyRepo applicationPropertyRepo;

	@Inject
	public ApplicationPropertyService(
		@NonNull ApplicationPropertyRepo applicationPropertyRepo
	) {
		this.applicationPropertyRepo = applicationPropertyRepo;
	}

	@NonNull
	public List<ApplicationProperty> findAll() {
		return applicationPropertyRepo.findAll();
	}

	@NonNull
	public ApplicationProperty getByKey(@NonNull ApplicationPropertyKey key) {
		return applicationPropertyRepo.getByKey(key)
			.orElseThrow(() -> AppFailureException.entityNotFound(ApplicationProperty.class, key));
	}

	@NonNull
	public Optional<ApplicationProperty> getByKeyOptional(@NonNull ApplicationPropertyKey key) {
		return applicationPropertyRepo.getByKey(key);
	}

	public void save(@NonNull ApplicationPropertyKey key, @NonNull String value) {
		final Optional<ApplicationProperty> propertyOptional = applicationPropertyRepo.getByKey(key);
		if (propertyOptional.isPresent()) {
			final ApplicationProperty applicationProperty = propertyOptional.get();
			applicationProperty.setValue(value);
			applicationPropertyRepo.update(applicationProperty);
		} else {
			ApplicationProperty property = new ApplicationProperty(key, value);
			applicationPropertyRepo.create(property);
		}
	}

	public void impfgruppeFreigeben(@NonNull List<String> values) {
		var impfgruppen = values.stream().map(Prioritaet::valueOf).map(Prioritaet::name).collect(Collectors.joining("-"));
		save(ApplicationPropertyKey.PRIO_FREIGEGEBEN_BIS, impfgruppen);
	}

	public boolean noFreieTermin() {
		return getBooleanApplicationProperty(ApplicationPropertyKey.GLOBAL_NO_FREIE_TERMINE);
	}

	public boolean isZertifikatEnabled() {
		return getBooleanApplicationProperty(ApplicationPropertyKey.COVID_ZERTIFIKAT_ENABLED);
	}

	public boolean isEmailKorrekturEnabled() {
		return getBooleanApplicationProperty(ApplicationPropertyKey.KORREKTUR_EMAIL_TELEPHONE);
	}

	private boolean getBooleanApplicationProperty(ApplicationPropertyKey propertyKey) {
		ApplicationProperty property = applicationPropertyRepo
			.getByKey(propertyKey)
			.orElse(null);
		if (property == null) {
			return false;
		}
		return Boolean.parseBoolean(property.getValue());
	}

	public void aquireBatchJobLock(ApplicationPropertyKey lockKey) {
		applicationPropertyRepo.aquireBatchJobLock(lockKey);
	}

	public void releaseBatchJobLock(ApplicationPropertyKey lockKey) {

		applicationPropertyRepo.releaseBatchJobLock(lockKey);
	}
}
