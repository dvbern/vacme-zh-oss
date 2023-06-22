package ch.dvbern.oss.vacme.reports.reportingOdiImpfungenTerminbuchungen;

import java.time.LocalDateTime;

import ch.dvbern.oss.vacme.entities.base.ImpfInfo;
import ch.dvbern.oss.vacme.entities.registration.Registrierung;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impfslot;
import ch.dvbern.oss.vacme.entities.terminbuchung.Impftermin;
import ch.dvbern.oss.vacme.entities.terminbuchung.OrtDerImpfung;
import lombok.Getter;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@Getter
@Setter
public class OdiTerminbuchungenDataRow extends OdiImpfungenDataRow {


	private @Nullable String letzteImpfungName;
	private @Nullable LocalDateTime letzteImpfungDatum;
	private @Nullable Boolean selbstFreigegeben;
	private @Nullable LocalDateTime terminOffset;

	public OdiTerminbuchungenDataRow(
		@NonNull Impfslot slot,
		@NonNull OrtDerImpfung odi,
		@NonNull Impftermin impftermin,
		@NonNull Registrierung registrierung,
		@NonNull Integer impffolgeNr
	) {
		super(slot, odi, null, registrierung, impffolgeNr);

		this.selbstFreigegeben = registrierung.isSelbstzahler();
		this.terminOffset = slot.getZeitfenster().getVon().plusMinutes(impftermin.getOffsetInMinutes());
	}

	public void setLetzteImpfung(@Nullable ImpfInfo impfung) {
		if (impfung != null) {
			this.letzteImpfungName = impfung.getImpfstoff().getName();
			this.letzteImpfungDatum = impfung.getTimestampImpfung();
		}
	}
}
