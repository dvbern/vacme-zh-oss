/*
 * Copyright (C) 2022 DV Bern AG, Switzerland
 */

package ch.dvbern.oss.vacme.service;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.repo.ImpfdossierRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ImpfdossierService {


	private final ImpfdossierRepo impfdossierRepo;

	@NonNull
	public List<String> findRegsWithoutImpfdossier() {
		return impfdossierRepo.findRegsWithoutImpfdossier();
	}

	public void createImpfdossier(@NonNull Registrierung registrierung) {
		impfdossierRepo.createImpfdossier(registrierung);
	}
}
