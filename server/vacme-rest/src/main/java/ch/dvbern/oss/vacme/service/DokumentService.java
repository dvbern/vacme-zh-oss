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
import java.util.Objects;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFile;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFileTyp;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.jax.registration.FileInfoJax;
import ch.dvbern.oss.vacme.repo.DokumentRepo;
import ch.dvbern.oss.vacme.repo.ImpfungRepo;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.util.EnumUtil;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.util.VacmeFileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DokumentService {

	private final DokumentRepo dokumentRepo;
	private final ImpfungRepo impfungRepo;
	private final PdfService pdfService;
	private final ImpfinformationenService impfinformationenService;


	public @NonNull RegistrierungFile createAndSave(@NonNull RegistrierungFile registrierungFile) {
		dokumentRepo.createRegistrierungFile(registrierungFile);
		return registrierungFile;
	}

	public RegistrierungFile createAndSave(
		byte[] content, @NonNull RegistrierungFileTyp typ,
		@NonNull Registrierung registrierung) {
		final RegistrierungFile registrierungFile = VacmeFileUtil.createRegistrierungFile(typ, registrierung, content);
		dokumentRepo.createRegistrierungFile(registrierungFile);
		return registrierungFile;
	}

	public void createAndSaveImpfdokumentationPdfIfNeeded(@NonNull ImpfinformationDto impfinformationDto) {
		if (impfinformationenService.hasVacmeImpfungen(impfinformationDto)) {
			createAndSaveImpfdokumentationPdf(impfinformationDto);
		}
	}

	public void createAndSaveImpfdokumentationPdf(@NonNull ImpfinformationDto impfinformationDto) {
		// Ein Impfdok-Pdf ohne VacMe-Impfung macht nicht so viel Sinn
		if (!impfinformationenService.hasVacmeImpfungen(impfinformationDto)) {
			throw AppValidationMessage.NO_VACME_IMPFUNG.create();
		}
		final byte[] content = pdfService.createImpfdokumentation(
			impfinformationDto.getRegistrierung(),
			impfinformationDto.getImpfung1(),
			impfinformationDto.getImpfung2(),
			impfinformationDto.getBoosterImpfungen());
		final RegistrierungFile registrierungFile = VacmeFileUtil.createRegistrierungFile(
			RegistrierungFileTyp.IMPF_DOKUMENTATION, impfinformationDto.getRegistrierung(), content);
		dokumentRepo.createRegistrierungFile(registrierungFile);
	}

	public void createAndSaveImpfdokumentationWithoutBoosterImpfungenPdf(@NonNull Registrierung registrierung) {
		// Die erste Impfung muss zu diesem Zeitpunkt zwingend erfolgt sein
		Objects.requireNonNull(registrierung.getImpftermin1());
		final Impfung impfung1 = impfungRepo.getByImpftermin(registrierung.getImpftermin1())
			.orElseThrow(() -> AppValidationMessage.UNKNOWN_IMPFTERMIN.create());

		// Die zweite Impfung ist optional
		Impfung impfung2 = null;
		if (registrierung.getImpftermin2() != null) {
			impfung2 = impfungRepo.getByImpftermin(registrierung.getImpftermin2()).orElse(null);
		}
		final byte[] content = pdfService.createImpfdokumentation(registrierung, impfung1, impfung2, null);
		final RegistrierungFile registrierungFile = VacmeFileUtil.createRegistrierungFile(
			RegistrierungFileTyp.IMPF_DOKUMENTATION, registrierung, content);
		dokumentRepo.createRegistrierungFile(registrierungFile);
	}

	public byte[] getOrCreateImpfdokumentationPdf(@NonNull Registrierung registrierung) {
		final RegistrierungFile registrierungFile =
			dokumentRepo.getRegistrierungFile(registrierung, RegistrierungFileTyp.IMPF_DOKUMENTATION);
		if (registrierungFile != null) {
			return registrierungFile.getContent();
		}
		if (EnumUtil.isOneOf(
			registrierung.getRegistrierungStatus(),
			RegistrierungStatus.IMPFUNG_1_DURCHGEFUEHRT,
			RegistrierungStatus.IMPFUNG_2_KONTROLLIERT,
			RegistrierungStatus.IMPFUNG_2_DURCHGEFUEHRT,
			RegistrierungStatus.ABGESCHLOSSEN,
			RegistrierungStatus.ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG,
			RegistrierungStatus.AUTOMATISCH_ABGESCHLOSSEN)
		) {
			// Das File sollte eigentlich da sein. Wir generieren es neu
			createAndSaveImpfdokumentationWithoutBoosterImpfungenPdf(registrierung);
			final RegistrierungFile registrierungFileCreated = dokumentRepo
				.getRegistrierungFile(registrierung, RegistrierungFileTyp.IMPF_DOKUMENTATION);
			if (registrierungFileCreated != null) {
				return registrierungFileCreated.getContent();
			}
		} else if (RegistrierungStatus.getAnyStatusOfGrundimmunisiert().contains(registrierung.getRegistrierungStatus())) {
			// In diesem Fall koennen bereits Booster Impfungen vorhanden sein. Diese Lesen und PDF neu erstellen
			final ImpfinformationDto impfinformationen =
				impfinformationenService.getImpfinformationenAndEnsureStatusIsAktuell(registrierung.getRegistrierungsnummer());
			createAndSaveImpfdokumentationPdf(impfinformationen);
			final RegistrierungFile registrierungFileCreated = dokumentRepo
				.getRegistrierungFile(registrierung, RegistrierungFileTyp.IMPF_DOKUMENTATION);
			if (registrierungFileCreated != null) {
				return registrierungFileCreated.getContent();
			}
		}
		throw AppValidationMessage.REGISTRIERUNG_DOKUMENTSTATUS_FALSCH.create(registrierung.getRegistrierungsnummer());
	}

	public void deleteImpfdokumentationPdf(@NonNull Registrierung registrierung) {
		final List<RegistrierungFile> registrierungFiles =
			dokumentRepo.getRegistrierungFiles(registrierung, RegistrierungFileTyp.IMPF_DOKUMENTATION);
		for (RegistrierungFile registrierungFile : registrierungFiles) {
			dokumentRepo.deleteRegistrierungFile(RegistrierungFile.toId(registrierungFile.getId()));
		}
	}

	public List<FileInfoJax> getUploadedDocInfos(@NonNull Registrierung registrierung) {
		return dokumentRepo.getUploadedFilesInfo(registrierung);
	}

	public RegistrierungFile getDokument(Registrierung registrierung, UUID fileId) {
		RegistrierungFile file = dokumentRepo.getDokument(fileId)
			.orElseThrow(() -> AppFailureException.entityNotFound(RegistrierungFile.class, fileId.toString()));
		if (!file.getRegistrierung().equals(registrierung)) {
			LOG.error(
				"File mit id {} gehoert nicht zu reg mit id {}",
				fileId.toString(),
				registrierung.getId().toString());
			throw AppValidationMessage.NOT_ALLOWED.create();
		}
		return file;
	}

	public void deleteRegistrierungbestaetigung(@NonNull Registrierung registrierung) {
		final RegistrierungFile registrierungFile =
			dokumentRepo.getRegistrierungFile(registrierung, RegistrierungFileTyp.REGISTRIERUNG_BESTAETIGUNG);
		if (registrierungFile != null) {
			dokumentRepo.deleteRegistrierungFile(registrierungFile.toId());
		}
	}

	public void deleteTerminbestaetigung(@NonNull Registrierung registrierung) {
		final List<RegistrierungFile> files =
			dokumentRepo.getRegistrierungFiles(registrierung, RegistrierungFileTyp.TERMIN_BESTAETIGUNG);
		for (RegistrierungFile file : files) {
			dokumentRepo.deleteRegistrierungFile(file.toId());
		}
	}

	public void deleteImpfdokumentation(@NonNull Registrierung registrierung) {
		final List<RegistrierungFile> files = dokumentRepo.getRegistrierungFiles(registrierung, RegistrierungFileTyp.IMPF_DOKUMENTATION);
		for (RegistrierungFile file : files) {
			dokumentRepo.deleteRegistrierungFile(file.toId());
		}
	}

}
