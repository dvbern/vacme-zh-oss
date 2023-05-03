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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.Query;
import javax.transaction.Transactional;

import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impfslot;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import ch.dvbern.oss.vacme.entities.terminbuchung.QImpfslot;
import ch.dvbern.oss.vacme.entities.terminbuchung.QImpftermin;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.smartdb.Db;
import ch.dvbern.oss.vacme.util.ImpfterminOffsetWuerfel;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.Expressions;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.opentracing.Traced;
import org.jetbrains.annotations.NotNull;

@RequestScoped
@Transactional
@Slf4j
public class ImpfterminRepo {

	@ConfigProperty(name = "impfslot.duration", defaultValue = "30")
	protected int slotDuration;

	@ConfigProperty(name = "vacme.terminslot.offset.groups", defaultValue = "3")
	protected int slotOffsetGroups;

	@ConfigProperty(name = "vacme.terminslot.offset.max.termine.to.divide", defaultValue = "20")
	protected int slotOffsetMaxTermineToDivide;

	@ConfigProperty(name = "vacme.terminslot.offset.deterministic.when.low.capacity", defaultValue = "false")
	protected boolean slotOffsetDeterministicWhenLowCapacity;

	@ConfigProperty(name = "vacme.terminreservation.enabled", defaultValue = "true")
	protected boolean terminReservationEnabled;

	@ConfigProperty(name = "vacme.terminreservation.dauer.in.min", defaultValue = "10")
	protected int terminReservationDauerInMinutes;

	private final Db db;
	private final ImpfungRepo impfungRepo;
	private @Nullable ImpfterminOffsetWuerfel wuerfel = null;

	@Inject
	public ImpfterminRepo(
		Db db,
		@NonNull ImpfungRepo impfungRepo
	) {
		this.db = db;
		this.impfungRepo = impfungRepo;
	}

	@NonNull
	private ImpfterminOffsetWuerfel getWuerfel() {
		if (wuerfel == null) {
			wuerfel = new ImpfterminOffsetWuerfel(slotDuration, slotOffsetGroups, slotOffsetMaxTermineToDivide, slotOffsetDeterministicWhenLowCapacity);
		}
		return wuerfel;
	}

	public void create(@NonNull Impftermin impftermin) {
		db.persist(impftermin);
	}

	@NonNull
	public Optional<Impftermin> getById(@NonNull ID<Impftermin> id) {
		return db.get(id);
	}

	@NonNull
	public List<Impftermin> findAll() {
		return db.findAll(QImpftermin.impftermin);
	}

	@NonNull
	public List<Impfslot> findFreieImpfslots(
		@NonNull OrtDerImpfung ortDerImpfung, @NonNull Impffolge impffolge, @NonNull LocalDate date
	) {
		date = dateMustBeAfterTomorrow(date);
		final LocalDateTime minDateTime = date.atStartOfDay();
		final LocalDateTime maxDateTime = date.plusDays(1).atStartOfDay();

		// Achtung, diese Methode gibt meine eigenen reservierten Termine nicht zurueck!
		// Problem: Wir wissen auf dem Backend die Registrierungsnummer nicht
		return db
			.select(QImpfslot.impfslot)
			.distinct()
			.from(QImpfslot.impfslot)
			.innerJoin(QImpftermin.impftermin).on(QImpftermin.impftermin.impfslot.eq(QImpfslot.impfslot))
			.where(QImpftermin.impftermin.gebucht.isFalse()
				.and(QImpfslot.impfslot.ortDerImpfung.eq(ortDerImpfung))
				.and(QImpftermin.impftermin.impffolge.eq(impffolge))
				.and(QImpfslot.impfslot.zeitfenster.bis.between(minDateTime, maxDateTime)
				.and(isTerminNichtReserviert()))
			)
			.fetch();
	}

	@NotNull
	private LocalDate dateMustBeAfterTomorrow(@NotNull LocalDate date) {
		LocalDate tomorrow = LocalDate.now().plusDays(1);
		if (date.isBefore(tomorrow)) {
			date = tomorrow;  // we can only search for free slots beginning from tomorrow midnight
		}
		return date;
	}

	public boolean hasAtLeastFreieImpfslots(Integer minTermin) {
		String queryFileName = "/db/queries/hasFreieTermin.sql";
		InputStream inputStream = ImpfterminRepo.class.getResourceAsStream(queryFileName);
		Objects.requireNonNull(inputStream);

		try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			var sb = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line);
				sb.append(System.lineSeparator());
			}

			String query = sb.toString();

			Query nativeQuery = this.db.getEntityManager().createNativeQuery(query);
			nativeQuery.setParameter("bisDate", LocalDate.now().plusDays(1).atStartOfDay());
			nativeQuery.setParameter("minTermin", minTermin);
			return !nativeQuery.getResultList().isEmpty();
		} catch (IOException e) {
			throw AppValidationMessage.ILLEGAL_STATE.create("Could not load sql query from file " + queryFileName);
		}
	}

	@Traced
	@Nullable
	public LocalDateTime findNextFreierImpftermin(
		@NonNull OrtDerImpfung ortDerImpfung,
		@NonNull Impffolge impffolge,
		@NonNull LocalDate minDate,
		@NonNull LocalDate maxDate
	) {
		final LocalDateTime minDateTime = dateMustBeAfterTomorrow(minDate).atStartOfDay();
		final LocalDateTime maxDateTime = dateMustBeAfterTomorrow(maxDate).plusDays(1).atStartOfDay();

		// Achtung, diese Methode gibt meine eigenen reservierten Termine nicht zurueck!
		// Problem: Wir wissen auf dem Backend die Registrierungsnummer nicht
		return db
			.select(QImpfslot.impfslot.zeitfenster.bis)
			.from(QImpfslot.impfslot)
			.innerJoin(QImpftermin.impftermin).on(QImpftermin.impftermin.impfslot.eq(QImpfslot.impfslot))
			.where(
				QImpftermin.impftermin.gebucht.isFalse()
					.and(QImpfslot.impfslot.ortDerImpfung.eq(ortDerImpfung))
					.and(QImpftermin.impftermin.impffolge.eq(impffolge))
					.and(QImpfslot.impfslot.zeitfenster.bis.between(minDateTime, maxDateTime))
					.and(isTerminNichtReserviert()))
			.orderBy(QImpfslot.impfslot.zeitfenster.bis.asc())
			.fetchFirst();
	}

	@NonNull
	public List<Impftermin> findImpftermine(@NonNull Impfslot slot, @NonNull Impffolge impffolge) {
		final List<Impftermin> freieTermine = db
			.select(QImpftermin.impftermin)
			.from(QImpftermin.impftermin)
			.where(QImpftermin.impftermin.impfslot.eq(slot)
				.and(QImpftermin.impftermin.impffolge.eq(impffolge)))
			.fetch();
		return freieTermine;
	}

	public List<Impftermin> findGebuchteTermine(
		@NonNull OrtDerImpfung ortDerImpfung,
		@NonNull Impffolge impffolge,
		@NonNull LocalDate minDate,
		@NonNull LocalDate maxDate
	) {
		final LocalDateTime minDateTime = minDate.atStartOfDay();
		final LocalDateTime maxDateTime = maxDate.plusDays(1).atStartOfDay();

		return db
			.select(QImpftermin.impftermin)
			.distinct()
			.from(QImpfslot.impfslot)
			.innerJoin(QImpftermin.impftermin).on(QImpftermin.impftermin.impfslot.eq(QImpfslot.impfslot))
			.where(QImpftermin.impftermin.gebucht.isTrue()
				.and(QImpfslot.impfslot.ortDerImpfung.eq(ortDerImpfung))
				.and(QImpftermin.impftermin.impffolge.eq(impffolge))
				.and(QImpfslot.impfslot.zeitfenster.bis.between(minDateTime, maxDateTime))
			)
			.fetch();
	}

	public List<Impftermin> findAlleTermine(
		@NonNull OrtDerImpfung ortDerImpfung,
		@NonNull Impffolge impffolge,
		@NonNull LocalDate minDate,
		@NonNull LocalDate maxDate
	) {
		final LocalDateTime minDateTime = minDate.atStartOfDay();
		final LocalDateTime maxDateTime = maxDate.plusDays(1).atStartOfDay();

		return db
			.select(QImpftermin.impftermin)
			.distinct()
			.from(QImpfslot.impfslot)
			.innerJoin(QImpftermin.impftermin).on(QImpftermin.impftermin.impfslot.eq(QImpfslot.impfslot))
			.where(QImpfslot.impfslot.ortDerImpfung.eq(ortDerImpfung)
				.and(QImpftermin.impftermin.impffolge.eq(impffolge))
				.and(QImpfslot.impfslot.zeitfenster.bis.between(minDateTime, maxDateTime))
			)
			.fetch();
	}

	@Nullable
	public Impftermin findFreienImpftermin(@NonNull Impfslot slot, @NonNull Impffolge impffolge) {
		return db
			.select(QImpftermin.impftermin)
			.from(QImpftermin.impftermin)
			.innerJoin(QImpftermin.impftermin.impfslot)
			.where(QImpftermin.impftermin.gebucht.isFalse()
				.and(QImpftermin.impftermin.impfslot.eq(slot))
				.and(QImpftermin.impftermin.impffolge.eq(impffolge))
				.and(isTerminNichtReserviert()))
			.fetchFirst();
	}

	@Nullable
	public Impftermin findMeinenReserviertenOrFreienImpftermin(
		@NonNull Registrierung registrierung, @NonNull Impfslot slot, @NonNull Impffolge impffolge
	) {
		// wenn Reservation ausgeschaltet dann direkt einen freiden Termin suchen und zurueckgeben
		if (!terminReservationEnabled) {
			return findFreienImpftermin(slot, impffolge);
		}

		final Impftermin impftermin = db
			.selectFrom(QImpftermin.impftermin)
			.innerJoin(QImpftermin.impftermin.impfslot)
			.where(QImpftermin.impftermin.gebucht.isFalse()
				.and(QImpftermin.impftermin.impfslot.eq(slot))
				.and(QImpftermin.impftermin.impffolge.eq(impffolge))
				.and(isFuerMichReservierterTermin(registrierung)))
			.fetchFirst();
		if (impftermin != null) {
			return impftermin;
		}
		LOG.warn("VACME-WARN: Kein reservierter Termin gefunden fuer Registrierung {}", registrierung.getRegistrierungsnummer());
		// Wir schauen, ob es zufaellig noch einen freien gibt
		return findFreienImpftermin(slot, impffolge);
	}

	public void delete(@NonNull ID<Impftermin> terminId) {
		db.remove(terminId);
	}

	public void terminReservieren(
		@NonNull Registrierung registrierung,
		@NonNull Impftermin termin
	) {
		if (!terminReservationEnabled) {
			return;
		}
		if (termin.getTimestampReserviert() != null
				&& termin.getTimestampReserviert().isAfter(LocalDateTime.now().minusMinutes(terminReservationDauerInMinutes))) {
			if (termin.getRegistrierungsnummerReserviert() != null && termin.getRegistrierungsnummerReserviert().equals(registrierung.getRegistrierungsnummer())) {
				// Der Termin ist schon fuer mich reserviert
				return;
			}
			// Der Termin ist fuer jemand anderes reserviert!
			throw AppValidationMessage.IMPFTERMIN_WITH_EXISTING_RESERVATION.create(termin.getId());
		}
		// Allfaellige bisherige Reservationen loeschen
		terminReservationAufheben(registrierung, termin.getImpffolge());
		// und den gewuenschten Termin reservieren
		termin.setTimestampReserviert(LocalDateTime.now());
		termin.setRegistrierungsnummerReserviert(registrierung.getRegistrierungsnummer());
		db.flush();
	}

	private void terminReservationAufheben(@NonNull Registrierung registrierung, @NonNull Impffolge impffolge) {
		if (!terminReservationEnabled) {
			return;
		}
		db.update(QImpftermin.impftermin)
			.setNull(QImpftermin.impftermin.timestampReserviert)
			.setNull(QImpftermin.impftermin.registrierungsnummerReserviert)
			.where(QImpftermin.impftermin.impffolge.eq(impffolge)
				.and(isFuerMichReservierterTermin(registrierung)))
			.execute();
	}

	public void abgelaufeneTerminReservationenAufheben() {
		if (!terminReservationEnabled) {
			return;
		}
		final long countDeleted = db.update(QImpftermin.impftermin)
			.setNull(QImpftermin.impftermin.timestampReserviert)
			.setNull(QImpftermin.impftermin.registrierungsnummerReserviert)
			.where(terminReservationAbgelaufen())
			.execute();
		LOG.debug("{} Reservationen sind abgelaufen und wurden entfernt.", countDeleted);
	}

	public long getAnzahlFreieTermine(
		@NonNull OrtDerImpfung ortDerImpfung, @NonNull Impffolge impffolge, @NonNull LocalDate start, @NonNull LocalDate end
	) {
		final LocalDateTime minDateTime = start.atStartOfDay();
		final LocalDateTime maxDateTime = end.plusDays(1).atStartOfDay();
		return db
			.select(QImpftermin.impftermin)
			.from(QImpfslot.impfslot)
			.innerJoin(QImpftermin.impftermin).on(QImpftermin.impftermin.impfslot.eq(QImpfslot.impfslot))
			.where(QImpftermin.impftermin.gebucht.isFalse()
				.and(QImpfslot.impfslot.ortDerImpfung.eq(ortDerImpfung))
				.and(QImpftermin.impftermin.impffolge.eq(impffolge))
				.and(QImpfslot.impfslot.zeitfenster.bis.between(minDateTime, maxDateTime))
			)
			.fetchCount();
	}

	public long getAnzahlGebuchteTermine(
		@NonNull Impfslot impfslot
	) {
		return db
			.select(QImpftermin.impftermin.id)
			.from(QImpfslot.impfslot)
			.innerJoin(QImpftermin.impftermin).on(QImpfslot.impfslot.id.eq(QImpftermin.impftermin.impfslot.id))
			.where(QImpftermin.impftermin.gebucht.isTrue()
				.and(QImpfslot.impfslot.id.eq(impfslot.getId()))
			)
			.fetchCount();
	}

	public void termineSpeichern(
		@NonNull Registrierung registrierung,
		@Nullable Impftermin termin1,
		@Nullable Impftermin termin2
	) {
		termin1Speichern(registrierung, termin1);
		termin2Speichern(registrierung, termin2);
	}

	public void termin1Speichern(
		@NonNull Registrierung registrierung,
		@Nullable Impftermin termin1
	) {
		if (registrierung.getImpftermin1() != null && registrierung.getImpftermin1().equals(termin1)) {
			// Der gleiche Termin ist schon angehaengt, wir muessen nichts mehr machen
			return;
		}
		if (registrierung.getImpftermin1() != null) {
			this.termin1Freigeben(registrierung);
		}
		if (termin1 != null) {
			terminBuchen(registrierung, termin1);
			registrierung.setImpftermin1FromImpfterminRepo(termin1);
			if (registrierung.getAbgesagteTermine() != null) {
				// Ein allfaelliger vorher vom Odi abgesagter Termin darf nicht mehr gespeichert bleiben
				registrierung.getAbgesagteTermine().setTermin1(null);
			}
		}
		db.persist(registrierung);
		db.flush();
	}

	public void termin2Speichern(
		@NonNull Registrierung registrierung,
		@Nullable Impftermin termin2
	) {
		if (registrierung.getImpftermin2() != null && registrierung.getImpftermin2().equals(termin2)) {
			// Der gleiche Termin ist schon angehaengt, wir muessen nichts mehr machen
			return;
		}
		if (registrierung.getImpftermin2() != null) {
			this.termin2Freigeben(registrierung);
		}
		if (termin2 != null) {
			terminBuchen(registrierung, termin2);
			registrierung.setImpftermin2FromImpfterminRepo(termin2);
			if (registrierung.getAbgesagteTermine() != null) {
				// Ein allfaelliger vorher vom Odi abgesagter Termin darf nicht mehr gespeichert bleiben
				registrierung.getAbgesagteTermine().setTermin2(null);
			}
		}
		db.persist(registrierung);
		db.flush();
	}

	public void boosterTerminSpeichern(
		@NonNull Registrierung registrierung,
		@NonNull Impfdossiereintrag impfdossiereintrag,
		@NonNull Impftermin termin
	) {
		if (impfdossiereintrag.getImpftermin() != null && impfdossiereintrag.getImpftermin().equals(termin)) {
			// Der gleiche Termin ist schon angehaengt, wir muessen nichts mehr machen
			return;
		}
		if (impfdossiereintrag.getImpftermin() != null) {
			this.boosterTerminFreigeben(impfdossiereintrag);
		}
		terminBuchen(registrierung, termin);
		impfdossiereintrag.setImpfterminFromImpfterminRepo(termin);
			// TODO BOOSTER : soll die abgsagte logik auch fuer booster implementiert werden? VACME-1344
//			if (registrierung.getAbgesagteTermine() != null) {
//				// Ein allfaelliger vorher vom Odi abgesagter Termin darf nicht mehr gespeichert bleiben
//				registrierung.getAbgesagteTermine().setBoosterTermin(null);
//			}
		db.persist(impfdossiereintrag);
		db.flush();
	}

	private void terminBuchen(@NonNull Registrierung registrierung, @NonNull Impftermin termin) {
		// Bevor wir den Termin speichern: Nochmals sicherstellen, dass keine Impfung an diesem Termin haengt
		assertTerminHasNoImpfung(termin);
		// Sicherstellen, dass der Termin entweder fuer mich reserviert, oder gar nicht reserviert ist
		assertTerminNotReserviertForAnotherRegistration(registrierung, termin);
		// Offset wuerfeln. Es ist egal ob evtl. schon ein Offset von einer frueheren Buchung drauf ist.
		getWuerfel().wuerfleOffset(
			termin,
			(currentTermin) -> this.getAnzahlGebuchteTermine(currentTermin.getImpfslot())
		);
		// und auf gebucht setzen
		termin.setGebuchtFromImpfterminRepo(true);
	}

	public void termine1Und2Freigeben(@NonNull Registrierung registrierung) {
		termin1Freigeben(registrierung);
		termin2Freigeben(registrierung);
	}

	public void termin1Freigeben(@NonNull Registrierung registrierung) {
		final Impftermin impftermin1 = registrierung.getImpftermin1();
		if (impftermin1 != null) {
			// Bevor wir den Termin freigeben: Nochmals sicherstellen, dass keine Impfung an diesem Termin haengt
			terminFreigeben(impftermin1);
			registrierung.setImpftermin1FromImpfterminRepo(null);
			db.persist(impftermin1);
			db.flush();
		}
	}

	public void termin2Freigeben(@NonNull Registrierung registrierung) {
		final Impftermin impftermin2 = registrierung.getImpftermin2();
		if (impftermin2 != null){
			// Bevor wir den Termin freigeben: Nochmals sicherstellen, dass keine Impfung an diesem Termin haengt
			terminFreigeben(impftermin2);
			registrierung.setImpftermin2FromImpfterminRepo(null);
			db.persist(impftermin2);
			db.flush();
		}
	}

	public void boosterTerminFreigeben(@NonNull Impfdossiereintrag impfdossiereintrag) {
		Impftermin impftermin = impfdossiereintrag.getImpftermin();
		if (impftermin != null) {
			// Bevor wir den Termin freigeben: Nochmals sicherstellen, dass keine Impfung an diesem Termin haengt
			terminFreigeben(impftermin);
			impfdossiereintrag.setImpfterminFromImpfterminRepo(null);
			db.persist(impftermin);
			db.flush();
		}
	}

	private void terminFreigeben(@NonNull Impftermin termin) {
		assertTerminHasNoImpfung(termin);
		termin.setGebuchtFromImpfterminRepo(false);
		termin.setRegistrierungsnummerReserviert(null);
		termin.setTimestampReserviert(null);
		// Den Offset zuruecksetzen, falls die Kapazitaet spaeter vermindert wird und kein Offset mehr gebraucht wird
		termin.setOffsetInMinutes(0);
	}

	private void assertTerminHasNoImpfung(@NonNull Impftermin termin) {
		final Optional<Impfung> existingImpfung = impfungRepo.getByImpftermin(termin);
		if (existingImpfung.isPresent()) {
			throw AppValidationMessage.IMPFTERMIN_WITH_EXISTING_IMPFUNG.create(termin.getId());
		}
	}

	private void assertTerminNotReserviertForAnotherRegistration(@NonNull Registrierung registrierung, @NonNull Impftermin termin) {

		if (!registrierung.getRegistrierungsnummer().equals(termin.getRegistrierungsnummerReserviert())
			&& termin.getTimestampReserviert() != null
			&& termin.getTimestampReserviert().isAfter(LocalDateTime.now().minusMinutes(terminReservationDauerInMinutes))) {
				throw AppValidationMessage.IMPFTERMIN_WITH_EXISTING_RESERVATION.create(termin.getId());

		}
	}

	@NonNull
	private Predicate isTerminNichtReserviert() {
		if (terminReservationEnabled) {
			return QImpftermin.impftermin.timestampReserviert.isNull()
				.or(terminReservationAbgelaufen());
		}
		// Expression, die immer TRUE ist
		return Expressions.asBoolean(Expressions.constant(true)).isTrue();
	}


	@NonNull
	private Predicate isFuerMichReservierterTermin(@NonNull Registrierung registrierung) {
		if (terminReservationEnabled) {
			return QImpftermin.impftermin.registrierungsnummerReserviert.eq(registrierung.getRegistrierungsnummer())
				.and((terminReservationAbgelaufen().not()));
		}
		// Expression, die immer FALSE ist
		return Expressions.asBoolean(Expressions.constant(true)).isTrue();
	}

	@NonNull
	private Predicate terminReservationAbgelaufen() {
		return QImpftermin.impftermin.timestampReserviert.before(LocalDateTime.now().minusMinutes(terminReservationDauerInMinutes));
	}
}
