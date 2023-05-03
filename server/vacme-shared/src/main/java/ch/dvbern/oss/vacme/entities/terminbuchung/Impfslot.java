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

package ch.dvbern.oss.vacme.entities.terminbuchung;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import ch.dvbern.oss.vacme.entities.base.AbstractUUIDEntity;
import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.embeddables.DateTimeRange;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.hibernate.envers.Audited;

@Entity
@Audited
@Getter
@Setter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Builder(builderMethodName = "hiddenBuilder")
@Table(
	uniqueConstraints = @UniqueConstraint(columnNames = { "ortDerImpfung_id", "bis" }, name = "UK_Impfslot_odi_bis"),
	indexes = {
		@Index(name = "IX_Impfslot_odi", columnList = "ortDerImpfung_id"),
		@Index(name = "IX_Impfslot_von", columnList = "von"),
		@Index(name = "IX_Impfslot_bis", columnList = "bis"),
		@Index(name = "IX_Impftermin_odi_id_bis", columnList = "ortDerImpfung_id, bis")
	}
)
public class Impfslot extends AbstractUUIDEntity<Impfslot> {

	private static final long serialVersionUID = -2854345385817970097L;

	@NotNull
	@NonNull
	@ManyToOne(optional = false)
	@JoinColumn(foreignKey = @ForeignKey(name = "FK_impfslot_impfzentrum_id"), nullable = false)
	private OrtDerImpfung ortDerImpfung;

	@NotNull
	@NonNull
	@Column(nullable = false, updatable = false)
	private DateTimeRange zeitfenster;

	@NotNull
	@Column(nullable = false)
	private int kapazitaetErsteImpfung;

	@NotNull
	@Column(nullable = false)
	private int kapazitaetZweiteImpfung;

	@NotNull
	@Column(nullable = false)
	private int kapazitaetBoosterImpfung;

	@NonNull
	public static Impfslot of(@NonNull final OrtDerImpfung ortDerImpfung, @NonNull DateTimeRange zeitfenster) {
		return hiddenBuilder().ortDerImpfung(ortDerImpfung)
			.zeitfenster(zeitfenster)
			.build();
	}

	@NonNull
	public static ID<Impfslot> toId(@NonNull UUID id) {
		return new ID<>(id, Impfslot.class);
	}

	public String toDateMessage() {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
		return formatter.format(getZeitfenster().getVon());
	}

	public int getKapazitaetTotal() {
		return kapazitaetErsteImpfung + kapazitaetZweiteImpfung + kapazitaetBoosterImpfung;
	}
}
