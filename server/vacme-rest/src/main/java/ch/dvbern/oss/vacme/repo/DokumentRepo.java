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

package ch.dvbern.oss.vacme.repo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.registration.QRegistrierung;
import ch.dvbern.oss.vacme.entities.registration.QRegistrierungFile;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFile;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFileTyp;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungsEingang;
import ch.dvbern.oss.vacme.jax.registration.FileInfoJax;
import ch.dvbern.oss.vacme.smartdb.Db;
import com.querydsl.core.types.Projections;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

@RequestScoped
@Transactional
@Slf4j
public class DokumentRepo {

	private final Db db;

	@Inject
	public DokumentRepo(Db db) {
		this.db = db;
	}

	public void createRegistrierungFile(@NonNull RegistrierungFile registrierungFile) {
		if (Boolean.TRUE.equals(registrierungFile.getRegistrierung().getVerstorben())) {
			registrierungFile.setRegenerated(true);
			LOG.warn("File-Generierung fuer verstorbene Reg {}. Auslieferung unterdrueckt",
				registrierungFile.getRegistrierung().getRegistrierungsnummer());
		}
		db.persist(registrierungFile);
		db.flush();
	}

	@Nullable
	public RegistrierungFile getRegistrierungFile(
		@NonNull Registrierung registrierung,
		@NonNull RegistrierungFileTyp fileTyp) {
		return db.selectFrom(QRegistrierungFile.registrierungFile)
			.where(QRegistrierungFile.registrierungFile.registrierung.eq(registrierung)
				.and(QRegistrierungFile.registrierungFile.fileTyp.eq(fileTyp)))
			.orderBy(QRegistrierungFile.registrierungFile.timestampErstellt.desc())
			.fetchFirst();
	}

	@NonNull
	public List<RegistrierungFile> getRegistrierungFiles(
		@NonNull Registrierung registrierung,
		@NonNull RegistrierungFileTyp fileTyp) {
		return db.selectFrom(QRegistrierungFile.registrierungFile)
			.where(QRegistrierungFile.registrierungFile.registrierung.eq(registrierung)
				.and(QRegistrierungFile.registrierungFile.fileTyp.eq(fileTyp)))
			.orderBy(QRegistrierungFile.registrierungFile.timestampErstellt.desc())
			.fetch();
	}

	@NonNull
	public List<FileInfoJax> getUploadedFilesInfo(@NonNull Registrierung registrierung) {
		return db.selectFrom(QRegistrierungFile.registrierungFile)
			.select(Projections.constructor(
				FileInfoJax.class,
				QRegistrierungFile.registrierungFile.id,
				QRegistrierungFile.registrierungFile.fileBlob.fileName,
				QRegistrierungFile.registrierungFile.fileBlob.fileSize)
			)
			.where(QRegistrierungFile.registrierungFile.registrierung.eq(registrierung)
				.and(QRegistrierungFile.registrierungFile.fileTyp.eq(RegistrierungFileTyp.IMPFFREIGABE_DURCH_HAUSARZT)))
			.fetch();
	}

	public void deleteRegistrierungFile(@NonNull ID<RegistrierungFile> id) {
		db.remove(id);
	}

	public Optional<RegistrierungFile> getDokument(UUID fileId) {
		return db.get(RegistrierungFile.toId(fileId));
	}

	public long getAnzahlCallcenterDokumente(@NonNull RegistrierungFileTyp typ, @NonNull LocalDate erstelltAm) {
		return getAnzahlCallcenterDokumenteVonBis(typ, erstelltAm.atStartOfDay(), erstelltAm.plusDays(1).atStartOfDay());
	}

	public long getAnzahlCallcenterDokumenteVonBis(@NotNull RegistrierungFileTyp typ, @NotNull LocalDateTime von, @NotNull LocalDateTime bis) {
		QRegistrierung aliasRegistrierung = new QRegistrierung("registrierung");
		return db.select(QRegistrierungFile.registrierungFile)
			.from(QRegistrierungFile.registrierungFile)
			.innerJoin(QRegistrierungFile.registrierungFile.registrierung, aliasRegistrierung)
			.where(QRegistrierungFile.registrierungFile.fileTyp.eq(typ)
				.and(aliasRegistrierung.registrierungsEingang.eq(RegistrierungsEingang.CALLCENTER_REGISTRATION))
				.and(QRegistrierungFile.registrierungFile.timestampErstellt.between(von, bis)))
			.fetchCount();
	}

	public void deleteAllRegistrierungFilesForReg(@NonNull Registrierung registrierung) {
		long result = db.delete(QRegistrierungFile.registrierungFile)
			.where(QRegistrierungFile.registrierungFile.registrierung.eq(registrierung))
			.execute();
		LOG.info("{} RegistrierungFiles geloescht", result);
	}
}
