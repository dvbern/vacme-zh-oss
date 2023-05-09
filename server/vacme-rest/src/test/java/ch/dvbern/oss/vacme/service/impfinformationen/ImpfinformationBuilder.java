package ch.dvbern.oss.vacme.service.impfinformationen;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import ch.dvbern.oss.vacme.entities.externeimpfinfos.ExternesZertifikat;
import ch.dvbern.oss.vacme.entities.impfen.Impfdossier;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.Impfstoff;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Prioritaet;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.service.ImpfdokumentationService;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.util.TestdataCreationUtil;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ImpfinformationBuilder {

	@NonNull
	private Fragebogen fragebogen;

	@Nullable
	private Impfung impfung1;

	@Nullable
	private Impfung impfung2;

	@Nullable
	private Impfdossier dossier;

	@Nullable
	private List<Impfung> boosterImpfungen;

	@Nullable
	private ExternesZertifikat externesZertifikat;

	private ImpfdokumentationService impfdokumentationService = new ImpfdokumentationService(null, null, null, null, null, null, null, null, null);


	public ImpfinformationBuilder create() {
		// Reset all previous data
		impfung1 = null;
		impfung2 = null;
		externesZertifikat = null;
		fragebogen = TestdataCreationUtil.createFragebogen();
		fragebogen.getRegistrierung().setStatusToNichtAbgeschlossenStatus(RegistrierungStatus.FREIGEGEBEN, null);
		fragebogen.getRegistrierung().setAbgleichElektronischerImpfausweis(true);
		fragebogen.getRegistrierung().setRegistrationTimestamp(LocalDateTime.now());
		return this;
	}

	public ImpfinformationBuilder withAge(int age) {
		fragebogen.getRegistrierung().setGeburtsdatum(LocalDate.now().minusYears(age));
		return this;
	}

	public ImpfinformationBuilder withBirthday(LocalDate bDay) {
		fragebogen.getRegistrierung().setGeburtsdatum(bDay);
		return this;
	}

	public ImpfinformationBuilder withPrioritaet(Prioritaet prio) {
		fragebogen.getRegistrierung().setPrioritaet(prio);
		return this;
	}

	public ImpfinformationBuilder withImpfung1(@NonNull LocalDate dateImpfung1, @NonNull Impfstoff impfstoff) {
		Objects.requireNonNull(fragebogen);
		fragebogen.getRegistrierung().setVollstaendigerImpfschutzFlagAndTyp(null);
		impfung1 = TestdataCreationUtil.createImpfungWithImpftermin(dateImpfung1.atStartOfDay(), Impffolge.ERSTE_IMPFUNG);
		impfung1.setImpfstoff(impfstoff);
		final ImpfinformationDto tempInfos = getInfos();
		impfdokumentationService.setNextStatus(tempInfos, Impffolge.ERSTE_IMPFUNG, impfung1, false); // todo  methode statisch machen und auslagern
		fragebogen.getRegistrierung().setRegistrierungStatus(tempInfos.getRegistrierung().getRegistrierungStatus());
		return this;
	}

	public ImpfinformationBuilder withImpfung2(@NonNull LocalDate dateImpfung2, @NonNull Impfstoff impfstoff) {
		Objects.requireNonNull(fragebogen);
		Objects.requireNonNull(impfung1);
		fragebogen.getRegistrierung().setVollstaendigerImpfschutzFlagAndTyp(null);
		impfung2 = TestdataCreationUtil.createImpfungWithImpftermin(dateImpfung2.atStartOfDay(), Impffolge.ZWEITE_IMPFUNG);
		impfung2.setImpfstoff(impfstoff);
		fragebogen.getRegistrierung().setStatusToAbgeschlossen(getInfos(), impfung2);
		fragebogen.getRegistrierung().setRegistrierungStatus(RegistrierungStatus.IMMUNISIERT);
		return this;
	}

	public ImpfinformationBuilder withCoronaTest(@NonNull LocalDate dateOfTest) {
		Objects.requireNonNull(fragebogen);
		Objects.requireNonNull(impfung1);
		fragebogen.getRegistrierung().setStatusToAbgeschlossenOhneZweiteImpfung(getInfos(),true, null, dateOfTest);
		return this;
	}

	public ImpfinformationBuilder withExternesZertifikat(@NonNull Impfstoff impfstoff, int anzahl, @NonNull LocalDate newestImpfdatum, @Nullable LocalDate dateOfTest) {
		Objects.requireNonNull(fragebogen);
		externesZertifikat = new ExternesZertifikat();
		externesZertifikat.setImpfstoff(impfstoff);
		if (dateOfTest != null) {
			externesZertifikat.setGenesen(true);
			externesZertifikat.setPositivGetestetDatum(dateOfTest);
		} else {
			externesZertifikat.setGenesen(false);
			externesZertifikat.setPositivGetestetDatum(null);
		}
		externesZertifikat.setAnzahlImpfungen(anzahl);
		externesZertifikat.setLetzteImpfungDate(newestImpfdatum);
		fragebogen.getRegistrierung().setStatusToImmunisiertWithExternZertifikat(externesZertifikat);
		return this;
	}

	public ImpfinformationBuilder withBooster(@NonNull LocalDate date, @NonNull Impfstoff impfstoff) {
		return withBooster(date, impfstoff, false);
	}

	public ImpfinformationBuilder withBooster(@NonNull LocalDate date, @NonNull Impfstoff impfstoff, boolean isGrundimmunisierung) {
		Impfdossier tempDossier = new Impfdossier();
		tempDossier.setRegistrierung(fragebogen.getRegistrierung());
		ImpfinformationDto tempInfos = getInfos();
		tempInfos = TestdataCreationUtil.addBoosterImpfung(tempInfos, date, impfstoff);
		boosterImpfungen = tempInfos.getBoosterImpfungen();
		Objects.requireNonNull(boosterImpfungen);
		dossier = tempInfos.getImpfdossier();
		final Impfung relevanteImpfung = boosterImpfungen.get(boosterImpfungen.size() - 1);
		relevanteImpfung.setGrundimmunisierung(isGrundimmunisierung);
		fragebogen.getRegistrierung().setStatusToImmunisiertAfterBooster(tempInfos, relevanteImpfung);
		return this;
	}

	@NonNull
	public Fragebogen getFragebogen() {
		return fragebogen;
	}

	@NonNull
	public Registrierung getRegistrierung() {
		return fragebogen.getRegistrierung();
	}

	@NonNull
	public ImpfinformationDto getInfos() {
		return new ImpfinformationDto(
			new ImpfinformationDto(fragebogen.getRegistrierung(), impfung1, impfung2, dossier, externesZertifikat),
			boosterImpfungen
		);
	}
}
