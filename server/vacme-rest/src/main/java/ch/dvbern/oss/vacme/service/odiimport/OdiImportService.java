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

package ch.dvbern.oss.vacme.service.odiimport;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.validation.constraints.NotNull;

import ch.dvbern.oss.vacme.entities.base.ID;
import ch.dvbern.oss.vacme.entities.benutzer.Benutzer;
import ch.dvbern.oss.vacme.entities.embeddables.Adresse;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfungTyp;
import ch.dvbern.oss.vacme.entities.types.FachRolle;
import ch.dvbern.oss.vacme.jax.base.AdresseJax;
import ch.dvbern.oss.vacme.jax.registration.OdiUserJax;
import ch.dvbern.oss.vacme.jax.registration.OrtDerImpfungJax;
import ch.dvbern.oss.vacme.repo.OrtDerImpfungRepo;
import ch.dvbern.oss.vacme.service.BenutzerService;
import ch.dvbern.oss.vacme.service.GeocodeService;
import ch.dvbern.oss.vacme.service.KeyCloakService;
import ch.dvbern.oss.vacme.service.MailService;
import ch.dvbern.oss.vacme.service.OrtDerImpfungService;
import ch.dvbern.oss.vacme.service.SmsService;
import ch.dvbern.oss.vacme.shared.errors.AppValidationException;
import ch.dvbern.oss.vacme.util.OdiImportUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.keycloak.representations.idm.UserRepresentation;

import static ch.dvbern.oss.vacme.util.OdiImportUtil.areAllCellsNullOrEmpty;
import static ch.dvbern.oss.vacme.util.OdiImportUtil.getAppValidationMessage;
import static ch.dvbern.oss.vacme.util.OdiImportUtil.getBooleanValue;
import static ch.dvbern.oss.vacme.util.OdiImportUtil.getEnumValue;
import static ch.dvbern.oss.vacme.util.OdiImportUtil.getStringFromColumn;
import static ch.dvbern.oss.vacme.util.OdiImportUtil.getStringValue;

@ApplicationScoped
@Slf4j
public class OdiImportService {

	private static final int ODI_NAME = 0;
	private static final int ODI_ADRESSE_1 = 1;
	private static final int ODI_ADRESSE_2 = 2;
	private static final int ODI_PLZ = 3;
	private static final int ODI_ORT = 4;
	private static final int ODI_TYP_TEXT = 5;
	private static final int ODI_TYP = 6;
	private static final int ODI_IS_MOBIL = 7;
	private static final int ODI_IS_OEFFENTLICH = 8;
	private static final int ODI_IS_TERMINVERWALTUNG = 9;
	private static final int ODI_ZSR_NR = 10;
	private static final int ODI_GLN_NR = 11;
	private static final int ODI_KOMMENTAR = 12;

	private static final int FACH_NAME = 13;
	private static final int FACH_VORNAME = 14;
	private static final int FACH_EMAIL = 15;
	private static final int FACH_MOBILE = 16;
	private static final int FACH_GLN_NR = 17;

	private static final int ORG_NAME = 18;
	private static final int ORG_VORNAME = 19;
	private static final int ORG_EMAIL = 20;
	private static final int ORG_MOBILE = 21;
	private static final int ORG_GLN_NR = 22;

	private final KeyCloakService keyCloakService;
	private final OrtDerImpfungService ortDerImpfungService;
	private final OrtDerImpfungRepo ortDerImpfungRepo;
	private final BenutzerService benutzerService;
	private final SmsService smsService;
	private final MailService mailService;
	private final GeocodeService geocodeService;

	@Inject
	public OdiImportService(
		@NonNull KeyCloakService keyCloakService,
		@NonNull OrtDerImpfungService ortDerImpfungService,
		@NonNull OrtDerImpfungRepo ortDerImpfungRepo,
		@NonNull BenutzerService benutzerService,
		@NonNull SmsService smsService,
		@NonNull MailService mailService,
		@NonNull  GeocodeService geocodeService) {
		this.keyCloakService = keyCloakService;
		this.ortDerImpfungService = ortDerImpfungService;
		this.ortDerImpfungRepo = ortDerImpfungRepo;
		this.benutzerService = benutzerService;
		this.smsService = smsService;
		this.mailService = mailService;
		this.geocodeService = geocodeService;
	}


	private void importOrtDerImpfung(
		@NonNull @NotNull OrtDerImpfungJax odiJax,
		@NonNull @NotNull OdiUserJax fachverantwortung,
		@Nullable OdiUserJax organisationsverantwortung,
		int rowNumber
	) {
		LOG.info("VACME-IMPORTODI: Starte Import von ODI/Benutzern {}, Excel Row {}", odiJax.getName(), rowNumber);

		// Zuerst wird die Gruppe in KeyCloak erstellt
		importOrtDerImpfungInKeyCloak(odiJax, rowNumber);

		// Fachverantwortlicher
		Benutzer fachverantwortungBenutzer = importBenutzer(fachverantwortung, odiJax, rowNumber);
		odiJax.setFachverantwortungbab(fachverantwortungBenutzer.getId().toString());
		// Organisationsverantwortlicher
		Benutzer organisationsverantwortungBenutzer = null;
		if (organisationsverantwortung != null) {
			organisationsverantwortungBenutzer = importBenutzer(organisationsverantwortung, odiJax, rowNumber);
			odiJax.setOrganisationsverantwortung(organisationsverantwortungBenutzer.getId().toString());
		}

		// Den ODI importieren
		final OrtDerImpfung ortDerImpfung = importOrtDerImpfungInVacme(odiJax, rowNumber);

		// Verknuepfungen (Gruppen und Rollen)
		final List<String> rolesOfFachverantwortlicher =
			keyCloakService.getRolesOfUser(keyCloakService.getUser(fachverantwortungBenutzer.getId().toString()).toRepresentation());
		benutzerService.addOdiToBenutzer(fachverantwortungBenutzer, rolesOfFachverantwortlicher, ortDerImpfung);
		if (organisationsverantwortungBenutzer != null) {
			final List<String> rolesOfOrganisationsverantwortlicher =
				keyCloakService.getRolesOfUser(keyCloakService.getUser(organisationsverantwortungBenutzer.getId().toString()).toRepresentation());
			benutzerService.addOdiToBenutzer(organisationsverantwortungBenutzer, rolesOfOrganisationsverantwortlicher, ortDerImpfung);
		}

		LOG.info("VACME-IMPORTODI: ODI Import erfolgreich beendet fuer {}", odiJax.getIdentifier());
	}

	private void importOrtDerImpfungInKeyCloak(@NonNull OrtDerImpfungJax odiJax, int rowNumber) {
		String groupName = odiJax.getIdentifier();
		// Gibt es es die Gruppe in KeyCloak?
		if (keyCloakService.checkGroupExists(groupName)) {
			// Wir wollen nicht abbrechen, nur weil es die Gruppe schon gibt. Es soll mehrmals durchfuehrbar sein
			LOG.info("VACME-IMPORTODI: Gruppe {} existiert bereits in KeyCloak", groupName);
		} else {
			// Der ODI i.e. die Gruppe existiert nicht in KeyCloak
			boolean success = keyCloakService.createGroup(groupName);
			if (!success) {
				throw getAppValidationMessage(rowNumber, "ODI / Gruppe konnte in KeyCloak nicht erstellt werden: " + groupName);
			}
			LOG.info("VACME-IMPORTODI: Gruppe {} wurde in KeyCloak erstellt", groupName);
		}
	}

	private OrtDerImpfung importOrtDerImpfungInVacme(@NonNull OrtDerImpfungJax odiJax, int rowNumber) {
		// Gibt es den ODI im Vacme?
		final String groupName = odiJax.getIdentifier();
		final Optional<OrtDerImpfung> odiOptional = ortDerImpfungRepo.getByOdiIdentifier(groupName);
		if (odiOptional.isEmpty()) {
			final OrtDerImpfung odiCreated = ortDerImpfungService.createOrtDerImpfung(odiJax.getUpdateEntityConsumer(true, geocodeService));
			LOG.info("VACME-IMPORTODI: ODI {} wurde in Vacme erstellt", groupName);
			return odiCreated;
		} else {
			LOG.info("VACME-IMPORTODI: ODI {} existiert bereits in VacMe. Vergleiche Daten...", groupName);
			// Daten vergleichen, um sicherzugehen, das wir den richtigen ODI gefunden haben
			final OrtDerImpfung odiFound = odiOptional.get();
			if (!odiFound.getName().equals(odiJax.getName())) {
				var msg = String.format("Name '%s' stimmt nicht mit bestehendem ODI '%s' ueberein", odiJax.getName()
					,odiFound.getName());
				throw getAppValidationMessage(rowNumber, msg);
			}
			// Die Benutzer vergleichen:
			// Fachverantwortlicher muss gleich sein
			if (!odiFound.getFachverantwortungbabKeyCloakId().equals(odiJax.getFachverantwortungbab())) {
				throw getAppValidationMessage(rowNumber, "Fachverantwortlicher stimmt nicht mit bestehendem ODI ueberein");
			}
			// Organisationsverantwortlicher muss gleich sein, falls einer da war, ansonsten kann er gesetzt werden
			if (odiFound.getOrganisationsverantwortungKeyCloakId() != null) {
				if (!odiFound.getOrganisationsverantwortungKeyCloakId().equals(odiJax.getOrganisationsverantwortung())) {
					throw getAppValidationMessage(rowNumber, "Organisationsverantwortlicher stimmt nicht mit bestehendem ODI ueberein");
				}
			} else {
				// Wenn es noch keinen gab, kann er gesetzt werden
				if (odiJax.getOrganisationsverantwortung() != null) {
					keyCloakService.joinGroup(odiJax.getOrganisationsverantwortung(), groupName);
				}
			}
			// GLN-Nummer
			if (odiFound.getGlnNummer() != null) {
				if (!odiFound.getGlnNummer().equals(odiJax.getGlnNummer())) {
					throw getAppValidationMessage(rowNumber, "GLN-Nummer stimmt nicht mit bestehendem ODI ueberein");
				}
			}
			// PLZ
			if (odiFound.getAdresse().getPlz() != null) {
				if (!odiFound.getAdresse().getPlz().equals(odiJax.getAdresse().getPlz())) {
					throw getAppValidationMessage(rowNumber, "PLZ stimmt nicht mit bestehendem ODI ueberein");
				}
			}
			// Ort
			if (odiFound.getAdresse().getOrt() != null) {
				if (!odiFound.getAdresse().getOrt().equals(odiJax.getAdresse().getOrt())) {
					throw getAppValidationMessage(rowNumber, "Ort stimmt nicht mit bestehendem ODI ueberein");
				}
			}
			LOG.info("VACME-IMPORTODI: ODI {} existiert bereits in VacMe. Datenvergleich erfolgreich", groupName);
			return odiFound;
		}
	}

	private Benutzer importBenutzer(@NonNull  OdiUserJax userJax, @NonNull OrtDerImpfungJax odi, int rowNumber
	) {
		LOG.info("VACME-IMPORTODI: Starte Import von Benutzer mit E-Mail/Username {} und Rolle {}", userJax.getEmail(), userJax.getFachRolle());
		String groupName = odi.getIdentifier();

		// Wir suchen zuerst nach Username UND E-Mail. Wenn beides uebereinstimmt, gehen wir davon aus, dass wir den
		// richtigen gefunden haben
		final Optional<UserRepresentation> userOptional = keyCloakService.findUserByUsernameAndEmail(userJax.getUsername(), userJax.getEmail());
		if (userOptional.isPresent()) {
			final UserRepresentation foundUser = userOptional.get();
			LOG.info("VACME-IMPORTODI: Benutzer mit E-Mail/Username {} existiert bereits in KeyCloak. Vergleiche Daten...", userJax.getEmail());
			// Sicherstellen, dass wir den richtigen gefunden haben:
			// Nachname
			if (!userJax.getLastName().equals(foundUser.getLastName())) {
				String description = String.format("Nachname %s stimmt nicht mit Angaben des bestehenden Benutzers %s ueberein", userJax.getLastName(), foundUser.getLastName());
				throw getAppValidationMessage(rowNumber, description);
			}
			// Vorname
			if (!userJax.getFirstName().equals(foundUser.getFirstName())) {
				String description = String.format("Vorname %s stimmt nicht mit Angaben des bestehenden Benutzers %s "
					+ "ueberein", userJax.getFirstName(), foundUser.getFirstName());
				throw getAppValidationMessage(rowNumber, description);
			}
			// Mobile-Nummer
			final Optional<String> mobileNumberOfUser = keyCloakService.getMobileNumberOfUser(foundUser);
			if (mobileNumberOfUser.isPresent()) {
				if (!userJax.getPhone().equals(mobileNumberOfUser.get())) {
					String description = String.format("Mobilenummer %s stimmt nicht mit Angaben des bestehenden "
						+ "Benutzers '%s' ueberein", userJax.getPhone(), mobileNumberOfUser.get());
					throw getAppValidationMessage(rowNumber, description);
				}
			}
			// Die Rolle muss zwingend dieselbe sein
			if (!keyCloakService.isUserInRole(foundUser, userJax.getFachRolle())) {
				throw getAppValidationMessage(rowNumber, "Der bestehende Benutzer hat eine andere Rolle");
			}
			// GLN-Nummer
			final Optional<String> glnNummerOfUser = keyCloakService.getGlnNummerOfUser(foundUser.getUsername());
			if (glnNummerOfUser.isPresent()) {
				if (!glnNummerOfUser.get().equals(userJax.getGlnNummer())) {
					var description = String.format("GLN-Nummer '%s' stimmt nicht mit Angaben des bestehenden Benutzers '%s'"
						+ "ueberein",  userJax.getGlnNummer(), glnNummerOfUser.get());
					throw getAppValidationMessage(rowNumber, description);
				}
			}
			LOG.info("VACME-IMPORTODI: Benutzer mit E-Mail/Username {} existiert bereits in KeyCloak. Datenvergleich erfolgreich", userJax.getEmail());

			// Falls er diesen ODI noch nicht als Gruppe hat, muss dies noch hinzugefuegt werden
			if (!keyCloakService.isUserInGroup(foundUser, groupName)) {
				// Gruppe joinen
				keyCloakService.joinGroup(foundUser.getId(), groupName);
				// Benachrichtigung: Momentan immer Deutsch da wir keine andere Infos haben.
				smsService.sendBenachrichtigungNeueBerechtigungSMS(odi.getName(), Locale.GERMAN, userJax.getPhone());
				LOG.info("VACME-IMPORTODI: Benutzer mit E-Mail/Username {} wurde der Gruppe {} hinzugefuegt", userJax.getEmail(), groupName);
			} else {
				LOG.info("VACME-IMPORTODI: Benutzer mit E-Mail/Username {} ist bereits Mitglied der Gruppe {}", userJax.getEmail(), groupName);
			}

			// Falls es den Benutzer in VacMe auch schon gibt, koennen wir hier abbrechen
			ID<Benutzer> id = new ID<>(UUID.fromString(foundUser.getId()), Benutzer.class);
			final Optional<Benutzer> benutzerVacmeFoundOptional = benutzerService.getById(id);
			if (benutzerVacmeFoundOptional.isPresent()) {
				return benutzerVacmeFoundOptional.get();
			} else{
				userJax.setId(foundUser.getId()); // id aus keycloak setzen damit wir den user bei uns anlegen koennen mit einer id
			}

		} else {
			LOG.info("VACME-IMPORTODI: Benutzer mit E-Mail/Username {} existiert noch nicht in KeyCloak", userJax.getEmail());

			// Falls nicht, muessen wir pruefen, ob entweder der Username ODER die E-Mail schon besetzt sind
			final Optional<UserRepresentation> userWithSameUsernameOptional = keyCloakService.findUserByUsername(userJax.getUsername());
			if (userWithSameUsernameOptional.isPresent()) {
				throw getAppValidationMessage(rowNumber, "Es gibt bereits einen Benutzer mit diesem Benutzernamen");
			}
			final Optional<UserRepresentation> userWithSameEmailOptional = keyCloakService.findUserByEmail(userJax.getEmail());
			if (userWithSameEmailOptional.isPresent()) {
				throw getAppValidationMessage(rowNumber, "Es gibt bereits einen Benutzer mit dieser E-Mail Adresse");
			}

			// Wenn wir hier hin kommen, kann der Benutzer erstellt werden
			keyCloakService.createUser(userJax, groupName);
			// Neue Benutzer erhalten ein Einladungsmail mit weiteren Infos
			mailService.sendEinladungFachapplikation(userJax, odi);

			LOG.info("VACME-IMPORTODI: Benutzer mit E-Mail/Username {} wurde mit Gruppe {} in KeyCloak erstellt", userJax.getEmail(), groupName);
		}

		// Wenn wir hier hin kommen, existiert der Benutzer in VacMe noch nicht
		return benutzerService.create(userJax);
	}




	public boolean isEmptyRow(Row row) {
		return areAllCellsNullOrEmpty(row,
			ODI_NAME, ODI_ADRESSE_1, ODI_ADRESSE_2, ODI_PLZ, ODI_ORT, ODI_TYP_TEXT, ODI_TYP, ODI_IS_MOBIL, ODI_IS_OEFFENTLICH,
			ODI_IS_TERMINVERWALTUNG, ODI_ZSR_NR, ODI_GLN_NR, ODI_KOMMENTAR,
			FACH_NAME, FACH_VORNAME, FACH_EMAIL, FACH_MOBILE, FACH_GLN_NR,
			ORG_NAME, ORG_VORNAME, ORG_EMAIL, ORG_MOBILE, ORG_GLN_NR);
	}

	@Transactional(TxType.REQUIRES_NEW)
	public void readOdiAndBenutzer(Row row) {
		int rowNumber = row.getRowNum() + 1;
		try {
			final OrtDerImpfungJax odi = readOrtDerImpfung(row);
			final OdiUserJax fachverantwortlicher = readFachverantwortlicher(row);
			final OdiUserJax organisationsverantwortlicher = readOrganisationsverantwortlicher(row);

			importOrtDerImpfung(odi, fachverantwortlicher, organisationsverantwortlicher, rowNumber);
		} catch (Throwable t) {
			if (t instanceof AppValidationException) {
				throw (AppValidationException) t;
			}
			throw OdiImportUtil.getAppValidationMessage(rowNumber, t.getMessage());
		}
	}

	@NonNull
	private OrtDerImpfungJax readOrtDerImpfung(@NonNull Row row) {
		OrtDerImpfungJax odi = new OrtDerImpfungJax();
		odi.setName(getStringValue(row, ODI_NAME, true));
		odi.setIdentifier(OdiImportUtil.generateOdiIdentifier(odi.getName()));
		odi.setAdresse(AdresseJax.from(readAdresse(row)));
		odi.setTyp(getEnumValue(row, ODI_TYP, OrtDerImpfungTyp.class));
		odi.setMobilerOrtDerImpfung(getBooleanValue(row, ODI_IS_MOBIL, false));
		odi.setOeffentlich(getBooleanValue(row, ODI_IS_OEFFENTLICH, true));
		odi.setTerminverwaltung(getBooleanValue(row, ODI_IS_TERMINVERWALTUNG, true));
		odi.setZsrNummer(getStringFromColumn(row, ODI_ZSR_NR));
		odi.setGlnNummer(getStringFromColumn(row, ODI_GLN_NR));
		odi.setKommentar(getStringFromColumn(row, ODI_KOMMENTAR));
		return odi;
	}

	@NonNull
	private Adresse readAdresse(@NonNull Row row) {
		Adresse adresse = new Adresse();
		adresse.setAdresse1(getStringValue(row, ODI_ADRESSE_1, true));
		adresse.setAdresse2(getStringValue(row, ODI_ADRESSE_2, false));
		adresse.setPlz(getStringFromColumn(row, ODI_PLZ));
		adresse.setOrt(getStringValue(row, ODI_ORT, true));
		return adresse;
	}

	@NonNull
	private OdiUserJax readFachverantwortlicher(@NonNull Row row) {
		OdiUserJax user = new OdiUserJax();
		user.setLastName(getStringValue(row, FACH_NAME, true));
		user.setFirstName(getStringValue(row, FACH_VORNAME, true));
		user.setEmail(getStringValue(row, FACH_EMAIL, true));
		// Wir setzen generell die E-Mail als Username
		user.setUsername(user.getEmail());
		user.setPhone(getStringValue(row, FACH_MOBILE, true));
		user.setGlnNummer(getStringFromColumn(row, FACH_GLN_NR));
		user.setFachRolle(FachRolle.FACHVERANTWORTUNG_BAB);
		return user;
	}

	@Nullable
	private OdiUserJax readOrganisationsverantwortlicher(@NonNull Row row) {
		OdiUserJax user = new OdiUserJax();
		if (isOrganisationsverantwortlicherEmpty(row)) {
			return null;
		}
		user.setLastName(getStringValue(row, ORG_NAME, true));
		user.setFirstName(getStringValue(row, ORG_VORNAME, true));
		user.setEmail(getStringValue(row, ORG_EMAIL, true));
		// Wir setzen generell die E-Mail als Username
		user.setUsername(user.getEmail());
		user.setPhone(getStringValue(row, ORG_MOBILE, true));
		user.setGlnNummer(getStringFromColumn(row, ORG_GLN_NR));
		user.setFachRolle(FachRolle.ORGANISATIONSVERANTWORTUNG);
		return user;
	}

	private boolean isOrganisationsverantwortlicherEmpty(@NonNull Row row) {
		return areAllCellsNullOrEmpty(row, ORG_NAME, ORG_VORNAME, ORG_EMAIL, ORG_MOBILE, ORG_GLN_NR);
	}
}
