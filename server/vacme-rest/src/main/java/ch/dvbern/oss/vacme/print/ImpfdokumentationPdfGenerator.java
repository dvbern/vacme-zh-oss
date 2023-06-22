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

package ch.dvbern.oss.vacme.print;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import ch.dvbern.lib.invoicegenerator.pdf.PdfElementGenerator;
import ch.dvbern.lib.invoicegenerator.pdf.PdfUtilities;
import ch.dvbern.oss.vacme.entities.impfen.Impfung;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.types.daterange.DateUtil;
import ch.dvbern.oss.vacme.print.base.PdfConstants;
import ch.dvbern.oss.vacme.print.base.PdfGenerator;
import ch.dvbern.oss.vacme.print.base.PdfUtil;
import ch.dvbern.oss.vacme.print.base.VacmePdfGenerator;
import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPTable;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generator fuer Impfbestaetigung
 */
public class ImpfdokumentationPdfGenerator extends VacmePdfGenerator {

	private final List<String> impfungen;
	private final boolean hatNurEineVacmeImpfung;

	public ImpfdokumentationPdfGenerator(
		@NotNull Registrierung registrierung,
		@Nullable final Impfung impfung1,
		@Nullable final Impfung impfung2,
		@Nullable List<Impfung> boosterImpfungen
	) {
		super(registrierung);
		List<Impfung> tmpImpfungen = new ArrayList<>();
		if (impfung1 != null) tmpImpfungen.add(impfung1);
		if (impfung2 != null) tmpImpfungen.add(impfung2);
		if (boosterImpfungen != null && !boosterImpfungen.isEmpty()) {
			tmpImpfungen.addAll(boosterImpfungen);
		}

		// hat nur genau eine Vacme Impfung
		hatNurEineVacmeImpfung = impfung1 != null && tmpImpfungen.stream().filter(Objects::nonNull).count() == 1;
		this.impfungen = tmpImpfungen.stream().filter(Objects::nonNull).sorted((o1, o2) -> o1.getTimestampImpfung().compareTo(o2.getTimestampImpfung())).map(impfung -> buildImpfungString(impfung)).collect(Collectors.toList());
	}

	@NotNull
	@Override
	protected String getDocumentTitle() {
		return translateAllLanguages("print_impfdokumentation_title", " / ");
	}

	@NotNull
	@Override
	protected PdfGenerator.CustomGenerator getCustomGenerator() {
		return (generator) -> {
			Document document = generator.getDocument();
			document.add(PdfUtil.createParagraph(
				registrierung.getVorname() + " " +
				registrierung.getName() + ", " +
				PdfConstants.DATE_FORMATTER.format(registrierung.getGeburtsdatum() ) + " - " +
				registrierung.getRegistrierungsnummer(), 2));
			document.add(PdfUtil.createParagraph(translateAllLanguages("print_impfdokumentation_intro", "\n\n") + "\n\n\n", 0));
			document.add(PdfUtil.createListInParagraph(this.impfungen, 2));
			if (hatNurEineVacmeImpfung) { //  d.h. Erstimpfung in Vacme, evtl noch nicht grundimmunisiert
				if (registrierung.getTimestampZuletztAbgeschlossen() != null) {
					if (registrierung.getZweiteImpfungVerzichtetZeit() != null) {
						document.add(PdfUtil.createParagraph(buildZweiteImpfungVerzichtetString(), 2));
					} else {
						if (!Boolean.TRUE.equals(registrierung.getVollstaendigerImpfschutz())) {
							document.add(PdfUtil.createParagraph(buildAutomatischAbgeschlossenString(), 2));
						}
					}
				} else {
					if (!Boolean.TRUE.equals(registrierung.getVollstaendigerImpfschutz())) {
						document.add(PdfUtil.createParagraph(translateAllLanguages("print_impfdokumentation_zweite_impfung_ausstehend", "\n\n"), 2));
					}
				}
			}
			document.add(createVaccinationCertificateFooter("print_impfdokumentation_vaccination_certificate"));
		};
	}

	@NonNull
	private PdfPTable createVaccinationCertificateFooter(@NonNull String key) {
		PdfPTable table = new PdfPTable(1);
		table.setWidthPercentage(PdfElementGenerator.FULL_WIDTH);
		PdfPTable innerTable = new PdfPTable(1);
		innerTable.setWidthPercentage(PdfElementGenerator.FULL_WIDTH);
		innerTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);
		innerTable.getDefaultCell().setLeading(0, PdfUtilities.DEFAULT_MULTIPLIED_LEADING);
		innerTable.addCell(PdfUtil.createParagraph(translate(key, Locale.GERMAN)));
		table.addCell(innerTable);
		return table;
	}

	@NonNull
	private String buildImpfungString(@NonNull Impfung impfung) {
		return PdfConstants.DATE_FORMATTER.format(impfung.getTimestampImpfung()) + ", " +
			impfung.getImpfstoff().getHersteller() + " - " +
			impfung.getImpfstoff().getName() + ", " +
			impfung.getImpfstoff().getCode() + ", " +
			impfung.getLot() + ", " +
			NumberFormat.getInstance().format(impfung.getMenge()) + "ml" +
			(impfung.isGrundimmunisierung() ? "" : ' ' + translate("print_impfdokumentation_booster")) +
			(impfung.isExtern() ? "" : ", " + impfung.getTermin().getImpfslot().getOrtDerImpfung().getName()) +
			(impfung.isSelbstzahlende() ? ", " + translate("print_impfdokumentation_selbstzahlende") : "") +
			(impfung.isExtern() ? ", " + translate("print_impfdokumentation_extern") + impfung.getTermin().getImpfslot().getOrtDerImpfung().getName()  : "");
	}

	@NonNull
	private String buildZweiteImpfungVerzichtetString() {
		Objects.requireNonNull(registrierung.getZweiteImpfungVerzichtetZeit());
		if (registrierung.abgeschlossenMitVollstaendigemImpfschutz()) {
			return translateAllLanguagesWithArgs("print_impfdokumentation_zweite_impfung_verzichtet_vollstaendiger_impfschutz", "\n\n",
				DateUtil.formatDate(registrierung.getZweiteImpfungVerzichtetZeit()));
		}
		return translateAllLanguagesWithArgs("print_impfdokumentation_zweite_impfung_verzichtet", "\n\n",
			DateUtil.formatDate(registrierung.getZweiteImpfungVerzichtetZeit()),
			registrierung.getZweiteImpfungVerzichtetGrund());
	}

	@NonNull
	private String buildAutomatischAbgeschlossenString() {
		Objects.requireNonNull(registrierung.getTimestampZuletztAbgeschlossen());
		return translateAllLanguagesWithArgs("print_impfdokumentation_automatisch_abgeschlossen", "\n\n",
			DateUtil.formatDate(registrierung.getTimestampZuletztAbgeschlossen()));
	}
}
