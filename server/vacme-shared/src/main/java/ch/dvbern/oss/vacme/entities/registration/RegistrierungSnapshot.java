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

package ch.dvbern.oss.vacme.entities.registration;

import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import ch.dvbern.oss.vacme.entities.base.AbstractUUIDEntity;
import ch.dvbern.oss.vacme.entities.util.DBConst;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Dies ist eine Auswahl relevanter Daten wie sie zum Zeitpunkt der Verschiebung nach "Immunisiert" im System vorhanden
 * waren. Dieses Entity kann als einfach zugaengliche Momentaufnahme dienen falls wir zum Beispiel den vorherigen
 * Status oder aehnliche Angaben noch beneotigen soltlen
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegistrierungSnapshot extends AbstractUUIDEntity<RegistrierungSnapshot> {

	private static final long serialVersionUID = 1242363909640064871L;

	@NotNull
	@NonNull
	@ManyToOne(optional = false)
	@JoinColumn(foreignKey = @ForeignKey(name = "FK_RegistrierungSnapshot_registrierung"), nullable = false, updatable = false)
	private Registrierung registrierung;

	@NotNull
	@NonNull
	@Column(nullable = false, length = DBConst.DB_ENUM_LENGTH)
	@Enumerated(EnumType.STRING)
	private RegistrierungStatus registrierungStatus;

	@NotEmpty @NonNull
	@Column(nullable = false, length = DBConst.DB_DEFAULT_MAX_LENGTH)
	@Size(max = DBConst.DB_DEFAULT_MAX_LENGTH)
	private String name;

	@NotEmpty @NonNull
	@Column(nullable = false, length = DBConst.DB_DEFAULT_MAX_LENGTH)
	@Size(max = DBConst.DB_DEFAULT_MAX_LENGTH)
	private String vorname;

	@NotNull @NonNull
	@Column(nullable = false)
	private LocalDate geburtsdatum;

	@Nullable
	@Column(nullable = true)
	private Boolean vollstaendigerImpfschutz;

	@NotNull
	@Column(nullable = false)
	private boolean abgleichElektronischerImpfausweis = false;

	@Nullable
	@Column(nullable = true)
	private Boolean contactTracing = false;

	@NotNull
	@Column(nullable = false)
	private boolean nichtVerwalteterOdiSelected = false;

	@NotNull @NonNull
	@Column(nullable = false, updatable = false, length = DBConst.DB_ENUM_LENGTH)
	@Enumerated(EnumType.STRING)
	private Prioritaet prioritaet;

	@NotEmpty
	@NonNull
	@Column(nullable = false, updatable = false, length = 8)
	@Size(max = 8)
	private String registrierungsnummer; // 5-stellig, Gross-Buchstaben+Zahlen, unique

	/**
	 * Zuletzt Abgeschlossen kann folgendes bedeuten:
	 * - Automatisch abgeschlossen,
	 * - Grundimmunisiert (Vacme oder Externes Zertifikat),
	 * - Geboostert
	 */
	@Nullable
	@Column(nullable = true)
	private LocalDateTime timestampZuletztAbgeschlossen;

	@Nullable
	@Size(max = DBConst.DB_DEFAULT_MAX_LENGTH)
	private String externalId;

	@NotNull
	@Column(nullable = false)
	private boolean anonymisiert = false;

	@Nullable
	@Column(nullable = true)
	private LocalDateTime zweiteImpfungVerzichtetZeit;

	@Nullable
	@Column(nullable = true)
	private LocalDateTime timestampArchiviert;

	@Nullable
	@Column(nullable = true)
	private LocalDate positivGetestetDatum;

	@Nullable
	@Column(nullable = true)
	private Boolean verstorben;

	@NotEmpty @NonNull
	@Column(nullable = false, length = DBConst.DB_DEFAULT_MAX_LENGTH)
	@Size(max = DBConst.DB_DEFAULT_MAX_LENGTH)
	private String krankenkasseKartenNr;

	@Size(max = DBConst.DB_UUID_LENGTH)
	@Column(nullable = true, length = DBConst.DB_UUID_LENGTH)
	private String gewuenschterOdiId;

	@Size(max = DBConst.DB_UUID_LENGTH)
	@Column(nullable = true, length = DBConst.DB_UUID_LENGTH)
	private String impftermin1Id;

	@Size(max = DBConst.DB_UUID_LENGTH)
	@Column(nullable = true, length = DBConst.DB_UUID_LENGTH)
	private String impftermin2Id;

	public static RegistrierungSnapshot fromRegistrierung(Registrierung reg) {
		return new RegistrierungSnapshot(
			reg,reg.getRegistrierungStatus(),
			reg.getName(),
			reg.getVorname(),
			reg.getGeburtsdatum(),
			reg.getVollstaendigerImpfschutz(),
			reg.isAbgleichElektronischerImpfausweis(),
			reg.getContactTracing(),
			reg.isNichtVerwalteterOdiSelected(),
			reg.getPrioritaet(),
			reg.getRegistrierungsnummer(),
			reg.getTimestampZuletztAbgeschlossen(),
			reg.getExternalId(),
			reg.isAnonymisiert(),
			reg.getZweiteImpfungVerzichtetZeit(),
			reg.getTimestampArchiviert(),
			reg.getPositivGetestetDatum(),
			reg.getVerstorben(),
			reg.getKrankenkasseKartenNr(),
			reg.getGewuenschterOdi() != null ? reg.getGewuenschterOdi().getId().toString() : null,
			reg.getImpftermin1() != null ? reg.getImpftermin1().getId().toString() : null,
			reg.getImpftermin2() != null ? reg.getImpftermin2().getId().toString() : null
		);
	}
}
