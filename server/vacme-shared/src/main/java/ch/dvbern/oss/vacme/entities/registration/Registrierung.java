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
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import ch.dvbern.oss.vacme.entities.base.AbstractUUIDEntity;
import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.embeddables.Adresse;
import ch.dvbern.oss.vacme.entities.externeimpfinfos.ExternesZertifikat;
import ch.dvbern.oss.vacme.entities.impfen.AuslandArt;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.impfen.Krankenkasse;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import ch.dvbern.oss.vacme.entities.types.VollstaendigerImpfschutzTyp;
import ch.dvbern.oss.vacme.entities.util.DBConst;
import ch.dvbern.oss.vacme.entities.util.RegistrierungEntityListener;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.shared.util.Constants;
import ch.dvbern.oss.vacme.util.DeservesZertifikatValidator;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.validators.CheckRegistrierungKontaktdaten;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import static ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus.IMMUNISIERT;

/**
 * Die Mobilenummer darf aber mehrfach verwendet werden, damit ich z.B. meine
 * Grosseltern anmelden kann.
 */
@Entity
@Audited
@Getter
@Setter
@NoArgsConstructor
@CheckRegistrierungKontaktdaten
@Table(
	indexes = {
		@Index(name = "IX_registrierungsnummer", columnList = "registrierungsnummer"),
		@Index(name = "IX_Registrierung_impftermin1", columnList = "impftermin1_id"),
		@Index(name = "IX_Registrierung_impftermin2", columnList = "impftermin2_id"),
		@Index(name = "IX_Registrierung_externalId", columnList = "externalId"),
		@Index(name = "IX_Registrierung_geburtsdatum", columnList = "geburtsdatum, id"),
	},
	uniqueConstraints = {
		@UniqueConstraint(name = "UC_Registrierung_registrierungsnummer", columnNames = "registrierungsnummer"),
		@UniqueConstraint(name = "UC_Registrierung_benutzer", columnNames = "benutzerId"),
		@UniqueConstraint(name = "UC_Registrierung_impftermin1", columnNames = "impftermin1_id"),
		@UniqueConstraint(name = "UC_Registrierung_impftermin2", columnNames = "impftermin2_id"),
		@UniqueConstraint(name = "UC_Registrierung_externalId", columnNames = "externalId"),
	}
)
@EntityListeners(RegistrierungEntityListener.class)
@Slf4j
public class Registrierung extends AbstractUUIDEntity<Registrierung> {

	private static final long serialVersionUID = 1471567013714242919L;

	@Nullable
	@Column(nullable = true, updatable = true, unique = true, length = DBConst.DB_UUID_LENGTH)
	@Type(type = "org.hibernate.type.UUIDCharType")
	private UUID benutzerId;

	@Nullable // Wir wissen die Sprache nur bei Online-Registrierungen
	@Column(nullable = true, length = DBConst.DB_ENUM_LENGTH)
	@Enumerated(EnumType.STRING)
	private Sprache sprache;

	@NotNull
	@NonNull
	@Column(nullable = false, length = DBConst.DB_ENUM_LENGTH)
	@Enumerated(EnumType.STRING)
	private RegistrierungStatus registrierungStatus = RegistrierungStatus.REGISTRIERT;

	@NotNull
	@NonNull
	@Column(nullable = false, length = DBConst.DB_ENUM_LENGTH)
	@Enumerated(EnumType.STRING)
	private Geschlecht geschlecht;

	@NotEmpty
	@NonNull
	@Column(nullable = false, length = DBConst.DB_DEFAULT_MAX_LENGTH)
	@Size(max = DBConst.DB_DEFAULT_MAX_LENGTH)
	private String name;

	@NotEmpty
	@NonNull
	@Column(nullable = false, length = DBConst.DB_DEFAULT_MAX_LENGTH)
	@Size(max = DBConst.DB_DEFAULT_MAX_LENGTH)
	private String vorname;

	@NotNull
	@NonNull
	@Column(nullable = false)
	private LocalDate geburtsdatum;

	@Valid
	@Embedded
	@NotNull
	@NonNull
	private Adresse adresse = new Adresse();

	@NotNull
	@Column(nullable = false)
	private boolean immobil = false; // benutzer kann nicht ins impfzentrum kommen sondern muss mobile Impfzentren wahlen

	@NotNull
	@Column(nullable = false)
	private boolean nichtVerwalteterOdiSelected = false;

	@NotNull
	@NonNull
	@Column(nullable = false, length = DBConst.DB_ENUM_LENGTH)
	@Enumerated(EnumType.STRING)
	private RegistrierungsEingang registrierungsEingang;

	@NotNull
	@NonNull
	@Column(nullable = false, updatable = false)
	private LocalDateTime registrationTimestamp;

	@Nullable
	@Column(nullable = true, length = DBConst.DB_DEFAULT_MAX_LENGTH)
	@Size(max = DBConst.DB_DEFAULT_MAX_LENGTH)
	private String mail;

	@NotNull
	@Column(nullable = false)
	private boolean mailValidiert = false;            // Benutzer hat auf Link im Mail geklickt

	@Nullable
	@Column(nullable = true, length = DBConst.DB_PHONE_LENGTH)
	@Size(max = DBConst.DB_PHONE_LENGTH)
	private String telefon;

	@Nullable
	@Column(nullable = true, length = DBConst.DB_BEMERKUNGEN_MAX_LENGTH)
	@Size(max = DBConst.DB_BEMERKUNGEN_MAX_LENGTH)
	private String bemerkung;

	@NotNull
	@NonNull
	@Column(nullable = false, length = DBConst.DB_ENUM_LENGTH)
	@Enumerated(EnumType.STRING)
	private Prioritaet prioritaet;

	@NotEmpty
	@NonNull
	@Column(nullable = false, updatable = false, length = 8)
	@Size(max = 8)
	private String registrierungsnummer; // 5-stellig, Gross-Buchstaben+Zahlen, unique

	@NotNull
	@NonNull
	@Column(nullable = false, length = DBConst.DB_ENUM_LENGTH)
	@Enumerated(EnumType.STRING)
	private Krankenkasse krankenkasse; // Enum oder Entity oder String?

	@NotEmpty
	@NonNull
	@Column(nullable = false, length = DBConst.DB_DEFAULT_MAX_LENGTH)
	@Size(max = DBConst.DB_DEFAULT_MAX_LENGTH)
	private String krankenkasseKartenNr;

	@NonNull
	@OneToMany(mappedBy = "registrierung", fetch = FetchType.LAZY, cascade = { CascadeType.ALL }, orphanRemoval = true)
	private Set<KkkNummerAlt> kkkNummerAltSet = new HashSet<>(); // damit man auch nach vorherigen Nummern suchen kann

	@Nullable
	@Column(nullable = true, length = DBConst.DB_ENUM_LENGTH)
	@Enumerated(EnumType.STRING)
	private AuslandArt auslandArt;

	@NotNull
	@Column(nullable = false)
	private boolean abgleichElektronischerImpfausweis = false;

	/**
	 * Null fuer alte Registrierungen und default auf false fuer neue
	 */
	@Nullable
	@Column(nullable = true)
	private Boolean contactTracing = false;

	/**
	 * Gewuenschtes ODI ist hauptsaechlich fuer die Pendenzenlisten der ODIs genutzt. Es wird nur
	 * beim buchen (egal ob mit oder ohne Termine) gesetzt. Wenn ich dann effektiv in einem anderen
	 * ODI geimpft werde, wird das "gewuenschteODI" nicht mehr angepasst.
	 * Es kann also sein gewuenschterODI != odiTermin1 != odiTermin2
	 */
	@Nullable
	@ManyToOne(optional = true)
	@JoinColumn(foreignKey = @ForeignKey(name = "FK_registrierung_impfzentrum_id"), nullable = true)
	private OrtDerImpfung gewuenschterOdi;

	@Nullable
	@OneToOne(optional = true)
	@JoinColumn(foreignKey = @ForeignKey(name = "FK_registrierung_impftermin1_id"), nullable = true)
	private Impftermin impftermin1;

	@Nullable
	@OneToOne(optional = true)
	@JoinColumn(foreignKey = @ForeignKey(name = "FK_registrierung_impftermin2_id"), nullable = true)
	private Impftermin impftermin2;

	@Valid
	@Nullable
	@OneToOne(optional = true, cascade = CascadeType.ALL)
	@JoinColumn(foreignKey = @ForeignKey(name = "FK_registrierung_abgesagtetermine_id"), nullable = true)
	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	private AbgesagteTermine abgesagteTermine;

	@Nullable
	@Column(nullable = true, updatable = false, unique = true, length = DBConst.DB_DEFAULT_MAX_LENGTH)
	@Size(max = DBConst.DB_DEFAULT_MAX_LENGTH)
	private String externalId;

	@NotNull
	@Column(nullable = false)
	private boolean anonymisiert = false;

	@Nullable
	@Column(nullable = true)
	/*
	Verwendung: a) fuer die Batchjob-Reihenfolge fuer Immunisiert und BoosterRule
	            b) bei automatisch abgeschlossenen wird auf geimpft angezeigt, wann automatisch abgeschlossen wurde
	Bedeutung: wann abgeschlossen wurde (mit/ohne Grundimmunisierung), wann externes Zertifikat erstellt wurde oder wann geboostert wurde
	 */
	private LocalDateTime timestampZuletztAbgeschlossen;

	@Nullable
	@Column(length = DBConst.DB_BEMERKUNGEN_MAX_LENGTH)
	@Size(max = DBConst.DB_BEMERKUNGEN_MAX_LENGTH)
	private String zweiteImpfungVerzichtetGrund;

	@Nullable
	@Column(nullable = true)
	private LocalDateTime zweiteImpfungVerzichtetZeit;

	@NotNull
	@NonNull
	@Column(nullable = false)
	private boolean genesen = false;

	@Nullable
	@Column(nullable = true)
	private LocalDate positivGetestetDatum;

	@Nullable
	@Column(nullable = true)
	private LocalDateTime timestampArchiviert;

	@Nullable
	@Column(nullable = true)
	private Boolean vollstaendigerImpfschutz; // deprecated -> use vollstaendigerImpfschutzTyp

	@Nullable
	@Column(nullable = true, length = DBConst.DB_ENUM_LENGTH)
	@Enumerated(EnumType.STRING)
	private VollstaendigerImpfschutzTyp vollstaendigerImpfschutzTyp; // vollstaendige Grundimmunisierung: in VacMe oder ExternesZertifikat, mit/ohne genesen

	@NotNull
	@Column(nullable = false)
	private boolean generateOnboardingLetter = false;

	@Nullable
	@Column(nullable = true)
	private Boolean verstorben;

	/**
	 * Null fuer alte Registrierungen und default auf false fuer neue
	 */
	@Nullable
	@Column(nullable = true)
	private Boolean schutzstatus;

	/**
	 * Null für alte Registrierungen und default auf false für neue
	 */
	@Nullable
	@Column(nullable = true)
	private Boolean keinKontakt;

	@NotNull
	@Column(nullable = false)
	private boolean selbstzahler = false;

	@Nullable
	@Column(nullable = true)
	 // Wird verwendet um die darstellung des umfragen-aktuell-daten.component zu bestimmen.
	private LocalDateTime timestampInfoUpdate;

	@Nullable
	@Column(nullable = true)
	// Wird verwendet um die darstellung das phonenumber-update popup zu bestimmen.
	private LocalDateTime timestampPhonenumberUpdate;

	@JsonIgnore
	@NonNull
	public String getNameVorname() {
		return getName() + ' ' + getVorname();
	}

	@JsonIgnore
	public boolean abgeschlossenMitCorona() {
		return genesen && abgeschlossenMitVollstaendigemImpfschutz();
	}

	@JsonIgnore
	public boolean abgeschlossenMitVollstaendigemImpfschutz() {
		return Boolean.TRUE.equals(this.getVollstaendigerImpfschutz());
	}

	@JsonIgnore
	public boolean verzichtetOhneVollstaendigemImpfschutz() {
		return Boolean.FALSE.equals(this.getVollstaendigerImpfschutz()) &&
			this.getZweiteImpfungVerzichtetZeit() != null;
	}

	@JsonIgnore
	@NonNull
	public Locale getLocale() {
		if (sprache != null) {
			return sprache.getLocale();
		}
		return Locale.GERMAN;
	}

	/**
	 * setzt den vollstaendigenImpfschutzTyp und das vollstaendigerImpfschutz Flag
	 *
	 * @param vollstaendigerImpfschutzTyp impfschutz welcher gesetzt wird. Wenn ein Wert vorhanden ist wird auch
	 * vollsteandigerImpfschutz gesetzt. Wenn null wird vollstaendigerImpfschutz auf null gesetzt
	 */
	public void setVollstaendigerImpfschutzFlagAndTyp(@Nullable VollstaendigerImpfschutzTyp vollstaendigerImpfschutzTyp) {
		// validieren, dass vollstaendigerImpfschutzTyp nicht direkt wechselt (man sollte immer ueber null gehen)
		if (vollstaendigerImpfschutzTyp != null
			&& this.vollstaendigerImpfschutzTyp != null
			&& vollstaendigerImpfschutzTyp != this.vollstaendigerImpfschutzTyp) {
			throw AppValidationMessage.ILLEGAL_STATE.create(String.format("vollstaendigerImpfschutzTyp changed from %s"
				+ " to %s. This is illegal", this.vollstaendigerImpfschutzTyp, vollstaendigerImpfschutzTyp));
		}
		// beide Felder ensprechend setzen
		this.vollstaendigerImpfschutzTyp = vollstaendigerImpfschutzTyp;
		this.vollstaendigerImpfschutz = vollstaendigerImpfschutzTyp != null;
	}

	@SuppressWarnings("unused")
	public void setVollstaendigerImpfschutz(@Nullable Boolean vollstaendigerImpfschutz) throws IllegalAccessException {
		throw new IllegalAccessException("Achtung, vollstaendigerImpfschutz muss ueber setVollstaendigerImpfschutzFlagAndTyp gesetzt werden!");
	}

	@SuppressWarnings("unused")
	public void setVollstaendigerImpfschutzTyp(VollstaendigerImpfschutzTyp vollstaendigerImpfschutzTyp) throws IllegalAccessException {
		throw new IllegalAccessException("Achtung, VollstaendigerImpfschutzTyp muss ueber setVollstaendigerImpfschutzFlagAndTyp gesetzt werden!");
	}

	@SuppressWarnings("unused")
	public void setImpftermin1(@Nullable Impftermin impftermin1) throws IllegalAccessException {
		throw new IllegalAccessException("Achtung, Impftermin muss ueber ImpfterminRepo gesetzt werden!");
	}

	public void setImpftermin1FromImpfterminRepo(@Nullable Impftermin impftermin1) {
		this.impftermin1 = impftermin1;
	}

	@SuppressWarnings("unused")
	public void setImpftermin2(@Nullable Impftermin impftermin2) throws IllegalAccessException {
		throw new IllegalAccessException("Achtung, Impftermin muss ueber ImpfterminRepo gesetzt werden!");
	}

	public void setImpftermin2FromImpfterminRepo(@Nullable Impftermin impftermin2) {
		this.impftermin2 = impftermin2;
	}

	/**
	 * handhabt Abschluss einer Reg nach 1. Impfung weil sie mit einem Impfstoff impft der nur 1 Dosis braucht oder
	 * weil sie ein externes Zertifikat hat welches belegt dass sie nur noch eine 1 Impfung braucht
	 */
	@JsonIgnore
	public void setStatusToAbgeschlossenAfterErstimpfung(
		@NonNull ImpfinformationDto infos,
		@NotNull Impfung erstimpfung
	) {
		if (erstimpfung.getImpfstoff().getAnzahlDosenBenoetigt() == 1) {
			setStatusToAbgeschlossen(infos, erstimpfung); // vollstaendige grundimmunisierung in VacMe, weil Impfstoff nur 1 Impfung braucht
		} else {
			setStatusToAbgeschlossenWithExtZertifikatPlusVacme(infos, erstimpfung); // externe Impfungen plus 1 VacMe-Impfung
		}
	}

	@JsonIgnore
	public void setStatusToAbgeschlossen(
		@NonNull ImpfinformationDto infos,
		@NotNull Impfung relevanteImpfung
	) {
		this.setRegistrierungStatus(RegistrierungStatus.ABGESCHLOSSEN);
		this.setTimestampZuletztAbgeschlossen(LocalDateTime.now());
		this.setVollstaendigerImpfschutzFlagAndTyp(VollstaendigerImpfschutzTyp.VOLLSTAENDIG_VACME);
		this.setZweiteImpfungVerzichtetGrund(null);
		this.setZweiteImpfungVerzichtetZeit(null);

		// Erst ganz am Schluss koennen wir ermitteln, ob ein Zertifikat erlaubt ist, sonst berechnen wir aufgrund
		// falscher Daten denn manche fuer die Zertifikatsentscheidung relevanten Felder werden erst gerade gesetzt!
		this.setGenerateZertifikatTrueIfAllowed(infos, relevanteImpfung);
	}

	@JsonIgnore
	public void setStatusToAbgeschlossenWithExtZertifikatPlusVacme(
		@NonNull ImpfinformationDto infos,
		@NotNull Impfung relevanteImpfung
	) {
		this.setRegistrierungStatus(RegistrierungStatus.ABGESCHLOSSEN);
		this.setTimestampZuletztAbgeschlossen(LocalDateTime.now());
		this.setVollstaendigerImpfschutzFlagAndTyp((infos.getExternesZertifikat() != null && infos.getExternesZertifikat().isGenesen())
			? VollstaendigerImpfschutzTyp.VOLLSTAENDIG_EXT_GENESEN_PLUS_VACME
			: VollstaendigerImpfschutzTyp.VOLLSTAENDIG_EXT_PLUS_VACME);
		this.setZweiteImpfungVerzichtetGrund(null);
		this.setZweiteImpfungVerzichtetZeit(null);

		// Erst ganz am Schluss koennen wir ermitteln, ob ein Zertifikat erlaubt ist, sonst berechnen wir aufgrund
		// falscher Daten denn manche fuer die Zertifikatsentscheidung relevanten Felder werden erst gerade gesetzt!
		this.setGenerateZertifikatTrueIfAllowed(infos, relevanteImpfung);
	}

	@JsonIgnore
	public void setStatusToImmunisiertWithExternZertifikat(ExternesZertifikat externesZertifikat) {
		setRegistrierungStatus(IMMUNISIERT);
		setVollstaendigerImpfschutzFlagAndTyp(externesZertifikat.isGenesen()
			? VollstaendigerImpfschutzTyp.VOLLSTAENDIG_EXTERNESZERTIFIKAT_GENESEN
			: VollstaendigerImpfschutzTyp.VOLLSTAENDIG_EXTERNESZERTIFIKAT);
		setTimestampZuletztAbgeschlossen(externesZertifikat.getLetzteImpfungDate().atStartOfDay());
	}

	private void validatePositivGetestetDatum(@Nullable LocalDate positivGetestetDatum) {
		if (positivGetestetDatum != null) {
			// Vergangenheit
			if (positivGetestetDatum.isAfter(LocalDate.now())) {
				throw AppValidationMessage.POSITIVER_TEST_DATUM_INVALID.create(positivGetestetDatum);
			}
			// nicht vor 1.1.2020
			if (positivGetestetDatum.isBefore(Constants.MIN_DATE_FOR_PCR_TEST)) {
				throw AppValidationMessage.POSITIVER_TEST_DATUM_INVALID.create(positivGetestetDatum);
			}
		}
	}

	@JsonIgnore
	public void setStatusToAbgeschlossenOhneZweiteImpfung(
		@NonNull ImpfinformationDto infos,
		boolean vollstaendigerImpfschutz,
		@Nullable String begruendung,
		@Nullable LocalDate positivGetestetDatum
	) {
		LocalDateTime abgeschlossenTime = LocalDateTime.now();
		Impfung impfung1 = infos.getImpfung1();
		Objects.requireNonNull(impfung1);

		this.setRegistrierungStatus(RegistrierungStatus.ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG);
		this.setTimestampZuletztAbgeschlossen(abgeschlossenTime);
		if (vollstaendigerImpfschutz) {
			// Vollstaendiger Impfschutz ohne zweite Impfung kann nur im Fall von Corona vorkommen, daher:
			// Hier kommen wir nur bei neu erfassten Corona-Infektionen hin, wir haben also immer ein PCR-Datum
			Objects.requireNonNull(positivGetestetDatum);
			this.setZweiteImpfungVerzichtetGrund(null);
			this.setGenesen(true);
			validatePositivGetestetDatum(positivGetestetDatum);
			this.setPositivGetestetDatum(positivGetestetDatum);
			this.setVollstaendigerImpfschutzFlagAndTyp(VollstaendigerImpfschutzTyp.VOLLSTAENDIG_VACME_GENESEN);
		} else {
			impfung1.setGenerateZertifikat(false);
			this.setZweiteImpfungVerzichtetGrund(begruendung);
			this.setGenesen(false);
			this.setPositivGetestetDatum(null);
			this.setVollstaendigerImpfschutzFlagAndTyp(null);
		}
		this.setZweiteImpfungVerzichtetZeit(abgeschlossenTime);

		// Erst ganz am Schluss koennen wir ermitteln, ob ein Zertifikat erlaubt ist,  sonst berechnen wir aufgrund
		// falscher Daten denn manche fuer die Zertifikatsentscheidung relevanten Felder werden erst gerade gesetzt!
		this.setGenerateZertifikatTrueIfAllowed(infos, impfung1);
	}

	@JsonIgnore
	public void setStatusToAutomatischAbgeschlossen() {
		this.setRegistrierungStatus(RegistrierungStatus.AUTOMATISCH_ABGESCHLOSSEN);
		this.setTimestampZuletztAbgeschlossen(LocalDateTime.now());
		this.setVollstaendigerImpfschutzFlagAndTyp(null);
		this.setZweiteImpfungVerzichtetGrund(null);
		this.setZweiteImpfungVerzichtetZeit(null);
	}

	@JsonIgnore
	public void setStatusToNichtAbgeschlossenStatus(@NonNull RegistrierungStatus status, @Nullable Impfung ersteImpfung) {
		// Darf nur fuer VOR-Booster-Status verwendet werden. Darum ist hier nur die Impfung 1 relevant
		Validate.isTrue(!RegistrierungStatus.getMindestensGrundimmunisiertOrAbgeschlossen().contains(status));
		this.setRegistrierungStatus(status);
		this.setTimestampZuletztAbgeschlossen(null);
		this.setVollstaendigerImpfschutzFlagAndTyp(null);
		this.setZweiteImpfungVerzichtetGrund(null);
		this.setZweiteImpfungVerzichtetZeit(null);
		this.setGenesen(false);
		this.setPositivGetestetDatum(null);
		if (ersteImpfung != null) {
			ersteImpfung.setGenerateZertifikat(false);
		}
	}

	@JsonIgnore
	public void setStatusToImmunisiertAfterBooster(
		@NonNull ImpfinformationDto infos,
		@NotNull Impfung relevanteImpfung
	) {
		this.setRegistrierungStatus(RegistrierungStatus.IMMUNISIERT);
		this.setTimestampZuletztAbgeschlossen(LocalDateTime.now());

		// Wenn beim Boostern der vollstaendigeImpfschutz fehlt, ist etwas schiefgelaufen!
		Validate.notNull(vollstaendigerImpfschutz);
		Validate.isTrue(vollstaendigerImpfschutz);
		Validate.notNull(vollstaendigerImpfschutzTyp);

		// Erst ganz am Schluss koennen wir ermitteln, ob ein Zertifikat erlaubt ist, sonst berechnen wir aufgrund
		// falscher Daten denn manche fuer die Zertifikatsentscheidung relevanten Felder werden erst gerade gesetzt!
		this.setGenerateZertifikatTrueIfAllowed(infos, relevanteImpfung);
	}

	@NonNull
	public static ID<Registrierung> toId(@NonNull UUID id) {
		return new ID<>(id, Registrierung.class);
	}

	public void setBenutzerId(@Nullable UUID benutzerId) {
		if (this.benutzerId != null && !this.benutzerId.equals(benutzerId)) {
			throw new AppFailureException("Not allowed to change the benutzerId from " + this.benutzerId + " to " + benutzerId);
		}
		this.benutzerId = benutzerId;
	}

	public void setGenerateZertifikatTrueIfAllowed(
		@NonNull ImpfinformationDto infos,
		@Nullable Impfung relevanteImpfung
	) {
		if (relevanteImpfung == null) {
			return;
		}
		final boolean deservesZertifikat = DeservesZertifikatValidator.deservesZertifikat(infos.getRegistrierung(), relevanteImpfung, infos.getExternesZertifikat());
		relevanteImpfung.setGenerateZertifikat(deservesZertifikat);
	}

	private void setKrankenkasseKartenNr(@NotNull @NonNull String kkkNummerNeu) {
		this.krankenkasseKartenNr = kkkNummerNeu; // use setKrankenkasseKartenNrAndArchive from outside this class
	}

	// ueberschreibt den default Setter, weil wir die alten Nummern archivieren wollen
	public void setKrankenkasseKartenNrAndArchive(@NotNull @NonNull String kkkNummerNeu) {
		archiviereKkkNummer(this.krankenkasseKartenNr, kkkNummerNeu);
		this.krankenkasseKartenNr = kkkNummerNeu;
	}

	private void archiviereKkkNummer(@Nullable String kkkNummerBisher, @NotNull @NonNull String kkkNummerNeu) {
		if (kkkNummerBisher != null && !kkkNummerBisher.equals(kkkNummerNeu)) {
			KkkNummerAlt kkkNummerAlt = new KkkNummerAlt();
			kkkNummerAlt.setRegistrierung(this);
			kkkNummerAlt.setNummer(kkkNummerBisher);
			kkkNummerAlt.setAktivBis(LocalDateTime.now());
			this.getKkkNummerAltSet().add(kkkNummerAlt);
		}
	}
}
