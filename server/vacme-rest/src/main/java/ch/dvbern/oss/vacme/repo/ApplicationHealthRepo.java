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

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.Query;
import javax.persistence.QueryTimeoutException;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.impfen.QImpfdossier;
import ch.dvbern.oss.vacme.entities.impfen.QImpfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.QImpfung;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFile;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impfslot;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.terminbuchung.QImpfslot;
import ch.dvbern.oss.vacme.entities.terminbuchung.QImpftermin;
import ch.dvbern.oss.vacme.entities.types.daterange.DateUtil;
import ch.dvbern.oss.vacme.entities.zertifikat.ZertifikatQueue;
import ch.dvbern.oss.vacme.jax.applicationhealth.RegistrierungTermineImpfungJax;
import ch.dvbern.oss.vacme.jax.applicationhealth.ResultDTO;
import ch.dvbern.oss.vacme.jax.registration.OrtDerImpfungDisplayNameJax;
import ch.dvbern.oss.vacme.service.RegistrierungService;
import ch.dvbern.oss.vacme.service.SettingsService;
import ch.dvbern.oss.vacme.util.DeservesZertifikatValidator;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.smartdb.Db;
import ch.dvbern.oss.vacme.smartdb.SmartJPAQuery;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static ch.dvbern.oss.vacme.entities.registration.QRegistrierung.registrierung;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ABGESCHLOSSEN;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.AUTOMATISCH_ABGESCHLOSSEN;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.FREIGEGEBEN;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.FREIGEGEBEN_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.GEBUCHT_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMMUNISIERT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_1_DURCHGEFUEHRT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_2_DURCHGEFUEHRT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMPFUNG_2_KONTROLLIERT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.KONTROLLIERT_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ODI_GEWAEHLT;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ODI_GEWAEHLT_BOOSTER;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.REGISTRIERT;

@RequestScoped
@Transactional
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ApplicationHealthRepo {

	@ConfigProperty(name = "vacme.stufe", defaultValue = "LOCAL")
	String stufe;

	@ConfigProperty(name = "vacme.healthcheck.query.timeout", defaultValue = "290")
	int dbQueryTimeoutInSeconds;

	private static final String DB_QUERY_TIMEOUT_HINT = "org.hibernate.timeout";

	private static final String NEWLINE = "\r\n";

	private final Db db;
	private final SettingsService settingsService;
	private final ImpfterminRepo impfterminRepo;
	private final ImpfungRepo impfungRepo;
	private final ImpfinformationenService impfinformationenService;



	/**
	 * Sucht Registrierungen, bei welchen die Impfung (timestampImpfung) nicht am Tag des Termins
	 * stattfand.
	 */
	@Transactional(TxType.REQUIRES_NEW)
	@TransactionConfiguration(timeout = 400 ) // Etwas mehr Zeit da wir noch verarbeitung machen
	public ResultDTO getHealthCheckRegistrierungenMitImpfungNichtAmTermindatum() {
		ResultDTO resultDTO = new ResultDTO();
		resultDTO.start("Health-Check Registrierungen mit Impfung nicht am Termindatum");

		try {
			final List<UUID> terminIdList = db.selectFrom(QImpftermin.impftermin)
				.select(QImpftermin.impftermin.id)
				.innerJoin(QImpftermin.impftermin.impfslot)
				.innerJoin(QImpfung.impfung).on(QImpfung.impfung.termin.eq(QImpftermin.impftermin))
				.where(QImpftermin.impftermin.gebucht.isTrue()
					.and(QImpftermin.impftermin.impfslot.zeitfenster.von.dayOfYear()
						.ne(QImpfung.impfung.timestampImpfung.dayOfYear())))
				.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds)
				.fetch();

			if (terminIdList.isEmpty()) {
				resultDTO.finish(true);
			} else {
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
				for (UUID uuid : terminIdList) {
					final Optional<Impftermin> terminOptional = impfterminRepo.getById(Impftermin.toId(uuid));
					if (terminOptional.isPresent()) {
						Impftermin termin = terminOptional.get();
						final Optional<Impfung> impfungOptional = impfungRepo.getByImpftermin(termin);
						if (impfungOptional.isPresent()) {
							Impfung impfung = impfungOptional.get();
							StringBuffer detail = new StringBuffer();
							detail.append("Impfung ")
								.append(impfung.getId())
								.append(" mit timestampImpfung ")
								.append(formatter.format(impfung.getTimestampImpfung()))
								.append(" war nicht am selben Tag wie Termin ")
								.append(termin.getId())
								.append(" mit Slot ")
								.append(termin.getImpfslot().toDateMessage())
								.append(". Impffolge: ")
								.append(termin.getImpffolge());
							resultDTO.addInfo(detail.toString());
						}
					}
				}
				resultDTO.finish(false);
			}
		} catch(QueryTimeoutException ex) {
			handleTimeoutException(resultDTO);
		}
		return resultDTO;
	}

	/**
	 * Sucht Termine, die von einer Registrierung referenziert werden, aber das gebucht-Flag nicht gesetzt haben
	 */
	@TransactionConfiguration(timeout = 300)
	@Transactional(TxType.REQUIRES_NEW)
	public List<RegistrierungTermineImpfungJax> getBesetzteTermineOhneGebuchtFlag() {
		final String jobText = "VACME-HEALTH: Besetzte Termine ohne gebucht-Flag:";
		QImpftermin aliasTermin1 = new QImpftermin("termin1");
		QImpftermin aliasTermin2 = new QImpftermin("termin2");
		QImpftermin aliasTerminN = new QImpftermin("terminN");

		final List<RegistrierungTermineImpfungJax> result = db
			.select(Projections.constructor(RegistrierungTermineImpfungJax.class,
				registrierung,
				aliasTermin1,
				aliasTermin2))
			.from(registrierung)
			.leftJoin(registrierung.impftermin1, aliasTermin1)
			.leftJoin(registrierung.impftermin2, aliasTermin2)
			.leftJoin(QImpfdossier.impfdossier).on(QImpfdossier.impfdossier.registrierung.eq(registrierung))
			.leftJoin(QImpfdossiereintrag.impfdossiereintrag).on(QImpfdossiereintrag.impfdossiereintrag.impfdossier.eq(QImpfdossier.impfdossier))
			.leftJoin(QImpfdossiereintrag.impfdossiereintrag.impftermin, aliasTerminN)
			.where((registrierung.impftermin1.isNotNull().and(aliasTermin1.gebucht.isFalse()))
				.or(registrierung.impftermin2.isNotNull().and(aliasTermin2.gebucht.isFalse()))
				.or(QImpfdossiereintrag.impfdossiereintrag.impftermin.isNotNull().and(aliasTerminN.gebucht.isFalse()))
			)
			.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds)
			.fetch();
		LOG.info(jobText + " Found Termine {}", result.size());
		return result;
	}

	/**
	 * Sucht Termine, die von keiner Registrierung referenziert werden, aber das gebucht-Flag gesetzt haben
	 */
	public List<RegistrierungTermineImpfungJax> getFreieTermineMitGebuchtFlag() {
		final String jobText = "VACME-HEALTH: Freie Termine mit gebucht-Flag:";

		final List<RegistrierungTermineImpfungJax> result = db
			.select(Projections.constructor(
				RegistrierungTermineImpfungJax.class,
				QImpftermin.impftermin))
			.from(QImpftermin.impftermin)
			.where(QImpftermin.impftermin.gebucht.isTrue()
				.and(
					JPAExpressions.selectOne().from(registrierung)
						.where(registrierung.impftermin1.id.eq(QImpftermin.impftermin.id)).notExists()
				)
				.and(
					JPAExpressions.selectOne().from(registrierung)
						.where(registrierung.impftermin2.id.eq(QImpftermin.impftermin.id)).notExists()
				)
				.and(
					JPAExpressions.selectOne().from(QImpfdossiereintrag.impfdossiereintrag)
						.where(QImpfdossiereintrag.impfdossiereintrag.impftermin.id.eq(QImpftermin.impftermin.id)).notExists()
				)
			)
			.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds)
			.fetch();
		LOG.info(jobText + " Found Termine {}", result.size());
		return result;
	}

	/**
	 * Divergenzen gebuchte Termine <-> mit Registrierungen verknuepte Termine
	 */
	public List<RegistrierungTermineImpfungJax> getInkonsistenzenTermine() {
		List<RegistrierungTermineImpfungJax> result = new ArrayList<>();
		result.addAll(getBesetzteTermineOhneGebuchtFlag());
		result.addAll(getFreieTermineMitGebuchtFlag());
		return result;
	}

	/**
	 * Korrigiert Divergenzen gebuchte Termine <-> mit Registrierungen verknuepte Termine
	 */
	public void handleInkonsistenzenTermine() {
		final long anzahlTermin1 = db.update(QImpftermin.impftermin)
			.set(QImpftermin.impftermin.gebucht, true)
			.where(QImpftermin.impftermin.id.in(db
				.select(registrierung.impftermin1.id)
				.from(registrierung)
				.leftJoin(registrierung.impftermin1)
				.asSubQuery())
				.and(QImpftermin.impftermin.gebucht.isFalse()))
			.execute();
		LOG.info("VACME-INFO: {} gebuchte Termine 1 mit fehlendem gebucht-Flag korrigiert", anzahlTermin1);

		final long anzahlTermin2 = db.update(QImpftermin.impftermin)
			.set(QImpftermin.impftermin.gebucht, true)
			.where(QImpftermin.impftermin.id.in(db
				.select(registrierung.impftermin2.id)
				.from(registrierung)
				.leftJoin(registrierung.impftermin2)
				.asSubQuery())
				.and(QImpftermin.impftermin.gebucht.isFalse()))
			.execute();
		LOG.info("VACME-INFO: {} gebuchte Termine 2 mit fehlendem gebucht-Flag korrigiert", anzahlTermin2);

		final long anzahlTerminN = db.update(QImpftermin.impftermin)
			.set(QImpftermin.impftermin.gebucht, true)
			.where(QImpftermin.impftermin.id.in(db
					.select(QImpfdossiereintrag.impfdossiereintrag.impftermin.id)
					.from(QImpfdossiereintrag.impfdossiereintrag)
					.leftJoin(QImpfdossiereintrag.impfdossiereintrag.impftermin)
					.asSubQuery())
				.and(QImpftermin.impftermin.gebucht.isFalse()))
			.execute();
		LOG.info("VACME-INFO: {} gebuchte Booster-Termine mit fehlendem gebucht-Flag korrigiert", anzahlTerminN);

		final long anzahlMitFalschemFlag = db.update(QImpftermin.impftermin)
			.set(QImpftermin.impftermin.gebucht, false)
			.where(QImpftermin.impftermin.id.in(db
				.select(QImpftermin.impftermin.id)
				.from(QImpftermin.impftermin)
				.where(QImpftermin.impftermin.gebucht.isTrue()
					.and(
						JPAExpressions.selectOne().from(registrierung)
							.where(registrierung.impftermin1.id.eq(QImpftermin.impftermin.id)).notExists()
					)
					.and(
						JPAExpressions.selectOne().from(registrierung)
							.where(registrierung.impftermin2.id.eq(QImpftermin.impftermin.id)).notExists()
					)
					.and(
						JPAExpressions.selectOne().from(QImpfdossiereintrag.impfdossiereintrag)
							.where(QImpfdossiereintrag.impfdossiereintrag.impftermin.id.eq(QImpftermin.impftermin.id)).notExists()
					)
				)
				.asSubQuery())
				.and(QImpftermin.impftermin.gebucht.isTrue()))
			.execute();
		LOG.info("VACME-INFO: {} freie Termine mit gesetztem gebucht-Flag korrigiert", anzahlMitFalschemFlag);
	}

	@NonNull
	public List<RegistrierungTermineImpfungJax> getInkonsistenzenStatus() {
		List<RegistrierungTermineImpfungJax> result = new ArrayList<>();
		QImpftermin aliasTermin1 = new QImpftermin("termin1");
		QImpftermin aliasTermin2 = new QImpftermin("termin2");
		QImpfung aliasImpfung1 = new QImpfung("impfung1");
		QImpfung aliasImpfung2 = new QImpfung("impfung2");

		// Zwei gebuchte Termine, aber nicht mind. im Status GEBUCHT
		final List<RegistrierungTermineImpfungJax> zweiGebuchteTermineAberNichtStatusGebucht =
			getQueryRegistrierungLeftJoinTermineLeftJoinImpfungen(aliasTermin1, aliasTermin2, aliasImpfung1, aliasImpfung2)
			.where(aliasTermin1.isNotNull()
				.and(aliasTermin2.isNotNull())
				.and(registrierung.registrierungStatus.in(REGISTRIERT, FREIGEGEBEN, ODI_GEWAEHLT)))
			.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds)
			.fetch();
		LOG.info("VACME-HEALTH: Registrierungen mit zwei gebuchten Terminen, die nicht mind. im Status GEBUCHT sind: {}",
			zweiGebuchteTermineAberNichtStatusGebucht.size());
		result.addAll(zweiGebuchteTermineAberNichtStatusGebucht);

		// Genau eine Impfung durchgefuehrt, aber Status stimmt nicht
		final List<RegistrierungTermineImpfungJax> eineImpfungDurchgefuehrt =
			getQueryRegistrierungLeftJoinTermineLeftJoinImpfungen(aliasTermin1, aliasTermin2, aliasImpfung1, aliasImpfung2)
			.where(aliasImpfung1.isNotNull()
					.and(aliasImpfung2.isNull())
					.and(registrierung.registrierungStatus.notIn(
						IMPFUNG_1_DURCHGEFUEHRT, IMPFUNG_2_KONTROLLIERT, AUTOMATISCH_ABGESCHLOSSEN, ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG)))
				.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds)
				.fetch();
		LOG.info("VACME-HEALTH: Registrierungen mit einer Impfung, die nicht zwischen IMPFUNG_1_DURCHGEFUEHRT und IMPFUNG_2_KONTROLLIERT sind: {}",
			eineImpfungDurchgefuehrt.size());
		result.addAll(eineImpfungDurchgefuehrt);

		// Zwei Imfpungen durchgefuhert, aber Status stimmt nicht
		final List<RegistrierungTermineImpfungJax> zweiImpfungenDurchgefuehrt =
			getQueryRegistrierungLeftJoinTermineLeftJoinImpfungen(aliasTermin1, aliasTermin2, aliasImpfung1, aliasImpfung2)
				.where(aliasImpfung1.isNotNull()
					.and(aliasImpfung2.isNotNull())
					.and(registrierung.registrierungStatus.notIn(IMPFUNG_2_DURCHGEFUEHRT, ABGESCHLOSSEN)))
				.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds)
				.fetch();
		LOG.info("VACME-HEALTH: Registrierungen mit zwei Impfungen, die nicht IMPFUNG_2_DURCHGEFUEHRT oder ABGESCHLOSSEN sind: {}",
			zweiImpfungenDurchgefuehrt.size());
		result.addAll(zweiImpfungenDurchgefuehrt);

		// Abgeschlossen oder mehr aber kein timestampZuletztAbgeschlossen
		final List<RegistrierungTermineImpfungJax> timestampZuletztAbgeschlossenNull =
			getQueryRegistrierungLeftJoinTermineLeftJoinImpfungen(aliasTermin1, aliasTermin2, aliasImpfung1, aliasImpfung2)
				.where(registrierung.registrierungStatus.in(
						ABGESCHLOSSEN, ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG, AUTOMATISCH_ABGESCHLOSSEN,
						IMMUNISIERT, FREIGEGEBEN_BOOSTER, KONTROLLIERT_BOOSTER, ODI_GEWAEHLT_BOOSTER, GEBUCHT_BOOSTER)
					.and(registrierung.timestampZuletztAbgeschlossen.isNull()))
				.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds)
				.fetch();
		LOG.info("VACME-HEALTH: Registrierungen ABGESCHLOSSEN aber ohne timestampZuletztAbgeschlossen eintrag sind: {}",
			timestampZuletztAbgeschlossenNull.size());
		result.addAll(timestampZuletztAbgeschlossenNull);

		return result;
	}

	/**
	 * Gibt alle Registrierungen mit unterschiedlichem gewuenschtemODI vs. TerminODI zurueck
	 */
	public ArrayList<RegistrierungTermineImpfungJax> getUnterschiedlicheOdis() {
		Set<RegistrierungTermineImpfungJax> result = new HashSet<>();
		QImpftermin aliasTermin1 = new QImpftermin("termin1");
		QImpftermin aliasTermin2 = new QImpftermin("termin2");
		QImpfslot aliasSlot1 = new QImpfslot("slot1");
		QImpfslot aliasSlot2 = new QImpfslot("slot2");

		final List<RegistrierungTermineImpfungJax> gewuenschterOdiTermin1 = db
			.select(Projections.constructor(RegistrierungTermineImpfungJax.class,
				registrierung,
				aliasTermin1))
			.from(registrierung)
			.innerJoin(registrierung.impftermin1, aliasTermin1)
			.innerJoin(aliasTermin1.impfslot, aliasSlot1)
			.where(registrierung.gewuenschterOdi.eq(aliasSlot1.ortDerImpfung).not())
			.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds)
			.fetch();
		LOG.info("VACME-HEALTH: Registrierungen unterschiedlichem ODI gewuenscht <-> Termin 1: {}",
			gewuenschterOdiTermin1.size());
		result.addAll(gewuenschterOdiTermin1);

		final List<RegistrierungTermineImpfungJax> gewuenschterOdiTermin2 = db
			.select(Projections.constructor(RegistrierungTermineImpfungJax.class,
				registrierung,
				aliasTermin2))
			.from(registrierung)
			.innerJoin(registrierung.impftermin2, aliasTermin2)
			.innerJoin(aliasTermin2.impfslot, aliasSlot2)
			.where(registrierung.gewuenschterOdi.eq(aliasSlot2.ortDerImpfung).not())
			.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds)
			.fetch();
		LOG.info("VACME-HEALTH: Registrierungen unterschiedlichem ODI gewuenscht <-> Termin 2: {}",
			gewuenschterOdiTermin2.size());
		result.addAll(gewuenschterOdiTermin2);

		final List<RegistrierungTermineImpfungJax> termin1Termin2 = db
			.select(Projections.constructor(RegistrierungTermineImpfungJax.class,
				registrierung,
				aliasTermin1,
				aliasTermin2))
			.from(registrierung)
			.innerJoin(registrierung.impftermin1, aliasTermin1)
			.innerJoin(registrierung.impftermin2, aliasTermin2)
			.innerJoin(aliasTermin1.impfslot, aliasSlot1)
			.innerJoin(aliasTermin2.impfslot, aliasSlot2)
			.where(
				aliasSlot1.ortDerImpfung.eq(aliasSlot2.ortDerImpfung).not()
				.and(registrierung.registrierungStatus.notIn(ABGESCHLOSSEN, ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG, AUTOMATISCH_ABGESCHLOSSEN))
			)
			.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds)
			.fetch();
		LOG.info("VACME-HEALTH: Registrierungen unterschiedlichem ODI Termin 1 <-> Termin 2: {}",
			termin1Termin2.size());
		result.addAll(termin1Termin2);

		return new ArrayList<>(result);
	}

	/**
	 * Gibt alle Registrierungen zurueck, deren Impfung 1 mehr als MAX_TAGE her ist,
	 * und bei denen die zweite Impfung fehlt.
	 */
	public List<RegistrierungTermineImpfungJax> getAusstehendeImpfungen2() {
		QImpftermin aliasTermin1 = new QImpftermin("termin1");
		QImpftermin aliasTermin2 = new QImpftermin("termin2");
		QImpfung aliasImpfung1 = new QImpfung("impfung1");
		QImpfung aliasImpfung2 = new QImpfung("impfung2");
		QImpfslot aliasSlot1 = new QImpfslot("slot1");

		final int maxDays = this.settingsService.getSettings().getDistanceImpfungenMaximal();
		final List<RegistrierungTermineImpfungJax> ausstehendeImpfungen2 =
			getQueryRegistrierungLeftJoinTermineLeftJoinImpfungen(
				aliasTermin1, aliasTermin2, aliasImpfung1, aliasImpfung2)
			.innerJoin(aliasTermin1.impfslot, aliasSlot1)
			.where(aliasSlot1.zeitfenster.von.before(LocalDateTime.now().minusDays(maxDays))
				.and(registrierung.registrierungStatus.in(IMPFUNG_1_DURCHGEFUEHRT, IMPFUNG_2_KONTROLLIERT))
			)
			.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds)
			.fetch();
		LOG.info("VACME-HEALTH: Ausstehende Impfungen 2 (Impfung 1 laenger als {} Tage her): {}",
			maxDays, ausstehendeImpfungen2.size());
		return ausstehendeImpfungen2;
	}

	@NonNull
	private SmartJPAQuery<RegistrierungTermineImpfungJax> getQueryRegistrierungLeftJoinTermineLeftJoinImpfungen(
		@NonNull QImpftermin aliasTermin1,
		@NonNull QImpftermin aliasTermin2,
		@NonNull QImpfung aliasImpfung1,
		@NonNull QImpfung aliasImpfung2
	) {
		final SmartJPAQuery<RegistrierungTermineImpfungJax> queryLeftJoinAll = db
			.select(Projections.constructor(RegistrierungTermineImpfungJax.class,
				registrierung,
				aliasTermin1,
				aliasTermin2,
				aliasImpfung1,
				aliasImpfung2))
			.from(registrierung)
			.leftJoin(registrierung.impftermin1, aliasTermin1)
			.leftJoin(registrierung.impftermin2, aliasTermin2)
			.leftJoin(aliasImpfung1).on(aliasImpfung1.termin.eq(aliasTermin1))
			.leftJoin(aliasImpfung2).on(aliasImpfung2.termin.eq(aliasTermin2));
		return queryLeftJoinAll;
	}

	@NonNull
	@Transactional(TxType.REQUIRES_NEW)
	@TransactionConfiguration(timeout = 600 ) // 2 queries werden gemacht, daher geben wir uns doppelt so lange
	public ResultDTO getHealthCheckGebuchteTermine()  {
		ResultDTO resultDTO = new ResultDTO();
		resultDTO.start("Health-Check gebucht-Flag Termine");

		try {
			List<RegistrierungTermineImpfungJax> missingGebuchtFlag = getBesetzteTermineOhneGebuchtFlag();
			List<RegistrierungTermineImpfungJax> gebuchtFlagButFrei = getFreieTermineMitGebuchtFlag();

			if (missingGebuchtFlag.isEmpty() && gebuchtFlagButFrei.isEmpty()) {
				resultDTO.finish(true);
			} else {
				for (RegistrierungTermineImpfungJax registrierung : missingGebuchtFlag) {
					resultDTO.addInfo(MessageFormat.format(
						"Registrierung {0} hat Termine, welche nicht als gebucht markiert sind.",
						registrierung.getRegistrierungsnummer()));
				}
				for (RegistrierungTermineImpfungJax dto : gebuchtFlagButFrei) {
					LocalDateTime terminTimestamp = dto.getTermin1Datum() != null ? dto.getTermin1Datum() : dto.getTermin2Datum();
					OrtDerImpfungDisplayNameJax terminODI = dto.getTermin1Odi() != null ? dto.getTermin1Odi() : dto.getTermin2Odi();
					String valMsg = String.format("Termin am %s im Odi %s hat gebucht=true ist aber frei",
						DateUtil.formatDateTime(Objects.requireNonNull(terminTimestamp)), Objects.requireNonNull(terminODI).getName());
					resultDTO.addInfo(valMsg);
				}
				resultDTO.addInfo(NEWLINE + NEWLINE);
				resultDTO.addInfo("Dies kann ueber sysadmin/application-health korrigiert werden!");
				resultDTO.finish(false);
			}
		} catch(QueryTimeoutException ex) {
			handleTimeoutException(resultDTO);
		}
		return resultDTO;
	}

	@NonNull
	@Transactional(TxType.REQUIRES_NEW)
	public ResultDTO getHealthCheckInvalidImpfslots() {
		ResultDTO resultDTO = new ResultDTO();
		resultDTO.start("Health-Check ungueltige Impfslots");
		String query = "select * from Impfslot where timediff(bis, von) != '00:30:00';";
		try {
			final Query nativeQuery = db.getEntityManager().createNativeQuery(query, Impfslot.class);
			nativeQuery.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds);
			List<Impfslot> result = nativeQuery.getResultList();
			if (result.isEmpty()) {
				resultDTO.finish(true);
			} else {
				resultDTO.addInfo("Impfslot Start- und End-Zeit nicht am selben Tag und/oder nicht 30 Minuten" + NEWLINE);
				for (Impfslot impfslot : result) {
					resultDTO.addInfo(MessageFormat.format(
						"Slot {0}: {1} - {2} (ODI: {3})",
						impfslot.getId(),
						DateUtil.formatDateTime(impfslot.getZeitfenster().getVon()),
						DateUtil.formatDateTime(impfslot.getZeitfenster().getBis()),
						impfslot.getOrtDerImpfung().getName()));
				}
				resultDTO.finish(false);
			}
		} catch(QueryTimeoutException ex) {
			handleTimeoutException(resultDTO);
		}
		return resultDTO;
	}

	@NonNull
	@Transactional(TxType.REQUIRES_NEW)
	@TransactionConfiguration(timeout = 600 ) // 2 queries werden gemacht, daher geben wir uns doppelt so lange
	public ResultDTO getHealthCheckVerwaisteImpfungen() {
		ResultDTO resultDTO = new ResultDTO();
		resultDTO.start("Health-Check verwaiste Impfungen");
		try {
			final List<Impfung> resultQuery1 = getHealthCheckVerwaisteImpfungenTermineNichtGebucht();
			final List<Impfung> resultQuery2 = getHealthCheckVerwaisteImpfungenNichtAnRegistrierung();
			if (resultQuery2.isEmpty() && resultQuery1.isEmpty()) {
				resultDTO.finish(true);
			} else {
				if (!resultQuery1.isEmpty()) {
					resultDTO.addInfo("Impfungen, deren Termin nicht gebucht ist" + NEWLINE);
					for (Impfung impfung : resultQuery1) {
						resultDTO.addInfo(impfungToHealthCheckString(impfung));
					}
				}
				if (!resultQuery2.isEmpty()) {
					resultDTO.addInfo("Impfungen, die nicht an einer Registrierung haengen (weder Termin1, Termin2 noch ImpfdossierEintrag)" + NEWLINE);
					for (Impfung impfung : resultQuery2) {
						resultDTO.addInfo(impfungToHealthCheckString(impfung));
					}
				}
				resultDTO.finish(false);
			}
		} catch(QueryTimeoutException ex) {
			handleTimeoutException(resultDTO);
		}
		return resultDTO;
	}

	@NonNull
	private List<Impfung> getHealthCheckVerwaisteImpfungenTermineNichtGebucht() {
		String query = "select * from Impfung inner join Impftermin I ON Impfung.termin_id = I.id where I.gebucht = false;";
		final Query nativeQuery = db.getEntityManager().createNativeQuery(query, Impfung.class);
		nativeQuery.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds);
		List<Impfung> result = nativeQuery.getResultList();
		return result;
	}

	@NonNull
	private List<Impfung> getHealthCheckVerwaisteImpfungenNichtAnRegistrierung() {
		String query = ""
			+ "SELECT I.* FROM Impfung I "
			+ "INNER JOIN Impftermin T ON I.termin_id = T.id "
			+ "WHERE NOT EXISTS(SELECT 1 FROM Registrierung R1 WHERE R1.impftermin1_id = T.id) "
			+ "AND NOT EXISTS(SELECT 1 FROM Registrierung R2 WHERE R2.impftermin2_id = T.id) "
			+ "AND NOT EXISTS(SELECT 1 FROM Impfdossiereintrag DE "
			+ "LEFT JOIN Impfdossier D ON DE.impfdossier_id = D.id WHERE DE.impftermin_id = T.id);";

		final Query nativeQuery = db.getEntityManager().createNativeQuery(query, Impfung.class);
		nativeQuery.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds);
		List<Impfung> result = nativeQuery.getResultList();
		return result;
	}

	@NonNull
	@Transactional(TxType.REQUIRES_NEW)
	public ResultDTO getHealthCheckVollstaendigerImpfschutzKeineImpfungen() {
		ResultDTO resultDTO = new ResultDTO();
		resultDTO.start("Health-Check vollstaendiger Impfschutz trotz keine Impfungen");
		String query = "select * from Registrierung R"
			+ " left join Impftermin T1 on R.impftermin1_id = T1.id"
			+ " left join Impfung I1 on T1.id = I1.termin_id"
			+ " left join ExternesZertifikat EZ ON R.id = EZ.registrierung_id"
			+ " where (T1.id is null or I1.id is null) and EZ.id is null and vollstaendigerImpfschutz = true;";
		try {
			final Query nativeQuery = db.getEntityManager().createNativeQuery(query, Registrierung.class);
			nativeQuery.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds);
			List<Registrierung> result = nativeQuery.getResultList();
			if (result.isEmpty()) {
				resultDTO.finish(true);
			} else {
				for (Registrierung registrierung : result) {
					resultDTO.addInfo(MessageFormat.format(
						"Registrierung {0}, im Status {1}  ohne Impfungen und ohne ExternesZertifikat hat trotzdem das Flag vollstaendigGeimpft gesetzt",
						registrierung.getRegistrierungsnummer(),
						registrierung.getRegistrierungStatus()));
				}
				resultDTO.finish(false);
			}
		} catch(QueryTimeoutException ex) {
			handleTimeoutException(resultDTO);
		}
		return resultDTO;
	}

	@NonNull
	@Transactional(TxType.REQUIRES_NEW)
	public ResultDTO getHealthCheckDoppeltGeimpftOhneVollsaendigerImpfschutz() {
		ResultDTO resultDTO = new ResultDTO();
		resultDTO.start("Health-Check kein vollstaendiger Impfschutz trotz zwei Impfungen");
		String query = "SELECT * FROM Registrierung r "
			+ "LEFT JOIN Impftermin t1 ON r.impftermin1_id = t1.id "
			+ "LEFT JOIN Impfslot s1 ON t1.impfslot_id = s1.id "
			+ "LEFT JOIN Impfung i1 ON t1.id = i1.termin_id  "
			+ "LEFT JOIN Impftermin t2 ON r.impftermin2_id = t2.id "
			+ "LEFT JOIN Impfslot s2 ON t2.impfslot_id = s2.id "
			+ "LEFT JOIN Impfung i2 ON t2.id = i2.termin_id "
			+ "WHERE i1.id is not null and i2.id is not null and vollstaendigerImpfschutz = false;";
		try {
			final Query nativeQuery = db.getEntityManager().createNativeQuery(query, Registrierung.class);
			nativeQuery.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds);
			List<Registrierung> result = nativeQuery.getResultList();
			if (result.isEmpty()) {
				resultDTO.finish(true);
			} else {
				for (Registrierung registrierung : result) {
					resultDTO.addInfo(MessageFormat.format(
						"Registrierung {0}, im Status {1}  mit zwei Impfungen hat trotzdem das Flag vollstaendigGeimpft NICHT gesetzt",
						registrierung.getRegistrierungsnummer(),
						registrierung.getRegistrierungStatus()));
				}
				resultDTO.finish(false);
			}
		} catch(QueryTimeoutException ex) {
			handleTimeoutException(resultDTO);
		}
		return resultDTO;
	}

	@Transactional(TxType.REQUIRES_NEW)
	public ResultDTO getHealthCheckAbgeschlossenOhneVollsaendigerImpfschutz() {
		ResultDTO resultDTO = new ResultDTO();
		resultDTO.start("Health-Check kein vollstaendiger Impfschutz aber abgeschlossen");
		String query =
			"SELECT * FROM Registrierung r WHERE r.registrierungStatus  in ('ABGESCHLOSSEN') "
				+ "and (r.vollstaendigerImpfschutz = false or r.vollstaendigerImpfschutz is null);";
		try {
			final Query nativeQuery = db.getEntityManager().createNativeQuery(query, Registrierung.class);
			nativeQuery.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds);
			List<Registrierung> result = nativeQuery.getResultList();
			if (result.isEmpty()) {
				resultDTO.finish(true);
			} else {
				for (Registrierung registrierung : result) {
					resultDTO.addInfo(MessageFormat.format(
						"Registrierung {0}, im Status Abgeschlossen hat trotzdem das Flag vollstaendigGeimpft NICHT gesetzt",
						registrierung.getRegistrierungsnummer()));
				}
				resultDTO.finish(false);
			}
		} catch(QueryTimeoutException ex) {
			handleTimeoutException(resultDTO);
		}
		return resultDTO;
	}

	@NonNull
	@Transactional(TxType.REQUIRES_NEW)
	public ResultDTO getHealthCheckNichtAbgeschlossenAberVollstaendigerImpfschutz() {
		ResultDTO resultDTO = new ResultDTO();
		resultDTO.start("Health-Check vollstaendiger Impfschutz aber nicht ABGESCHLOSSEN oder ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG");
		String query = "SELECT * FROM Registrierung r "
			+ "WHERE r.registrierungStatus not in ('ABGESCHLOSSEN','ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG', 'IMMUNISIERT', 'FREIGEGEBEN_BOOSTER', 'ODI_GEWAEHLT_BOOSTER', 'GEBUCHT_BOOSTER', 'KONTROLLIERT_BOOSTER') and r.vollstaendigerImpfschutz = true;";
		try {
			final Query nativeQuery = db.getEntityManager().createNativeQuery(query, Registrierung.class);
			nativeQuery.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds);
			List<Registrierung> result = nativeQuery.getResultList();
			if (result.isEmpty()) {
				resultDTO.finish(true);
			} else {
				for (Registrierung registrierung : result) {
					resultDTO.addInfo(MessageFormat.format(
						"Registrierung {0}, im Status {1} ist nicht ABGESCHLOSSEN oder "
							+ "ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG, 'IMMUNISIERT', 'FREIGEGEBEN_BOOSTER', "
							+ "'ODI_GEWAEHLT_BOOSTER', 'GEBUCHT_BOOSTER', 'KONTROLLIERT_BOOSTER', "
							+ "hat aber trotzdem das Flag vollstaendigGeimpft gesetzt",
						registrierung.getRegistrierungsnummer(),
						registrierung.getRegistrierungStatus()));
				}
				resultDTO.finish(false);
			}
		} catch(QueryTimeoutException ex) {
			handleTimeoutException(resultDTO);
		}
		return resultDTO;
	}

	@NonNull
	@Transactional(TxType.REQUIRES_NEW)
	public ResultDTO getHealthCheckAbgeschlossenOhneCoronaAberVollstaendigerImpfschutz() {
		ResultDTO resultDTO = new ResultDTO();
		resultDTO.start("Health-Check vollstaendiger Impfschutz trotz Abgeschlossen ohne Corona");
		String query = "SELECT * "
			+ "FROM Registrierung r "
			+ "WHERE r.zweiteImpfungVerzichtetGrund IS NOT NULL AND r.zweiteImpfungVerzichtetGrund != '' AND r.vollstaendigerImpfschutz = TRUE;";
		try {
			final Query nativeQuery = db.getEntityManager().createNativeQuery(query, Registrierung.class);
			nativeQuery.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds);
			List<Registrierung> result = nativeQuery.getResultList();
			if (result.isEmpty()) {
				resultDTO.finish(true);
			} else {
				for (Registrierung registrierung : result) {
					resultDTO.addInfo(MessageFormat.format(
						"Registrierung {0}, im Status {1} ist abgeschlossen ohne Corona, hat aber trotzdem das Flag vollstaendigGeimpft gesetzt",
						registrierung.getRegistrierungsnummer(),
						registrierung.getRegistrierungStatus()));
				}
				resultDTO.finish(false);
			}
		} catch(QueryTimeoutException ex) {
			handleTimeoutException(resultDTO);
		}
		return resultDTO;
	}

	@NonNull
	@Transactional(TxType.REQUIRES_NEW)
	public ResultDTO getHealthCheckFailedZertifikatRevocations() {
		ResultDTO resultDTO = new ResultDTO();
		resultDTO.start("Health-Check failed Zertifikat Revocations");
		String query = "select * from ZertifikatQueue ZQ INNER join Zertifikat Z ON ZQ.zertifikatToRevoke_id = Z.id INNER "
			+ "JOIN Registrierung R ON Z.registrierung_id = R.id where ZQ.status = 'FAILED';\n";
		try {
			final Query nativeQuery = db.getEntityManager().createNativeQuery(query, ZertifikatQueue.class);
			nativeQuery.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds);
			List<ZertifikatQueue> result = nativeQuery.getResultList();
			if (result.isEmpty()) {
				resultDTO.finish(true);
			} else {
				for (ZertifikatQueue queue : result) {
					resultDTO.addInfo(MessageFormat.format(
						"Fuer die Registrierung {0} ist die Stornierung des Zertifikats fehlgeschlagen. Fehlercode: {1}. Status: {2}. Anzahl Versuche: {3}. Betroffenes Zertifikat ID: {4}",
						queue.getZertifikatToRevoke().getRegistrierung().getRegistrierungsnummer(),
						queue.getLastError(),
						queue.getStatus(),
						queue.getErrorCount(),
						queue.getZertifikatToRevoke().getId()));
				}
				resultDTO.finish(false);
			}
		} catch(QueryTimeoutException ex) {
			handleTimeoutException(resultDTO);
		}
		return resultDTO;
	}

	@NonNull
	@Transactional(TxType.REQUIRES_NEW)
	public ResultDTO getHealthCheckFailedZertifikatRecreations() {
		ResultDTO resultDTO = new ResultDTO();
		resultDTO.start("Health-Check failed Zertifikat Recreations");
		String query = "SELECT R.registrierungsnummer, I.id "
			+ "FROM Zertifikat "
			+ "INNER JOIN Registrierung R ON Zertifikat.registrierung_id = R.id "
			+ "LEFT JOIN Impfung I ON Zertifikat.impfung_id = I.id "
			+ "WHERE vollstaendigerImpfschutz = TRUE AND I.generateZertifikat = FALSE "
			+ "AND abgleichElektronischerImpfausweis = TRUE AND "
			+ "R.id IN (SELECT registrierung_id FROM Zertifikat Z "
			+ "WHERE NOT exists(SELECT 1 FROM Zertifikat Z2 "
			+ "WHERE Z2.registrierung_id = Z.registrierung_id AND Z2.registrierung_id = R.id AND Z2.revoked = FALSE));";
		try {
			final Query nativeQuery = db.getEntityManager().createNativeQuery(query);
			nativeQuery.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds);
			List<Object[]> result = nativeQuery.getResultList();
			if (result.isEmpty()) {
				resultDTO.finish(true);
			} else {
				boolean foundRealPositive = false;
				for (Object[] tuple : result) {
					final String registrierungsnummer = tuple[0].toString();
					final String impfungId = tuple[1].toString();
					Objects.requireNonNull(registrierungsnummer);
					Objects.requireNonNull(impfungId);
					ImpfinformationDto infos =
						impfinformationenService.getImpfinformationenNoCheck(registrierungsnummer);
					final Impfung impfung = impfungRepo.getById(Impfung.toId(UUID.fromString(impfungId)))
						.orElseThrow(AppValidationMessage.NO_IMPFUNG_OR_IMPFSCHUTZ::create);
					if (DeservesZertifikatValidator.deservesAndWantsZertifikat(infos.getRegistrierung(), impfung, infos.getExternesZertifikat())) {
						resultDTO.addInfo(MessageFormat.format(
							"Fuer die Registrierung {0} wurde kein gueltiges Zertifikat gefunden, obwohl "
								+ "andere, revozierte Zertifikate vorhanden sind und die Registrierung scheinbar ein Zertifikat "
								+ "haben duerfte.",
							registrierungsnummer));
						foundRealPositive = true;
					}

				}
				resultDTO.finish(!foundRealPositive);
			}

		} catch(QueryTimeoutException ex) {
			handleTimeoutException(resultDTO);
		}
		return resultDTO;
	}

	@NonNull
	public ResultDTO getHealthCheckNichtGeschickteRegistrierungFile() {
		ResultDTO resultDTO = new ResultDTO();
		resultDTO.start("Health-Check Nicht geschickte Post-Dokumente");

		// Diese Pruefung wollen wir nur auf der PROD durchfuehren, da auf den Testumgebungen
		// nie Briefe verschickt werden
		if (!"PROD".equalsIgnoreCase(stufe)) {
			resultDTO.addInfo("Diese Pruefung wird auf Testsystemen nicht durchgefuehrt");
			resultDTO.finish(true);
			return resultDTO;
		}

		final LocalDate stichtag = LocalDate.now().minusDays(4);// Wegen Wochenende
		String query = "select * from RegistrierungFile "
			+ "where regenerated = false "
			+ "and fileTyp in ('TERMIN_BESTAETIGUNG', 'REGISTRIERUNG_BESTAETIGUNG') "
			+ "and timestampErstellt < '" + DateUtil.FILENAME_DATE_PATTERN.apply(Locale.GERMAN).format(stichtag)+ "';";
		try {
			final Query nativeQuery = db.getEntityManager().createNativeQuery(query, RegistrierungFile.class);
			nativeQuery.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds);
			List<RegistrierungFile> result = nativeQuery.getResultList();
			if (result.isEmpty()) {
				resultDTO.finish(true);
			} else {
				for (RegistrierungFile registrierung : result) {
					resultDTO.addInfo(MessageFormat.format(
						"Dokument wurde nicht geschickt. Registrierung: {0}, im Status {1}, erstellt am {2}",
						registrierung.getRegistrierung().getRegistrierungsnummer(),
						registrierung.getFileTyp(),
						registrierung.getTimestampErstellt()));
				}
				resultDTO.finish(false);
			}
		} catch(QueryTimeoutException ex) {
			handleTimeoutException(resultDTO);
		}
		return resultDTO;
	}

	private void handleTimeoutException(@NonNull ResultDTO resultDTO) {
		final String msg = String.format(
			"Query '%s' aborted after %ds because it exceeded the configured query timeout (%s)",
			resultDTO.getTitle(),
			dbQueryTimeoutInSeconds,
			DB_QUERY_TIMEOUT_HINT);
		LOG.warn(msg);
		resultDTO.addInfo(msg);
		resultDTO.finish(false);
	}

	@NonNull
	private String impfungToHealthCheckString(@NonNull Impfung impfung) {
		StringBuilder sb = new StringBuilder();
		sb.append("Impfung ").append(impfung.getId()).append(", durchgefuehrt am ")
			.append(DateUtil.formatDateTime(impfung.getTimestampImpfung()))
			.append(" (Termin: ").append(impfung.getTermin().getId()).append(')')
			.append(" ist mit keiner Registrierung verknuepft")
			.append(NEWLINE);
		return sb.toString();
	}

	@NonNull
	@Transactional(TxType.REQUIRES_NEW)
	@TransactionConfiguration(timeout = 900 ) // 3 queries werden gemacht, daher geben wir uns doppelt so lange
	public ResultDTO getHealthCheckFalschVerknuepfteZertifikate() {
		ResultDTO resultDTO = new ResultDTO();
		resultDTO.start("Health-Check Falsch verknuepfte Zertifikate");

		String query1 = "select R.registrierungsnummer, R.id as rid, Z.uvci, I.id as iid, Z.registrierung_id as zrid "
			+ "from Zertifikat Z "
			+ "inner join Impfung I ON Z.impfung_id = I.id "
			+ "inner join Impftermin T ON I.termin_id = T.id "
			+ "inner join Registrierung R ON T.id = R.impftermin1_id "
			+ "where R.id != Z.registrierung_id;";
		String query2 = "select R.registrierungsnummer, R.id as rid, Z.uvci, I.id as iid, Z.registrierung_id as zrid "
			+ "from Zertifikat Z "
			+ "inner join Impfung I ON Z.impfung_id = I.id "
			+ "inner join Impftermin T ON I.termin_id = T.id "
			+ "inner join Registrierung R ON T.id = R.impftermin2_id "
			+ "where R.id != Z.registrierung_id;";
		String queryN = "select R.registrierungsnummer, R.id as rid, Z.uvci, I.id as iid, Z.registrierung_id as zrid "
			+ "from Zertifikat Z "
			+ "inner join Impfung I ON Z.impfung_id = I.id "
			+ "inner join Impftermin T ON I.termin_id = T.id "
			+ "inner join Impfdossiereintrag E ON T.id = E.impftermin_id "
			+ "inner join Impfdossier D on E.impfdossier_id = D.id "
			+ "inner join Registrierung R ON D.registrierung_id = R.id "
			+ "where R.id != Z.registrierung_id;";

		try {
			List<Object[]> results = new ArrayList<>();

			final Query nativeQuery1 = db.getEntityManager().createNativeQuery(query1);
			nativeQuery1.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds);
			results.addAll(nativeQuery1.getResultList());

			final Query nativeQuery2 = db.getEntityManager().createNativeQuery(query2);
			nativeQuery2.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds);
			results.addAll(nativeQuery2.getResultList());

			final Query nativeQueryN = db.getEntityManager().createNativeQuery(queryN);
			nativeQueryN.setHint(DB_QUERY_TIMEOUT_HINT, dbQueryTimeoutInSeconds);
			results.addAll(nativeQueryN.getResultList());

			if (results.isEmpty()) {
				resultDTO.finish(true);
			} else {
				for (Object[] r : results) {
					String registrierungsnummer = (String) r[0];
					String registrierungIdKorrekt = (String) r[1];
					String uvci = (String) r[2];
					String impfungId = (String) r[3];
					String registrierungIdFalsch = (String) r[4];
					resultDTO.addInfo(MessageFormat.format(
						"Registrierung {0} mit ID {1} hat ein Zertifikat {2} fuer Impfung {3} welches an einer anderen Registrierung haengt: {4}",
						registrierungsnummer,
						registrierungIdKorrekt,
						uvci,
						impfungId,
						registrierungIdFalsch));
				}
				resultDTO.finish(false);
			}
		} catch(QueryTimeoutException ex) {
			handleTimeoutException(resultDTO);
		}
		return resultDTO;
	}

	/**
	 * internal, only for testing of https://support.dvbern.ch/browse/VACME-1933 (remove after review)
	 * @return
	 * todo reviewer of VACME-1933 remove after testing
	 */
	@Transactional(TxType.REQUIRES_NEW)
	public ResultDTO runSleepQuery() {
		ResultDTO resultDTO = new ResultDTO();
		resultDTO.start("sleepQuery");
		try{
			LOG.info("startet native query");
			Query nativeQuery = this.db.getEntityManager().createNativeQuery("SELECT doSleep(6)");
			nativeQuery.setHint(DB_QUERY_TIMEOUT_HINT, 5);
			nativeQuery.executeUpdate();
		} catch(QueryTimeoutException ex) {
			handleTimeoutException(resultDTO);
		}
		return resultDTO;
	}
}
