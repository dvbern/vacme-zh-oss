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

import java.util.UUID;

import ch.dvbern.oss.vacme.entities.registration.RegistrierungFile;
import ch.dvbern.oss.vacme.shared.util.CleanFileName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileInfoJax {

	@NonNull
	private UUID id;

	@NonNull
	private String name;

	private long size;

	public FileInfoJax(@NotNull UUID id, CleanFileName fileName, Long size) {
		this.id = id;
		this.name = fileName.getFileName();
		this.size = size;
	}

	public static FileInfoJax from(RegistrierungFile registrierungFile) {
		return new FileInfoJax(
			registrierungFile.getId(),
			registrierungFile.getFileBlob().getFileName().toString(),
			registrierungFile.getFileBlob().getFileSize()
		);
	}

}
