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

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossier;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.QImpfdossier;
import ch.dvbern.oss.vacme.entities.impfen.QImpfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.QImpfschutz;
import ch.dvbern.oss.vacme.entities.impfen.QImpfung;
import ch.dvbern.oss.vacme.entities.registration.Prioritaet;
import ch.dvbern.oss.vacme.entities.registration.QKkkNummerAlt;
import ch.dvbern.oss.vacme.entities.registration.QRegistrierung;
import ch.dvbern.oss.vacme.entities.registration.QRegistrierungSnapshot;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungSnapshot;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impfslot;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfungTyp;
import ch.dvbern.oss.vacme.entities.terminbuchung.QImpfslot;
import ch.dvbern.oss.vacme.entities.terminbuchung.QImpftermin;
import ch.dvbern.oss.vacme.jax.registration.PersonalienSucheJax;
import ch.dvbern.oss.vacme.service.HashIdService;
import ch.dvbern.oss.vacme.smartdb.Db;
import ch.dvbern.oss.vacme.smartdb.SmartJPAQuery;
import com.querydsl.core.types.Projections;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

import static ch.dvbern.oss.vacme.entities.impfen.QImpfdossier.impfdossier;
import static ch.dvbern.oss.vacme.entities.impfen.QImpfdossiereintrag.impfdossiereintrag;
import static ch.dvbern.oss.vacme.entities.impfen.QImpfung.impfung;
import static ch.dvbern.oss.vacme.entities.registration.QRegistrierung.registrierung;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ABGESCHLOSSEN;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.AUTOMATISCH_ABGESCHLOSSEN;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.FREIGEGEBEN_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.GEBUCHT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.GEBUCHT_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMMUNISIERT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_1_DURCHGEFUEHRT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_2_DURCHGEFUEHRT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_2_KONTROLLIERT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.KONTROLLIERT_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ODI_GEWAEHLT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ODI_GEWAEHLT_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungsEingang.DATA_MIGRATION;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungsEingang.MASSENUPLOAD;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungsEingang.ORT_DER_IMPFUNG;
import static ch.dvbern.oss.vacme.entities.terminbuchung.QImpfslot.impfslot;
import static ch.dvbern.oss.vacme.entities.terminbuchung.QImpftermin.impftermin;
import static ch.dvbern.oss.vacme.entities.terminbuchung.QOrtDerImpfung.ortDerImpfung;

@RequestScoped
@Transactional
@Slf4j
public class RegistrierungRepo {

	private final Db db;
	private final EntityManager em;
	private final HashIdService hashIdService;

	@Inject
	public RegistrierungRepo(Db db, EntityManager em, HashIdService hashIdService) {
		this.db = db;
		this.em = em;
		this.hashIdService = hashIdService;
	}

	public void create(@NonNull Registrierung registrierung) {
		db.persist(registrierung);
		db.flush();
	}

	@NonNull
	public Optional<Registrierung> getByRegistrierungnummer(@NonNull String registrierungsnummer) {
		var result = db.selectFrom(QRegistrierung.registrierung)
			.where(QRegistrierung.registrierung.registrierungsnummer.eq(registrierungsnummer))
			.fetchOne();
		return result;
	}

	@NonNull
	public Optional<Registrierung> getByUserId(UUID userid) {
		var result = db.selectFrom(QRegistrierung.registrierung)
			.where(QRegistrierung.registrierung.benutzerId.eq(userid))
			.fetchOne();
		return result;
	}

	@NonNull
	public Optional<Registrierung> getRegistrierungForGrundimpftermin(@NonNull Impftermin termin) {
		Validate.isTrue(!Impffolge.BOOSTER_IMPFUNG.equals(termin.getImpffolge()),
			"Die Funktion getRegistrierungForGrundimpftermin darf nur fuer Impffolge1/2 verwendet werden. "
				+ "Fuer Boostertermine getRegistrierungForBoosterImpftermin verwenden");
		final Optional<Registrierung> registrierungOptional = db.select(QRegistrierung.registrierung)
			.from(QRegistrierung.registrierung)
			.where(QRegistrierung.registrierung.impftermin1.eq(termin)
				.or(QRegistrierung.registrierung.impftermin2.eq(termin))).fetchOne();
		return registrierungOptional;
	}

	@NonNull
	public Optional<Registrierung> getRegistrierungForBoosterImpftermin(@NonNull Impftermin termin) {
		Validate.isTrue(Impffolge.BOOSTER_IMPFUNG.equals(termin.getImpffolge()),
			"Die Funktion getRegistrierungForBoosterImpftermin darf nur fuer Boostertermine verwendet werden. "
				+ "Fuer Impfolge 1/2 getRegistrierungForGrundimpftermin verwenden");

		final Optional<Registrierung> registrierungOptional =
			db.select(QRegistrierung.registrierung)
				.from(QRegistrierung.registrierung)
				.innerJoin(impfdossier).on(registrierung.eq(QImpfdossier.impfdossier.registrierung))
				.innerJoin(impfdossiereintrag).on(impfdossier.eq(impfdossiereintrag.impfdossier))
				.where(impfdossiereintrag.impftermin.eq(termin)).fetchOne();
		return registrierungOptional;
	}

	@NonNull
	public List<Registrierung> getPendenteByImpfslot(@NonNull Impfslot slot) {
		List<Registrierung> list = new LinkedList<>();
		list.addAll(this.getPendente1ByImpfslot(slot));
		list.addAll(this.getPendente2ByImpfslot(slot));
		list.addAll(this.getPendenteNByImpfslot(slot));
		return list;
	}

	@NonNull
	public List<Registrierung> getPendente1ByImpfslot(@NonNull Impfslot slot) {
		QImpftermin aliasTermin1 = new QImpftermin("termin1");
		QImpfslot aliasSlot1 = new QImpfslot("slot1");
		QImpfung aliasImpfung1 = new QImpfung("impfung1");
		return db
			.select(registrierung)
			.from(registrierung)
			.innerJoin(registrierung.impftermin1, aliasTermin1)
			.innerJoin(aliasTermin1.impfslot, aliasSlot1)
			.leftJoin(aliasImpfung1).on(aliasImpfung1.termin.eq(aliasTermin1))
			.where(aliasSlot1.eq(slot).and(aliasImpfung1.isNull()))
			.fetch();
	}

	@NonNull
	public List<Registrierung> getPendente2ByImpfslot(@NonNull Impfslot slot) {
		QImpftermin aliasTermin2 = new QImpftermin("termin2");
		QImpfslot aliasSlot2 = new QImpfslot("slot2");
		QImpfung aliasImpfung2 = new QImpfung("impfung2");
		return db
			.select(registrierung)
			.from(registrierung)
			.innerJoin(registrierung.impftermin2, aliasTermin2)
			.innerJoin(aliasTermin2.impfslot, aliasSlot2)
			.leftJoin(aliasImpfung2).on(aliasImpfung2.termin.eq(aliasTermin2))
			.where(aliasSlot2.eq(slot).and(aliasImpfung2.isNull()))
			.fetch();
	}

	@NonNull
	public List<Registrierung> getPendenteNByImpfslot(@NonNull Impfslot slot) {
		QImpftermin termin = new QImpftermin("termin");
		QImpfslot impfslot = new QImpfslot("impfslot");
		QImpfung impfung = new QImpfung("impfung");
		return db
			.select(registrierung)
			.from(registrierung)
			.innerJoin(QImpfdossier.impfdossier).on(QImpfdossier.impfdossier.registrierung.eq(registrierung))
			.innerJoin(QImpfdossiereintrag.impfdossiereintrag).on(QImpfdossiereintrag.impfdossiereintrag.impfdossier.eq(QImpfdossier.impfdossier))
			.innerJoin(QImpfdossiereintrag.impfdossiereintrag.impftermin, termin)
			.innerJoin(impfslot).on(impfslot.id.eq(termin.impfslot.id))
			.leftJoin(impfung).on(impfung.termin.id.eq(termin.id)) // Impfung nur falls vorhanden
			.where(impfslot.eq(slot).and(impfung.isNull()))
			.fetch();
	}

	@NonNull
	@Transactional(Transactional.TxType.SUPPORTS)
	/* use transaction if there is one in the context (keep in mind sequences can't be rolled back)
	otherwise run without */
	public String getNextRegistrierungnummer() {
		BigInteger nextValue =
			(BigInteger) em.createNativeQuery("SELECT NEXT VALUE FOR register_sequence;").getSingleResult();
		return hashIdService.getHashFromNumber(nextValue.longValue());
	}

	public void update(@NonNull Registrierung registrierung) {
		db.merge(registrierung);
		db.flush();
	}

	// TODO Performance testen. Falls es haeufig zu langsam geht, sollte man vielleicht die beiden Varianten separat machen
	// (aktuelle Nummer versus archivierte)
	@NonNull
	public List<Registrierung> searchRegistrierungByKvKNummer(@NonNull String kvkNummer) {
		var kkkNummerAltAlias = QKkkNummerAlt.kkkNummerAlt;
		return db.selectFrom(registrierung)
			.groupBy(registrierung.id)
			.leftJoin(kkkNummerAltAlias).on(kkkNummerAltAlias.registrierung.eq(registrierung))
			.where(registrierung.krankenkasseKartenNr.eq(kvkNummer)
			.or(kkkNummerAltAlias.nummer.eq(kvkNummer)))
			.fetch();
	}

	@NonNull
	public List<PersonalienSucheJax> findRegistrierungByGeburtsdatum(@NonNull LocalDate geburtsdatum) {
		return db.select(Projections.constructor(
			PersonalienSucheJax.class,
			registrierung.id,
			registrierung.name,
			registrierung.vorname))
			.from(registrierung)
			.where(registrierung.geburtsdatum.eq(geburtsdatum)
				.and(registrierung.registrierungStatus.in(
					ODI_GEWAEHLT, GEBUCHT,
					ABGESCHLOSSEN, ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG, AUTOMATISCH_ABGESCHLOSSEN,
					IMPFUNG_2_DURCHGEFUEHRT, IMPFUNG_2_KONTROLLIERT,
					IMPFUNG_1_DURCHGEFUEHRT, IMMUNISIERT,
					FREIGEGEBEN_BOOSTER,
					ODI_GEWAEHLT_BOOSTER,
					GEBUCHT_BOOSTER,
					KONTROLLIERT_BOOSTER)))
			.fetch();
	}

	@NonNull
	public List<PersonalienSucheJax> findRegistrierungByGeburtsdatumGeimpft(@NonNull LocalDate geburtsdatum) {
		return db.select(Projections.constructor(
				PersonalienSucheJax.class,
				registrierung.id,
				registrierung.name,
				registrierung.vorname))
			.from(registrierung)
			.where(registrierung.geburtsdatum.eq(geburtsdatum)
				.and(registrierung.registrierungStatus.in(
					ABGESCHLOSSEN, ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG, AUTOMATISCH_ABGESCHLOSSEN,
					IMPFUNG_2_DURCHGEFUEHRT, IMPFUNG_2_KONTROLLIERT,
					IMPFUNG_1_DURCHGEFUEHRT, IMMUNISIERT,
					FREIGEGEBEN_BOOSTER,
					ODI_GEWAEHLT_BOOSTER,
					GEBUCHT_BOOSTER,
					KONTROLLIERT_BOOSTER)))
			.fetch();
	}

	public long getAnzahlRegistrierungen() {
		return db.selectFrom(QRegistrierung.registrierung).fetchCount();
	}

	@NonNull
	public Optional<Registrierung> getById(@NonNull ID<Registrierung> id) {
		return db.get(id);
	}

	public long getAnzahlErstimpfungen(@NonNull OrtDerImpfungTyp typ, @NonNull LocalDate von, @NonNull LocalDate bis) {
		var anzahl = db
			.selectFrom(registrierung)
			.innerJoin(registrierung.impftermin1, impftermin)
			.innerJoin(impftermin.impfslot, impfslot)
			.innerJoin(impfslot.ortDerImpfung, ortDerImpfung)
			.innerJoin(impfung).on(impftermin.eq(impfung.termin))
			.where(ortDerImpfung.typ.eq(typ)
				.and(registrierung.registrierungsEingang.eq(DATA_MIGRATION).not()) // Migration wird nicht mitgezaehlt
				.and(impfung.timestampErstellt.between(von.atStartOfDay(), bis.plusDays(1).atStartOfDay()))
				.and(impftermin.impffolge.eq(Impffolge.ERSTE_IMPFUNG)))
			.fetchCount();
		return anzahl;
	}

	public long getAnzahlBoosterOrGrundimunisierungGT3(@NonNull OrtDerImpfungTyp typ, @NonNull LocalDate von, @NonNull LocalDate bis) {
		var anzahl = db
			.selectFrom(impfung)
			.innerJoin(impfung.termin, impftermin)
			.innerJoin(impftermin.impfslot, impfslot)
			.innerJoin(impfslot.ortDerImpfung, ortDerImpfung)
			.innerJoin(impfdossiereintrag).on(impfdossiereintrag.impftermin.eq(impftermin))
			.where(ortDerImpfung.typ.eq(typ)
				.and(impfung.timestampErstellt.between(von.atStartOfDay(), bis.plusDays(1).atStartOfDay()))
				.and(impfung.grundimmunisierung.isFalse().or(impfdossiereintrag.impffolgeNr.gt(3)))
				.and(impftermin.impffolge.eq(Impffolge.BOOSTER_IMPFUNG)))
			.fetchCount();

		return anzahl;
	}

	public long getAnzahlBoosterOhneErstimpfungOderBoosterImKalenderjahr(
		OrtDerImpfungTyp typ,
		LocalDate von,
		LocalDate bis) {
		// Das "aktuelle Kalenderjahr" muss dasjenige des Reportzeitraums sein
		LocalDate firstDayOfYear = von.with(TemporalAdjusters.firstDayOfYear());

		return db
			.selectFrom(impfung)
			.innerJoin(impfung.termin, impftermin)
			.innerJoin(impftermin.impfslot, impfslot)
			.innerJoin(impfslot.ortDerImpfung, ortDerImpfung)
			.innerJoin(impfdossiereintrag).on(impfdossiereintrag.impftermin.eq(impftermin))
			.innerJoin(impfdossiereintrag.impfdossier, impfdossier)
			.innerJoin(impfdossier.registrierung, registrierung)
			.where(ortDerImpfung.typ.eq(typ)
				.and(impfung.timestampErstellt.between(von.atStartOfDay(), bis.plusDays(1).atStartOfDay()))
				.and(impfung.grundimmunisierung.isFalse().or(impfdossiereintrag.impffolgeNr.gt(3)))
				.and(impftermin.impffolge.eq(Impffolge.BOOSTER_IMPFUNG))
				.and(registrierung.notIn(getErstimpfungen(firstDayOfYear, bis).asSubQuery()))
				.and(impfdossier.notIn(getImpfdossierMitMehrerenBooster(firstDayOfYear, bis).asSubQuery())))
			.fetchCount();
	}

	private SmartJPAQuery<Registrierung> getErstimpfungen(
		@NotNull LocalDate von,
		@NotNull LocalDate bis) {
		return db
			.selectFrom(registrierung)
			.innerJoin(registrierung.impftermin1, impftermin)
			.innerJoin(impftermin.impfslot, impfslot)
			.innerJoin(impfung).on(impftermin.eq(impfung.termin))
			.where(registrierung.registrierungsEingang.eq(DATA_MIGRATION).not() // Migration wird nicht mitgezaehlt
				.and(impfung.timestampErstellt.between(von.atStartOfDay(), bis.plusDays(1).atStartOfDay()))
				.and(impftermin.impffolge.eq(Impffolge.ERSTE_IMPFUNG)));
	}

	private SmartJPAQuery<Impfdossier> getImpfdossierMitMehrerenBooster(LocalDate von, LocalDate bis) {
		return db
			.select(impfdossier)
			.from(impfung)
			.innerJoin(impfung.termin, impftermin)
			.innerJoin(impftermin.impfslot, impfslot)
			.innerJoin(impfdossiereintrag).on(impfdossiereintrag.impftermin.eq(impftermin))
			.innerJoin(impfdossiereintrag.impfdossier, impfdossier)
			.where(impfung.timestampErstellt.between(von.atStartOfDay(), bis.plusDays(1).atStartOfDay())
				.and(impfung.grundimmunisierung.isFalse().or(impfdossiereintrag.impffolgeNr.gt(3)))
				.and(impftermin.impffolge.eq(Impffolge.BOOSTER_IMPFUNG)))
			.groupBy(impfdossier)
			.having(impfdossier.count().gt(1));
	}

	public Optional<Registrierung> getByExternalId(String externalId) {
		return db.selectFrom(registrierung)
			.where(registrierung.externalId.eq(externalId))
			.fetchOne();
	}

	public void delete(ID<Registrierung> registrierungId) {
		db.remove(registrierungId);
		db.flush();
	}

	/**
	 * Liest alle Registrierungen aus der Datenbank die im Status IMPFUNG_1_DURCHGEFUEHRT sind
	 * und deren erster Termin schon weiter in der Vergangenheit liegt als pastDate
	 * und die entweder gar keinen 2.Termin haben oder deren 2.Termin in der Vergangenheit war (und also wohl nicht
	 * wahrgenommen wurde)
	 *
	 * @param pastDate cutoff date vor dem gesucht wird
	 * @return Liste von Registrierungen
	 */
	public List<Registrierung> getErsteImpfungNoZweiteSince(LocalDateTime pastDate) {
		QImpftermin impftermin1 = new QImpftermin("impftermin1");
		QImpftermin impftermin2 = new QImpftermin("impftermin2");
		QImpfslot impfslot1 = new QImpfslot("impfslot1");
		QImpfslot impfslot2 = new QImpfslot("impfslot2");
		return db.selectFrom(registrierung)
			.innerJoin(registrierung.impftermin1, impftermin1)
			.innerJoin(impftermin1.impfslot, impfslot1)
			.leftJoin(registrierung.impftermin2, impftermin2)
			.leftJoin(impftermin2.impfslot, impfslot2)
			.where(impfslot1.zeitfenster.bis.lt(pastDate)
				.and(registrierung.registrierungStatus.eq(IMPFUNG_1_DURCHGEFUEHRT))
				.and(impftermin2.isNull().or(impfslot2.zeitfenster.bis.lt(LocalDateTime.now()))))
			.fetch();
	}

	public List<String> findRegistrierungenForOnboarding(long limit) {
		// TODO Booster??

		QImpftermin impftermin1 = new QImpftermin("impftermin1");
		QImpfslot impfslot1 = new QImpfslot("impfslot1");
		return db.select(QRegistrierung.registrierung.registrierungsnummer)
			.from(QRegistrierung.registrierung)
			.distinct()
			.innerJoin(registrierung.impftermin1, impftermin1)
			.innerJoin(impftermin1.impfslot, impfslot1)
			.innerJoin(QImpfung.impfung).on(QImpfung.impfung.termin.eq(impftermin1))
			.where(registrierung.registrierungsEingang.in(List.of(ORT_DER_IMPFUNG, MASSENUPLOAD, DATA_MIGRATION))
				.and(registrierung.generateOnboardingLetter.isTrue())
				.and(registrierung.anonymisiert.isFalse())
			)
			.orderBy(impfung.timestampImpfung.asc())
			.limit(limit)
			.fetch();
	}

	/**
	 * Gibt Registrierungsnummern von lebenen Personen zurueck welche vollstaendigen Impfschutz haben
	 *
	 * @param limit maximale Anzahl zurueckgegebener
	 * @return Liste von Registrierungsnummern
	 */
	@NonNull
	public List<String> findRegsWithVollstImpfschutzToMoveToImmunisiert(long limit) {
		return db.select(QRegistrierung.registrierung.registrierungsnummer)
			.from(QRegistrierung.registrierung)
			.where(
				registrierung.vollstaendigerImpfschutz.isTrue()
					.and(registrierung.registrierungStatus.in(ABGESCHLOSSEN, ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG))
					.and(registrierung.verstorben.isFalse().or(registrierung.verstorben.isNull()))) // Feld kann null
			.orderBy(registrierung.timestampZuletztAbgeschlossen.asc())
			.limit(limit)
			.fetch();
	}

	@NonNull
	public List<String> findRegsToMoveToFreigegebenBooster(long limit) {
		return db.select(QRegistrierung.registrierung.registrierungsnummer)
			.from(QImpfschutz.impfschutz)
			.innerJoin(QImpfdossier.impfdossier).on(QImpfdossier.impfdossier.impfschutz.eq(QImpfschutz.impfschutz))
			.innerJoin(registrierung).on(registrierung.eq(QImpfdossier.impfdossier.registrierung))
			.where(registrierung.registrierungStatus.in(IMMUNISIERT)
				.and(QImpfschutz.impfschutz.freigegebenNaechsteImpfungAb.lt(LocalDateTime.now()))
				.and(registrierung.verstorben.isFalse().or(registrierung.verstorben.isNull())))// Feld kann null
			.orderBy(QImpfschutz.impfschutz.freigegebenNaechsteImpfungAb.asc())
			.limit(limit)
			.fetch();
	}

	public void createSnapshot(@NonNull Registrierung registrierung) {
		RegistrierungSnapshot registrierungSnapshot = RegistrierungSnapshot.fromRegistrierung(registrierung);
		db.persist(registrierungSnapshot);
	}

	public void deleteSnapshot(@NonNull Registrierung registrierung) {
		final List<RegistrierungSnapshot> allSnapshots = db.select(QRegistrierungSnapshot.registrierungSnapshot)
			.from(QRegistrierungSnapshot.registrierungSnapshot)
			.where(QRegistrierungSnapshot.registrierungSnapshot.registrierung.eq(registrierung))
			.fetch();
		for (RegistrierungSnapshot snapshot : allSnapshots) {
			db.remove(snapshot);
		}
	}

	@NonNull
	public Optional<LocalDateTime> getLastAbgeschlossenTimestampFromSnapshot(@NonNull ID<Registrierung> id) {
		return Optional.ofNullable(db.select(QRegistrierungSnapshot.registrierungSnapshot.timestampZuletztAbgeschlossen)
			.from(QRegistrierungSnapshot.registrierungSnapshot)
			.where(registrierung.id.eq(id.getId()))
			.orderBy(QRegistrierungSnapshot.registrierungSnapshot.timestampZuletztAbgeschlossen.desc())
			.fetchFirst());
	}

	public List<String> getRegnumsOfGroupWithAgeGreaterOrEq(Prioritaet prioritaetFrom, int age) {
		LocalDate ageBorderDate = LocalDate.now().minusYears(age);
		List<String> foundRegs = db.select(registrierung.registrierungsnummer)
			.from(registrierung)
			.where(registrierung.prioritaet.eq(prioritaetFrom)
				.and(registrierung.geburtsdatum.loe(ageBorderDate))).fetch();
		return foundRegs;
	}
}
