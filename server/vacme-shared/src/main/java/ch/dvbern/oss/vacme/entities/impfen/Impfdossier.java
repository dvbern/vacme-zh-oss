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

package ch.dvbern.oss.vacme.entities.impfen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import ch.dvbern.oss.vacme.entities.base.AbstractUUIDEntity;
import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hibernate.envers.Audited;

/**
 * This class is used to store information about Impfungen and Kontrollen for a specific disease
 */
@Entity
@Audited
@Getter
@Setter
@NoArgsConstructor
@Table(
	indexes = {
		@Index(name = "IX_Impfdossier_registrierung", columnList = "registrierung_id, id"),
		@Index(name = "IX_Impfdossier_impfschutz", columnList = "impfschutz_id, id")
	},
	uniqueConstraints = {
		@UniqueConstraint(name = "UC_Impfdossier_registrierung", columnNames = "registrierung_id"),
		@UniqueConstraint(name = "UC_Impfdossier_impfschutz", columnNames = "impfschutz_id"),
	}
)
public class Impfdossier extends AbstractUUIDEntity<Impfdossier> {

	private static final long serialVersionUID = 2255721458819528453L;

	@NotNull
	@NonNull
	@OneToOne(optional = false)
	@JoinColumn(foreignKey = @ForeignKey(name = "FK_Impfdossier_registrierung"), nullable = false, updatable = false)
	private Registrierung registrierung;

	@Nullable
	@OneToOne(optional = true, cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(foreignKey = @ForeignKey(name = "FK_Impfdossier_impfschutz"), nullable = true, updatable = true)
	private Impfschutz impfschutz;

	@NonNull
	@OneToMany(mappedBy = "impfdossier", fetch = FetchType.LAZY, cascade = { CascadeType.ALL}, orphanRemoval = true)
	private List<Impfdossiereintrag> impfdossierEintraege = new ArrayList<>();

	@NonNull
	@OneToMany(mappedBy = "impfdossier", fetch = FetchType.LAZY, cascade = { CascadeType.ALL}, orphanRemoval = true)
	private List<Erkrankung> erkrankungen = new ArrayList<>();

	public static ID<Impfdossier> toId(UUID id) {
		return new ID<>(id, Impfdossier.class);
	}

	@NonNull
	public Optional<Impfdossiereintrag> findEintragForImpffolgeNr(@NonNull Integer impffolgeNr) {
		return impfdossierEintraege.stream()
			.filter(eintrag -> eintrag.getImpffolgeNr().equals(impffolgeNr))
			.findFirst();
	}

	@NonNull
	public Impfdossiereintrag getEintragForImpffolgeNr(@NonNull Integer impffolgeNr) {
		return findEintragForImpffolgeNr(impffolgeNr)
			.orElseThrow(() -> AppValidationMessage.ILLEGAL_STATE.create(Impfdossiereintrag.class.getSimpleName()
				+" nicht gefunden fuer Impffolge " + impffolgeNr));

	}

	@NonNull
	public List<Impfdossiereintrag> getOrderedEintraege() {
		Collections.sort(impfdossierEintraege);
		return impfdossierEintraege;
	}
	@NonNull
	public List<Erkrankung> getErkrankungenSorted() {
		Collections.sort(erkrankungen);
		return erkrankungen;
	}
}
