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

package ch.dvbern.oss.vacme.service.boosterprioritaet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import ch.dvbern.oss.vacme.entities.base.ImpfInfo;
import ch.dvbern.oss.vacme.entities.externeimpfinfos.ExternesZertifikat;
import ch.dvbern.oss.vacme.entities.impfen.Erkrankung;
import ch.dvbern.oss.vacme.entities.impfen.Impfschutz;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Prioritaet;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.types.daterange.DateUtil;
import ch.dvbern.oss.vacme.enums.Mandant;
import ch.dvbern.oss.vacme.i18n.MandantUtil;
import ch.dvbern.oss.vacme.service.boosterprioritaet.rules.BoosterAgePunktePrioritaetImpfstoffRule;
import ch.dvbern.oss.vacme.service.boosterprioritaet.rules.IBoosterPrioritaetRule;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Slf4j
@ApplicationScoped
public class BoosterPrioritaetService {

	final List<IBoosterPrioritaetRule> rules = new ArrayList<>();

	@ConfigProperty(name = "boosterrule.enable.pfizer.only.for.u30", defaultValue = "false")
	boolean enablePfizerOnlyForU30 = false;

	@ConfigProperty(name = "boosterrule.zh.minage", defaultValue = "16")
	public int minAgeZh = 16;

	@ConfigProperty(name = "boosterrule.be.minage", defaultValue = "16")
	public int minAgeBe = 16;

	@ConfigProperty(name = "boosterrule.freigabeoffset.impfung.monate", defaultValue = "6")
	public int freigabeOffsetImpfungMonate = 6;

	@ConfigProperty(name = "boosterrule.freigabeoffset.impfung.tage", defaultValue = "0")
	public int freigabeOffsetImpfungTage = 0;

	@NonNull
	@ConfigProperty(name = "boosterrule.freigabeoffset.krankheit.monate")
	public Optional<Integer> freigabeOffsetKrankheitMonate = Optional.empty();

	@NonNull
	@ConfigProperty(name = "boosterrule.freigabeoffset.krankheit.tage")
	public Optional<Integer> freigabeOffsetKrankheitTage = Optional.empty();

	@NonNull
	@ConfigProperty(name = "boosterrule.selbstzahler.cutoff")
	public Optional<LocalDate> cutoffSelbstzahler = Optional.empty();


	@ConfigProperty(name = "boosterrule.be.priorities", defaultValue = "A,B,C,D,E,F,G,H,I,O")
	protected List<String> prioritiesBe = List.of("A","B","C","D","E","F","G","H","I","O");


	@NonNull
	private ImpfstoffInfosForRules specifiedImpfstoffe;

	@Inject
	public BoosterPrioritaetService(@NonNull ImpfstoffInfosForRules specifiedImpfstoffe) {
		this.specifiedImpfstoffe = specifiedImpfstoffe;
	}

	/**
	 * Initialisiere die Regeln mit ihren Parametern
	 */
	@PostConstruct
	void initRules() {
		if (MandantUtil.getMandant().equals(Mandant.ZH)) {
			addZhRules();
		} else if (MandantUtil.getMandant().equals(Mandant.BE)) {
			addBeRules();
		}
		// do init in postConstruct so all injected configs are ready to be used
		orderListByAnzahlMonateBisFreigabe();
	}

	void addBeRules() {
		// 1. Booster
		rules.add(BoosterAgePunktePrioritaetImpfstoffRule.createMinAgeRule(
			specifiedImpfstoffe,
			minAgeBe,
			freigabeOffsetImpfungMonate, freigabeOffsetImpfungTage,
			freigabeOffsetKrankheitMonate.orElse(null), freigabeOffsetKrankheitTage.orElse(null),
			0, 0,
			enablePfizerOnlyForU30, false, null));
		// ab 2. Booster
		Set<Prioritaet> prioritaeten = prioritiesBe.stream().map(Prioritaet::valueOfCode).collect(Collectors.toSet());
		rules.add(BoosterAgePunktePrioritaetImpfstoffRule.createPrioritaetenRule(
			specifiedImpfstoffe,
			prioritaeten,
			freigabeOffsetImpfungMonate, freigabeOffsetImpfungTage,
			freigabeOffsetKrankheitMonate.orElse(null), freigabeOffsetKrankheitTage.orElse(null),
			1, null,
			enablePfizerOnlyForU30, false, null));

		rules.add(BoosterAgePunktePrioritaetImpfstoffRule.createMinAgeRule(
			specifiedImpfstoffe,
			65,
			freigabeOffsetImpfungMonate, freigabeOffsetImpfungTage,
			freigabeOffsetKrankheitMonate.orElse(null), freigabeOffsetKrankheitTage.orElse(null),
			1, null,
			enablePfizerOnlyForU30, false, null));
		addCommonRules();
	}

	void addZhRules() {
		// Alle ab 12 sind als Selbstzahler freigegeben
		rules.add(BoosterAgePunktePrioritaetImpfstoffRule.createMinAgeRule(
			specifiedImpfstoffe,
			12,
			freigabeOffsetImpfungMonate, freigabeOffsetImpfungTage,
			freigabeOffsetKrankheitMonate.orElse(null), freigabeOffsetKrankheitTage.orElse(null),
			0, null,
			enablePfizerOnlyForU30, true, null));
		addCommonRules();
	}

	private void addCommonRules() {
		rules.add(BoosterAgePunktePrioritaetImpfstoffRule.createOhneFreigabeRule(
			specifiedImpfstoffe,
			enablePfizerOnlyForU30)); // rule die alle Regs matched und nur das immunisiertBis berechnet
	}

	private void orderListByAnzahlMonateBisFreigabe() {
		Comparator<IBoosterPrioritaetRule> comparator =
			Comparator.comparing(
				IBoosterPrioritaetRule::getAnzahlMonateBisFreigabe, Comparator.nullsLast(Comparator.naturalOrder())
			);
		rules.sort(comparator);
	}

	/**
	 * Funktion welche fuer die gegeebene Registrierung berechnen soll ob und wie lange ihr Impfschutz noch besteht
	 *
	 * @param fragebogen Am Fragebogen haengt auch die Registrierung
	 * @param impfinformationDto DTO mit Impfinformationen zu Impfung 1/2 und Boosterimpfungen
	 * @return Impfschutz wenn die Person bereits einen Impfschutz hat oder null wenn nicht. Achtung es wird immer ein neues
	 * Object zurueckgegeben auch wenn im impfinformationDto schon eines besteht
	 */
	@NonNull
	public Optional<Impfschutz> calculateImpfschutz(
		@NonNull Fragebogen fragebogen,
		@NonNull ImpfinformationDto impfinformationDto
	) {
		if (isGrundimmunisiertGemaessVacMeOrSelbstdeklaration(impfinformationDto.getRegistrierung())) {
			return findImpfschutzMitFruehesterFreigabe(fragebogen, impfinformationDto);
		}
		return Optional.empty();
	}

	@NonNull
	private Optional<Impfschutz> findImpfschutzMitFruehesterFreigabe(
		@NonNull Fragebogen fragebogen,
		@NonNull ImpfinformationDto impfinformationDto
	) {
		List<ImpfInfo> orderedImpfInfos = BoosterPrioUtil.getImpfinfosOrderedByTimestampImpfung(impfinformationDto);
		List<Erkrankung> orderedErkrankungen = impfinformationDto.getImpfdossier() == null ? List.of() : impfinformationDto.getImpfdossier().getErkrankungenSorted();
		final ExternesZertifikat externesZertifikat = impfinformationDto.getExternesZertifikat();

		return rules.stream()
			.map(rule -> rule.calculateImpfschutz(
				fragebogen,
				orderedImpfInfos,
				orderedErkrankungen,
				externesZertifikat)) // pro Regel den Impfschutz ausrechnen
			.reduce(this::getMergedImpfschutz)
			.orElse(Optional.empty());
	}

	@NonNull
	private Optional<Impfschutz> getMergedImpfschutz(
		@NonNull Optional<Impfschutz> impfschutzOptional1,
		@NonNull Optional<Impfschutz> impfschutzOptional2
	){
		if (impfschutzOptional1.isPresent()) {
			// 1 vorhanden, 2 nicht: 1
			if (impfschutzOptional2.isEmpty()) {
				return impfschutzOptional1;
			}
			// beide vorhanden: frueheres nehmen
			return Optional.of(mergeImpfschuetze(impfschutzOptional1.get(), impfschutzOptional2.get()));
		}
		// 1 nicht vorhanden: 2
		return impfschutzOptional2;
	}

	/**
	 * Immer den fuer den Impfling beste variante nehmen
	 * @param impfschutzA
	 * @param impfschutzB
	 * @return
	 */
	@NonNull
	private Impfschutz mergeImpfschuetze(
		@NonNull Impfschutz impfschutzA,
		@NonNull Impfschutz impfschutzB
	) {
		@Nullable LocalDateTime mergedImmunisiertBis =
			DateUtil.getLaterDateTimeOrNull(impfschutzA.getImmunisiertBis(), impfschutzB.getImmunisiertBis());
		@Nullable LocalDateTime mergedFreigegebenAb =
			DateUtil.getEarlierDateTimeOrNull(impfschutzA.getFreigegebenNaechsteImpfungAb(), impfschutzB.getFreigegebenNaechsteImpfungAb());
		@Nullable LocalDateTime mergedFreigegebenAbSelbstzahler =
			DateUtil.getEarlierDateTimeOrNull(impfschutzA.getFreigegebenAbSelbstzahler(), impfschutzB.getFreigegebenAbSelbstzahler());
		Set<UUID> mergedErlaubteImpfstoffe =
			new HashSet<>(impfschutzA.getErlaubteImpfstoffeCollection());
		mergedErlaubteImpfstoffe.addAll(impfschutzB.getErlaubteImpfstoffeCollection());

		return new Impfschutz(
			mergedImmunisiertBis,
			mergedFreigegebenAb,
			mergedFreigegebenAbSelbstzahler,
			mergedErlaubteImpfstoffe,
			impfschutzA.isBenachrichtigungBeiFreigabe());
	}

	/**
	 * berechnet ob eine Registrierung ueberhaupt schon grundimmunisiert ist und somit fuer eine Boosterimpfung in Frage
	 * kommt
	 *
	 * @param registrierung Die Registrierung
	 * @return true wenn vollstaendiger Impfschutz erreicht ist
	 */
	boolean isGrundimmunisiertGemaessVacMeOrSelbstdeklaration(@NonNull Registrierung registrierung) {
		return Boolean.TRUE.equals(registrierung.getVollstaendigerImpfschutz());
	}
}
