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

package ch.dvbern.oss.vacme.service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.enterprise.context.ApplicationScoped;

import ch.dvbern.lib.invoicegenerator.errors.InvoiceGeneratorException;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.onboarding.Onboarding;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.zertifikat.Zertifikat;
import ch.dvbern.oss.vacme.i18n.ServerMessageUtil;
import ch.dvbern.oss.vacme.print.FreigabeBoosterPdfGenerator;
import ch.dvbern.oss.vacme.print.ImpfdokumentationPdfGenerator;
import ch.dvbern.oss.vacme.print.OnboardingPdfGenerator;
import ch.dvbern.oss.vacme.print.RegistrationsbestaetigungPdfGenerator;
import ch.dvbern.oss.vacme.print.TerminabsagePdfGenerator;
import ch.dvbern.oss.vacme.print.TerminbestaetigungPdfGenerator;
import ch.dvbern.oss.vacme.print.ZertifikatStornierungPdfGenerator;
import ch.dvbern.oss.vacme.print.base.VacmePdfGenerator;
import ch.dvbern.oss.vacme.shared.errors.AppFailureException;
import ch.dvbern.oss.vacme.shared.util.CleanFileName;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Slf4j
public class PdfService {

	@ConfigProperty(name = "vacme.sms.link", defaultValue = "https://be.vacme.ch")
	String link;

	@Nonnull
	public byte[] createRegistrationsbestaetigung(@Nonnull Registrierung registrierung) {
		RegistrationsbestaetigungPdfGenerator pdfGenerator = new RegistrationsbestaetigungPdfGenerator(registrierung, link);
		return generateDokument(pdfGenerator);
	}

	@Nonnull
	public byte[] createTerminabsage(@Nonnull Registrierung registrierung, @NonNull Impftermin termin,
		@NonNull String terminEffectiveStartBeforeOffsetReset) {
		TerminabsagePdfGenerator pdfGenerator = new TerminabsagePdfGenerator(registrierung, termin, null, terminEffectiveStartBeforeOffsetReset, null);
		return generateDokument(pdfGenerator);
	}

	@Nonnull
	public byte[] sendTerminabsageBeideTermine(@Nonnull Registrierung registrierung, @Nonnull Impftermin termin1, @Nonnull Impftermin termin2,
		@NonNull String terminEffectiveStart, @NonNull String termin2EffectiveStart) {
		TerminabsagePdfGenerator pdfGenerator = new TerminabsagePdfGenerator(registrierung, termin1, termin2, terminEffectiveStart, termin2EffectiveStart);
		return generateDokument(pdfGenerator);
	}

	@Nonnull
	public byte[] createTerminbestaetigung(@Nonnull Registrierung registrierung, @Nullable Impftermin boosterTerminOrNull) {
		TerminbestaetigungPdfGenerator pdfGenerator = new TerminbestaetigungPdfGenerator(registrierung, boosterTerminOrNull);
		return generateDokument(pdfGenerator);
	}

	@Nonnull
	public byte[] createFreigabeBoosterBrief(@Nonnull Registrierung reg, @NonNull LocalDate lastImpfungTimestamp) {

		FreigabeBoosterPdfGenerator freigabeBoosterPdfGenerator =
			new FreigabeBoosterPdfGenerator(reg, lastImpfungTimestamp);
		return generateDokument(freigabeBoosterPdfGenerator);
	}

	@Nonnull
	public byte[] createImpfdokumentation(@Nonnull Registrierung registrierung, @Nullable Impfung impfung1, @Nullable Impfung impfung2,
		@Nullable List<Impfung> boosterImpfungen) {
		ImpfdokumentationPdfGenerator pdfGenerator = new ImpfdokumentationPdfGenerator(registrierung, impfung1, impfung2, boosterImpfungen);
		return generateDokument(pdfGenerator);
	}

	@Nonnull
	public byte[] createZertifikatStornierung(@Nonnull Registrierung registrierung, @NonNull Zertifikat zertifikat) {
		ZertifikatStornierungPdfGenerator pdfGenerator = new ZertifikatStornierungPdfGenerator(registrierung, zertifikat);
		return generateDokument(pdfGenerator);
	}

	@Nonnull
	public byte[] createOnboardingLetter(@Nonnull Onboarding onboarding) {
		OnboardingPdfGenerator pdfGenerator = new OnboardingPdfGenerator(onboarding.getRegistrierung(), onboarding.getCode());
		return generateDokument(pdfGenerator);
	}

	@Nonnull
	private byte[] generateDokument(
		@Nonnull VacmePdfGenerator pdfGenerator
	) throws AppFailureException {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			pdfGenerator.generate(baos);
			byte[] content = baos.toByteArray();
			return content;
		} catch (InvoiceGeneratorException e) {
			throw new AppFailureException("Bei der Generierung des Dokuments ist ein Fehler aufgetreten", e);
		}
	}

	@NonNull
	public String createFilename(@NonNull final Enum<?> e, Locale locale, Object... args) {
		return CleanFileName.clean(ServerMessageUtil.translateEnumValue(e, locale, args));
	}
}
