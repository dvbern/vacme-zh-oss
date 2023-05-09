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

package ch.dvbern.oss.vacme.jax.impfslot;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import ch.dvbern.oss.vacme.entities.terminbuchung.Impfslot;
import ch.dvbern.oss.vacme.shared.util.OpenApiConst;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import static ch.dvbern.oss.vacme.entities.types.daterange.DateUtil.DEFAULT_DATE_FORMAT;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Setter
public class ImpfslotDisplayDayJax {

	@NonNull
	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	@Schema(required = false, readOnly = true, description = "Tag der Impfslots", format = OpenApiConst.Format.DATE)
	private LocalDate day;

	private String dayDisplay;

	@NonNull
	@Schema(required = false, readOnly = true, description = "Liste aller Impfslots des Tages")
	private List<ImpfslotDisplayJax> impfslotDisplayJaxList;

	public static ImpfslotDisplayDayJax of(@NonNull Entry<LocalDate, List<Impfslot>> localDateListEntry) {
		LocalDate dayDate = localDateListEntry.getKey();
		String displayDate = DEFAULT_DATE_FORMAT.apply(Locale.getDefault()).format(dayDate);
		return new ImpfslotDisplayDayJax(dayDate, displayDate,
			localDateListEntry.getValue().stream()
				.map(ImpfslotDisplayJax::of)
				.sorted(Comparator.comparing(impfslotDisplayJax -> impfslotDisplayJax.getZeitfenster().getVon()))
				.collect(Collectors.toList()));
	}
}
