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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import ch.dvbern.oss.vacme.service.MailService;
import ch.dvbern.oss.vacme.shared.errors.AppValidationException;
import ch.dvbern.oss.vacme.shared.errors.mappers.AppValidationExceptionMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Dieser Service wird vor allem benoetigt damit wir den Impoft Transaktionslos managen koennen.
 * Transaktionen werden dann erstellt wenn wir einen "Subservice" aufrufen.
 * Er hat daher absichtlich KEIN @Transactional
 */
@ApplicationScoped
@Slf4j
public class OdiImportManagerService {

	private final OdiImportService odiImportservice;
	private final MailService mailService;
	private final AppValidationExceptionMapper mapper;

	public OdiImportManagerService(
		@NonNull OdiImportService odiImportservice,
		@NonNull MailService mailService,
		@NonNull AppValidationExceptionMapper mapper
	) {
		this.odiImportservice = odiImportservice;
		this.mailService = mailService;
		this.mapper = mapper;
	}

	@Transactional(TxType.NOT_SUPPORTED)
	public void createMassenupload(@NonNull InputStream inputStream, @NonNull OutputStream outputStream, @NonNull String emailToNotify)
		throws IOException {
		try (Workbook workbook = new XSSFWorkbook(inputStream)) {
			this.readOdiAndBenutzer(workbook, emailToNotify);
			IOUtils.close(inputStream);
			workbook.write(outputStream);
		} finally {
			IOUtils.close(inputStream);
			IOUtils.close(outputStream);
		}
	}

	@Transactional(TxType.NOT_SUPPORTED)
	private void readOdiAndBenutzer(@NonNull Workbook workbook, @NonNull String emailToNotify) {
		StopWatch stopwatch = StopWatch.createStarted();
		Iterator<Row> iterator = workbook.getSheetAt(0).iterator();
		iterator.next(); // ignore description line
		iterator.next(); // ignore header line
		int i = 0;
		try {
			while (iterator.hasNext()) {
				Row row = iterator.next();
				if (!odiImportservice.isEmptyRow(row)) {
					odiImportservice.readOdiAndBenutzer(row);
					i++;
				}
			}
		} catch (Exception e) {

			String msg = e.getMessage();
			if (e instanceof AppValidationException) {
				msg = mapper.toMessage((AppValidationException) e);
			}
			mailService.sendTextMail( emailToNotify, "ODI Import fehlgeschlagen", msg, false);
			LOG.info("VACME-IMPORTODI: failed after {} rows from odi-massenimport, took {}s", i,stopwatch.getTime(TimeUnit.SECONDS));
			throw e;
		}
		LOG.info("VACME-IMPORTODI: imported {} rows from odi-massenimport, took {}s", i,
			stopwatch.getTime(TimeUnit.SECONDS));
		String msg = String.format("Import war erfolgreich fuer %s odis", i);
		mailService.sendTextMail( emailToNotify, "ODI Import done", msg, false);
	}

}
