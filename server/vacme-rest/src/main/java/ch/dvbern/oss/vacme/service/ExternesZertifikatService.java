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

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import ch.dvbern.oss.vacme.entities.UserPrincipal;
import ch.dvbern.oss.vacme.entities.benutzer.Benutzer;
import ch.dvbern.oss.vacme.entities.externeimpfinfos.ExternesZertifikat;
import ch.dvbern.oss.vacme.entities.impfen.Impfstoff;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.jax.registration.ExternGeimpftJax;
import ch.dvbern.oss.vacme.repo.ExternesZertifikatRepo;
import ch.dvbern.oss.vacme.service.booster.BoosterService;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ExternesZertifikatService {

	private final ExternesZertifikatRepo externesZertifikatRepo;
	private final ImpfstoffService impfstoffService;
	private final UserPrincipal userPrincipal;
	private final ImpfinformationenService impfinformationenService;
	private final RegistrierungService registrierungService;
	private final BoosterService boosterService;
	private final DossierService dossierService;

	// Der Impfling kann sein externes Zertifikat editieren, aber nur in freigegeben/registriert/immunisiert und solange er keine
	// Vacme-Impfung gemacht hat.
	public void saveExternGeimpftImpfling(@NonNull Registrierung registrierung, @NonNull ExternGeimpftJax externGeimpftJax) {

		// Status-Validierung
		ValidationUtil.validateStatusOneOf(registrierung,
			RegistrierungStatus.REGISTRIERT,
			RegistrierungStatus.FREIGEGEBEN,
			RegistrierungStatus.IMMUNISIERT,
			RegistrierungStatus.FREIGEGEBEN_BOOSTER);

		// Validieren: es darf noch keine Vacme-Impfungen haben.
		if (impfinformationenService.hasVacmeImpfungen(registrierung)) {
			throw AppValidationMessage.EXISTING_VACME_IMPFUNGEN_CANNOT_EDIT_EXTERN_GEIMPFT.create();
		}

		ExternesZertifikat nachher = this.createUpdateOrRemoveExternGeimpft(registrierung, externGeimpftJax, false);

		RegistrierungStatus regStatusVorher = registrierung.getRegistrierungStatus();
		switch (regStatusVorher) {
		case IMMUNISIERT:
		case FREIGEGEBEN_BOOSTER:
			if (nachher == null || !nachher.isGrundimmunisiert()) {
				// Externes Zertifikat wurde weggenommen -> zurueck zum Start.
				registrierung.setRegistrierungStatus(registrierungService.ermittleLetztenStatusVorKontrolle1(registrierung));
				registrierung.setVollstaendigerImpfschutzFlagAndTyp(null);
				registrierung.setTimestampZuletztAbgeschlossen(null);
			}
			break;
		case REGISTRIERT:
		case FREIGEGEBEN:
			if (nachher != null && nachher.isGrundimmunisiert()) {
				// vollstaendiges externes Zertifikat neu hinzugefuegt -> zu immunisiert springen
				registrierung.setStatusToImmunisiertWithExternZertifikat(nachher);
			}
			break;
		default:
			// wird nicht passieren, siehe Status-Validierung am Anfang dieser Methode.
			throw new IllegalStateException("Unerwarteter Status: " + regStatusVorher);
		}
		boosterService.recalculateImpfschutzAndStatusmovesForSingleReg(registrierung);
		dossierService.freigabestatusUndTermineEntziehenFallsImpfschutzNochNichtFreigegeben(registrierung);
	}

	/**
	 * 4 Faelle:
	 * - A. vorher ohne - nachher ohne
	 * - B. vorher ohne - nachher mit
	 * - C. vorher mit - nachher ohne
	 * - D. vorher mit - nachher mit
	 */
	@Nullable
	public ExternesZertifikat createUpdateOrRemoveExternGeimpft(
		@NonNull Registrierung registrierung, @NonNull ExternGeimpftJax externGeimpftJax, boolean kontrolle
	) {
		ExternesZertifikat existingInfo = findExternesZertifikatForReg(registrierung).orElse(null);
		if (existingInfo == null) {
			if (externGeimpftJax.isExternGeimpft()) {
				// B. neu erstellen
				return createExternGeimpft(registrierung, externGeimpftJax, kontrolle);
			}
			// A. vorher und nachher nicht extern geimpft
			return null;
		} else {
			if (externGeimpftJax.isExternGeimpft()) {
				// D. Aenderung der externen Impfung
				updateExternGeimpft(registrierung, externGeimpftJax, existingInfo, kontrolle);
				return existingInfo;
			} else {
				// C. extern loeschen
				remove(existingInfo);
				return null;
			}
		}
	}

	public boolean isExternesZertGrundimmunisiertUndKontrolliert(@Nullable ExternesZertifikat externesZertifikatOrNull) {
		return externesZertifikatOrNull != null
			&& externesZertifikatOrNull.isKontrolliert()
			&& externesZertifikatOrNull.isGrundimmunisiert();
	}

	@NonNull
	public ExternesZertifikat createExternGeimpft(@NonNull Registrierung registrierung, @NonNull ExternGeimpftJax externGeimpftJax, boolean kontrolle) {

		// Validieren: es darf noch keine Vacme-Impfungen haben.
		if (impfinformationenService.hasVacmeImpfungen(registrierung)) {
			throw AppValidationMessage.EXISTING_VACME_IMPFUNGEN_CANNOT_ADD_EXTERN_GEIMPFT.create();
		}

		ExternesZertifikat externesZertifikat = new ExternesZertifikat();
		externesZertifikat.setRegistrierung(registrierung);

		updateExternesZertifikatBasic(externGeimpftJax, externesZertifikat, kontrolle);

		create(externesZertifikat);
		return externesZertifikat;
	}

	private void updateExternGeimpft(
		@NonNull Registrierung registrierung,
		@NonNull ExternGeimpftJax jax,
		@NonNull ExternesZertifikat externesZertifikat,
		boolean kontrolle
	) {
		if (!externesZertifikat.getRegistrierung().equals(registrierung)) {
			throw AppValidationMessage.NOT_ALLOWED.create();
		}

		validateAnzahlImpfungenNotReduced(registrierung, jax, externesZertifikat);
		updateExternesZertifikatBasic(jax, externesZertifikat, kontrolle);

		update(externesZertifikat);
	}

	private void validateAnzahlImpfungenNotReduced(@NonNull Registrierung registrierung, @NonNull ExternGeimpftJax jax,
		@NonNull ExternesZertifikat externesZertifikat) {
		assert jax.getAnzahlImpfungen() != null && jax.getAnzahlImpfungen() > 0;
		if (jax.getAnzahlImpfungen() < externesZertifikat.getAnzahlImpfungen()) {
			if (impfinformationenService.hasVacmeImpfungen(registrierung)) {
				throw AppValidationMessage.EXISTING_VACME_IMPFUNGEN_CANNOT_REDUCE_EXTERN_NUMBER.create();
			}
		}
	}

	private void updateExternesZertifikatBasic(@NonNull ExternGeimpftJax jax, @NonNull ExternesZertifikat externesZertifikat, boolean kontrolle) {
		Validate.notNull(jax.getLetzteImpfungDate(), "Ohne letzteImpfungDatum speichern wir das ExterneZertifikat nicht");
		externesZertifikat.setLetzteImpfungDate(jax.getLetzteImpfungDate());
		Objects.requireNonNull(jax.getImpfstoff());
		Impfstoff impfstoff = impfstoffService.findById(Impfstoff.toId(jax.getImpfstoff().getId()));
		externesZertifikat.setImpfstoff(impfstoff);
		assert jax.getAnzahlImpfungen() != null && jax.getAnzahlImpfungen() > 0;
		externesZertifikat.setAnzahlImpfungen(jax.getAnzahlImpfungen());
		externesZertifikat.setGenesen(Boolean.TRUE.equals(jax.getGenesen()));
		externesZertifikat.setPositivGetestetDatum(jax.getPositivGetestetDatum());

		if (kontrolle) {
			externesZertifikat.setTrotzdemVollstaendigGrundimmunisieren(jax.getTrotzdemVollstaendigGrundimmunisieren());
			if (externesZertifikat.getKontrolliertTimestamp() == null) {
				final Benutzer currentBenutzer = userPrincipal.getBenutzerOrThrowException();
				externesZertifikat.setKontrolliertTimestamp(LocalDateTime.now());
				externesZertifikat.setKontrollePersonUUID(currentBenutzer.getId().toString());
			} else {
				// Wenn es schon kontrolliert wurde, kein neuer Timestamp und User setzen!
			}
		} else {
			// Impfwilliger:
			externesZertifikat.setKontrollePersonUUID(null);
			externesZertifikat.setKontrolliertTimestamp(null);
		}
	}

	public void create(@NonNull ExternesZertifikat externesZertifikat) {
		externesZertifikatRepo.create(externesZertifikat);
	}

	public void update(@NonNull ExternesZertifikat externesZertifikat) {
		externesZertifikatRepo.update(externesZertifikat);
	}

	public void remove(@NonNull ExternesZertifikat externesZertifikat) {
		externesZertifikatRepo.remove(externesZertifikat);
	}

	@NonNull
	public Optional<ExternesZertifikat> findExternesZertifikatForReg(@NonNull Registrierung registrierung) {
		return externesZertifikatRepo.findExternesZertifikatForReg(registrierung);
	}

	public boolean isConfirmedExternGrundimmunisiert(@NonNull Registrierung registrierung) {
		ExternesZertifikat externesZertifikatOrNull = findExternesZertifikatForReg(registrierung).orElse(null);
		return isExternesZertGrundimmunisiertUndKontrolliert(externesZertifikatOrNull);
	}
}
