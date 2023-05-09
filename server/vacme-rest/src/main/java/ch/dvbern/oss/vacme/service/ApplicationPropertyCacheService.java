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

import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jetbrains.annotations.NotNull;

import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.COVID_ZERTIFIKAT_ENABLED;
import static ch.dvbern.oss.vacme.entities.base.ApplicationPropertyKey.GLOBAL_NO_FREIE_TERMINE;

/**
 * This is an in-memory cache that should cache the value of the GLOBAL_NO_FREIE_TERMINE ApplicationProperty for fast
 * reads from Memory
 */
@ApplicationScoped
@Slf4j
public class ApplicationPropertyCacheService {

	public static final String DEFAULT_CACHE_TIME = "60";

	@ConfigProperty(name = "vacme.cache.no.freietermine.ttl.sconds", defaultValue = DEFAULT_CACHE_TIME)
	protected String noFreietermineCacheTimeToLive;

	@ConfigProperty(name = "vacme.cache.zertifikat.enabled.ttl.sconds", defaultValue = DEFAULT_CACHE_TIME)
	protected String zertifikatEnabledCacheTimeToLive;

	private final ApplicationPropertyService applicationPropertyService;

	private LoadingCache<ApplicationPropertyKey, Boolean> noTerminCache;
	private LoadingCache<ApplicationPropertyKey, Boolean> zertifikatEnabledCache;

	@Inject
	public ApplicationPropertyCacheService(ApplicationPropertyService applicationPropertyService) {
		this.applicationPropertyService = applicationPropertyService;

	}

	@PostConstruct
	void init() {
		// do init in postConstruct so all injected configs are ready to be used

		// Loader function
		CacheLoader<ApplicationPropertyKey, Boolean> loader;
		loader = new CacheLoader<>() {
			@Override
			public Boolean load(@NotNull ApplicationPropertyKey key) throws Exception {
				switch (key) {
				case GLOBAL_NO_FREIE_TERMINE:
					return applicationPropertyService.noFreieTermin();
				case COVID_ZERTIFIKAT_ENABLED:
					return applicationPropertyService.isZertifikatEnabled();
				default:
					throw new UnsupportedOperationException("Currently only " +
						GLOBAL_NO_FREIE_TERMINE + " and " + COVID_ZERTIFIKAT_ENABLED
						+ " is a supported cached ApplicationProperty value");
				}
			}

		};

		// cache Settings
		noTerminCache = CacheBuilder.newBuilder()
			.expireAfterWrite(getConfiguredTTL("vacme.cache.no.freietermine.ttl.sconds", noFreietermineCacheTimeToLive), TimeUnit.SECONDS)
			.maximumSize(10)
			.build(loader);
		zertifikatEnabledCache = CacheBuilder.newBuilder()
			.expireAfterWrite(getConfiguredTTL("vacme.cache.zertifikat.enabled.ttl.sconds", zertifikatEnabledCacheTimeToLive), TimeUnit.SECONDS)
			.maximumSize(10)
			.build(loader);
	}

	private long getConfiguredTTL(String configName, String cacheTime) {
		try {
			String cachetime = cacheTime;
			if (cachetime == null) {
				LOG.warn("using default config value for" + configName);
				final Config config = ConfigProvider.getConfig(); // read from static config if not set otherwise,
				// makes testing easier
				cachetime =
					config.getOptionalValue(configName, String.class).orElse(DEFAULT_CACHE_TIME);
			}
			return Long.parseLong(cachetime);
		} catch (NumberFormatException exception) {
			LOG.error("Missconfiguration: " + configName + " must be numeric, using default value "
				+ DEFAULT_CACHE_TIME);
			return Long.parseLong(DEFAULT_CACHE_TIME);
		}
	}

	public boolean noFreieTermin() {
		return noTerminCache.getUnchecked(GLOBAL_NO_FREIE_TERMINE);
	}

	public boolean isZertifikatEnabled() {
		return zertifikatEnabledCache.getUnchecked(COVID_ZERTIFIKAT_ENABLED);
	}
}
