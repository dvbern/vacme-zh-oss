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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.impfen.QImpfdossier;
import ch.dvbern.oss.vacme.entities.impfen.QImpfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.QImpfung;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.QRegistrierung;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import ch.dvbern.oss.vacme.entities.terminbuchung.QImpfslot;
import ch.dvbern.oss.vacme.entities.terminbuchung.QImpftermin;
import ch.dvbern.oss.vacme.entities.terminbuchung.QOrtDerImpfung;
import ch.dvbern.oss.vacme.reports.reportingOdiImpfungenTerminbuchungen.OdiImpfungenDataRow;
import ch.dvbern.oss.vacme.reports.reportingOdiImpfungenTerminbuchungen.OdiTerminbuchungenDataRow;
import ch.dvbern.oss.vacme.smartdb.Db;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberTemplate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.opentracing.Traced;

import static ch.dvbern.oss.vacme.entities.registration.QRegistrierung.registrierung;

@RequestScoped
@Transactional
@Slf4j
public class OrtDerImpfungRepo {

	public static final int SLOW_THRESHOLD_MS =  15  * 1000;

	private final Db db;
	private final ImpfungRepo impfungRepo;

	@Inject
	public OrtDerImpfungRepo(Db db, ImpfungRepo impfungRepo) {
		this.db = db;
		this.impfungRepo = impfungRepo;
	}

	public void create(@NonNull OrtDerImpfung ortDerImpfung) {
		db.persist(ortDerImpfung);
		db.flush();
	}

	@NonNull
	public OrtDerImpfung update(@NonNull OrtDerImpfung ortDerImpfung) {
		return db.merge(ortDerImpfung);
	}

	@NonNull
	public Optional<OrtDerImpfung> getById(@NonNull ID<OrtDerImpfung> id) {
		return db.get(id);
	}

	@NonNull
	public Optional<OrtDerImpfung> getByOdiIdentifier(@NonNull String odiIdentifier) {
		return db.selectFrom(QOrtDerImpfung.ortDerImpfung)
			.where(QOrtDerImpfung.ortDerImpfung.identifier.eq(odiIdentifier))
			.fetchOne();
	}

	@NonNull
	public Optional<OrtDerImpfung> getByName(@NonNull String name) {
		return db.selectFrom(QOrtDerImpfung.ortDerImpfung)
			.where(QOrtDerImpfung.ortDerImpfung.name.eq(name)).fetchOne();
	}

	@NonNull
	public List<OrtDerImpfung> findAll() {
		return db.findAll(QOrtDerImpfung.ortDerImpfung);
	}

	@NonNull
	public List<OrtDerImpfung> findAllActiveOeffentlich() {
		var result = db.selectFrom(QOrtDerImpfung.ortDerImpfung)
			.where(QOrtDerImpfung.ortDerImpfung.oeffentlich.isTrue()
				.and(QOrtDerImpfung.ortDerImpfung.deaktiviert.isFalse()))
			.fetch();
		return result;
	}

	@NonNull
	public List<UUID> findIdsOfAllAktivOeffentlichWithTerminVerwaltung() {
		var result = db
			.select(QOrtDerImpfung.ortDerImpfung.id)
			.from(QOrtDerImpfung.ortDerImpfung)
			.where(QOrtDerImpfung.ortDerImpfung.oeffentlich.eq(true)
				.and(QOrtDerImpfung.ortDerImpfung.deaktiviert.isFalse())
				.and(QOrtDerImpfung.ortDerImpfung.terminverwaltung.eq(true)))
			.fetch();
		return result;
	}

	@NonNull
	@Traced
	public List<OrtDerImpfung> findAllActivePublicWithFilters() {
		var result = db.selectFrom(QOrtDerImpfung.ortDerImpfung)
			.distinct()
			.leftJoin(QOrtDerImpfung.ortDerImpfung.filters).fetchJoin()
			.where(QOrtDerImpfung.ortDerImpfung.oeffentlich.eq(true)
				.and(QOrtDerImpfung.ortDerImpfung.deaktiviert.isFalse()))
			.fetch();
		return result;
	}

	@NonNull
	@Traced
	public List<OrtDerImpfung> findOdisAvailableForRegistrierung(
		@NonNull Fragebogen fragebogen,
		boolean isBoosterStatus,
		@NonNull Set<UUID> allowedImpfstoffe
	) {
		BooleanBuilder builder = new BooleanBuilder();
		builder.and(QOrtDerImpfung.ortDerImpfung.oeffentlich.eq(true));
		builder.and(QOrtDerImpfung.ortDerImpfung.deaktiviert.isFalse());
		if (isBoosterStatus) {
			builder.and(QOrtDerImpfung.ortDerImpfung.booster.isTrue());
			if (fragebogen.getRegistrierung().isSelbstzahler()) {
				builder.and(QOrtDerImpfung.ortDerImpfung.impfungGegenBezahlung.isTrue());
			}
			builder.and(QOrtDerImpfung.ortDerImpfung.impfstoffs.any().id.in(allowedImpfstoffe));
		}
		if (isRegistrierungBetweenImpfungen(fragebogen.getRegistrierung())) {
			builder.and(QOrtDerImpfung.ortDerImpfung.impfstoffs.any().id.in(allowedImpfstoffe));
		}
		final Predicate predicates = builder.getValue();
		Objects.requireNonNull(predicates);
		var result = db.selectFrom(QOrtDerImpfung.ortDerImpfung)
			.distinct()
			.leftJoin(QOrtDerImpfung.ortDerImpfung.filters).fetchJoin()
			.where(predicates)
			.fetch();
		return result;
	}

	private boolean isRegistrierungBetweenImpfungen(@NonNull Registrierung registrierung) {
		return RegistrierungStatus.isErsteImpfungDoneAndZweitePending().contains(registrierung.getRegistrierungStatus());
	}

	@NonNull
	public List<OrtDerImpfung> getByGLN(@NonNull String glnNummer) {
		return db.selectFrom(QOrtDerImpfung.ortDerImpfung)
			.where(QOrtDerImpfung.ortDerImpfung.glnNummer.eq(glnNummer))
			.orderBy(QOrtDerImpfung.ortDerImpfung.timestampErstellt.asc())
			.fetch();
	}

	@NonNull
	public List<OdiTerminbuchungenDataRow> getOdiTerminbuchungenReport(@NonNull List<UUID> berechtigteOdiList) {
		List<OdiTerminbuchungenDataRow> allImpfungen = new ArrayList<>();

		StopWatch stopwatch1 = StopWatch.createStarted();
		final NumberTemplate<Integer> eins = Expressions.numberTemplate(Integer.class, "1");
		final List<OdiTerminbuchungenDataRow> impfungen1 = db.select(Projections.constructor(
			OdiTerminbuchungenDataRow.class,
			QImpfslot.impfslot,
			QOrtDerImpfung.ortDerImpfung,
			QImpftermin.impftermin,
			registrierung,
			eins))
			.from(QImpftermin.impftermin)
			.innerJoin(registrierung).on(registrierung.impftermin1.eq(QImpftermin.impftermin))
			.innerJoin(QImpfslot.impfslot).on(QImpftermin.impftermin.impfslot.eq(QImpfslot.impfslot))
			.innerJoin(QOrtDerImpfung.ortDerImpfung).on(QImpfslot.impfslot.ortDerImpfung.eq(QOrtDerImpfung.ortDerImpfung))
			.where(QOrtDerImpfung.ortDerImpfung.id.in(berechtigteOdiList)
				.and(QImpfslot.impfslot.zeitfenster.von.gt(LocalDateTime.now())))
			.fetch();

		logIfSlow(stopwatch1, impfungen1.size(), "getOdiTerminbuchungenReport - impfungen1");
		allImpfungen.addAll(impfungen1);

		StopWatch stopwatch2 = StopWatch.createStarted();
		final NumberTemplate<Integer> zwei = Expressions.numberTemplate(Integer.class, "2");

		final List<OdiTerminbuchungenDataRow> impfungen2 = db.select(Projections.constructor(
				OdiTerminbuchungenDataRow.class,
				QImpfslot.impfslot,
				QOrtDerImpfung.ortDerImpfung,
				QImpftermin.impftermin,
				registrierung,
				zwei))
			.from(QImpftermin.impftermin)
			.innerJoin(registrierung).on(registrierung.impftermin2.eq(QImpftermin.impftermin))
			.innerJoin(QImpfslot.impfslot).on(QImpftermin.impftermin.impfslot.eq(QImpfslot.impfslot))
			.innerJoin(QOrtDerImpfung.ortDerImpfung).on(QImpfslot.impfslot.ortDerImpfung.eq(QOrtDerImpfung.ortDerImpfung))
			.where(QOrtDerImpfung.ortDerImpfung.id.in(berechtigteOdiList)
				.and(QImpfslot.impfslot.zeitfenster.von.gt(LocalDateTime.now())))
			.fetch();

		logIfSlow(stopwatch2, impfungen2.size(), "getOdiTerminbuchungenReport - impfungen2");
		allImpfungen.addAll(impfungen2);

		StopWatch stopwatchN = StopWatch.createStarted();

		final List<OdiTerminbuchungenDataRow> impfungenN =  db.select(Projections.constructor(
				OdiTerminbuchungenDataRow.class,
				QImpfslot.impfslot,
				QOrtDerImpfung.ortDerImpfung,
				QImpftermin.impftermin,
				registrierung,
				QImpfdossiereintrag.impfdossiereintrag.impffolgeNr))
			.from(QImpftermin.impftermin)
			.innerJoin(QImpfdossiereintrag.impfdossiereintrag).on(QImpfdossiereintrag.impfdossiereintrag.impftermin.eq(QImpftermin.impftermin))
			.innerJoin(QImpfdossier.impfdossier).on(QImpfdossier.impfdossier.eq(QImpfdossiereintrag.impfdossiereintrag.impfdossier))
			.innerJoin(QRegistrierung.registrierung).on(registrierung.eq(QImpfdossier.impfdossier.registrierung))
			.innerJoin(QImpfslot.impfslot).on(QImpftermin.impftermin.impfslot.eq(QImpfslot.impfslot))
			.innerJoin(QOrtDerImpfung.ortDerImpfung).on(QImpfslot.impfslot.ortDerImpfung.eq(QOrtDerImpfung.ortDerImpfung))
			.distinct()
			.where(QOrtDerImpfung.ortDerImpfung.id.in(berechtigteOdiList)
				.and(QImpfslot.impfslot.zeitfenster.von.gt(LocalDateTime.now())))
			.fetch();

		logIfSlow(stopwatchN, impfungenN.size(), "getOdiTerminbuchungenReport - impfungenN");
		allImpfungen.addAll(impfungenN);

		return allImpfungen;
	}

	@NonNull
	public List<OdiImpfungenDataRow> getOdiImpfungenReport(@NonNull List<UUID> berechtigteOdiList) {
		List<OdiImpfungenDataRow> allImpfungen = new ArrayList<>();

		StopWatch stopwatch1 = StopWatch.createStarted();
		final NumberTemplate<Integer> eins = Expressions.numberTemplate(Integer.class, "1");
		final List<OdiImpfungenDataRow> impfungen1 = db.select(Projections.constructor(
			OdiImpfungenDataRow.class,
				QImpfslot.impfslot,
				QOrtDerImpfung.ortDerImpfung,
				QImpfung.impfung,
				registrierung,
				eins))
			.from(QImpftermin.impftermin)
			.leftJoin(QImpfung.impfung).on(QImpftermin.impftermin.eq(QImpfung.impfung.termin))
			.innerJoin(registrierung).on(registrierung.impftermin1.eq(QImpftermin.impftermin))
			.innerJoin(QImpfslot.impfslot).on(QImpftermin.impftermin.impfslot.eq(QImpfslot.impfslot))
			.innerJoin(QOrtDerImpfung.ortDerImpfung).on(QImpfslot.impfslot.ortDerImpfung.eq(QOrtDerImpfung.ortDerImpfung))
			.where(QOrtDerImpfung.ortDerImpfung.id.in(berechtigteOdiList)
				.and(QOrtDerImpfung.ortDerImpfung.personalisierterImpfReport.isTrue())
				.and(QImpfslot.impfslot.zeitfenster.von.loe(LocalDateTime.now())))
			.fetch();
		logIfSlow(stopwatch1, impfungen1.size(), "getOdiImpfungenReport - impfungen1");
		allImpfungen.addAll(impfungen1);

		StopWatch stopwatch2 = StopWatch.createStarted();
		final NumberTemplate<Integer> zwei = Expressions.numberTemplate(Integer.class, "2");
		final List<OdiImpfungenDataRow> impfungen2 = db.select(Projections.constructor(
				OdiImpfungenDataRow.class,
				QImpfslot.impfslot,
				QOrtDerImpfung.ortDerImpfung,
				QImpfung.impfung,
				registrierung,
				zwei))
			.from(QImpftermin.impftermin)
			.leftJoin(QImpfung.impfung).on(QImpftermin.impftermin.eq(QImpfung.impfung.termin))
			.innerJoin(registrierung).on(registrierung.impftermin2.eq(QImpftermin.impftermin))
			.innerJoin(QImpfslot.impfslot).on(QImpftermin.impftermin.impfslot.eq(QImpfslot.impfslot))
			.innerJoin(QOrtDerImpfung.ortDerImpfung).on(QImpfslot.impfslot.ortDerImpfung.eq(QOrtDerImpfung.ortDerImpfung))
			.where(QOrtDerImpfung.ortDerImpfung.id.in(berechtigteOdiList)
				.and(QOrtDerImpfung.ortDerImpfung.personalisierterImpfReport.isTrue())
				.and(QImpfslot.impfslot.zeitfenster.von.loe(LocalDateTime.now())))
			.fetch();
		logIfSlow(stopwatch2, impfungen2.size(), "getOdiImpfungenReport - impfungen2");
		allImpfungen.addAll(impfungen2);

		StopWatch stopwatch3 = StopWatch.createStarted();
		final List<OdiImpfungenDataRow> impfungenN =  db.select(Projections.constructor(
				OdiImpfungenDataRow.class,
				QImpfslot.impfslot,
				QOrtDerImpfung.ortDerImpfung,
				QImpfung.impfung,
				registrierung,
				QImpfdossiereintrag.impfdossiereintrag.impffolgeNr))
			.from(QImpftermin.impftermin)
			.leftJoin(QImpfung.impfung).on(QImpftermin.impftermin.eq(QImpfung.impfung.termin))
			.innerJoin(QImpfdossiereintrag.impfdossiereintrag).on(QImpfdossiereintrag.impfdossiereintrag.impftermin.eq(QImpftermin.impftermin))
			.innerJoin(QImpfdossier.impfdossier).on(QImpfdossier.impfdossier.eq(QImpfdossiereintrag.impfdossiereintrag.impfdossier))
			.innerJoin(QRegistrierung.registrierung).on(registrierung.eq(QImpfdossier.impfdossier.registrierung))
			.innerJoin(QImpfslot.impfslot).on(QImpftermin.impftermin.impfslot.eq(QImpfslot.impfslot))
			.innerJoin(QOrtDerImpfung.ortDerImpfung).on(QImpfslot.impfslot.ortDerImpfung.eq(QOrtDerImpfung.ortDerImpfung))
			.distinct()
			.where(QOrtDerImpfung.ortDerImpfung.id.in(berechtigteOdiList)
				.and(QOrtDerImpfung.ortDerImpfung.personalisierterImpfReport.isTrue())
				.and(QImpfslot.impfslot.zeitfenster.von.loe(LocalDateTime.now())))
			.fetch();
		logIfSlow(stopwatch3, impfungenN.size(), "getOdiImpfungenReport - impfungenN");
		allImpfungen.addAll(impfungenN);

		return allImpfungen;
	}

	public void delete(ID<OrtDerImpfung> odiId) {
		db.remove(odiId);
		db.flush();
	}

	private void logIfSlow(@NonNull StopWatch stopwatch, int resultCnt, @NonNull String queryname) {
		stopwatch.stop();
		if (stopwatch.getTime(TimeUnit.MILLISECONDS) > SLOW_THRESHOLD_MS) {
			LOG.warn("VACME-REPORTING: Querytime for query '{}' with resultcount {} was {}ms", queryname, resultCnt, stopwatch.getTime(TimeUnit.MILLISECONDS));
		}
	}
}
