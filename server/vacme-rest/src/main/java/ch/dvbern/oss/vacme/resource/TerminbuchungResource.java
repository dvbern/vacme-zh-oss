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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungStatus;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impfslot;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import ch.dvbern.oss.vacme.entities.types.daterange.DateUtil;
import ch.dvbern.oss.vacme.jax.impfslot.TermineAbsagenJax;
import ch.dvbern.oss.vacme.jax.registration.DashboardJax;
import ch.dvbern.oss.vacme.jax.registration.ImpfslotJax;
import ch.dvbern.oss.vacme.jax.registration.NextFreierTerminJax;
import ch.dvbern.oss.vacme.repo.ImpfterminRepo;
import ch.dvbern.oss.vacme.rest.auth.Authorizer;
import ch.dvbern.oss.vacme.service.DossierService;
import ch.dvbern.oss.vacme.service.FragebogenService;
import ch.dvbern.oss.vacme.service.OrtDerImpfungService;
import ch.dvbern.oss.vacme.service.RegistrierungService;
import ch.dvbern.oss.vacme.service.TerminbuchungService;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.shared.util.OpenApiConst;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.tags.Tags;

import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.AS_REGISTRATION_OI;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.OI_DOKUMENTATION;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.OI_IMPFVERANTWORTUNG;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.OI_KONTROLLE;
import static ch.dvbern.oss.vacme.rest.auth.ClientApplications.VACME_WEB;

@ApplicationScoped
@Transactional
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Tags(@Tag(name = OpenApiConst.TAG_TERMINBUCHUNG))
@Path(VACME_WEB + "/terminbuchung")
public class TerminbuchungResource {

	@ConfigProperty(name = "vacme.terminreservation.enabled", defaultValue = "true")
	protected boolean terminReservationEnabled;

	private final OrtDerImpfungService ortDerImpfungService;
	private final TerminbuchungService terminbuchungService;
	private final RegistrierungService registrierungService;
	private final DossierService dossierService;
	private final ImpfterminRepo impfterminRepo;
	private final Authorizer authorizer;
	private final ImpfinformationenService impfinformationenService;
	private final FragebogenService fragebogenService;

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("createAdHocTermin1AndBucheTermin2/{registrierungsnummer}/{odiId}/{impfslot2Id}")
	@RolesAllowed({ OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, OI_KONTROLLE })
	public DashboardJax createAdHocTermin1AndBucheTermin2(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer,
		@NonNull @PathParam("odiId") UUID odiId,
		@NonNull @PathParam("impfslot2Id") UUID impfslot2Id
	) {
		Objects.requireNonNull(registrierungsnummer);
		Objects.requireNonNull(odiId);
		Objects.requireNonNull(impfslot2Id);

		final Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		authorizer.checkUpdateAuthorization(registrierung);

		final OrtDerImpfung ortDerImpfung = ortDerImpfungService.getById(OrtDerImpfung.toId(odiId));
		terminbuchungService.createAdHocTermin1AndBucheTermin2(registrierung, ortDerImpfung, Impfslot.toId(impfslot2Id));
		authorizer.checkBenutzerAssignedToOdi(ortDerImpfung);

		ImpfinformationDto impfinformationen = this.impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
		Fragebogen fragebogen = this.fragebogenService.findFragebogenByRegistrierungsnummer(registrierungsnummer);

		return new DashboardJax(impfinformationen, fragebogen);
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("reservieren/{registrierungsnummer}/{impfslotId}/{impffolge}")
	@RolesAllowed({ OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, OI_KONTROLLE })
	public Response reservieren(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer,
		@NonNull @NotNull @PathParam("impfslotId") UUID impfslotId,
		@NonNull @NotNull @PathParam("impffolge") Impffolge impffolge
	) {
		// Falls die Reservation ausgeschaltet ist, wollen wir direkt abbrechen
		if (!terminReservationEnabled) {
			return Response.ok().build();
		}

		Objects.requireNonNull(registrierungsnummer);
		Objects.requireNonNull(impfslotId);
		Objects.requireNonNull(impffolge);

		final Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		authorizer.checkUpdateAuthorization(registrierung);

		terminbuchungService.reservieren(registrierung, Impfslot.toId(impfslotId), impffolge);
		return Response.ok().build();
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("umbuchenGrundimmunisierung/{registrierungsnummer}/{impfslot1Id}/{impfslot2Id}")
	@RolesAllowed({ OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, OI_KONTROLLE })
	public DashboardJax umbuchenGrundimmunisierung(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer,
		@NonNull @PathParam("impfslot1Id") UUID impfslot1Id,
		@NonNull @PathParam("impfslot2Id") UUID impfslot2Id
	) {
		Objects.requireNonNull(registrierungsnummer);
		Objects.requireNonNull(impfslot1Id);
		Objects.requireNonNull(impfslot2Id);

		final Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		authorizer.checkUpdateAuthorization(registrierung);

		if (RegistrierungStatus.getAnyStatusOfGrundimmunisiert().contains(registrierung.getRegistrierungStatus())) {
			// Wir durften hier nicht hinkommen, sondern umbuchenBooster aufrufen
			throw AppValidationMessage.ILLEGAL_STATE.create("Umbuchung von Booster-Terminen muss andere Methode verwenden");
		}

		// UMBUCHEN
		terminbuchungService.umbuchenGrundimmunisierung(registrierung, Impfslot.toId(impfslot1Id), Impfslot.toId(impfslot2Id));

		return reloadToReturn(registrierung);
	}

	private DashboardJax reloadToReturn(Registrierung registrierung) {
		// nach dem Umbuchen ist im Termin N der neue Termin, nun pruefen wir noch ob der User berechtigt ist
		Objects.requireNonNull(registrierung.getGewuenschterOdi(), "Gewuenschter ODI wird beim buchen gesetzt");
		authorizer.checkBenutzerAssignedToOdi(registrierung.getGewuenschterOdi());

		ImpfinformationDto impfinformationen = this.impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
		Fragebogen fragebogen = this.fragebogenService.findFragebogenByRegistrierungsnummer(registrierung.getRegistrierungsnummer());
		return new DashboardJax(impfinformationen, fragebogen);
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("umbuchenBooster/{registrierungsnummer}/{impfslotNId}")
	@RolesAllowed({ OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, OI_KONTROLLE })
	public DashboardJax umbuchenBooster(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer,
		@NonNull @PathParam("impfslotNId") UUID impfslotNId
	) {
		Objects.requireNonNull(registrierungsnummer);
		Objects.requireNonNull(impfslotNId);

		final Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		authorizer.checkUpdateAuthorization(registrierung);

		if (!RegistrierungStatus.getAnyStatusOfGrundimmunisiert().contains(registrierung.getRegistrierungStatus())) {
			// wir duerften hier nicht hinkommen, sondern umbuchenGrundimmunisierung() aufrufen
			throw AppValidationMessage.ILLEGAL_STATE.create("Umbuchung von Booster-Terminen muss andere Methode verwenden");
		}

		// UMBUCHEN
		terminbuchungService.umbuchenBooster(registrierung, Impfslot.toId(impfslotNId));

		return reloadToReturn(registrierung);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/termine/nextfrei/{ortDerImpfungId}/{impffolge}")
	@RolesAllowed({ OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, OI_KONTROLLE })
	@Schema(format = OpenApiConst.Format.DATE_TIME)
	@Nullable
	public NextFreierTerminJax getNextFreierImpftermin(
		@NonNull @NotNull @PathParam("ortDerImpfungId") UUID ortDerImpfungId,
		@NonNull @NotNull @PathParam("impffolge") Impffolge impffolge,
		@Nullable @Parameter(description = "Datum zu dem der erste passende Gegenstuecktermin gefunden werden soll")
			NextFreierTerminJax otherTerminDate
	) {
		LocalDateTime nextDate = otherTerminDate != null ? otherTerminDate.getNextDate() : null;
		// it would be possible to use the NextTerminCacheService here
		LocalDateTime nextFreierImpftermin = ortDerImpfungService
			.getNextFreierImpftermin(OrtDerImpfung.toId(ortDerImpfungId), impffolge, nextDate, false);
		if (nextFreierImpftermin == null) {
			return null;
		}
		return new NextFreierTerminJax(nextFreierImpftermin);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/termine/frei/{ortDerImpfungId}/{impffolge}")
	@RolesAllowed({ OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, OI_KONTROLLE })
	public List<ImpfslotJax> getFreieImpftermine(
		@NonNull @NotNull @PathParam("ortDerImpfungId") UUID ortDerImpfungId,
		@NonNull @NotNull @PathParam("impffolge") Impffolge impffolge,
		@NonNull @NotNull @Parameter(description = "Datum zu dem der erste passende Gegenstuecktermin gefunden werden soll")
			NextFreierTerminJax date
	) {
		return ortDerImpfungService
			.getFreieImpftermine(OrtDerImpfung.toId(ortDerImpfungId), impffolge, date.getNextDate().toLocalDate())
			.stream()
			.map(ImpfslotJax::new)
			.collect(Collectors.toList());
	}

	@DELETE
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@RolesAllowed({ OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, OI_KONTROLLE })
	@Path("/cancel/{registrierungsnummer}")
	public DashboardJax terminAbsagen(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer
	) {
		final Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);

		authorizer.checkUpdateAuthorization(registrierung);

		dossierService.odiAndTermineAbsagen(registrierung);

		ImpfinformationDto impfinformationen = this.impfinformationenService.getImpfinformationenNoCheck(registrierung.getRegistrierungsnummer());
		Fragebogen fragebogen = this.fragebogenService.findFragebogenByRegistrierungsnummer(registrierungsnummer);
		return new DashboardJax(impfinformationen, fragebogen);
	}

	@POST
	@TransactionConfiguration(timeout = 6000000)
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.WILDCARD)
	@RolesAllowed({ AS_REGISTRATION_OI })
	@Path("/absagen/")
	@Operation(description = "Absagen aller Termine eines bestimmten Datums eines OdI falls zB. aus logistischen Gruenden an einem Tag nicht geimpft werden "
		+ "kann")
	public Response termineAbsagenForOdiAndDatum(
		@Valid @NonNull TermineAbsagenJax absagenJax
	) {
		OrtDerImpfung ortDerImpfung = ortDerImpfungService.getById(OrtDerImpfung.toId(absagenJax.getOdiId()));

		authorizer.checkUpdateAuthorization(ortDerImpfung);

		Impffolge impffolge = absagenJax.getImpffolge();
		LocalDate datum = absagenJax.getDatum();
		final List<Impftermin> gebuchteTermine = impfterminRepo.findGebuchteTermine(ortDerImpfung, impffolge, datum, datum);
		LOG.info("VACME-INFO: Es werden {} Termine abgesagt am {} vom OdI {}",
			gebuchteTermine.size(), DateUtil.formatDate(datum, Locale.GERMAN), ortDerImpfung.getName());
		for (Impftermin termin : gebuchteTermine) {
			terminbuchungService.terminAbsagenForOdiAndDatum(termin);

		}
		// Geloescht werden nicht nur die gebuchten, sondern ALLE Termine dieses Tages
		terminbuchungService.deleteAllTermineOfOdiAndDatum(ortDerImpfung, impffolge, datum);

		return Response.ok().build();
	}
}
