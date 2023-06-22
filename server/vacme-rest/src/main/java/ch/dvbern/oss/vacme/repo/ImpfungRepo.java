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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.Query;
import javax.transaction.Transactional;

import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.externeimpfinfos.QExternesZertifikat;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossier;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.impfen.QImpfdossier;
import ch.dvbern.oss.vacme.entities.impfen.QImpfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.QImpfung;
import ch.dvbern.oss.vacme.entities.registration.Prioritaet;
import ch.dvbern.oss.vacme.entities.registration.QFragebogen;
import ch.dvbern.oss.vacme.entities.registration.QRegistrierung;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.terminbuchung.QImpftermin;
import ch.dvbern.oss.vacme.jax.vmdl.VMDLUploadJax;
import ch.dvbern.oss.vacme.reports.zweitBoosterMail.ZweitBoosterMailDataRow;
import ch.dvbern.oss.vacme.smartdb.Db;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.SubQueryExpression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.opentracing.Traced;
import org.jetbrains.annotations.NotNull;

import static ch.dvbern.oss.vacme.entities.registration.QRegistrierung.registrierung;

@RequestScoped
@Transactional
@Slf4j
public class ImpfungRepo {

	public static final int SLOW_THRESHOLD_MS =  15  * 1000;
	private final Db db;

	@Inject
	public ImpfungRepo(Db db) {
		this.db = db;
	}

	public void create(Impfung impfung) {
		db.persist(impfung);
		db.flush();
	}

	public void update(@NonNull Impfung impfung) {
		db.merge(impfung);
		db.flush();
	}

	public void delete(@NonNull ID<Impfung> id) {
		db.remove(id);
		db.flush();
	}

	public Optional<Impfung> getById(ID<Impfung> id) {
		return db.get(id);
	}

	@Traced
	@NonNull
	public Optional<Impfung> getByImpftermin(@NonNull Impftermin termin) {
		final Optional<Impfung> registrierungOptional = db.select(QImpfung.impfung)
			.from(QImpfung.impfung)
			.where(QImpfung.impfung.termin.eq(termin)).fetchOne();
		return registrierungOptional;
	}

	@NonNull
	public List<VMDLUploadJax> getVMDLPendenteImpfungen3Queries(int uploadChunkLimit, String reportingUnitID) {
		final Expression<String> reportingUnitIDExpression = Expressions.constant(reportingUnitID);
		BooleanExpression joinExpressionT1 = QImpftermin.impftermin.eq(registrierung.impftermin1);
		List<VMDLUploadJax> impfungen1 = runVMDLQueryRegistrierungstermine(uploadChunkLimit, reportingUnitIDExpression, joinExpressionT1, "vmdl_t1_impfungen");
		List<VMDLUploadJax> result = new ArrayList<>(impfungen1);

		int remainingChunkLimit = uploadChunkLimit - impfungen1.size();

		if (remainingChunkLimit > 0) {
			BooleanExpression joinExpressionT2 = QImpftermin.impftermin.eq(registrierung.impftermin2);
			List<VMDLUploadJax> impfungen2 = runVMDLQueryRegistrierungstermine(remainingChunkLimit, reportingUnitIDExpression, joinExpressionT2, "vmdl_t2_impfungen");
			result.addAll(impfungen2);
			remainingChunkLimit = remainingChunkLimit - impfungen2.size();
		}

		if (remainingChunkLimit > 0) {
			List<VMDLUploadJax> impfungenN = runDossierImpfungenVMDLQuery(reportingUnitIDExpression, remainingChunkLimit);
			result.addAll(impfungenN);
		}

		return result;
	}



	@NonNull
	public List<VMDLUploadJax> getVMDLPendenteImpfungen2Queries(int uploadChunkLimit, String reportingUnitID) {
		final Expression<String> reportingUnitIDExpression = Expressions.constant(reportingUnitID);
		BooleanExpression joinExpressionT1T2 = QImpftermin.impftermin.eq(QRegistrierung.registrierung.impftermin1)
			.or(QImpftermin.impftermin.eq(QRegistrierung.registrierung.impftermin2));

		List<VMDLUploadJax> impfungen1Or2 = runVMDLQueryRegistrierungstermine(uploadChunkLimit, reportingUnitIDExpression, joinExpressionT1T2, "vmdl_t1_t2_impfungen");

		List<VMDLUploadJax> result = new ArrayList<>(impfungen1Or2);
		long remainingChunkLimit = uploadChunkLimit - result.size();

		if (remainingChunkLimit > 0) {
			List<VMDLUploadJax> impfungenN = runDossierImpfungenVMDLQuery(reportingUnitIDExpression, remainingChunkLimit);
			result.addAll(impfungenN);
		}

		return result;
	}

	@NotNull
	private List<VMDLUploadJax> runVMDLQueryRegistrierungstermine(
		int limit,
		Expression<String> reportingUnitIDExpression,
		Predicate regjoinfExpression,
		String queryName
	) {
		StopWatch stopwatch = StopWatch.createStarted();

		List<VMDLUploadJax> impfungen =
			db.select(
				Projections.constructor(VMDLUploadJax.class,
					QImpfung.impfung,
					QRegistrierung.registrierung,
					QFragebogen.fragebogen,
					reportingUnitIDExpression)
			)
				.from(QImpfung.impfung)
				.innerJoin(QImpftermin.impftermin).on(QImpftermin.impftermin.eq(QImpfung.impfung.termin))
				.innerJoin(QRegistrierung.registrierung)
				.on(regjoinfExpression)
				.leftJoin(QFragebogen.fragebogen).on(registrierung.eq(QFragebogen.fragebogen.registrierung))
				.on(QRegistrierung.registrierung.eq(QFragebogen.fragebogen.registrierung))
				.where(QImpfung.impfung.timestampVMDL.isNull()
					.and(QImpfung.impfung.extern.isFalse())
				)
				.limit(limit)
				.fetch();

		logIfSlow(stopwatch, impfungen.size(), queryName);
		return impfungen;
	}

	@NotNull
	private List<VMDLUploadJax> runDossierImpfungenVMDLQuery(Expression<String> reportingUnitIDExpression,
		long remainingChunkLimit) {
		StopWatch stopwatchQ2 = StopWatch.createStarted();
		List<VMDLUploadJax> impfungenN = db.select(
			Projections.constructor(VMDLUploadJax.class,
				QImpfung.impfung,
				registrierung,
				QFragebogen.fragebogen,
				QImpfdossiereintrag.impfdossiereintrag,
				reportingUnitIDExpression))
			.from(QImpfung.impfung)
			.limit(remainingChunkLimit)
			.innerJoin(QImpftermin.impftermin).on(QImpftermin.impftermin.eq(QImpfung.impfung.termin))
			.innerJoin(QImpfdossiereintrag.impfdossiereintrag).on(QImpfdossiereintrag.impfdossiereintrag.impftermin.eq(QImpftermin.impftermin))
			.innerJoin(QImpfdossier.impfdossier).on(QImpfdossier.impfdossier.eq(QImpfdossiereintrag.impfdossiereintrag.impfdossier))
			.innerJoin(registrierung)
			.on(QImpfdossier.impfdossier.registrierung.eq(registrierung))
			.innerJoin(QFragebogen.fragebogen).on(registrierung.eq(QFragebogen.fragebogen.registrierung))
			.where(QImpfung.impfung.timestampVMDL.isNull()
				.and(QImpfung.impfung.extern.isFalse())
			)
			.fetch();
		logIfSlow(stopwatchQ2, impfungenN.size(), "vmdl_dossiereintragimpfungen");
		return impfungenN;
	}


	private void logIfSlow(StopWatch stopwatch, int resultCnt, String queryname) {
		stopwatch.stop();
		if (stopwatch.getTime(TimeUnit.MILLISECONDS) > SLOW_THRESHOLD_MS) {
			LOG.warn("VACME-VMDL: Querytime for query '{}' with resultcount {} was {}ms", queryname, resultCnt, stopwatch.getTime(TimeUnit.MILLISECONDS));

		}
	}

	@NonNull
	public Optional<ImpfinformationDto> getImpfinformationenOptional(@NonNull String registrierungsNummer) {
		QImpftermin aliasTermin1 = new QImpftermin("termin1");
		QImpftermin aliasTermin2 = new QImpftermin("termin2");
		QImpfung aliasImpfung1 = new QImpfung("impfung1");
		QImpfung aliasImpfung2 = new QImpfung("impfung2");
		QImpfdossier dossier = QImpfdossier.impfdossier;
		QExternesZertifikat externesZertifikat = new QExternesZertifikat("externesZertifikat");
		Optional<ImpfinformationDto> optional = db
			.select(Projections.constructor(ImpfinformationDto.class, registrierung, aliasImpfung1, aliasImpfung2, dossier, externesZertifikat))
			.from(registrierung)
			.leftJoin(registrierung.impftermin1, aliasTermin1)
			.leftJoin(registrierung.impftermin2, aliasTermin2)
			.leftJoin(aliasImpfung1).on(aliasImpfung1.termin.eq(aliasTermin1))
			.leftJoin(aliasImpfung2).on(aliasImpfung2.termin.eq(aliasTermin2))
			.leftJoin(dossier).on(dossier.registrierung.eq(registrierung))
			.leftJoin(externesZertifikat).on(externesZertifikat.registrierung.eq(registrierung))
			.where(registrierung.registrierungsnummer.eq(registrierungsNummer))
			.fetchOne();

		return optional.map(impfinformationen -> {
			List<Impfung> boosterImpfungen = getBoosterImpfungen(registrierungsNummer);
			return new ImpfinformationDto(impfinformationen, boosterImpfungen);
		});
	}

	@NonNull
	public List<Impfung> getBoosterImpfungen(@NonNull String registrierungsNummer) {
		return db.selectFrom(QImpfung.impfung)
			.innerJoin(QImpftermin.impftermin)
			.on(QImpfung.impfung.termin.eq(QImpftermin.impftermin))
			.innerJoin(QImpfdossiereintrag.impfdossiereintrag)
			.on(QImpfdossiereintrag.impfdossiereintrag.impftermin.eq(QImpftermin.impftermin))
			.innerJoin(QImpfdossier.impfdossier)
			.on(QImpfdossiereintrag.impfdossiereintrag.impfdossier.eq(QImpfdossier.impfdossier))
			.innerJoin(registrierung)
			.on(QImpfdossier.impfdossier.registrierung.eq(registrierung))
			.where(registrierung.registrierungsnummer.eq(registrierungsNummer))
			.orderBy(QImpfdossiereintrag.impfdossiereintrag.impffolgeNr.asc())
			.fetch();
	}

	/**
	 * Prueft ob diese Impfung jemals an VMDL gesendet wurde. Dabei wird auch die Autit Tabelle durchsucht
	 *
	 * @return true wenn timestempVMDL dieser Impfung mal gesetzt war
	 */
	public boolean wasSentToVMDL(Impfung impfung) {
		// current timestampVMDL is not null
		if (impfung.getTimestampVMDL() != null) {
			return true;
		}
		// Otherwise search in the audit table
		String query = "SELECT REV FROM Impfung_AUD WHERE id = ?1 and timestampVMDL is not null;";
		Query nativeQuery = db.getEntityManager().createNativeQuery(query);
		nativeQuery.setParameter(1, impfung.getId().toString());
		return nativeQuery.getResultList().size() > 0;
	}

	/**
	 * List die zu einer Impfung gehoerenden Impfinformationen aus. Dabei spielt es keine Rolle ob
	 * es sich um eine Impfung 1 /2 oder N handelt
	 */
	@NonNull
	public Optional<ImpfinformationDto> getImpfinformationenOptional(@NonNull ID<Impfung> impfungId) {
		Optional<Registrierung> registrierungForImpfung = getRegistrierungForImpfung(impfungId);
		return registrierungForImpfung.flatMap(value -> getImpfinformationenOptional(value.getRegistrierungsnummer()));
	}

	@NonNull
	public Optional<Registrierung> getRegistrierungForImpfung(@NonNull ID<Impfung> impfungId) {
		BooleanExpression joinExpressionT1 = QImpftermin.impftermin.eq(registrierung.impftermin1);
		Optional<Registrierung> regOpt = runQueryReadRegistrierungForGrundImpfung(impfungId,  joinExpressionT1, "reg_t1_impfungen");

		Optional<Registrierung> result =
			regOpt.or(() -> {
				BooleanExpression joinExpressionT2 = QImpftermin.impftermin.eq(registrierung.impftermin2);
				return runQueryReadRegistrierungForGrundImpfung(impfungId, joinExpressionT2, "reg_t2_impfungen");
			}).or(() -> runQueryReadRegistrierungForNImpfung(impfungId));
		return result;
	}

	@NonNull
	private Optional<Registrierung> runQueryReadRegistrierungForGrundImpfung(@NonNull ID<Impfung> impfungId, @NonNull Predicate regjoinfExpression, @NonNull String queryName) {
		StopWatch stopwatch = StopWatch.createStarted();
		Optional<Registrierung> registrierung = db.select(QRegistrierung.registrierung)
			.from(QImpfung.impfung)
			.innerJoin(QImpftermin.impftermin).on(QImpftermin.impftermin.eq(QImpfung.impfung.termin))
			.innerJoin(QRegistrierung.registrierung).on(regjoinfExpression)
			.where(QImpfung.impfung.id.eq(impfungId.getId()))
			.fetchOne();

		logIfSlow(stopwatch, registrierung.isPresent() ? 1 : 0 , queryName);
		return registrierung;
	}

	@NonNull
	private Optional<Registrierung> runQueryReadRegistrierungForNImpfung(@NonNull ID<Impfung> impfungId) {
		StopWatch stopwatch = StopWatch.createStarted();
		Optional<Registrierung> regOpt =
			db.select(registrierung)
			.from(QImpfung.impfung)
			.innerJoin(QImpftermin.impftermin).on(QImpftermin.impftermin.eq(QImpfung.impfung.termin))
			.innerJoin(QImpfdossiereintrag.impfdossiereintrag).on(QImpfdossiereintrag.impfdossiereintrag.impftermin.eq(QImpftermin.impftermin))
			.innerJoin(QImpfdossier.impfdossier).on(QImpfdossier.impfdossier.eq(QImpfdossiereintrag.impfdossiereintrag.impfdossier))
			.innerJoin(registrierung)
			.on(QImpfdossier.impfdossier.registrierung.eq(registrierung))
			.where(QImpfung.impfung.id.eq(impfungId.getId()))
			.fetchOne();
		logIfSlow(stopwatch, regOpt.isPresent() ? 1 : 0 , "reg_N_impfungen");
		return regOpt;
	}

	@NonNull
	public Map<Prioritaet, Long> getCountAllErstImpfungenPerPrioritaet() {
		StopWatch stopWatch = StopWatch.createStarted();
		Map<Prioritaet, Long> erstImpfungen = new EnumMap<>(Prioritaet.class);
		final List<Tuple> tuples = db
			.select(registrierung.prioritaet, QImpfung.impfung.count())
			.from(QImpfung.impfung)
			.innerJoin(QImpfung.impfung.termin, QImpftermin.impftermin)
			.innerJoin(registrierung).on(registrierung.impftermin1.eq(QImpftermin.impftermin))
			.groupBy(registrierung.prioritaet)
			.fetch();
		for (Tuple tuple : tuples) {
			erstImpfungen.put(tuple.get(0, Prioritaet.class), tuple.get(1, Long.class));
		}
		logIfSlow(stopWatch, erstImpfungen.size(), "getCountAllErstImpfungenPerPrioritaet");
		return erstImpfungen;
	}

	@NonNull
	public Map<Prioritaet, Long> getCountAllZweitImpfungenPerPrioritaet() {
		StopWatch stopWatch = StopWatch.createStarted();
		Map<Prioritaet, Long> zweitImpfungen = new EnumMap<>(Prioritaet.class);
		final List<Tuple> tuples = db
			.select(registrierung.prioritaet, QImpfung.impfung.count())
			.from(QImpfung.impfung)
			.innerJoin(QImpfung.impfung.termin, QImpftermin.impftermin)
			.innerJoin(registrierung).on(registrierung.impftermin2.eq(QImpftermin.impftermin))
			.groupBy(registrierung.prioritaet)
			.fetch();
		for (Tuple tuple : tuples) {
			zweitImpfungen.put(tuple.get(0, Prioritaet.class), tuple.get(1, Long.class));
		}
		logIfSlow(stopWatch, zweitImpfungen.size(), "getCountAllZweitImpfungenPerPrioritaet");
		return zweitImpfungen;
	}

	@NonNull
	public Map<Prioritaet, Long> getCountAllErstBoosterPerPrioritaet() {
		StopWatch stopWatch = StopWatch.createStarted();
		Map<Prioritaet, Long> erstBooster = new EnumMap<>(Prioritaet.class);
		final List<Tuple> tuples = db
			.select(registrierung.prioritaet, QImpfdossier.impfdossier.countDistinct())
			.from(QImpfung.impfung)
			.innerJoin(QImpfung.impfung.termin, QImpftermin.impftermin)
			.innerJoin(QImpfdossiereintrag.impfdossiereintrag)
			.on(QImpfdossiereintrag.impfdossiereintrag.impftermin.eq(QImpftermin.impftermin))
			.innerJoin(QImpfdossier.impfdossier)
			.on(QImpfdossiereintrag.impfdossiereintrag.impfdossier.eq(QImpfdossier.impfdossier))
			.innerJoin(registrierung)
			.on(registrierung.eq(QImpfdossier.impfdossier.registrierung))
			.where(QImpfung.impfung.grundimmunisierung.isFalse()
				.or(QImpfdossiereintrag.impfdossiereintrag.impffolgeNr.gt(3)))
			.groupBy(registrierung.prioritaet)
			.fetch();
		for (Tuple tuple : tuples) {
			erstBooster.put(tuple.get(0, Prioritaet.class), tuple.get(1, Long.class));
		}
		logIfSlow(stopWatch, erstBooster.size(), "getCountAllErstBoosterPerPrioritaet");
		return erstBooster;
	}

	/**
	 * Mit Abfrage der min. impffolgeNr. Ist aber (zumindest momentan noch) weniger performant
	 */
	public Map<Prioritaet, Long> getCountAllErstBoosterPerPrioritaetV2() {
		QImpfdossiereintrag aliasDossiereintragMain = new QImpfdossiereintrag("eintrag_main");
		QImpfdossier aliasDossierMain = new QImpfdossier("dossier_main");
		StopWatch stopWatch = StopWatch.createStarted();
		Map<Prioritaet, Long> erstBooster = new EnumMap<>(Prioritaet.class);
		final List<Tuple> tuples = db
			.select(registrierung.prioritaet, QImpfung.impfung.count())
			.from(QImpfung.impfung)
			.innerJoin(QImpfung.impfung.termin, QImpftermin.impftermin)
			.innerJoin(aliasDossiereintragMain)
			.on(aliasDossiereintragMain.impftermin.eq(QImpftermin.impftermin))
			.innerJoin(aliasDossierMain)
			.on(aliasDossiereintragMain.impfdossier.eq(aliasDossierMain))
			.innerJoin(registrierung)
			.on(registrierung.eq(aliasDossierMain.registrierung))
			.where(aliasDossiereintragMain.impffolgeNr.eq(getMinimalBoosterImpffolgeNrQuery(aliasDossierMain))
				.and(QImpfung.impfung.grundimmunisierung.isFalse())
			)
			.groupBy(registrierung.prioritaet)
			.fetch();
		for (Tuple tuple : tuples) {
			erstBooster.put(tuple.get(0, Prioritaet.class), tuple.get(1, Long.class));
		}
		logIfSlow(stopWatch, erstBooster.size(), "getCountAllErstBoosterPerPrioritaet");
		return erstBooster;
	}

	private SubQueryExpression<Integer> getMinimalBoosterImpffolgeNrQuery(Expression<Impfdossier> outerDossier) {
		QImpfdossiereintrag aliasDossiereintraqSub = new QImpfdossiereintrag("eintrag_subquery");
		QImpfdossier aliasDossierSub = new QImpfdossier("dossier_subquery");
		return db.select(aliasDossiereintraqSub.impffolgeNr.min())
			.from(aliasDossiereintraqSub)
			.innerJoin(aliasDossierSub)
			.on(aliasDossiereintraqSub.impfdossier.eq(aliasDossierSub))
			.innerJoin(QImpftermin.impftermin)
			.on(aliasDossiereintraqSub.impftermin.eq(QImpftermin.impftermin))
			.innerJoin(QImpfung.impfung)
			.on(QImpftermin.impftermin.eq(QImpfung.impfung.termin))
			.where(aliasDossierSub.eq(outerDossier)
				.and(QImpfung.impfung.grundimmunisierung.isFalse()))
			.asSubQuery();
	}

	@NonNull
	public List<ZweitBoosterMailDataRow> getAllZweitOderMehrBooster() {
		StopWatch stopWatch = StopWatch.createStarted();

		final int maxImpffolgeNr = getMaxImpffolgeNr();

		final List<ZweitBoosterMailDataRow> allImpfungen = new ArrayList<>();
		for (int i = 2; i < maxImpffolgeNr; i++) {
			StopWatch stopWatch2 = StopWatch.createStarted();
			List<ZweitBoosterMailDataRow> impfungen = getAllNBooster(i);
			logIfSlow(stopWatch2, impfungen.size(), "zweit_booster_mit_impffolgeNr " + (i + 1));
			allImpfungen.addAll(impfungen);
		}

		logIfSlow(stopWatch, allImpfungen.size(), "all_zweit_oder_mehr_booster");

		return allImpfungen;
	}

	/**
	 * Mit Abfrage der min. impffolgeNr. Ist aber (zumindest momentan noch) weniger performant
	 * Aendert sich evtl. wenn bei der anderen Variante noch mehr Iterationen faellig werden
	 */
	@NonNull
	public List<ZweitBoosterMailDataRow> getAllZweitOderMehrBoosterV2() {
		QImpfdossiereintrag aliasDossiereintragMain = new QImpfdossiereintrag("eintrag_main");
		QImpfdossier aliasDossierMain = new QImpfdossier("dossier_main");
		StopWatch stopWatch = StopWatch.createStarted();
		final List<ZweitBoosterMailDataRow> dataRows = db.select(Projections.constructor(
				ZweitBoosterMailDataRow.class,
				QImpfung.impfung.selbstzahlende,
				registrierung.geburtsdatum,
				QFragebogen.fragebogen.immunsupprimiert,
				registrierung.prioritaet
			))
			.from(QImpfung.impfung)
			.innerJoin(QImpfung.impfung.termin, QImpftermin.impftermin)
			.innerJoin(aliasDossiereintragMain).on(aliasDossiereintragMain.impftermin.eq(QImpftermin.impftermin))
			.innerJoin(aliasDossierMain).on(aliasDossiereintragMain.impfdossier.eq(aliasDossierMain))
			.innerJoin(registrierung).on(registrierung.eq(aliasDossierMain.registrierung))
			.innerJoin(QFragebogen.fragebogen).on(registrierung.eq(QFragebogen.fragebogen.registrierung))
			.where(aliasDossiereintragMain.impffolgeNr.ne(getMinimalBoosterImpffolgeNrQuery(aliasDossierMain))
				.and(QImpfung.impfung.grundimmunisierung.isFalse())
			)
			.fetch();
		logIfSlow(stopWatch, dataRows.size(), "getAllZweitOderMehrBooster");
		return dataRows;
	}

	private int getMaxImpffolgeNr() {
		return db.select(QImpfdossiereintrag.impfdossiereintrag.impffolgeNr.max())
			.from(QImpfdossiereintrag.impfdossiereintrag)
			.fetchFirst();
	}

	private List<ZweitBoosterMailDataRow> getAllNBooster(int impffolgeNr) {
		QImpfdossiereintrag aliasImpfdossiereintrag1 = new QImpfdossiereintrag("impfdossiereintrag1");
		QImpfdossiereintrag aliasImpfdossiereintrag2 = new QImpfdossiereintrag("impfdossiereintrag2");
		QImpfung aliasImpfung1 = new QImpfung("impfung1");
		QImpfung aliasImpfung2 = new QImpfung("impfung2");
		QImpftermin aliasImpftermin1 = new QImpftermin("impftermin1");
		QImpftermin aliasImpftermin2 = new QImpftermin("impftermin2");

		return db.select(Projections.constructor(
				ZweitBoosterMailDataRow.class,
				aliasImpfung2.selbstzahlende,
				registrierung.geburtsdatum,
				QFragebogen.fragebogen.immunsupprimiert,
				registrierung.prioritaet
			))
			.from(registrierung)
			.innerJoin(QImpfdossier.impfdossier).on(QImpfdossier.impfdossier.registrierung.eq(registrierung))
			.innerJoin(QFragebogen.fragebogen).on(QFragebogen.fragebogen.registrierung.eq(registrierung))

			.innerJoin(aliasImpfdossiereintrag1).on(aliasImpfdossiereintrag1.impfdossier.eq(QImpfdossier.impfdossier)
				.and(aliasImpfdossiereintrag1.impffolgeNr.eq(impffolgeNr)))
			.innerJoin(aliasImpfdossiereintrag1.impftermin, aliasImpftermin1)
			.innerJoin(aliasImpfung1).on(aliasImpfung1.termin.eq(aliasImpftermin1))

			.innerJoin(aliasImpfdossiereintrag2).on(aliasImpfdossiereintrag2.impfdossier.eq(QImpfdossier.impfdossier)
				.and(aliasImpfdossiereintrag2.impffolgeNr.eq(impffolgeNr + 1)))
			.innerJoin(aliasImpfdossiereintrag2.impftermin, aliasImpftermin2)
			.innerJoin(aliasImpfung2).on(aliasImpfung2.termin.eq(aliasImpftermin2))

			.where(aliasImpfung1.grundimmunisierung.isFalse()
				.and(aliasImpfung2.grundimmunisierung.isFalse())
				.and(aliasImpftermin1.impffolge.eq(Impffolge.BOOSTER_IMPFUNG))
				.and(aliasImpftermin2.impffolge.eq(Impffolge.BOOSTER_IMPFUNG))
			)
			.fetch();
	}
}
