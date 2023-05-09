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

import javax.validation.constraints.NotNull;

import ch.dvbern.oss.vacme.entities.util.DBConst;
import ch.dvbern.oss.vacme.shared.errors.AppValidationException;
import ch.dvbern.oss.vacme.shared.errors.AppValidationMessage;
import io.quarkus.runtime.util.ExceptionUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@Slf4j
public final class OdiImportUtil {

	private OdiImportUtil() {
	}

	public static String generateOdiIdentifier(@NonNull @NotNull String odiName) {
		String identifier = odiName;
		identifier = identifier.replaceAll("/", "_");
		identifier = identifier.replaceAll("&", "_");
		identifier = identifier.replaceAll("ä", "ae");
		identifier = identifier.replaceAll("ö", "oe");
		identifier = identifier.replaceAll("ü", "ue");
		identifier = identifier.replaceAll("è", "e");
		identifier = identifier.replaceAll("é", "e");
		identifier = identifier.replaceAll("à", "a");
		identifier = identifier.replaceAll(" ", "_");

		if (identifier.length() > DBConst.DB_DEFAULT_MAX_LENGTH) {
			identifier = identifier.substring(0, DBConst.DB_DEFAULT_MAX_LENGTH);
		}
		return identifier;
	}

	@NonNull
	public static <T extends Enum<T>> T getEnumValue(Row row, int col, Class<T> enumClass) {
		try {
			var value = row.getCell(col).getStringCellValue().trim();
			var enumVal = Enum.valueOf(enumClass, value);
			return enumVal;
		} catch (Exception e) {
			throw getAppValidationMessage(row, col, e);
		}
	}

	@NonNull
	public static String getStringValue(Row row, int col, boolean mandatory) {
		try {
			if (!mandatory && row.getCell(col) == null) {
				return "";
			}
			if (isCellNullOrEmpty(row, col)) {
				throw getAppValidationMessage(row, col, null);
			}
			var value = row.getCell(col).getStringCellValue();
			return value;
		} catch (Exception e) {
			if (ExceptionUtil.getRootCause(e).getClass().equals(AppValidationException.class)) {
				throw e;
			}
			throw getAppValidationMessage(row, col, e);
		}
	}

	public static boolean getBooleanValue(Row row, int col, boolean mandatory) {
		try {
			if (!mandatory && row.getCell(col) == null) {
				return false;
			}
			var value = row.getCell(col).getBooleanCellValue();
			return value;
		} catch (Exception e) {
			throw getAppValidationMessage(row, col, e);
		}
	}

	@NonNull
	public static String getStringFromColumn(Row row, int col) {
		try {
			var cell = row.getCell(col);
			if (cell != null) {
				if (cell.getCellType() == CellType.STRING) {
					return cell.getStringCellValue();
				}
				if (cell.getCellType() == CellType.NUMERIC) {
					return NumberToTextConverter.toText(cell.getNumericCellValue());
				}
			}
			return "";
		} catch (Exception e) {
			throw getAppValidationMessage(row, col, e);
		}
	}

	public static boolean areAllCellsNullOrEmpty(@NonNull Row row, int... cells) {
		for (int cell : cells) {
			if (!isCellNullOrEmpty(row, cell)) {
				return false;
			}
		}
		return true;
	}

	public static boolean isCellNullOrEmpty(@NonNull Row row, int cell) {
		if (row.getCell(cell) != null && StringUtils.isNotEmpty(getStringFromColumn(row, cell))) {
			return false;
		}
		return true;
	}

	@NonNull
	public static AppValidationException getAppValidationMessage(Row row, int col, @Nullable Exception e) {
		final String cellRef = CellReference.convertNumToColString(col) + (row.getRowNum() + 1);
		String message = e != null ? e.getMessage() : "";
		if (e != null) {
			LOG.error("VACME-IMPORTODI: Es ist ein Fehler aufgetreten: {}", cellRef, e);
		}
		return AppValidationMessage.ODI_IMPORT_FEHLER.create(cellRef, message);
	}

	@NonNull
	public static AppValidationException getAppValidationMessage(int rowNumber, @NonNull String description) {
		final AppValidationException appValidationException = AppValidationMessage.ODI_IMPORT_FEHLER.create(
			rowNumber, description);
		LOG.error("VACME-IMPORTODI: Es ist ein Fehler aufgetreten in Zeile: {}, {}", rowNumber, appValidationException.getMessage());
		return appValidationException;
	}
}
