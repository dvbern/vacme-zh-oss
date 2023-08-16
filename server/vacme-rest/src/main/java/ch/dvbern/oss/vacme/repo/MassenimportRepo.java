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

import java.util.Optional;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import ch.dvbern.oss.vacme.entities.massenimport.Massenimport;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.smartdb.Db;
import org.checkerframework.checker.nullness.qual.NonNull;

import static ch.dvbern.oss.vacme.entities.massenimport.QMassenimport.massenimport;
import static ch.dvbern.oss.vacme.entities.massenimport.QMassenimportRegistrierung.massenimportRegistrierung;

@RequestScoped
@Transactional
public class MassenimportRepo {

	private final Db db;

	@Inject
	public MassenimportRepo(Db db) {
		this.db = db;
	}

	public Optional<Massenimport> getByRegistrierung(@NonNull Registrierung registrierung) {
		return db.selectFrom(massenimport)
			.innerJoin(massenimport.registrierungen, massenimportRegistrierung)
			.on(massenimportRegistrierung.registrierung.eq(registrierung))
			.fetchOne();
	}

	public void removeRegistrierungFromMassenimport(@NonNull Massenimport massenimport, @NonNull Registrierung registrierung) {
		massenimport.getRegistrierungen().stream()
			.filter(entry -> entry.getRegistrierung().equals(registrierung))
			.forEach(entry -> db.getEntityManager().remove(entry));
		massenimport.getRegistrierungen().removeIf(entry -> entry.getRegistrierung().equals(registrierung));
	}
}
