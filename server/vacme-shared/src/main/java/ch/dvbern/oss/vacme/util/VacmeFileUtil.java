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

package ch.dvbern.oss.vacme.util;

import ch.dvbern.oss.vacme.entities.embeddables.FileBlob;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFile;
import ch.dvbern.oss.vacme.entities.registration.RegistrierungFileTyp;
import ch.dvbern.oss.vacme.entities.zertifikat.ZertifikatFile;
import ch.dvbern.oss.vacme.shared.util.CleanFileName;
import ch.dvbern.oss.vacme.shared.util.MimeType;
import org.checkerframework.checker.nullness.qual.NonNull;

public final class VacmeFileUtil {

	private VacmeFileUtil() {
	}

	@NonNull
	public static RegistrierungFile createRegistrierungFile(
		@NonNull RegistrierungFileTyp fileTyp,
		@NonNull Registrierung registrierung,
		@NonNull byte[] content
	) {
		RegistrierungFile registrierungFile = new RegistrierungFile();
		registrierungFile.setRegistrierung(registrierung);
		registrierungFile.setFileTyp(fileTyp);
		CleanFileName cleanFileName = new CleanFileName(fileTyp.name().toLowerCase());
		FileBlob file = FileBlob.of(cleanFileName, MimeType.APPLICATION_PDF, content);
		registrierungFile.setFileBlob(file);
		return registrierungFile;
	}

	@NonNull
	public static ZertifikatFile createZertifikatFile(
		@NonNull Registrierung registrierung,
		@NonNull MimeType type,
		byte[] content
	) {
		ZertifikatFile registrierungFile = new ZertifikatFile();
		CleanFileName cleanFileName = new CleanFileName(registrierung.getRegistrierungsnummer());
		FileBlob file = FileBlob.of(cleanFileName, type, content);
		registrierungFile.setFileBlob(file);
		return registrierungFile;
	}
}
