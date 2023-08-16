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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.entities.zertifikat.Zertifikat;
import ch.dvbern.oss.vacme.jax.tracing.TracingRegistrierungJax;
import ch.dvbern.oss.vacme.jax.tracing.TracingResponseJax;
import ch.dvbern.oss.vacme.repo.TracingRepo;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ABGESCHLOSSEN;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.AUTOMATISCH_ABGESCHLOSSEN;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.FREIGEGEBEN_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.GEBUCHT_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMMUNISIERT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_1_DURCHGEFUEHRT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_2_DURCHGEFUEHRT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_2_KONTROLLIERT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.KONTROLLIERT_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ODI_GEWAEHLT_BOOSTER;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class TracingService {

	private static final Set<RegistrierungStatus> TRACING_RELEVANT_STATUS = Set.of( // Es muss mindestens einmal geimpft worden, sonst ist es fuer Tracing nicht relevant
		IMPFUNG_1_DURCHGEFUEHRT,
		IMPFUNG_2_KONTROLLIERT,
		IMPFUNG_2_DURCHGEFUEHRT,
		ABGESCHLOSSEN,
		AUTOMATISCH_ABGESCHLOSSEN,
		ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG,
		IMMUNISIERT,
		FREIGEGEBEN_BOOSTER,
		ODI_GEWAEHLT_BOOSTER,
		GEBUCHT_BOOSTER,
		KONTROLLIERT_BOOSTER

	);

	private final TracingRepo tracingRepo;
	private final ZertifikatService zertifikatService;
	private final ImpfinformationenService impfinformationenService;


	public TracingResponseJax findByRegistrierungsnummer(String registrierungsnummer) {
		List<Registrierung> registrierungList = tracingRepo.getByRegistrierungnummerAndStatus(registrierungsnummer, TRACING_RELEVANT_STATUS)
			.map(Collections::singletonList)
			.orElseGet(Collections::emptyList);
		return buildTracingResponseJax(mapTracingRegistrierungJaxList(registrierungList));
	}

	public TracingResponseJax findByCertificatUVCI(String uvci) {
		List<Registrierung> registrierungList = tracingRepo.getByZertifikatUVCIAndStatus(uvci, TRACING_RELEVANT_STATUS)
			.map(Collections::singletonList)
			.orElseGet(Collections::emptyList);
		return buildTracingResponseJax(mapTracingRegistrierungJaxList(registrierungList));
	}

	public TracingResponseJax findByKrankenkassennummer(String krankenkassennummer) {
		List<Registrierung> registrierungList = tracingRepo.getByKrankenkassennummerAndStatus(krankenkassennummer, TRACING_RELEVANT_STATUS);
		return buildTracingResponseJax(mapTracingRegistrierungJaxList(registrierungList));
	}

	@NotNull
	private List<TracingRegistrierungJax> mapTracingRegistrierungJaxList(List<Registrierung> registrierungList) {
		return registrierungList.stream()
			.map(registrierung -> {
				ImpfinformationDto impfinfos =
					impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
				if (!impfinformationenService.hasVacmeImpfungen(impfinfos)) {
					return null; // es werden nur solche ueber die Schnittstelle returned die eine Impfung haben bei
					// uns haben
				}
				Impfung impfung1 = impfinfos.getImpfung1();
				Impfung impfung2 = impfinfos.getImpfung2();
				List<Impfung> boosterImpfungen = impfinfos.getBoosterImpfungen();

				Zertifikat zertifikat = zertifikatService.getNewestNonRevokedZertifikat(registrierung).orElse(null);
				return TracingRegistrierungJax.from(registrierung, impfung1, impfung2, boosterImpfungen, zertifikat);
			})
			.filter(Objects::nonNull) // null kommt zurueck wenn es bei uns keine Impfung gibt
			.collect(Collectors.toList());
	}


	@NotNull
	private TracingResponseJax buildTracingResponseJax(List<TracingRegistrierungJax> registrierungJaxList) {
		TracingResponseJax responseJax = new TracingResponseJax();
		responseJax.setRegistrierungen(registrierungJaxList);
		return responseJax;
	}
}
