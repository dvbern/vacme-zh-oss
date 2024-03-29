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
import java.util.List;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.Query;
import javax.transaction.Transactional;

import ch.dvbern.oss.vacme.entities.booster.QRegistrierungQueue;
import ch.dvbern.oss.vacme.entities.booster.RegistrierungQueue;
import ch.dvbern.oss.vacme.entities.booster.RegistrierungQueueStatus;
import ch.dvbern.oss.vacme.entities.booster.RegistrierungQueueTyp;
import ch.dvbern.oss.vacme.entities.util.DBConst;
import ch.dvbern.oss.vacme.smartdb.Db;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.nullness.qual.NonNull;

@RequestScoped
@Transactional
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BoosterQueueRepo {

	private final Db db;

	public int queueRelevantRegsForImpfschutzRecalculation(){
		String query = "INSERT INTO RegistrierungQueue (id, timestampErstellt, timestampMutiert, userErstellt, "
			+ "userMutiert, version, errorCount, lastError, registrierungNummer, status, typ) "
			+ "SELECT NEXTVAL(req_queue_sequence), now(), now(), '%s', '%s', 1, 0, NULL, registrierungsnummer, "
			+ "'NEW', 'BOOSTER_RULE_RECALCULATION' FROM Registrierung LEFT JOIN Impfdossier I "
			+ "ON Registrierung.id = I.registrierung_id WHERE vollstaendigerImpfschutz is true or I.id is not null;";
		query =  String.format(query, DBConst.SYSTEM_ADMIN_ID, DBConst.SYSTEM_ADMIN_ID);
		final Query nativeQuery = db.getEntityManager().createNativeQuery(query);
		return nativeQuery.executeUpdate();
	}

	public void createRegistrierungQueueItems(@NonNull List<String> regNumsToCalculate) {
		for (String regNum : regNumsToCalculate) {
			RegistrierungQueue registrierungQueue = RegistrierungQueue.forRecalculation(regNum);
			Long nextQueueSequence = this.getNextQueueId();
			registrierungQueue.setId(nextQueueSequence);
			db.persist(registrierungQueue);
		}
	}

	public long removeAllSuccessfullEntries(){
		long deletedNum = db.delete(QRegistrierungQueue.registrierungQueue)
			.where(QRegistrierungQueue.registrierungQueue.status.eq(RegistrierungQueueStatus.SUCCESS))
			.execute();
		return deletedNum;
	}

	@NonNull
	public List<RegistrierungQueue> findRegsToRecalculateImpfschutzFromQueue(long batchsize) {
		return db.select(QRegistrierungQueue.registrierungQueue)
			.from(QRegistrierungQueue.registrierungQueue)
			.where(QRegistrierungQueue.registrierungQueue.typ.eq(RegistrierungQueueTyp.BOOSTER_RULE_RECALCULATION)
				.and(QRegistrierungQueue.registrierungQueue.status.in(RegistrierungQueueStatus.NEW, RegistrierungQueueStatus.FAILED_RETRY)))
			.orderBy(QRegistrierungQueue.registrierungQueue.timestampErstellt.asc()) // Die aeltesten zuerst
			.limit(batchsize)
			.fetch();
	}

	@NonNull
	public Long getNextQueueId(){
		return ((BigInteger) this.db.getEntityManager()
			.createNativeQuery("SELECT NEXT VALUE FOR req_queue_sequence;")
			.getSingleResult()).longValue();
	}

	public void updateQueueItem(@NonNull RegistrierungQueue queueItem) {
		db.merge(queueItem);
	}
}
