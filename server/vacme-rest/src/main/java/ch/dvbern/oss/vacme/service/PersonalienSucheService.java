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

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import ch.dvbern.oss.vacme.entities.UserPrincipal;
import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.benutzer.Benutzer;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import ch.dvbern.oss.vacme.entities.types.BenutzerRolle;
import ch.dvbern.oss.vacme.entities.zertifikat.Zertifikat;
import ch.dvbern.oss.vacme.jax.registration.PersonalienSucheJax;
import ch.dvbern.oss.vacme.repo.RegistrierungRepo;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Dieser Service ermoeglicht es Personen nach Name, Vorname, Geburtsdatum zu suchen
 */
@ApplicationScoped
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PersonalienSucheService {

	private final RegistrierungRepo registrierungRepo;
	private final ZertifikatService zertifikatService;
	private final ImpfinformationenService impfinformationenService;
	private final UserPrincipal userPrincipal;

	/**
	 * Diese Suche ist sehr grosszuegig. Wichtig ist hier, dass das Callcenter keine Reg-Codes erhaelt, sondern nur die Reg-UUID (fuer Adressaenderungen)
	 */
	@NonNull
	public List<Registrierung> suchen(@NonNull String vorname, @NonNull String name, @NonNull Date geburtsdatum) {
		LocalDate geburtsdatumLocalDate = LocalDate.ofInstant(geburtsdatum.toInstant(), ZoneId.systemDefault());
		List<PersonalienSucheJax> sucheJaxes = registrierungRepo.findRegistrierungByGeburtsdatumGeimpft(geburtsdatumLocalDate);
		List<UUID> matchingIds = filterMatchingJaxes(vorname, name, sucheJaxes);
		return matchingIds.stream()
			.map(id -> registrierungRepo.getById(Registrierung.toId(id))
				.orElseThrow(() -> AppFailureException.entityNotFound(Registrierung.class, id.toString())))
			.collect(Collectors.toList());
	}

	/**
	 * Suche nuer fuer bestimmte ODIs
	 */
	@NonNull
	public List<Registrierung> suchenFuerODI(@NonNull String vorname, @NonNull String name, @NonNull Date geburtsdatum, @NonNull Benutzer benutzer) {
		LocalDate geburtsdatumLocalDate = LocalDate.ofInstant(geburtsdatum.toInstant(), ZoneId.systemDefault());
		List<PersonalienSucheJax> sucheJaxes = registrierungRepo.findRegistrierungByGeburtsdatum(geburtsdatumLocalDate);
		List<UUID> matchingIds = filterMatchingJaxes(vorname, name, sucheJaxes);
		return matchingIds.stream()
			.map(id -> registrierungRepo.getById(Registrierung.toId(id))
				.orElseThrow(() -> AppFailureException.entityNotFound(Registrierung.class, id.toString())))
			.filter(registrierung -> registrierungHasTerminInODI(registrierung, benutzer.getOrtDerImpfung()))
			.collect(Collectors.toList());
	}

	private boolean registrierungHasTerminInODI(@NonNull Registrierung registrierung, @NonNull Set<OrtDerImpfung> odis) {
		if (!userPrincipal.isCallerInAnyOfRole(BenutzerRolle.getOrtDerImpfungRoles())) {
			// Nicht ODI-Rollen duerfen immer alle Regs sehen
			return true;
		}
		// Termin 1?
		if (registrierung.getImpftermin1() != null &&
			odis.contains(registrierung.getImpftermin1().getImpfslot().getOrtDerImpfung())) {
			return true;
		}
		// Termin 2?
		if (registrierung.getImpftermin2() != null &&
			odis.contains(registrierung.getImpftermin2().getImpfslot().getOrtDerImpfung())) {
			return true;
		}
		// Gewuenschter ODI?
		if (odis.contains(registrierung.getGewuenschterOdi())) {
			return true;
		}

		// Boostertermin?
		ImpfinformationDto infos = impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
		//noinspection RedundantIfStatement
		if (infos.getImpfdossier() != null
			&& infos.getImpfdossier().getImpfdossierEintraege().stream().anyMatch(impfdossiereintrag ->
			impfdossiereintrag.getImpftermin() != null
				&& odis.contains(impfdossiereintrag.getImpftermin().getImpfslot().getOrtDerImpfung()))) {
			return true;
		}

		// Alle anderen Faelle nicht erlaubt
		return false;
	}

	/**
	 * Diese Suche ist sehr eingeschraenkt (weil UVCI), dafuer geben wir dem Callcenter den Reg-Code zurueck!
	 */
	@NonNull
	public List<Registrierung> suchen(@NonNull String vorname, @NonNull String name, @NonNull Date geburtsdatum, @NonNull String uvci) {
		LocalDate geburtsdatumLocalDate = LocalDate.ofInstant(geburtsdatum.toInstant(), ZoneId.systemDefault());
		List<PersonalienSucheJax> sucheJaxes = registrierungRepo.findRegistrierungByGeburtsdatumGeimpft(geburtsdatumLocalDate);
		List<UUID> matchingIds = filterMatchingJaxes(vorname, name, uvci, sucheJaxes);
		return matchingIds.stream()
			.map(id -> registrierungRepo.getById(Registrierung.toId(id))
				.orElseThrow(() -> AppFailureException.entityNotFound(Registrierung.class, id.toString())))
			.collect(Collectors.toList());
	}

	/**
	 * goes through the passed list and filters it for matching vorname and name
	 *
	 * @param vorname to match
	 * @param name to match
	 * @param dataToSearchIn list of DTOs to find matches in
	 * @return DTOs where name and vorname matched
	 */
	@NonNull
	List<UUID> filterMatchingJaxes(@NonNull String vorname, @NonNull String name, @NonNull List<PersonalienSucheJax> dataToSearchIn) {
		String normalizedVorname = normalizeSplitString(vorname);
		String normalizedName = normalizeSplitString(name);
		return dataToSearchIn.stream()
			.filter(dataJax -> matchesNormalized(normalizedVorname, dataJax.getVorname()) &&
				matchesNormalized(normalizedName, dataJax.getName()))
			.map(PersonalienSucheJax::getRegistrierungId)
			.collect(Collectors.toList());
	}

	@NonNull
	List<UUID> filterMatchingJaxes(@NonNull String vorname, @NonNull String name, @NonNull String uvci, @NonNull List<PersonalienSucheJax> dataToSearchIn) {
		String normalizedVorname = normalizeSplitString(vorname);
		String normalizedName = normalizeSplitString(name);
		return dataToSearchIn.stream()
			.filter(dataJax -> matchesNormalized(normalizedVorname, dataJax.getVorname()) &&
				matchesNormalized(normalizedName, dataJax.getName()))
			.map(PersonalienSucheJax::getRegistrierungId)
			.filter(registrierungId -> hasUvci(registrierungId, uvci))
			.collect(Collectors.toList());
	}

	private boolean hasUvci(@NonNull UUID registrierungId, @NonNull String uvci) {
		@NonNull Optional<Registrierung> registrierung = registrierungRepo.getById(new ID<>(registrierungId, Registrierung.class));
		if (!registrierung.isPresent()) {
			return false;
		}
		List<Zertifikat> zertifikatList = zertifikatService.getAllZertifikateRegardlessOfRevocation(registrierung.get());
		return zertifikatList.stream()
			.anyMatch(zertifikat -> zertifikat.getUvci().toLowerCase(Locale.GERMAN).endsWith(uvci.toLowerCase(Locale.GERMAN)));
	}

	/**
	 * normalizes  the input
	 *
	 * @param normalizedSearch the normalized search-string
	 * @param comparedString data to find match in
	 */
	private boolean matchesNormalized(String normalizedSearch, String comparedString) {
		String normalizedData = normalizeString(comparedString);
		String[] splitData = normalizedData.split("[ \\-]");
		String concatenated = "";

		for (String singleDataString : splitData) {
			concatenated = concatenated.concat(singleDataString);
			if (singleDataString.equals(normalizedSearch) || concatenated.equals(normalizedSearch)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Normalizes and split the string returning a list of to which we can compare other
	 * normalized strings for matches
	 *
	 * @param toNormalizeSplit the string to be worked
	 * @return a list of normalized string to compare with .equals
	 */
	private String normalizeSplitString(String toNormalizeSplit) {
		String normalizedString = normalizeString(toNormalizeSplit); // remove special chars
		return normalizedString.replaceAll("[ \\-]", "");
	}

	/**
	 * remove special characters
	 *
	 * @param toNormalize string to normalize
	 * @return normalized string
	 */
	private String normalizeString(String toNormalize) {
		return Normalizer.normalize(toNormalize, Normalizer.Form.NFD)
			.replaceAll("[^\\p{ASCII}]", "") // remove non ascii symbols
			.toLowerCase();
	}
}
