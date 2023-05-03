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

package ch.dvbern.oss.vacme.jax.registration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import ch.dvbern.oss.vacme.entities.impfen.Impfdossiereintrag;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Geschlecht;
import ch.dvbern.oss.vacme.entities.registration.Personenkontrolle;
import ch.dvbern.oss.vacme.entities.registration.Prioritaet;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungsEingang;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.PlzMappingException;
import ch.dvbern.oss.vacme.util.DeservesZertifikatValidator;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.util.ValidationUtil;
import lombok.Getter;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@Getter
@Setter
public class DashboardJax {

	private String registrierungsnummer;
	private UUID registrierungId;
	private RegistrierungStatus status;
	private Prioritaet prioritaet;
	private OrtDerImpfungDisplayNameJax gewuenschterOrtDerImpfung;
	private boolean nichtVerwalteterOdiSelected;
	private ImpfterminJax termin1;
	private ImpfterminJax termin2;
	private ImpfterminJax terminNPending; // Immer der aktuelle, nur Booster, nur solange nicht geimpft!
	private String vorname;
	private String name;
	private Geschlecht geschlecht;
	private LocalDate geburtsdatum;
	private boolean immobil;
	private @Nullable ImpfungJax impfung1;
	private @Nullable ImpfungJax impfung2;
	private RegistrierungsEingang eingang;
	private @Nullable AbgesagteTermineJax abgesagteTermine;
	private boolean elektronischerImpfausweis;
	private boolean vollstaendigerImpfschutz;
	private @Nullable LocalDateTime timestampLetzterPostversand;
	private boolean gueltigeSchweizerAdresse;
	private boolean hasBenutzer;
	private @Nullable UUID benutzerId;
	private @Nullable ImpfdossierJax impfdossier;
	private @Nullable ExternGeimpftJax externGeimpft;
	private boolean selbstzahler = false;
	private @Nullable Boolean immunsupprimiert;

	// Daten von bisherigem GeimpftJax:
	@Nullable
	private String zweiteImpfungVerzichtetGrund;

	@Nullable
	private LocalDate positivGetestetDatum;

	@Nullable
	private LocalDateTime zweiteImpfungVerzichtetZeit;

	@Nullable
	private LocalDateTime timestampZuletztAbgeschlossen;

	@Nullable
	private LocalDateTime timestampArchiviert;

	@Nullable
	private RegistrierungsEingang registrierungsEingang;

	@Nullable
	private List<ImpfungJax> boosterImpfungen;

	private boolean abgleichElektronischerImpfausweis = false;

	// Von KommentarJax:

	@Nullable
	private String bemerkungenRegistrierung;

	@Nullable
	private List<KontrolleKommentarJax> kommentare = new LinkedList<>();

	@NonNull
	private CurrentZertifikatInfo currentZertifikatInfo = new CurrentZertifikatInfo();

	@Nullable
	private LocalDateTime timestampPhonenumberUpdate;

	public DashboardJax(@NonNull ImpfinformationDto infos, @NonNull Fragebogen fragebogen) {
		Registrierung registrierung = infos.getRegistrierung();

		this.registrierungsnummer = registrierung.getRegistrierungsnummer();
		this.registrierungId = registrierung.getId();
		this.status = registrierung.getRegistrierungStatus();
		this.prioritaet = registrierung.getPrioritaet();
		if (registrierung.getGewuenschterOdi() != null) {
			this.gewuenschterOrtDerImpfung = new OrtDerImpfungDisplayNameJax(registrierung.getGewuenschterOdi());
		}
		this.nichtVerwalteterOdiSelected = registrierung.isNichtVerwalteterOdiSelected();
		if (registrierung.getImpftermin1() != null) {
			this.termin1 = new ImpfterminJax(registrierung.getImpftermin1());
		}
		if (registrierung.getImpftermin2() != null) {
			this.termin2 = new ImpfterminJax(registrierung.getImpftermin2());
		}
		// terminNPending ist der aktuell gebuchte Booster-Termin (noch nicht wahrgenommen)
		final Optional<Impftermin> pendingBoosterTerminOptional = ImpfinformationenService.getPendingBoosterTermin(infos);
		pendingBoosterTerminOptional.ifPresent(impftermin -> this.terminNPending = new ImpfterminJax(impftermin));
		this.vorname = registrierung.getVorname();
		this.name = registrierung.getName();
		this.geschlecht = registrierung.getGeschlecht();
		this.geburtsdatum = registrierung.getGeburtsdatum();
		this.immobil = registrierung.isImmobil();
		this.eingang = registrierung.getRegistrierungsEingang();
		if (registrierung.getAbgesagteTermine() != null) {
			this.abgesagteTermine = AbgesagteTermineJax.of(registrierung.getAbgesagteTermine());
		}
		this.elektronischerImpfausweis = registrierung.isAbgleichElektronischerImpfausweis();
		this.vollstaendigerImpfschutz = registrierung.abgeschlossenMitVollstaendigemImpfschutz();

		// Wir muessen das Flag auch fuer Online abfuellen, da man den Post-Versand auch dort erzwingen kann
		try {
			ValidationUtil.validateAndNormalizePlz(registrierung.getAdresse().getPlz());
			this.gueltigeSchweizerAdresse = true;
		} catch (PlzMappingException ignore) {
			this.gueltigeSchweizerAdresse = false;
		}

		this.hasBenutzer = registrierung.getBenutzerId() != null;
		this.benutzerId = registrierung.getBenutzerId();

		// von bisherigem GeimpftJax:
		this.setRegistrierungsnummer(registrierung.getRegistrierungsnummer());
		this.setStatus(registrierung.getRegistrierungStatus());
		this.setVorname(registrierung.getVorname());
		this.setName(registrierung.getName());
		this.setGeburtsdatum(registrierung.getGeburtsdatum());
		this.setTimestampZuletztAbgeschlossen(registrierung.getTimestampZuletztAbgeschlossen());
		this.setZweiteImpfungVerzichtetGrund(registrierung.getZweiteImpfungVerzichtetGrund());
		this.setPositivGetestetDatum(registrierung.getPositivGetestetDatum());
		this.setZweiteImpfungVerzichtetZeit(registrierung.getZweiteImpfungVerzichtetZeit());
		this.setAbgleichElektronischerImpfausweis(registrierung.isAbgleichElektronischerImpfausweis());
		this.setRegistrierungsEingang(registrierung.getRegistrierungsEingang());

		if (infos.getImpfung1() != null) {
			this.impfung1 = ImpfungJax.from(infos.getImpfung1(),
				ImpfinformationenService.getImpffolgeNr(infos, infos.getImpfung1()));
		}
		if (infos.getImpfung2() != null) {
			this.impfung2 = ImpfungJax.from(infos.getImpfung2(),
				ImpfinformationenService.getImpffolgeNr(infos, infos.getImpfung2()));
		}
		this.setTimestampArchiviert(registrierung.getTimestampArchiviert());

		final Impfung newestVacmeImpfung = ImpfinformationenService.getNewestVacmeImpfung(infos);
		this.currentZertifikatInfo.setDeservesZertifikat(ImpfinformationenService.deservesZertifikatForAnyImpfung(infos));
		if (newestVacmeImpfung != null) {
			this.currentZertifikatInfo.setHasPendingZertifikatGeneration(newestVacmeImpfung.isGenerateZertifikat());
			this.currentZertifikatInfo.setDeservesZertifikatForNewestImpfung(
				DeservesZertifikatValidator.deservesZertifikat(registrierung, newestVacmeImpfung, infos.getExternesZertifikat())
			);
		}

		if (infos.getBoosterImpfungen() != null) {
			this.setBoosterImpfungen(infos.getBoosterImpfungen().stream()
				.map(impfung -> ImpfungJax.from(impfung,
					ImpfinformationenService.getImpffolgeNr(infos, impfung)))
				.collect(Collectors.toList()));
		}

		this.impfdossier = infos.getImpfdossier() != null
			? new ImpfdossierJax(infos.getImpfdossier(), infos.getBoosterImpfungen(), infos.getExternesZertifikat())
			: null;

		this.externGeimpft = ExternGeimpftJax.from(infos.getExternesZertifikat());

		// Kontrollkommentare:
		bemerkungenRegistrierung = registrierung.getBemerkung();

		final Personenkontrolle personenkontrolle = fragebogen.getPersonenkontrolle();
		final List<KontrolleKommentarJax> kommentareList = new LinkedList<>();
		KontrolleKommentarJax kommentarJax1 = KontrolleKommentarJax.from(
			personenkontrolle.getKontrolleTermin1(),
			Impffolge.ERSTE_IMPFUNG,
			ImpfinformationenService.getImpffolgeNrOfImpfung1Or2(infos.getExternesZertifikat(), Impffolge.ERSTE_IMPFUNG));
		if (kommentarJax1 != null) {
			kommentareList.add(kommentarJax1);
		}
		KontrolleKommentarJax kommentarJax2 = KontrolleKommentarJax.from(
			personenkontrolle.getKontrolleTermin2(),
			Impffolge.ZWEITE_IMPFUNG,
			ImpfinformationenService.getImpffolgeNrOfImpfung1Or2(infos.getExternesZertifikat(), Impffolge.ZWEITE_IMPFUNG));
		if (kommentarJax2 != null) {
			kommentareList.add(kommentarJax2);
		}
		if (infos.getImpfdossier() != null) {
			for (Impfdossiereintrag eintrag : infos.getImpfdossier().getOrderedEintraege()) {
				KontrolleKommentarJax kommentarJaxN = KontrolleKommentarJax.from(
					eintrag.getImpfungkontrolleTermin(),
					Impffolge.BOOSTER_IMPFUNG,
					eintrag.getImpffolgeNr());
				if (kommentarJaxN != null) {
					kommentareList.add(kommentarJaxN);
				}
			}
		}
		this.kommentare = kommentareList;
		this.selbstzahler = registrierung.isSelbstzahler();
		this.immunsupprimiert = fragebogen.getImmunsupprimiert();
		this.timestampPhonenumberUpdate = registrierung.getTimestampPhonenumberUpdate();
	}

	public DashboardJax(
		@NonNull ImpfinformationDto infos,
		@NonNull Fragebogen fragebogen,
		@Nullable LocalDateTime timestampLetzterPostversand
	) {
		this(infos, fragebogen);
		this.timestampLetzterPostversand = timestampLetzterPostversand;
	}

	public DashboardJax(
		@NonNull ImpfinformationDto infos,
		@NonNull Fragebogen fragebogen,
		boolean hasCovidZertifikat,
		@Nullable LocalDateTime timestampLetzterPostversand
	) {
		this(infos, fragebogen, timestampLetzterPostversand);
		this.currentZertifikatInfo.setHasCovidZertifikat(hasCovidZertifikat);
		this.timestampLetzterPostversand = timestampLetzterPostversand;
	}
}
