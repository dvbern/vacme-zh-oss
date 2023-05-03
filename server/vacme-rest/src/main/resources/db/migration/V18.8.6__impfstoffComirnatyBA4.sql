#Impfstoff Comirnaty Original / Omicron BA.4-5
INSERT IGNORE INTO Impfstoff (id, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version,
							  anzahlDosenBenoetigt, code, name, hersteller, covidCertProdCode, hexFarbe,
							  zulassungsStatus,
							  informationsLink, impfstofftyp, zulassungsStatusBooster, wHoch2Code)
VALUES ('b5a910a1-f629-434d-8f31-d4515c78ea71', UTC_TIMESTAMP(), UTC_TIMESTAMP(), 'flyway', 'flyway', 0,
		2, '7680691270017', 'Comirnaty® Original / Omicron BA.4-5', 'Pfizer/BioNTech', 'EU/1/20/1528', '#279DC1',
		'EMPFOHLEN',
		'https://www.swissmedic.ch/swissmedic/de/home/news/coronavirus-covid-19/stand-zl-bekaempfung-covid-19.html',
		'MRNA', 'EMPFOHLEN', 'Comirnaty®');

# Impfempfehlung wenn bereits geimpft
INSERT IGNORE INTO ImpfempfehlungChGrundimmunisierung (id, timestampErstellt, timestampMutiert, userErstellt,
													   userMutiert, version,
													   anzahlVerabreicht, notwendigFuerChGrundimmunisierung,
													   impfstoff_id)
VALUES ('57537172-d65b-433a-a450-1fcf971dbd03', UTC_TIMESTAMP(), UTC_TIMESTAMP(), 'flyway',
		'flyway', 0,
		1, 1, 'b5a910a1-f629-434d-8f31-d4515c78ea71');

INSERT IGNORE INTO ImpfempfehlungChGrundimmunisierung (id, timestampErstellt, timestampMutiert, userErstellt,
													   userMutiert, version,
													   anzahlVerabreicht, notwendigFuerChGrundimmunisierung,
													   impfstoff_id)
VALUES ('fe41339b-1b3b-4a0f-8542-13e8c0dd8942', UTC_TIMESTAMP(), UTC_TIMESTAMP(), 'flyway',
		'flyway', 0,
		2, 0, 'b5a910a1-f629-434d-8f31-d4515c78ea71');

-- Fuege eine Verknuepfung zu Comirnaty Original / Omicron BA.4-5 hinzu fuer alle Odis die bisher Comirnaty Bivalent Original/Omicron BA.1® hatten
INSERT IGNORE INTO OrtDerImpfung_Impfstoff (impfstoff_id, ortDerImpfung_id)
SELECT 'b5a910a1-f629-434d-8f31-d4515c78ea71', odi.id
FROM OrtDerImpfung odi
	 INNER JOIN OrtDerImpfung_Impfstoff ODII ON odi.id = ODII.ortDerImpfung_id
	 INNER JOIN Impfstoff I ON ODII.impfstoff_id = I.id
WHERE I.id = '765dd8e2-5294-4d85-87bb-6fce77362348';

/*
-- UNDO:
DELETE
FROM OrtDerImpfung_Impfstoff
WHERE impfstoff_id = 'b5a910a1-f629-434d-8f31-d4515c78ea71' AND ortDerImpfung_id IN (SELECT odi.id
	   FROM OrtDerImpfung odi
			INNER JOIN OrtDerImpfung_Impfstoff ODII ON odi.id = ODII.ortDerImpfung_id
			INNER JOIN Impfstoff I ON ODII.impfstoff_id = I.id
	   WHERE I.id = '765dd8e2-5294-4d85-87bb-6fce77362348');

DELETE FROM ImpfempfehlungChGrundimmunisierung where impfstoff_id ='b5a910a1-f629-434d-8f31-d4515c78ea71';
DELETE FROM Impfstoff where id = 'b5a910a1-f629-434d-8f31-d4515c78ea71';

DELETE from flyway_schema_history where flyway_schema_history.script = 'db/migration/V18.8.6__impfstoffComirnatyBA4.sql';

*/