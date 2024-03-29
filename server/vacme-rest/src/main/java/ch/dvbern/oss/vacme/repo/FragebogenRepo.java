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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.print.archivierung.ArchivierungDataRow;
import ch.dvbern.oss.vacme.smartdb.Db;
import com.querydsl.core.types.Projections;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;

import static ch.dvbern.oss.vacme.entities.registration.QFragebogen.fragebogen;
import static ch.dvbern.oss.vacme.entities.registration.QRegistrierung.registrierung;

@RequestScoped
@Transactional
@Slf4j
public class FragebogenRepo {

	private final Db db;

	@Inject
	public FragebogenRepo(Db db) {
		this.db = db;
	}

	public void create(@NonNull Fragebogen fragebogen) {
		db.persist(fragebogen);
		db.flush();
	}

	public void update(@NonNull Fragebogen fragebogen) {
		db.merge(fragebogen);
		db.flush();
	}

	public void delete(@NonNull ID<Fragebogen> fragebogenId) {
		db.remove(fragebogenId);
		db.flush();
	}

	@NonNull
	public Optional<Fragebogen> getById(@NonNull ID<Fragebogen> id) {
		return db.get(id);
	}

	@NonNull
	public Optional<Fragebogen> getByRegistrierung(@NonNull Registrierung registrierung) {
		final Optional<Fragebogen> registrierungOptional = db.select(fragebogen)
			.from(fragebogen)
			.where(fragebogen.registrierung.eq(registrierung)).fetchOne();
		return registrierungOptional;
	}

	@NonNull
	public List<Fragebogen> findAll() {
		return db.findAll(fragebogen);
	}

	/**
	 * Gibt eine Liste von DTOs mit den Daten der Regs zurueck die seit mehr als einem Monat unveraendert abgeschlossen sind
	 */
	public List<ArchivierungDataRow> getAbgeschlossenNotArchiviertDataOlderThan(LocalDateTime dateTime) {
		return db.select(Projections.constructor(ArchivierungDataRow.class, fragebogen))
			.from(fragebogen)
			.join(fragebogen.registrierung, registrierung)
			.where(registrierung.registrierungStatus.in(
					RegistrierungStatus.ABGESCHLOSSEN,
					RegistrierungStatus.ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG,
					RegistrierungStatus.AUTOMATISCH_ABGESCHLOSSEN,
					RegistrierungStatus.FREIGEGEBEN_BOOSTER,
					RegistrierungStatus.IMMUNISIERT,
					RegistrierungStatus.ODI_GEWAEHLT_BOOSTER,
					RegistrierungStatus.GEBUCHT_BOOSTER,
					RegistrierungStatus.KONTROLLIERT_BOOSTER
				)
				.and(registrierung.timestampZuletztAbgeschlossen.lt(dateTime))
				.and(registrierung.timestampArchiviert.isNull()))
			.fetch();
	}
}
