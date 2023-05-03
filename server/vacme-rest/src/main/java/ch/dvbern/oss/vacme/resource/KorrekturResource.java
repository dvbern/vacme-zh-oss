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

package ch.dvbern.oss.vacme.resource;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import ch.dvbern.oss.vacme.entities.UserPrincipal;
import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import ch.dvbern.oss.vacme.entities.zertifikat.Zertifikat;
import ch.dvbern.oss.vacme.jax.korrektur.EmailTelephoneKorrekturJax;
import ch.dvbern.oss.vacme.jax.korrektur.ImpfungDatumKorrekturJax;
import ch.dvbern.oss.vacme.jax.korrektur.ImpfungKorrekturJax;
import ch.dvbern.oss.vacme.jax.korrektur.ImpfungLoeschenJax;
import ch.dvbern.oss.vacme.jax.korrektur.ImpfungOdiKorrekturJax;
import ch.dvbern.oss.vacme.jax.korrektur.ImpfungSelbstzahlendeKorrekturJax;
import ch.dvbern.oss.vacme.jax.korrektur.ImpfungVerabreichungKorrekturJax;
import ch.dvbern.oss.vacme.jax.korrektur.PersonendatenKorrekturJax;
import ch.dvbern.oss.vacme.jax.registration.KorrekturDashboardJax;
import ch.dvbern.oss.vacme.jax.registration.PersonalienJax;
import ch.dvbern.oss.vacme.rest.auth.Authorizer;
import ch.dvbern.oss.vacme.service.ApplicationPropertyService;
import ch.dvbern.oss.vacme.service.FragebogenService;
import ch.dvbern.oss.vacme.service.KorrekturService;
import ch.dvbern.oss.vacme.service.PersonalienSucheService;
import ch.dvbern.oss.vacme.service.RegistrierungService;
import ch.dvbern.oss.vacme.service.ZertifikatRunnerService;
import ch.dvbern.oss.vacme.service.ZertifikatRunnerService.ZertifikatResultDto;
import ch.dvbern.oss.vacme.service.ZertifikatService;
import ch.dvbern.oss.vacme.service.covidcertificate.CovidCertBatchType;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.shared.util.OpenApiConst;
import ch.dvbern.oss.vacme.util.RegistrierungUtil;
import ch.dvbern.oss.vacme.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.tags.Tags;

import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.AS_BENUTZER_VERWALTER;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.KT_MEDIZINISCHE_NACHDOKUMENTATION;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.KT_NACHDOKUMENTATION;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.OI_DOKUMENTATION;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.OI_IMPFVERANTWORTUNG;
import static ch.dvbern.oss.vacme.rest.auth.ClientApplications.VACME_WEB;
import static ch.dvbern.oss.vacme.service.covidcertificate.CovidCertBatchType.mapCreationBatchTypeFromRegistrierungEingangsart;
import static ch.dvbern.oss.vacme.service.covidcertificate.CovidCertBatchType.mapRevocationBatchTypeFromRegistrierungEingangsart;

@ApplicationScoped
@Transactional
@Tags(@Tag(name = OpenApiConst.TAG_KORREKTUR))
@Path(VACME_WEB + "/korrektur")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class KorrekturResource {

	private final Authorizer authorizer;
	private final KorrekturService korrekturService;
	private final ZertifikatRunnerService zertifikatRunnerService;
	private final PersonalienSucheService sucheService;
	private final ZertifikatService zertifikatService;
	private final FragebogenService fragebogenService;
	private final ImpfinformationenService impfinformationenService;
	private final RegistrierungService registrierungService;
	private final ApplicationPropertyService propertyService;
	private final UserPrincipal userPrincipal;

	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/dashboard/{registrierungsnummer}")
	@RolesAllowed({ OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, AS_BENUTZER_VERWALTER, KT_NACHDOKUMENTATION,
		KT_MEDIZINISCHE_NACHDOKUMENTATION })
	public KorrekturDashboardJax getDashboardRegistrierung(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		ImpfinformationDto impfinformationen =
			this.impfinformationenService.getImpfinformationenAndEnsureStatusIsAktuell(registrierungsnummer);
		Registrierung registrierung = impfinformationen.getRegistrierung();
		authorizer.checkReadAuthorization(registrierung);

		LocalDateTime timestampLetzterPostversand =
			zertifikatRunnerService.getTimestampOfLastPostversand(registrierung);
		Fragebogen fragebogen = this.fragebogenService.findFragebogenByRegistrierungsnummer(registrierungsnummer);

		return new KorrekturDashboardJax(
			impfinformationen,
			fragebogen,
			timestampLetzterPostversand);
	}

	@PUT
	@Operation(summary = "Korrigiert eine Impfung")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("impfung/{regNummer}")
	@RolesAllowed({ OI_IMPFVERANTWORTUNG, OI_DOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION })
	public Response impfungKorrigieren(
		@PathParam("regNummer") @NonNull String regNummer,
		@NonNull @Valid ImpfungKorrekturJax korrekturJax
	) {
		return basicImpfungKorrigieren(
			regNummer,
			korrekturJax.getImpffolge(),
			korrekturJax.getImpffolgeNr(),
			(impftermin, impfinformationen) -> {
				final OrtDerImpfung ortDerImpfung = impftermin.getImpfslot().getOrtDerImpfung();
				authorizer.checkBenutzerAssignedToOdi(ortDerImpfung);
				// Fuer nicht FachBABs muss geprueft werden, der ODI noch aktiv ist
				if (ortDerImpfung.isDeaktiviert() && !authorizer.isUserFachBABOrKanton()) {
					throw AppValidationMessage.ODI_DEACTIVATED.create(ortDerImpfung.getName());
				}
				this.korrekturService.impfungKorrigieren(korrekturJax, impfinformationen, impftermin);
			});
	}

	@PUT
	@Operation(summary = "Korrigiert den ODI einer Impfung")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("odi/{regNummer}")
	@RolesAllowed({ OI_IMPFVERANTWORTUNG, KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION })
	public Response impfungOdiKorrigieren(
		@PathParam("regNummer") @NonNull String regNummer,
		@NonNull @Valid ImpfungOdiKorrekturJax korrekturJax
	) {
		return basicImpfungKorrigieren(
			regNummer,
			korrekturJax.getImpffolge(),
			korrekturJax.getImpffolgeNr(),
			(impftermin, impfinformation) -> {
				authorizer.checkBenutzerAssignedToOdi(Objects.requireNonNull(impftermin)
					.getImpfslot()
					.getOrtDerImpfung());
				this.korrekturService.impfungOdiKorrigieren(korrekturJax, impftermin, impfinformation);
			});
	}

	@PUT
	@Operation(summary = "Loescht eine Impfung")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("impfungLoeschen/{regNummer}")
	@RolesAllowed({ OI_IMPFVERANTWORTUNG, KT_MEDIZINISCHE_NACHDOKUMENTATION })
	public Response impfungLoeschen(
		@NonNull @PathParam("regNummer") String regNummer,
		@NonNull @Valid ImpfungLoeschenJax korrekturJax
	) {
		return basicImpfungKorrigieren(
			regNummer,
			korrekturJax.getImpffolge(),
			korrekturJax.getImpffolgeNr(),
			(impftermin, impfinformation) ->
				this.korrekturService.impfungLoeschen(
					impfinformation,
					korrekturJax.getImpffolge(),
					korrekturJax.getImpffolgeNr())
		);
	}

	@PUT
	@Operation(summary = "Korrigiert ob eine Impfung durch die Impfkampagne bezahlt wurde")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("impfungSelbstzahlendeKorrigieren/{regNummer}")
	@RolesAllowed({ OI_IMPFVERANTWORTUNG, KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION })
	public Response impfungSelbstzahlendeKorrigieren(
		@NonNull @PathParam("regNummer") String regNummer,
		@NonNull @Valid ImpfungSelbstzahlendeKorrekturJax korrekturJax
	) {
		return basicImpfungKorrigieren(
			regNummer,
			korrekturJax.getImpffolge(),
			korrekturJax.getImpffolgeNr(),
			(impftermin, impfinformation) -> korrekturService.impfungSelbstzahlendeKorrigieren(
				korrekturJax,
				impftermin,
				impfinformation));
	}

	@PUT
	@Operation(summary = "Korrigiert die Verabreichung einer Impfung")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("impfungVerabreichungKorrigieren/{regNummer}")
	@RolesAllowed({ OI_IMPFVERANTWORTUNG, KT_MEDIZINISCHE_NACHDOKUMENTATION })
	public Response impfungVerabreichungKorrigieren(
		@NonNull @PathParam("regNummer") String regNummer,
		@NonNull @Valid ImpfungVerabreichungKorrekturJax korrekturJax
	) {
		return basicImpfungKorrigieren(
			regNummer,
			korrekturJax.getImpffolge(),
			korrekturJax.getImpffolgeNr(),
			(impftermin, impfinformation) -> {
				this.korrekturService.impfungVerabreichungKorrigieren(korrekturJax, impftermin, impfinformation);
			});
	}

	@PUT
	@Operation(summary = "Korrigiert das Datum einer Impfung")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("impfungDatumKorrigieren/{regNummer}")
	@RolesAllowed({ OI_IMPFVERANTWORTUNG, KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION })
	public Response impfungDatumKorrigieren(
		@NonNull @PathParam("regNummer") String regNummer,
		@NonNull @Valid ImpfungDatumKorrekturJax korrekturJax
	) {
		return basicImpfungKorrigieren(
			regNummer,
			korrekturJax.getImpffolge(),
			korrekturJax.getImpffolgeNr(),
			(impftermin, impfinformation) -> {
				this.korrekturService.impfungDatumKorrigieren(korrekturJax, impftermin, impfinformation);
			});
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("emailTelephone/{regNummer}")
	@RolesAllowed({ KT_NACHDOKUMENTATION })
	public Response emailTelephoneKorrigieren(
		@NonNull @PathParam("regNummer") String regNummer,
		@NonNull @Valid EmailTelephoneKorrekturJax korrekturJax
	) {
		Registrierung registrierung = registrierungService.findRegistrierung(regNummer);
		authorizer.checkUpdateAuthorization(registrierung);
		if (!propertyService.isEmailKorrekturEnabled()) {
			throw new AppFailureException("Should not call this korrektur when it is disabled");
		}
		if (registrierung.getBenutzerId() == null) {
			throw AppValidationMessage.NO_USER_FOR_REGISTRATION.create(regNummer);
		}
		korrekturService.updateEmailTelephone(registrierung.getBenutzerId(), korrekturJax);
		return Response.ok().build();
	}

	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/personendaten/{registrierungsnummer}")
	@RolesAllowed({ OI_IMPFVERANTWORTUNG, KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION })
	public PersonendatenKorrekturJax getPersonendaten(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		final Fragebogen fragebogen = fragebogenService.findFragebogenByRegistrierungsnummer(registrierungsnummer);
		authorizer.checkReadAuthorization(fragebogen.getRegistrierung());

		return PersonendatenKorrekturJax.from(fragebogen);
	}

	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("suchen/{vorname}/{name}/{geburtsdatum}")
	@RolesAllowed({ KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION })
	public List<PersonalienJax> suchen(
		@NonNull @NotNull @PathParam("vorname") String vorname,
		@NonNull @NotNull @PathParam("name") String name,
		@NonNull @NotNull @PathParam("geburtsdatum") Date geburtsdatum
	) {
		return sucheService.suchen(vorname, name, geburtsdatum)
			.stream()
			.map(PersonalienJax::createWithRegNumber)
			.collect(Collectors.toList());
	}

	@PUT
	@Operation(summary = "Korrigiert allgemeine Personendaten")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("personendatenKorrigieren/{regNummer}")
	@RolesAllowed({ OI_IMPFVERANTWORTUNG, KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION })
	public Response personendatenKorrigieren(
		@NonNull @PathParam("regNummer") String regNummer,
		@NonNull @Valid PersonendatenKorrekturJax korrekturJax
	) {
		final Fragebogen fragebogen = fragebogenService.findFragebogenByRegistrierungsnummer(regNummer);
		final ImpfinformationDto impfinformationen = impfinformationenService.getImpfinformationenNoCheck(regNummer);
		final Registrierung registrierung = fragebogen.getRegistrierung();
		ValidationUtil.validateNotArchiviert(registrierung);
		authorizer.checkUpdateAuthorization(registrierung);
		if (hasAbglElekAuswChangedToFalse(registrierung, korrekturJax)) {
			checkHasNoNonRevokedZertifikat(registrierung);
		}

		// wir validieren dass eine Impfung besteht und dass der User fuer den Ort berechtigt ist
		Collection<Impftermin> existingImpftermineOfReg =
			RegistrierungUtil.getExistingImpftermineOfReg(impfinformationen);
		checkHasAnImpfungInOdIUserIsAllowedFor(existingImpftermineOfReg);

		this.korrekturService.personendatenKorrigieren(fragebogen, impfinformationen, korrekturJax);

		return Response.ok().build();
	}

	private void checkHasNoNonRevokedZertifikat(@NonNull Registrierung registrierung) {
		Optional<Zertifikat> zertifikat = zertifikatService.getNewestNonRevokedZertifikat(registrierung);
		boolean hasAnyZertifikat = zertifikat.isPresent();
		if (hasAnyZertifikat) {
			throw AppValidationMessage.REGISTRIERUNG_ZERTIFIKAT_ALREADY_GENERATED.create(
				registrierung.getRegistrierungsnummer(),
				zertifikat.get().getTimestampErstellt());
		}
	}

	private boolean hasAbglElekAuswChangedToFalse(
		@NonNull Registrierung registrierung,
		@NonNull PersonendatenKorrekturJax korrekturJax
	) {
		return registrierung.isAbgleichElektronischerImpfausweis()
			&& !korrekturJax.isAbgleichElektronischerImpfausweis();
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("regenerateZertifikate/{regNummer}")
	@RolesAllowed(AS_BENUTZER_VERWALTER)
	public Response regenerateZertifikate(
		@NonNull @PathParam("regNummer") String regNummer,
		@NotNull boolean forcePostzustellung
	) throws Exception {

		ImpfinformationDto impfinformationen = impfinformationenService.getImpfinformationenNoCheck(regNummer);
		Registrierung registrierung = impfinformationen.getRegistrierung();
		CovidCertBatchType type = forcePostzustellung == Boolean.TRUE ? CovidCertBatchType.POST :
			mapCreationBatchTypeFromRegistrierungEingangsart(registrierung.getRegistrierungsEingang());
		authorizer.checkUpdateAuthorization(registrierung);
		// wir validieren dass eine Impfung besteht und dass der User fuer den Ort berechtigt ist
		Collection<Impftermin> existingImpftermineOfReg =
			RegistrierungUtil.getExistingImpftermineOfReg(impfinformationen);
		checkHasAnImpfungInOdIUserIsAllowedFor(existingImpftermineOfReg);
		ValidationUtil.validateCanGetZertifikat(registrierung);

		// In diesen Fall versuchen wir, fuer die neueste Impfung ein Zertifikat zu erstellen
		final ID<Impfung> idOfNewestImpfung = ImpfinformationenService.getNewestVacmeImpfungId(impfinformationen);
		ZertifikatResultDto result = zertifikatRunnerService.createZertifikatForRegistrierung(
			registrierung.getRegistrierungsnummer(),
			idOfNewestImpfung,
			type);
		if (!result.success) {
			throw result.exception != null ?
				result.exception :
				AppValidationMessage.ZERTIFIKAT_GENERIERUNG_FEHLER.create();
		}

		return Response.ok().build();
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("revokeZertifikate/{regNummer}")
	@RolesAllowed(AS_BENUTZER_VERWALTER)
	public Response revokeZertifikate(
		@NonNull @PathParam("regNummer") String regNummer,
		@NotNull boolean forcePostzustellung
	) {
		ImpfinformationDto impfinformationen = impfinformationenService.getImpfinformationenNoCheck(regNummer);
		Registrierung registrierung = impfinformationen.getRegistrierung();
		CovidCertBatchType type = forcePostzustellung == Boolean.TRUE ? CovidCertBatchType.POST :
			mapRevocationBatchTypeFromRegistrierungEingangsart(registrierung.getRegistrierungsEingang());
		authorizer.checkUpdateAuthorization(registrierung);
		// wir validieren dass FALLS eine Impfung besteht der User fuer den Ort berechtigt ist
		// Es kann aber auch den Fall geben, dass es gar keine Termine mehr gibt, weil alle Impfungen geloescht
		// wurden und deshalb revoziert werden soll.
		Collection<Impftermin> existingImpftermineOfReg =
			RegistrierungUtil.getExistingImpftermineOfReg(impfinformationen);
		if (!existingImpftermineOfReg.isEmpty()) {
			checkHasAnImpfungInOdIUserIsAllowedFor(existingImpftermineOfReg);
		}

		try {
			boolean success = zertifikatRunnerService.revokeZertifikateForRegistrierung(regNummer, type);
			if (!success) {
				throw AppValidationMessage.ZERTIFIKAT_REVOCATION_FEHLER.create();
			}
		} catch (Exception e) {
			throw AppValidationMessage.ZERTIFIKAT_REVOCATION_FEHLER.create();
		}
		return Response.ok().build();
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/recreateZertifikat/{regNummer}")
	@RolesAllowed({ KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION })
	public Response recreateZertifikat(
		@NonNull @PathParam("regNummer") String regNummer,
		@NotNull boolean forcePostzustellung
	) throws Exception {
		ImpfinformationDto impfinformationen = impfinformationenService.getImpfinformationenNoCheck(regNummer);
		Registrierung registrierung = impfinformationen.getRegistrierung();
		CovidCertBatchType typeCreation = forcePostzustellung == Boolean.TRUE ? CovidCertBatchType.POST :
			mapCreationBatchTypeFromRegistrierungEingangsart(registrierung.getRegistrierungsEingang());
		authorizer.checkUpdateAuthorization(registrierung);

		// wir validieren dass eine Impfung besteht und dass der User fuer den Ort berechtigt ist
		Collection<Impftermin> existingImpftermineOfReg =
			RegistrierungUtil.getExistingImpftermineOfReg(impfinformationen);
		checkHasAnImpfungInOdIUserIsAllowedFor(existingImpftermineOfReg);

		// In diesen Fall versuchen wir, fuer die neueste Impfung ein Zertifikat zu erstellen
		final ID<Impfung> idOfNewestImpfung = ImpfinformationenService.getNewestVacmeImpfungId(impfinformationen);

		this.zertifikatRunnerService.createZertifikatForRegistrierung(registrierung, idOfNewestImpfung, typeCreation);
		return Response.ok().build();
	}

	private void checkHasAnImpfungInOdIUserIsAllowedFor(Collection<Impftermin> existingImpftermineOfReg) {
		if (existingImpftermineOfReg.isEmpty()) {
			throw AppValidationMessage.ILLEGAL_STATE.create("Es muss ein Impftermin existieren");
		}
		List<Impfung> impfungenByTerminen = this.korrekturService.getImpfungenByTerminen(existingImpftermineOfReg);
		if (impfungenByTerminen.isEmpty()) {
			throw AppValidationMessage.ILLEGAL_STATE.create("Es muss eine Impfung existieren");
		}
		if (authorizer.isUserIndependentFromOdis()) { // independent user are always allowed
			return;
		}
		List<OrtDerImpfung> odis = existingImpftermineOfReg.stream()
			.map(impftermin -> impftermin.getImpfslot().getOrtDerImpfung()).collect(Collectors.toList());
		authorizer.checkBenutzerAssignedToAtLeastOneOdi(odis);
	}

	private Response basicImpfungKorrigieren(
		@NonNull String regNummer,
		@NonNull Impffolge impffolge,
		@Nullable Integer impffolgeNr,
		@NonNull ImpfKorrektur korrekturMethod
	) {
		ImpfinformationDto impfinformationen = this.impfinformationenService.getImpfinformationenNoCheck(regNummer);
		final Registrierung registrierung = impfinformationen.getRegistrierung();
		ValidationUtil.validateNotArchiviert(registrierung);
		authorizer.checkUpdateAuthorization(registrierung);
		ImpfinformationenService.validateImpffolgeExists(impffolge, impffolgeNr, impfinformationen);
		final Impftermin impftermin = ImpfinformationenService.getImpftermin(impfinformationen, impffolge,
			impffolgeNr);
		if (impftermin == null) {
			throw AppValidationMessage.IMPFFOLGE_NOT_EXISTING.create(impffolge);
		}
		authorizer.checkBenutzerAssignedToOdi(impftermin.getImpfslot().getOrtDerImpfung());
		korrekturMethod.doImpfKorrektur(impftermin, impfinformationen);

		return Response.ok().build();
	}

	private interface ImpfKorrektur {
		void doImpfKorrektur(@NonNull Impftermin impftermin, @NonNull ImpfinformationDto impfinformation);
	}

}
