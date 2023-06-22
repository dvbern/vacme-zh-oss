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

import ch.dvbern.oss.vacme.entities.registration.*;
import ch.dvbern.oss.vacme.entities.zertifikat.QZertifikat;
import ch.dvbern.oss.vacme.smartdb.Db;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.Expressions;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RequestScoped
@Transactional
public class TracingRepo {

	private static final String FORBIDDEN_KKK = "00000000000000000000"; // Filter out EDA und Ausland

	@ConfigProperty(name = "tracing.respect.choice", defaultValue = "true")
	boolean respectTracingChoice;

	private final Db db;

	@Inject
	public TracingRepo(Db db) {
		this.db = db;
	}

	@NonNull
	public Optional<Registrierung> getByRegistrierungnummerAndStatus(String registrierungsnummer, Set<RegistrierungStatus> statusList) {
		var result = db.selectFrom(QRegistrierung.registrierung)
			.where(QRegistrierung.registrierung.registrierungsnummer.eq(registrierungsnummer)
				.and(QRegistrierung.registrierung.registrierungStatus.in(statusList))
				.and(checkTracingChoice()))
			.fetchOne();
		return result;
	}

	@NonNull
	public Optional<Registrierung> getByZertifikatUVCIAndStatus(String uvci, Set<RegistrierungStatus> statusList) {
		var result = db.selectFrom(QRegistrierung.registrierung)
			.innerJoin(QZertifikat.zertifikat)
			.on(QRegistrierung.registrierung.eq(QZertifikat.zertifikat.registrierung))
			.where(QZertifikat.zertifikat.uvci.eq(uvci)
				.and(QRegistrierung.registrierung.registrierungStatus.in(statusList))
				.and(checkTracingChoice()))
			.fetchOne();
		return result;
	}

	public List<Registrierung> getByKrankenkassennummerAndStatus(String krankenkassennummer, Set<RegistrierungStatus> statusList) {
		var result = db.selectFrom(QRegistrierung.registrierung)
			.where(QRegistrierung.registrierung.krankenkasseKartenNr.eq(krankenkassennummer)
				.and(QRegistrierung.registrierung.krankenkasseKartenNr.ne(FORBIDDEN_KKK))
				.and(QRegistrierung.registrierung.registrierungStatus.in(statusList))
				.and(checkTracingChoice()))
			.fetch();
		return result;
	}

	private Predicate checkTracingChoice() {
		if (respectTracingChoice) {
			return QRegistrierung.registrierung.contactTracing.eq(Boolean.TRUE);
		}
		// Expression, die immer TRUE ist
		return Expressions.asBoolean(Expressions.constant(true)).isTrue();
	}
}
