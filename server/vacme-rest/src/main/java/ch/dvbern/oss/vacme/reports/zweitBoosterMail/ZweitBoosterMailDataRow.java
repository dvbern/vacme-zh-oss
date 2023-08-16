package ch.dvbern.oss.vacme.reports.zweitBoosterMail;

import java.time.LocalDate;

import ch.dvbern.oss.vacme.entities.registration.Prioritaet;
import lombok.Getter;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;

@Getter
@Setter
public class ZweitBoosterMailDataRow {
	private boolean isSelbstzahlerImpfung;
	private LocalDate geburtsdatum;
	private Boolean immunsupprimiert;
	private Prioritaet prioritaet;

	public ZweitBoosterMailDataRow(
		boolean isSelbstzahlerImpfung,
		@NonNull LocalDate geburtsdatum,
		@NonNull Boolean immunsupprimiert,
		@NonNull Prioritaet prioritaet
	) {
		this.isSelbstzahlerImpfung = isSelbstzahlerImpfung;
		this.geburtsdatum = geburtsdatum;
		this.immunsupprimiert = immunsupprimiert;
		this.prioritaet = prioritaet;
	}
}
