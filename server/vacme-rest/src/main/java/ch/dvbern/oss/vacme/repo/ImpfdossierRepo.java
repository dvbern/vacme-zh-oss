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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.impfen.Erkrankung;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossier;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.Impfschutz;
import ch.dvbern.oss.vacme.entities.impfen.QImpfdossier;
import ch.dvbern.oss.vacme.entities.impfen.QImpfdossiereintrag;
import ch.dvbern.oss.vacme.entities.registration.QRegistrierung;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import ch.dvbern.oss.vacme.smartdb.Db;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import static ch.dvbern.oss.vacme.entities.impfen.QImpfdossiereintrag.impfdossiereintrag;

@RequestScoped
@Transactional
@Slf4j
public class ImpfdossierRepo {

	private final Db db;

	@Inject
	public ImpfdossierRepo(Db db) {
		this.db = db;
	}

	public void create(@NonNull Impfdossier impfdossier) {
		db.persist(impfdossier);
		db.flush();
	}

	public void delete(ID<Impfdossier> impfdossierID) {
		db.remove(impfdossierID);
		db.flush();
	}

	public void createEintrag(@NonNull Impfdossiereintrag eintrag) {
		db.persist(eintrag);
		db.flush();
	}

	private void deleteEintrag(ID<Impfdossiereintrag> eintragID) {
		db.remove(eintragID);
		db.flush();
	}

	@NonNull
	public Impfdossiereintrag addEintrag(Integer impffolgeNr, Impfdossier impfdossier) {
		Impfdossiereintrag newEintrag = new Impfdossiereintrag();
		newEintrag.setImpffolgeNr(impffolgeNr);
		newEintrag.setImpfdossier(impfdossier);
		impfdossier.getOrderedEintraege().add(newEintrag);
		createEintrag(newEintrag);
		return newEintrag;
	}

	public void deleteEintrag(@NonNull Impfdossiereintrag eintrag, @NonNull Impfdossier dossier) {
		boolean remove = dossier.getOrderedEintraege().remove(eintrag);
		if (!remove) {
			LOG.warn("deleteEintrag was called on Dossier {} for Eintrag {} but it was not in list", dossier.getId(), eintrag.getId());
		}

		deleteEintrag(eintrag.toId());
	}

	@NonNull
	public Impfdossier getImpfdossier(ID<Impfdossier> id) {
		return db.get(id)
			.orElseThrow(() -> AppFailureException.entityNotFound(Impfdossier.class, id));
	}

	@NonNull
	public Optional<Impfdossier> findImpfdossierForReg(@NonNull Registrierung registrierung) {
		var result = db.select(QImpfdossier.impfdossier)
			.from(QImpfdossier.impfdossier)
			.where(
				QImpfdossier.impfdossier.registrierung.eq(registrierung)
			)
			.fetchFirst();
		return Optional.ofNullable(result);
	}

	/**
	 * @deprecated VACME-1474
	 */
	@Deprecated
	@NonNull
	public List<Impfdossiereintrag> getImpfdossierEintraege(@NonNull Registrierung registrierung) {
		var result = db.select(QImpfdossier.impfdossier.impfdossierEintraege)
			.from(QImpfdossier.impfdossier)
			.where(
				QImpfdossier.impfdossier.registrierung.eq(registrierung)
			)
			.fetchFirst();
		return result;
	}

	/**
	 * @deprecated VACME-1474
	 */
	@Deprecated
	@NonNull
	public Optional<Impfdossiereintrag> findImpfdossiereintragForImpffolgeNr(@NonNull Registrierung registrierung, int impffolgeNr) {
		var result = db.select(QImpfdossiereintrag.impfdossiereintrag)
			.from(QImpfdossier.impfdossier)
			.innerJoin(QImpfdossiereintrag.impfdossiereintrag).on(QImpfdossiereintrag.impfdossiereintrag.impfdossier.eq(QImpfdossier.impfdossier))
			.where(
				QImpfdossier.impfdossier.registrierung.eq(registrierung)
					.and(QImpfdossiereintrag.impfdossiereintrag.impffolgeNr.eq(impffolgeNr))
			)
			.fetchFirst();
		return Optional.ofNullable(result);
	}

	@NonNull
	public Optional<UUID> findIdOfImpfdossiereintragForImpftermin(@NonNull Impftermin termin) {
		return db.select(impfdossiereintrag.id)
			.from(impfdossiereintrag)
			.where(impfdossiereintrag.impftermin.eq(termin)).fetchOne();
	}

	@NonNull
	public Optional<Impfdossiereintrag> findImpfdossiereintragForImpftermin(@NonNull Impftermin termin) {
		return db.select(impfdossiereintrag)
			.from(impfdossiereintrag)
			.where(impfdossiereintrag.impftermin.eq(termin)).fetchOne();
	}

	public void update(@NonNull Impfdossier impfdossier) {
		db.merge(impfdossier);
		db.flush();
	}

	public void updateImpfschutz(@NonNull Impfdossier impfdossier, @Nullable Impfschutz impfschutz) {
		if (impfdossier.getImpfschutz() != null) {
			if (impfschutz == null) {
				LOG.warn("Es existierte fuer das Dossier {} bereits ein Impfschutz welcher nun weggefallen ist", impfdossier.getId().toString());
				impfdossier.setImpfschutz(null);
			} else {
				impfdossier.getImpfschutz().apply(impfschutz);
			}
		} else {
			impfdossier.setImpfschutz(impfschutz);
		}
		this.update(impfdossier);
	}

	@NonNull
	public List<String> findRegsWithoutImpfdossier(){
		return db.select(QRegistrierung.registrierung.registrierungsnummer)
			.from(QRegistrierung.registrierung)
			.leftJoin(QImpfdossier.impfdossier).on(QImpfdossier.impfdossier.registrierung.eq(QRegistrierung.registrierung))
			.where(QImpfdossier.impfdossier.id.isNull())
			.fetch();
	}

	@NonNull
	public Impfdossier createImpfdossier(@NonNull Registrierung registrierung) {
		LOG.info("Creating Impfdossier for Reg {}", registrierung);
		Impfdossier dossier = new Impfdossier();
		dossier.setRegistrierung(registrierung);
		create(dossier);
		return dossier;
	}

	@NonNull
	public Impfdossier getOrCreateImpfdossier(@NonNull Registrierung registrierung) {
		Impfdossier impfdossier = this.findImpfdossierForReg(registrierung)
			.orElseGet(() -> createImpfdossier(registrierung));
		return impfdossier;
	}

	public void updateErkrankungen(@NonNull Impfdossier impfdossier, @NonNull List<Erkrankung> erkrankungen) {
		// mit clear und addAll die bestehende Collection abaendern, damit Hibernate alles automatisch updatet
		impfdossier.getErkrankungen().clear();
		impfdossier.getErkrankungen().addAll(erkrankungen);
		for (Erkrankung erkrankung : erkrankungen) {
			erkrankung.setImpfdossier(impfdossier);
		}
		this.update(impfdossier);
	}
}
