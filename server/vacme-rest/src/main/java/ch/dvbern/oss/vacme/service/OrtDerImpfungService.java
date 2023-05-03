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

package ch.dvbern.oss.vacme.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import ch.dvbern.oss.vacme.entities.UserPrincipal;
import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.Impfstoff;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impfslot;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import ch.dvbern.oss.vacme.entities.types.BenutzerRolle;
import ch.dvbern.oss.vacme.entities.types.FachRolle;
import ch.dvbern.oss.vacme.entities.types.ZulassungsStatus;
import ch.dvbern.oss.vacme.jax.registration.ImpfstoffJax;
import ch.dvbern.oss.vacme.jax.registration.OdiUserDisplayNameJax;
import ch.dvbern.oss.vacme.jax.registration.OrtDerImpfungJax;
import ch.dvbern.oss.vacme.repo.ImpfstoffRepo;
import ch.dvbern.oss.vacme.repo.ImpfterminRepo;
import ch.dvbern.oss.vacme.repo.OrtDerImpfungRepo;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.shared.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.opentracing.Traced;
import org.jetbrains.annotations.NotNull;
import org.keycloak.admin.client.resource.UserResource;

@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class OrtDerImpfungService {

	@ConfigProperty(name = "vacme.cache.nextfrei.maxrange.months", defaultValue = Constants.DEFAULT_CUTOFF_TIME_MONTHS_FOR_FREE_TERMINE)
	String maxrangeToLookForFreieTermineInMonths;


	@ConfigProperty(name = "vacme.query.slow.threshold.ms", defaultValue = "10000")
	Long vacmeQuerySlowThresholdMs;


	private final UserPrincipal userPrincipal;
	private final OrtDerImpfungRepo ortDerImpfungRepo;
	private final ImpfterminRepo impfterminRepo;
	private final ImpfslotService impfslotService;
	private final KeyCloakService keyCloakService;
	private final OdiFilterService odiFilterService;
	private final SettingsService settingsService;
	private final ImpfstoffRepo impfstoffRepo;
	private final GeocodeService geocodeService;


	@NonNull
	public OrtDerImpfung getById(@NonNull ID<OrtDerImpfung> ortDerImpfungId) {
		return ortDerImpfungRepo
			.getById(ortDerImpfungId)
			.orElseThrow(() -> AppValidationMessage.UNKNOWN_ODI.create(ortDerImpfungId));
	}

	@NonNull
	public List<Impfslot> getFreieImpftermine(@NonNull ID<OrtDerImpfung> ortDerImpfungId,
											  @NonNull Impffolge impffolge,
											  @NonNull LocalDate date) {
		OrtDerImpfung ortDerImpfung = ortDerImpfungRepo
			.getById(ortDerImpfungId)
			.orElseThrow(() -> AppValidationMessage.UNKNOWN_ODI.create(ortDerImpfungId));
		if (ortDerImpfung.isTerminverwaltung()) {
			return impfterminRepo.findFreieImpfslots(ortDerImpfung, impffolge, date);
		}
		// Dieser OrtDerImpfung hat keine Terminvergabe
		return Collections.emptyList();
	}

	/**
	 * Query welches den ersten freien Termin relativ zum uebergebenen Temrin an einem OdI zurueckgibt
	 * @param ortDerImpfungId der relevante OdI fuer den der nachste Termin gesucht wird
	 * @param impffolge die Impffolge fuer die ein Termin gesucht wird
	 * @param otherTerminDate der Termin relativ zu dem der 2. gesucht wird
	 * @param limitMaxFutureDate wenn true dann wird der maximalabstand fuer die Terminsuche  beruecksichtigt,
	 * 		nur relevant wenn otherTerminDate gesetzt ist
	 * @return Datum an dem sich der naechste Termin befindet der allen Anforderungen entspricht
	 */
	@Nullable
	@Traced
	public LocalDateTime getNextFreierImpftermin(
		@NonNull ID<OrtDerImpfung> ortDerImpfungId,
		@NonNull Impffolge impffolge,
		@Nullable LocalDateTime otherTerminDate,
		boolean limitMaxFutureDate
	) {
		OrtDerImpfung ortDerImpfung = ortDerImpfungRepo
			.getById(ortDerImpfungId)
			.orElseThrow(() -> AppValidationMessage.UNKNOWN_ODI.create(ortDerImpfungId));
		if (ortDerImpfung.isTerminverwaltung()) {
			LocalDate minDate = null;
			LocalDate maxDate = null;
			if (otherTerminDate != null) {
				// es wird die desired distance als der "Optimale" Abstand vorgeschlagen
				minDate = calculateMinDateWithoutToleranzBefore(otherTerminDate.toLocalDate(), impffolge);
				if (limitMaxFutureDate) { // VACME-437 Impfkontrolle/Impfdoku: Termin Heute.
					maxDate = calculateMaxDate(otherTerminDate.toLocalDate(), impffolge);
				}
			}

			// Wir wollen fruehestens ab morgen (auch wenn aufgrund des anderen Termins ein frueheres Min-Datum verlangt wuerde)
			if (minDate == null || minDate.isBefore(LocalDate.now().plusDays(1))) {
				minDate = LocalDate.now().plusDays(1);
			}
			// Wir zeigen Termine bis in spaetestens drei Monaten an
			if (maxDate == null) {
				maxDate = LocalDate.now().plusMonths(getTerminsucheMaxRangeMonths());
			}

			LocalDateTime impfslotBisTime = impfterminRepo.findNextFreierImpftermin(ortDerImpfung, impffolge, minDate, maxDate);
			if (impfslotBisTime == null && otherTerminDate != null) {
				// wenn wir nichts gefunden haben mit genau 28 Tagen dann versuchen wir noch mit minimum
				// Allerdings muessen wir jetzt nur noch vom (jetzt frueheren) Min bis zum bisherigen Min suchen, da wir ab
				// dann bereits gesucht und nichts gefunden haben
				maxDate = minDate;
				minDate = calculateMinDate(otherTerminDate.toLocalDate(), impffolge);
				impfslotBisTime = impfterminRepo.findNextFreierImpftermin(ortDerImpfung, impffolge, minDate, maxDate);
			}
			if (impfslotBisTime != null) {
				return impfslotBisTime.toLocalDate().atStartOfDay().plusHours(12);
			}
		}
		return null;
	}

	private long getTerminsucheMaxRangeMonths() {
		try {
			String maxMonthsFuture = maxrangeToLookForFreieTermineInMonths;
			if (maxMonthsFuture == null) {
				final Config config = ConfigProvider.getConfig(); // read from static config if not set otherwise, makes testing easier
				maxMonthsFuture = config.getValue("vacme.cache.nextfrei.maxrange.months", String.class);
			}
			return Long.parseLong(maxMonthsFuture);
		} catch (NumberFormatException exception) {
			LOG.error("Missconfiguration: vacme.cache.nextfrei.maxrange.months must be numeric, using default value " + Constants.DEFAULT_CUTOFF_TIME_MONTHS_FOR_FREE_TERMINE );
			return Long.parseLong(Constants.DEFAULT_CUTOFF_TIME_MONTHS_FOR_FREE_TERMINE);
		}
	}

	private LocalDate calculateMinDateWithoutToleranzBefore(LocalDate otherTerminDate, Impffolge impffolge) {
		// Neu wird als minimum der "Optimale" Abstand vorgeschlagen
		final int minimumDaysBetweenImpfungenInFuture = this.settingsService.getSettings().getDistanceImpfungenDesired();
		final int minimumDaysBetweenImpfungenInPast = this.settingsService.getSettings().getDistanceImpfungenMaximal();
		switch (impffolge) {
			case ERSTE_IMPFUNG: // otherTerminDate ist vom 2 Termin. Wir muessen 28 + 5 in der Vergangenheit suchen
				return otherTerminDate.minusDays(minimumDaysBetweenImpfungenInPast);
			case ZWEITE_IMPFUNG: // otherTerminDate ist vom 1 Termin. Wir muessen 28 - 1 in der Zukunft suchen
				return otherTerminDate.plusDays(minimumDaysBetweenImpfungenInFuture);
		}
		return otherTerminDate;
	}

	private LocalDate calculateMinDate(LocalDate otherTerminDate, Impffolge impffolge) {
		final int minimumDaysBetweenImpfungenInFuture = this.settingsService.getSettings().getDistanceImpfungenMinimal();
		final int minimumDaysBetweenImpfungenInPast = this.settingsService.getSettings().getDistanceImpfungenMaximal();
		switch (impffolge) {
		case ERSTE_IMPFUNG: // otherTerminDate ist vom 2 Termin. Wir muessen 28 + 5 in der Vergangenheit suchen
			return otherTerminDate.minusDays(minimumDaysBetweenImpfungenInPast);
		case ZWEITE_IMPFUNG: // otherTerminDate ist vom 1 Termin. Wir muessen 28 - 1 in der Zukunft suchen
			return otherTerminDate.plusDays(minimumDaysBetweenImpfungenInFuture);
		}
		return otherTerminDate;
	}

	private LocalDate calculateMaxDate(LocalDate referenzDate, Impffolge impffolge) {
		final int maximumDaysBetweenImpfungenInFuture = this.settingsService.getSettings().getDistanceImpfungenMaximal();
		final int maximumDaysBetweenImpfungenInPast = this.settingsService.getSettings().getDistanceImpfungenMinimal();
		switch (impffolge) {
			case ERSTE_IMPFUNG: // otherTerminDate ist vom 2 Termin. Wir muessen 28 - 1 in der Vergangenheit suchen
				return referenzDate.minusDays(maximumDaysBetweenImpfungenInPast);
			case ZWEITE_IMPFUNG: // otherTerminDate ist vom 1 Termin. Wir muessen 28 + 5 in der Zukunft suchen
				return referenzDate.plusDays(maximumDaysBetweenImpfungenInFuture);
		}
		return referenzDate;
	}



	@NonNull
	private List<OrtDerImpfung> findAll() {
		return ortDerImpfungRepo.findAll();
	}

	/**
	 * @param fragebogen Fragebogen / Registration that will be used to check the filtercriteria against
	 * @return List of OdI that were not filtered out due to filtercriteria that were not matched by the passed
	 * fragebogen
	 */
	@NonNull
	@Traced
	public List<OrtDerImpfung> findAllActivePublicFiltered(@NonNull Fragebogen fragebogen) {
		return odiFilterService.filterOdisForRegistrierung(fragebogen);
	}

	@NonNull
	public List<OrtDerImpfung> findAllActivePublic() {
		List <OrtDerImpfung> odis = ortDerImpfungRepo.findAllActiveOeffentlich();
		return odis;
	}

	@NonNull
	public List<OrtDerImpfung> findAllForCurrentBenutzer() {
		if (userPrincipal.isCallerInAnyOfRole(BenutzerRolle.getOrtDerImpfungRoles())) {
			return new ArrayList<>(userPrincipal.getBenutzerOrThrowException().getOrtDerImpfung());
		}
		return findAll();
	}

	/**
	 * Erstellt eine neue OrtDerImpfung (ODI) und initialisiert dessen Impfslot's
	 */
	@NonNull
	public OrtDerImpfung createOrtDerImpfung(@NonNull Consumer<OrtDerImpfung> ortDerImpfungConsumer) {
		OrtDerImpfung ortDerImpfung = new OrtDerImpfung();
		ortDerImpfungConsumer.accept(ortDerImpfung);
		if (ortDerImpfungRepo.getByName(ortDerImpfung.getName()).isPresent()) {
			throw AppValidationMessage.EXISTING_ORTDERIMPFUNG.create(ortDerImpfung.getName());
		}
		ortDerImpfungRepo.create(ortDerImpfung);
		if (ortDerImpfung.isTerminverwaltung()) {
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime startDay = LocalDateTime.of(now.getYear(), now.getMonth(), 1, 0, 0);
			impfslotService.createEmptyImpfslots(ortDerImpfung, startDay, 3);
		}

		assert ortDerImpfung.getFachverantwortungbabKeyCloakId() != null;
		keyCloakService.joinGroup(ortDerImpfung.getFachverantwortungbabKeyCloakId(), ortDerImpfung.getIdentifier());

		if (ortDerImpfung.getOrganisationsverantwortungKeyCloakId() != null) {
			keyCloakService.joinGroup(ortDerImpfung.getOrganisationsverantwortungKeyCloakId(), ortDerImpfung.getIdentifier());
		}

		return ortDerImpfung;
	}

	@NonNull
	public OrtDerImpfung updateOrtDerImpfung(@NonNull OrtDerImpfung ortDerImpfung, @NonNull OrtDerImpfungJax ortDerImpfungJax) {
		if (this.userPrincipal.isCallerInRole(BenutzerRolle.AS_REGISTRATION_OI)) {
			final Consumer<OrtDerImpfung> updateEntityConsumer = ortDerImpfungJax.getUpdateEntityConsumer(true, geocodeService);
			updateEntityConsumer.accept(ortDerImpfung);
			addImpfstoffeToOdi(ortDerImpfung, ortDerImpfungJax.getImpfstoffe());
		} else {
			final Consumer<OrtDerImpfung> updateEntityConsumer = ortDerImpfungJax.getUpdateEntityConsumer(false, geocodeService);
			updateEntityConsumer.accept(ortDerImpfung);
		}

		final OrtDerImpfung update = ortDerImpfungRepo.update(ortDerImpfung);

		assert ortDerImpfung.getFachverantwortungbabKeyCloakId() != null;
		changeUserWithRoleInGroup(FachRolle.FACHVERANTWORTUNG_BAB, ortDerImpfung.getFachverantwortungbabKeyCloakId(),
			ortDerImpfung.getIdentifier());

		if (ortDerImpfung.getOrganisationsverantwortungKeyCloakId() != null) {
			changeUserWithRoleInGroup(FachRolle.ORGANISATIONSVERANTWORTUNG, ortDerImpfung.getOrganisationsverantwortungKeyCloakId(),
				ortDerImpfung.getIdentifier());
		}
		return update;
	}

	/**
	 * Diese Methode ist dafuer gedacht die Haupt- Fachverantwortlichen die dem OdI gesetzt werden im GUI
	 * richtig zu setzen. Insbesondere muss geschaut werden ob der Fachverantwortliche neu ausgewaehlt wurde
	 * Dann muss er in die Gruppe unseres ODI kommen.
	 * Der der vorher drin war bleibt aber drin
	 * @param fachRolle die Fachrolle die gesetzt werden soll (Fachverantwortung BAB oder Organsiationsverantworlticher)
	 * @param keyCloakId keycloak uuid des users
	 * @param groupIdentifier die Gruppe identifiziert den odi in den der user eingefuegt werden soll
	 */
	private void changeUserWithRoleInGroup(FachRolle fachRolle, @NotNull String keyCloakId, @NotNull String groupIdentifier) {
		final List<OdiUserDisplayNameJax> fachVerBabGroupList = keyCloakService.getUsersInRoleAndGroup(fachRolle,
			groupIdentifier);
		if (fachVerBabGroupList.size() != 1) {
			LOG.info("There is more than one Users with Role {} in group {} ({}). This might be ok but it might be a mistake",
				fachRolle.getKeyCloakRoleName(), groupIdentifier, fachVerBabGroupList.size());
		}
		// die beiden Fachuser muessen vor ihrer Zuweisung auf jeden fall in der richtigen Rolel sein.
		//	das pruefen wir hier nochmal
		UserResource user = keyCloakService.getUser(keyCloakId);
		boolean isInFachrolle =
			user.roles().realmLevel().listEffective().stream()
				.anyMatch(roleRepresentation -> fachRolle.getKeyCloakRoleName().equals(roleRepresentation.getName()));
		if (!isInFachrolle) {
			throw AppValidationMessage.USER_HAS_WRONG_ROLE.create(user.toRepresentation().getFirstName() + " "+user.toRepresentation().getLastName());
		}

		keyCloakService.joinGroup(keyCloakId, groupIdentifier);

	}

	@NonNull
	public OrtDerImpfung getByOdiIdentifier(@NonNull String odiIdentifier) {
		return ortDerImpfungRepo
			.getByOdiIdentifier(odiIdentifier)
			.orElseThrow(() -> AppValidationMessage.UNKNOWN_ODI.create(odiIdentifier));

	}

	@NonNull
	public List<OrtDerImpfung> getByGLN(@NonNull String glnNummer) {
		return ortDerImpfungRepo.getByGLN(glnNummer);
	}

	public void updateOdiNoTermin() {
		List<UUID> odiIds = ortDerImpfungRepo.findIdsOfAllAktivOeffentlichWithTerminVerwaltung();
		LocalDate minDate = LocalDate.now().plusDays(1);
		LocalDate maxDate = LocalDate.now().plusMonths(getTerminsucheMaxRangeMonths());
		odiIds.forEach(odiId -> {

			StopWatch stopwatch1 = StopWatch.createStarted();
			final OrtDerImpfung odi = ortDerImpfungRepo.getById(OrtDerImpfung.toId(odiId)).orElseThrow(() -> AppValidationMessage.UNKNOWN_ODI.create(odiId));
			// Termin 1
			boolean noTermine1 = true;
			LocalDateTime nextFreierTermin1 = impfterminRepo.findNextFreierImpftermin(odi, Impffolge.ERSTE_IMPFUNG, minDate, maxDate);
			if (nextFreierTermin1 != null) {
				noTermine1 = false;
			}
			if (odi.isNoFreieTermine1() != noTermine1) {
				odi.setNoFreieTermine1(noTermine1);
				ortDerImpfungRepo.update(odi);
			}
			logIfSlow(stopwatch1, odi, "Termin 1");

			// Termin 2
			StopWatch stopwatch2 = StopWatch.createStarted();
			boolean noTermine2 = true;
			LocalDateTime nextFreierTermin2 = impfterminRepo.findNextFreierImpftermin(odi, Impffolge.ZWEITE_IMPFUNG, minDate, maxDate);
			if (nextFreierTermin2 != null) {
				noTermine2 = false;
			}

			if (odi.isNoFreieTermine2() != noTermine2) {
				odi.setNoFreieTermine2(noTermine2);
				ortDerImpfungRepo.update(odi);
			}
			logIfSlow(stopwatch2, odi, "Termin 2");

			// Termin N
			StopWatch stopwatchn = StopWatch.createStarted();
			boolean noTermineN = true;
			LocalDateTime nextFreierTerminN = impfterminRepo.findNextFreierImpftermin(odi, Impffolge.BOOSTER_IMPFUNG, minDate, maxDate);
			if (nextFreierTerminN != null) {
				noTermineN = false;
			}
			if (odi.isNoFreieTermineN() != noTermineN) {
				odi.setNoFreieTermineN(noTermineN);
				ortDerImpfungRepo.update(odi);
			}
			logIfSlow(stopwatchn, odi, "Termin N");
		});
	}

	private void logIfSlow(StopWatch stopWatch, OrtDerImpfung odi, String name) {
		stopWatch.stop();
		if (stopWatch.getTime(TimeUnit.MILLISECONDS) > vacmeQuerySlowThresholdMs) {
			LOG.warn(
				"VACME-NO-FREIE-TERMINE: Querytime for noFreieTermine query '{}' for odi '{}'  was {}ms",
				name,
				odi.getName(),
				stopWatch.getTime(TimeUnit.MILLISECONDS));
		}
	}

	/**
	 * fuegt dem Ort der Impfung die in der Liste uebergebenen Impfstoffe als erlaubte Impfungen hinzu
	 */
	public void addImpfstoffeToOdi(@NonNull OrtDerImpfung ortDerImpfung, @Nullable List<ImpfstoffJax> impfstoffe) {
		Set<Impfstoff> impfstoffSet = new HashSet<>();
		if (impfstoffe != null) {
			impfstoffSet = impfstoffe.stream()
				.map(jax -> {
					Impfstoff impfstoff = impfstoffRepo.getById(Impfstoff.toId(jax.getId()))
						.orElseThrow(() -> AppFailureException.entityNotFound(Impfstoff.class, jax.getId()));
					if (impfstoff.getZulassungsStatus() != ZulassungsStatus.ZUGELASSEN && impfstoff.getZulassungsStatus() != ZulassungsStatus.EMPFOHLEN) {
						throw new AppFailureException("Impfstoff is not ZUGELASSEN or EMPFOHLEN");
					}
					return impfstoff;
				})
				.collect(Collectors.toSet());
		}
		ortDerImpfung.setImpfstoffs(impfstoffSet);
	}
}
