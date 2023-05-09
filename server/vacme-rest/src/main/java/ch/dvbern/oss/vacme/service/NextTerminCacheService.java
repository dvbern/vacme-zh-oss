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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Slf4j
public class NextTerminCacheService {

	public static final String DEFAULT_CACHE_TIME = "60";

	@ConfigProperty(name = "vacme.cache.nextfrei.ttl.sconds", defaultValue = DEFAULT_CACHE_TIME)
	protected String nextfreiCacheTimeToLive;

	private final OrtDerImpfungService ortDerImpfungService;

	private LoadingCache<ID<OrtDerImpfung>, Optional<LocalDateTime>> nextFreeErstimpfungSlotByOdiCache;
	private LoadingCache<ID<OrtDerImpfung>, Optional<LocalDateTime>> nextFreeZweitimpfungSlotByOdiCache;
	private LoadingCache<ID<OrtDerImpfung>, Optional<LocalDateTime>> nextFreeNImpfungSlotByOdiCache;

	@Inject
	public NextTerminCacheService(OrtDerImpfungService ortDerImpfungService) {
		this.ortDerImpfungService = ortDerImpfungService;

	}

	@PostConstruct
	void init(){
		// do init in postConstruct so all injected configs are ready to be used

		// Loader functions
		CacheLoader<ID<OrtDerImpfung>,  Optional<LocalDateTime>> loaderForNextSlotWithFreeT1;
		loaderForNextSlotWithFreeT1 = new CacheLoader<>() {
			@Override
			public  Optional<LocalDateTime> load(@NonNull ID<OrtDerImpfung> ortDerImpfungId) {
				LocalDateTime nextFreierImpftermin = ortDerImpfungService.getNextFreierImpftermin(ortDerImpfungId,
					Impffolge.ERSTE_IMPFUNG, null, false);
				return Optional.ofNullable(nextFreierImpftermin);
			}
		};

		CacheLoader<ID<OrtDerImpfung>, Optional<LocalDateTime>> loaderForNextSlotWithFreeT2;
		loaderForNextSlotWithFreeT2 = new CacheLoader<>() {
			@Override
			public Optional<LocalDateTime> load(ID<OrtDerImpfung> ortDerImpfungId) {
				LocalDateTime nextFreierImpftermin2 = ortDerImpfungService.getNextFreierImpftermin(ortDerImpfungId,
					Impffolge.ZWEITE_IMPFUNG, null, false);
				return Optional.ofNullable(nextFreierImpftermin2);
			}
		};

		CacheLoader<? super ID<OrtDerImpfung>, Optional<LocalDateTime>> loaderForNextSlotWithFreeTerminN;
		loaderForNextSlotWithFreeTerminN = new CacheLoader<>() {
			@Override
			public  Optional<LocalDateTime> load(@NonNull ID<OrtDerImpfung> ortDerImpfungId) {
				LocalDateTime nextFreierImpfterminN = ortDerImpfungService.getNextFreierImpftermin(ortDerImpfungId,
					Impffolge.BOOSTER_IMPFUNG, null, false);
				return Optional.ofNullable(nextFreierImpfterminN);
			}
		};

		// cache Settings
		nextFreeErstimpfungSlotByOdiCache = CacheBuilder.newBuilder()
			.expireAfterWrite(getConfiguredTTL(), TimeUnit.SECONDS)
			.maximumSize(1000)
			.build(loaderForNextSlotWithFreeT1);

		nextFreeZweitimpfungSlotByOdiCache = CacheBuilder.newBuilder()
			.expireAfterWrite(getConfiguredTTL(), TimeUnit.SECONDS)
			.maximumSize(1000)
			.build(loaderForNextSlotWithFreeT2);

		nextFreeNImpfungSlotByOdiCache = CacheBuilder.newBuilder()
			.expireAfterWrite(getConfiguredTTL(), TimeUnit.SECONDS)
			.maximumSize(1000)
			.build(loaderForNextSlotWithFreeTerminN);
	}

	private long getConfiguredTTL() {
		try {
			String cachetime = this.nextfreiCacheTimeToLive;
			if (cachetime == null) {
				LOG.warn("using default config value for vacme.cache.nextfrei.ttl.sconds");
				final Config config = ConfigProvider.getConfig(); // read from static config if not set otherwise, makes testing easier
				cachetime = config.getOptionalValue("vacme.cache.nextfrei.ttl.sconds", String.class).orElse(DEFAULT_CACHE_TIME);
			}
			return Long.parseLong(cachetime);
		} catch (NumberFormatException exception) {
			LOG.error("Missconfiguration: vacme.cache.nextfrei.ttl.sconds must be numeric, using default value " + DEFAULT_CACHE_TIME );
			return Long.parseLong(DEFAULT_CACHE_TIME);
		}
	}

	@Nullable
	public LocalDateTime getNextFreierImpfterminThroughCache(
		@NonNull ID<OrtDerImpfung> ortDerImpfungId,
		@NonNull Impffolge impffolge,
		@Nullable LocalDateTime otherTerminDate,
		boolean limitMaxFutureDate
	) {
		if (impffolge == Impffolge.ERSTE_IMPFUNG && otherTerminDate == null && !limitMaxFutureDate) {
			Optional<LocalDateTime> cachedDate = nextFreeErstimpfungSlotByOdiCache.getUnchecked(ortDerImpfungId);
			return cachedDate.orElse(null);
		}
		if (impffolge == Impffolge.ZWEITE_IMPFUNG && otherTerminDate == null && !limitMaxFutureDate) {
			Optional<LocalDateTime> cachedDate = nextFreeZweitimpfungSlotByOdiCache.getUnchecked(ortDerImpfungId);
			return cachedDate.orElse(null);
		}
		if(impffolge == Impffolge.BOOSTER_IMPFUNG && otherTerminDate == null && !limitMaxFutureDate){
			Optional<LocalDateTime> cachedDate = nextFreeNImpfungSlotByOdiCache.getUnchecked(ortDerImpfungId);
			return cachedDate.orElse(null);
		}
		LOG.warn("Cache can only be used for Termine with no otherTermin and no limit");
		return ortDerImpfungService.getNextFreierImpftermin(ortDerImpfungId, impffolge, otherTerminDate, limitMaxFutureDate);
	}
}
