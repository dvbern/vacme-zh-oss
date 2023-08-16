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
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
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

import ch.dvbern.oss.vacme.entities.base.AbstractUUIDEntity;
import ch.dvbern.oss.vacme.entities.benutzer.Benutzer;
import ch.dvbern.oss.vacme.entities.impfen.Impffolge;
import ch.dvbern.oss.vacme.entities.impfen.Impfstoff;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.impfen.ImpfungkontrolleTermin;
import ch.dvbern.oss.vacme.entities.registration.Fragebogen;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import ch.dvbern.oss.vacme.entities.types.BenutzerRolle;
import ch.dvbern.oss.vacme.entities.types.ZulassungsStatus;
import ch.dvbern.oss.vacme.jax.registration.BenutzerDisplayNameJax;
import ch.dvbern.oss.vacme.jax.registration.ImpfdokumentationJax;
import ch.dvbern.oss.vacme.jax.registration.ImpfstoffJax;
import ch.dvbern.oss.vacme.rest.auth.Authorizer;
import ch.dvbern.oss.vacme.service.BenutzerService;
import ch.dvbern.oss.vacme.service.FragebogenService;
import ch.dvbern.oss.vacme.service.ImpfdokumentationService;
import ch.dvbern.oss.vacme.service.ImpfstoffService;
import ch.dvbern.oss.vacme.service.OrtDerImpfungService;
import ch.dvbern.oss.vacme.service.RegistrierungService;
import ch.dvbern.oss.vacme.service.booster.BoosterService;
import ch.dvbern.oss.vacme.service.impfinformationen.ImpfinformationenService;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import ch.dvbern.oss.vacme.shared.util.OpenApiConst;
import ch.dvbern.oss.vacme.util.ImpfinformationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.tags.Tags;

import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.AS_BENUTZER_VERWALTER;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.AS_REGISTRATION_OI;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.KT_IMPFDOKUMENTATION;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.KT_MEDIZINISCHE_NACHDOKUMENTATION;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.KT_NACHDOKUMENTATION;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.OI_DOKUMENTATION;
import static ch.dvbern.oss.vacme.entities.types.BenutzerRolleName.OI_IMPFVERANTWORTUNG;
import static ch.dvbern.oss.vacme.rest.auth.ClientApplications.VACME_WEB;

@ApplicationScoped
@Transactional
@Slf4j
@Tags(@Tag(name = OpenApiConst.TAG_IMPFDOKU))
@Path(VACME_WEB + "/impfdokumentation")
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ImpfdokumentationResource {

	private final BenutzerService benutzerService;
	private final OrtDerImpfungService ortDerImpfungService;
	private final ImpfstoffService impfstoffService;
	private final ImpfdokumentationService impfdokumentationService;
	private final RegistrierungService registrierungService;
	private final Authorizer authorizer;
	private final ImpfinformationenService impfinformationenService;
	private final BoosterService boosterService;
	private final FragebogenService fragebogenService;

	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/benutzer/verantwortliche/{odiId}")
	@RolesAllowed({ OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, KT_IMPFDOKUMENTATION })
	public List<BenutzerDisplayNameJax> getVerantwortlicheList(
		@NonNull @PathParam("odiId") UUID odiId
	) {
		final OrtDerImpfung ortDerImpfung = ortDerImpfungService.getById(OrtDerImpfung.toId(odiId));

		authorizer.checkReadAuthorization(ortDerImpfung);

		final List<Benutzer> benutzerList = benutzerService.getBenutzerByRolleAndOrtDerImpfung(
			ortDerImpfung,
			BenutzerRolle.OI_IMPFVERANTWORTUNG);
		return benutzerList.stream().map(BenutzerDisplayNameJax::from).collect(Collectors.toList());
	}

	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/benutzer/durchfuehrende/{odiId}")
	@RolesAllowed({ OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, KT_IMPFDOKUMENTATION })
	public List<BenutzerDisplayNameJax> getDurchfuehrendeList(
		@NonNull @PathParam("odiId") UUID odiId
	) {
		final OrtDerImpfung ortDerImpfung = ortDerImpfungService.getById(OrtDerImpfung.toId(odiId));

		authorizer.checkReadAuthorization(ortDerImpfung);

		final List<Benutzer> benutzerList = benutzerService.getBenutzerByRolleAndOrtDerImpfung(
			ortDerImpfung,
			BenutzerRolle.OI_DOKUMENTATION);
		return benutzerList.stream().map(BenutzerDisplayNameJax::from).collect(Collectors.toList());
	}

	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/impfstoffeZugelassen")
	@RolesAllowed({ OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, AS_BENUTZER_VERWALTER,
		KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION, KT_IMPFDOKUMENTATION })
	public List<ImpfstoffJax> getZugelasseneImpfstoffeList() {
		return impfstoffService.getImpfstoffeOfStati(Set.of(ZulassungsStatus.EMPFOHLEN, ZulassungsStatus.ZUGELASSEN));
	}

	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/impfstoffeZugelassenAndExternZugelassen")
	@RolesAllowed({ OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, AS_BENUTZER_VERWALTER, AS_REGISTRATION_OI,
		KT_NACHDOKUMENTATION, KT_MEDIZINISCHE_NACHDOKUMENTATION, KT_IMPFDOKUMENTATION })
	public List<ImpfstoffJax> getZugelasseneAndExternZugelasseneImpfstoffeList() {
		return impfstoffService.getImpfstoffeOfStati(Set.of(ZulassungsStatus.EMPFOHLEN, ZulassungsStatus.ZUGELASSEN, ZulassungsStatus.EXTERN_ZUGELASSEN));
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("saveImpfung/{odiId}/{impffolge}")
	@RolesAllowed({ OI_DOKUMENTATION, KT_IMPFDOKUMENTATION })
	public Response saveImpfung(
		@NonNull @PathParam("odiId") UUID odiId,
		@NonNull @PathParam("impffolge") String impffolgeParam,
		@NonNull ImpfdokumentationJax impfdokumentationJax
	) {
		final Registrierung registrierung = registrierungService.findRegistrierung(impfdokumentationJax.getRegistrierungsnummer());

		authorizer.checkUpdateAuthorization(registrierung);

		Impffolge impffolge = Impffolge.valueOf(impffolgeParam);
		final Impfstoff impfstoff = impfstoffService.findById(Impfstoff.toId(impfdokumentationJax.getImpfstoff().getId()));

		final Benutzer verantwortlicher = benutzerService
			.getById(Benutzer.toId(impfdokumentationJax.getVerantwortlicherBenutzerId()))
			.orElseThrow(() -> AppFailureException.entityNotFound(Benutzer.class, impfdokumentationJax.getVerantwortlicherBenutzerId()));
		boolean verantwBelongsToOdi =
			verantwortlicher.getOrtDerImpfung().stream().map(AbstractUUIDEntity::getId).anyMatch(odiId::equals);
		if (!verantwBelongsToOdi) {
			LOG.warn("Gewaehlter verantwortlicher {} ist nicht im ODI {}", verantwortlicher.getBenutzername(), odiId.toString());
			throw AppValidationMessage.USER_NOT_IN_ODI.create(odiId.toString());
		}
		final Benutzer durchfuehrender = benutzerService
			.getById(Benutzer.toId(impfdokumentationJax.getDurchfuehrenderBenutzerId()))
			.orElseThrow(() -> AppFailureException.entityNotFound(Benutzer.class, impfdokumentationJax.getDurchfuehrenderBenutzerId()));
		boolean durchfBelongsToOdi =
			durchfuehrender.getOrtDerImpfung().stream().map(AbstractUUIDEntity::getId).anyMatch(odiId::equals);
		if (!durchfBelongsToOdi) {
			LOG.warn("Gewaehlter durchfuehrender {} ist nicht im ODI {}", durchfuehrender.getBenutzername(), odiId.toString());
			throw AppValidationMessage.USER_NOT_IN_ODI.create(odiId.toString());
		}

		final OrtDerImpfung ortDerImpfung = ortDerImpfungService.getById(OrtDerImpfung.toId(odiId));

		final Impfung impfung = impfdokumentationJax.toEntity(verantwortlicher, durchfuehrender, impfstoff);
		final boolean isNachtrag = impfdokumentationJax.isNachtraeglicheErfassung();
		final LocalDate nachtraeglichesDatum = impfdokumentationJax.getDatumFallsNachtraeglich();

		// Wenn es eine nachtraegliche Erfassung ist, muss der Termin zum damaligen Zeitpunkt erstellt werden!
		LocalDateTime timestampOfImpfung = LocalDateTime.now();
		if (isNachtrag) {
			Objects.requireNonNull(nachtraeglichesDatum, "Datum erste Impfung muss bei einem Nachtrag gesetzt sein");
			// Wir haben fuer den ersten Termin keine Zeit. Wir nehmen jeweils die aktuelle Zeit zum uebergebenen Datum
			timestampOfImpfung = nachtraeglichesDatum.atTime(LocalTime.now());
		}

		impfdokumentationService.createImpfung(
			registrierung,
			ortDerImpfung,
			impffolge,
			impfung,
			isNachtrag,
			timestampOfImpfung);

		impfdokumentationService.cleanupTemporarySelbstzahlendeFlagOnImpfkontrolleTermin(registrierung.getRegistrierungsnummer());

		boosterService.recalculateImpfschutzAndStatusmovesForSingleReg(registrierung);

		fragebogenService.setAndUpdateImmunsupprimiert(registrierung.getRegistrierungsnummer(), impfdokumentationJax.getImmunsupprimiert());

		return Response.ok().build();
	}

	@PUT
	@Operation(summary = "Impfung verweigert. Registrierung wieder zurueck zum Status vor der Kontrolle")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/impfungverweigert/{registrierungsnummer}")
	@RolesAllowed({ OI_DOKUMENTATION, OI_IMPFVERANTWORTUNG, KT_IMPFDOKUMENTATION })
	public Response impfungVerweigert(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer) {
		final Registrierung registrierung = registrierungService.findRegistrierung(registrierungsnummer);
		authorizer.checkUpdateAuthorization(registrierung);
		registrierungService.impfungVerweigert(registrierung);
		return Response.ok().build();
	}

	@GET
	@Operation(summary = "Grundimmunisierung kann nur ausgewaehlt werden, wenn vorherige Impfung auch zur Grundimmunisierung gehoert")
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/canBeGrundimmunisierung/{registrierungsnummer}")
	@RolesAllowed({ OI_DOKUMENTATION, KT_IMPFDOKUMENTATION })
	public boolean canBeGrundimmunisierung(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer) {
		ImpfinformationDto impfinformationenJax =
			impfinformationenService.getImpfinformationenNoCheck(registrierungsnummer);
		final Registrierung registrierung = impfinformationenJax.getRegistrierung();
		authorizer.checkReadAuthorization(registrierung);

		Impfung latestImpfungOrNull = ImpfinformationenService.getNewestVacmeImpfung(impfinformationenJax);
		if (latestImpfungOrNull == null) {
			return true; // das hier ist die erste Impfung, die darf sowieso
		}

		return latestImpfungOrNull.isGrundimmunisierung(); // nur wenn vorherige zur Grundimmunisierung gehoert, kann es die neue auch
	}

	@GET
	@Operation(summary = "Selbstzahlende-Flag wird in Kontrolle gesetzt und kann in Impfdokumentation nochmal geaendert werden")
	@Consumes(MediaType.WILDCARD)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/currentImpfungWillBeSelbstzahlend/{registrierungsnummer}")
	@RolesAllowed({ OI_DOKUMENTATION, KT_IMPFDOKUMENTATION })
	@Nullable
	public Boolean currentImpfungWillBeSelbstzahlend(
		@NonNull @NotNull @PathParam("registrierungsnummer") String registrierungsnummer) {
		ImpfinformationDto impfinformationenJax =
			impfinformationenService.getImpfinformationenNoCheck(registrierungsnummer);
		final Registrierung registrierung = impfinformationenJax.getRegistrierung();
		authorizer.checkReadAuthorization(registrierung);

		Fragebogen fragebogen = fragebogenService.findFragebogenByRegistrierungsnummerNoCheck(registrierungsnummer);
		ImpfungkontrolleTermin impfkontrolleTermin = ImpfinformationenService.getCurrentKontrolleTerminOrNull(impfinformationenJax, fragebogen);
		if (impfkontrolleTermin == null) {
			return null;
		}

		return impfkontrolleTermin.getSelbstzahlende();
	}

}
