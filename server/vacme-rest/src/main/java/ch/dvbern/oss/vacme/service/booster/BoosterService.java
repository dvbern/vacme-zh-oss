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

package ch.dvbern.oss.vacme.service.booster;

import java.util.List;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import ch.dvbern.oss.vacme.entities.booster.RegistrierungQueue;
import ch.dvbern.oss.vacme.entities.booster.RegistrierungQueueTyp;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossier;
import ch.dvbern.oss.vacme.entities.impfen.Impfschutz;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFileTyp;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.repo.BoosterQueueRepo;
import ch.dvbern.oss.vacme.repo.ImpfdossierRepo;
import ch.dvbern.oss.vacme.repo.RegistrierungRepo;
import ch.dvbern.oss.vacme.service.ConfirmationService;
import ch.dvbern.oss.vacme.service.FragebogenService;
import ch.dvbern.oss.vacme.service.boosterprioritaet.BoosterPrioUtil;
import ch.dvbern.oss.vacme.service.boosterprioritaet.BoosterPrioritaetService;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;

import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ABGESCHLOSSEN;
import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BoosterService {

	private final RegistrierungRepo registrierungRepo;
	private final ConfirmationService confirmationService;
	private final BoosterPrioritaetService boosterPrioritaetService;
	private final BoosterQueueRepo boosterQueueRepo;
	private final FragebogenService fragebogenService;
	private final ImpfinformationenService impfinformationenService;
	private final ImpfdossierRepo impfdossierRepo;


	@NonNull
	public List<String> findRegsToMoveToImmunisiert(long limit) {
		return this.registrierungRepo.findRegsWithVollstImpfschutzToMoveToImmunisiert(limit);
	}

	@Transactional(TxType.REQUIRES_NEW)
	public boolean moveStatusToImmunisiertForRegistrierung(@NonNull String regNum) {
		try {
			Optional<Registrierung> regOpt = registrierungRepo.getByRegistrierungnummer(regNum);
			Registrierung registrierung = regOpt
				.orElseThrow(() -> AppFailureException.entityNotFound(Registrierung.class, regNum));
			moveStatusToImmunisiertForRegistrierung(registrierung);
			return true;

		} catch (Exception exception) {
			LOG.error("VACME-BOOSTER-IMMUNISIERT: Error during statuschange  for {} ", regNum, exception);
			return false;
		}
	}

	private void moveStatusToImmunisiertForRegistrierung(Registrierung registrierung) {
		registrierungRepo.createSnapshot(registrierung);
		// explizit nicht ueber registrierung#setStatusToImmunisiert();
		// da wir davon ausgehen dass das Zertifikat schon generiert wurde weil wir nur abgeschlossene laden
		registrierung.setRegistrierungStatus(RegistrierungStatus.IMMUNISIERT);
		registrierung.setGewuenschterOdi(null);
		registrierungRepo.update(registrierung);
	}

	@Transactional(TxType.REQUIRES_NEW)
	public boolean calculateImpfschutz(@NonNull RegistrierungQueue queueItem ) {
		String regNum = queueItem.getRegistrierungNummer();
		try {
			if (!queueItem.needsToRecalculate()) {
				LOG.warn("VACME-BOOSTER-RULE_ENGINE: queueItem {} is not ready to be recalculated. Maybe it was already processed?", queueItem.getId());
				return true;
			}

			calculateAndStoreImpfschutz(regNum);
			queueItem.markSuccessful();
			return true;

		} catch (Exception exception) {
			LOG.error("VACME-BOOSTER-RULE_ENGINE: Error during Impfschutzcalculation  for {} ", regNum, exception);
			queueItem.markFailed(exception.getMessage());
			return false;
		} finally {
			if (queueItem.getTyp() == RegistrierungQueueTyp.BOOSTER_RULE_RECALCULATION) {
				boosterQueueRepo.updateQueueItem(queueItem);
			}
		}
	}

	private Optional<Impfschutz> calculateAndStoreImpfschutz(String regNum) {
		Fragebogen fragebogen = fragebogenService.findFragebogenByRegistrierungsnummerNoCheck(regNum);
		ImpfinformationDto impfinformationDto = impfinformationenService.getImpfinformationenNoCheck(regNum);

		Optional<Impfschutz> impfschutzOpt = boosterPrioritaetService.calculateImpfschutz(fragebogen, impfinformationDto);
		if (impfschutzOpt.isEmpty()) {
			LOG.trace("VACME-BOOSTER-RULE_ENGINE: Die Registrierung {} erfuellt nicht die Kriterien um einen Impfschutz zu erhalten gemeass den aktuellen Regeln ", regNum);
		}
		Impfdossier impfdossierToUpdt = impfdossierRepo.getOrCreateImpfdossier(impfinformationDto.getRegistrierung());
		impfdossierRepo.updateImpfschutz(impfdossierToUpdt, impfschutzOpt.orElse(null));

		return impfschutzOpt;
	}

	public void recalculateImpfschutzAndStatusmovesForSingleReg(@NonNull Registrierung reg) {
		// Wenn noetig verschieben nach immunisiert
		if (needsToBeMovedFromAbgeschlToImmunisiert(reg)) {
			moveStatusToImmunisiertForRegistrierung(reg);
		} else {
			LOG.debug("Registrierung {} hat Kriterien zum verschieben nach Immunisiert nicht erfuellt, status"
				+ " {}", reg.getRegistrierungsnummer(), reg.getRegistrierungStatus());
		}
		// Impschutz immer neu berechnen, sogar wenn wir keinen vollst. Impfschutz haben weil wir ihn dann evtl loeschen mussen
		Optional<Impfschutz> impfschutzOpt = calculateAndStoreImpfschutz(reg.getRegistrierungsnummer());

		// verschieben nach Freigegeben
		if (BoosterPrioUtil.meetsCriteriaForFreigabeBooster(reg, impfschutzOpt.orElse(null))) {
			moveStatusToFreigegebenAndSendBenachrichtigung(reg);
		} else {
			LOG.debug("Registrierung {} hat Kriterien zum verschieben nach Freigegeben_Booster nicht erfuellt, status"
					+ " {}, freigabe ab {}", reg.getRegistrierungsnummer(), reg.getRegistrierungStatus(),
				impfschutzOpt.orElse(new Impfschutz()).getFreigegebenNaechsteImpfungAb());
		}
	}

	/**
	 * prueft ob die Registrierung die Bedingungen erfuellt um nach Immunisiert verschoben zu werden. Beim batchjob wird dies durch das Query sichergestellt
	 *
	 */
	private boolean needsToBeMovedFromAbgeschlToImmunisiert(@NonNull Registrierung reg) {
		boolean vollstImpfschutz = Boolean.TRUE.equals(reg.getVollstaendigerImpfschutz());
		boolean isMoveableStatus =  reg.getRegistrierungStatus() == ABGESCHLOSSEN || reg.getRegistrierungStatus() == ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG;
		boolean nichtVerstorben = !Boolean.TRUE.equals(reg.getVerstorben());

		return vollstImpfschutz && isMoveableStatus && nichtVerstorben;
	}

	@NonNull
	public List<String> findRegsToMoveToFreigegebenBooster(long limit) {
		return this.registrierungRepo.findRegsToMoveToFreigegebenBooster(limit);
	}

	@Transactional(TxType.REQUIRES_NEW)
	public boolean moveStatusToFreigegebenAndSendBenachrichtigung(@NonNull String regNum) {
		try {
			Optional<Registrierung> regOpt = registrierungRepo.getByRegistrierungnummer(regNum);
			Registrierung registrierung = regOpt.orElseThrow(() -> AppFailureException.entityNotFound(Registrierung.class,
				regNum));

			moveStatusToFreigegebenAndSendBenachrichtigung(registrierung);
			return true;

		} catch (Exception exception) {
			LOG.error("VACME-BOOSTER-FREIGABEMOVE: Error during statuschange  for {} ", regNum, exception);
			return false;
		}
	}

	private void moveStatusToFreigegebenAndSendBenachrichtigung(Registrierung registrierung) {
		registrierung.setRegistrierungStatus(RegistrierungStatus.FREIGEGEBEN_BOOSTER);
		registrierungRepo.update(registrierung);
		confirmationService.sendConfirmationNoBoosterTermin(RegistrierungFileTyp.FREIGABE_BOOSTER_INFO, registrierung);
	}

	public int queueAllRegsForBoosterRuleengine() {
		LOG.info("VACME-BOOSTER-RULE_ENGINE: Loesche alle erfolgreich durchgefuehrten Queue Items");
		long deletedNum = this.boosterQueueRepo.removeAllSuccessfullEntries();
		LOG.info("VACME-BOOSTER-RULE_ENGINE: Loeschen von {} items beendet", deletedNum);
		LOG.info("VACME-BOOSTER-RULE_ENGINE: Ermittle zu berechnende Registierungen...");
		int num =  this.boosterQueueRepo.queueRelevantRegsForImpfschutzRecalculation();
				LOG.info("VACME-BOOSTER-RULE_ENGINE: Einfuegen von {} QueueItems zur Impfschutzneuberechnung beendet", num);
		return num;
	}
}
