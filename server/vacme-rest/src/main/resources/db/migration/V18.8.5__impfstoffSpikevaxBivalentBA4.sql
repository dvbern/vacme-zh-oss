#Impfstoff Spikevax Bivalent Original / Omicron BA.4-5
INSERT IGNORE INTO Impfstoff (id, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version,
							  anzahlDosenBenoetigt, code, name, hersteller, covidCertProdCode, hexFarbe,
							  zulassungsStatus,
							  informationsLink, impfstofftyp, zulassungsStatusBooster, wHoch2Code)
VALUES ('449e41ae-b328-471f-9ed5-37c2bfc0327d', UTC_TIMESTAMP(), UTC_TIMESTAMP(), 'flyway', 'flyway', 0,
		2, '7680691890017', 'Spikevax® Bivalent Original / Omicron BA.4-5', 'Moderna', 'EU/1/20/1507', '#0000DD',
		'EMPFOHLEN',
		'https://www.swissmedic.ch/swissmedic/de/home/news/coronavirus-covid-19/smc-laesst-bivalente-covid-19-origial-omicron-ba4-5-auffrischimpfung-moderna-zu.html',
		'MRNA', 'EMPFOHLEN', 'Spikevax®');

# Impfempfehlung wenn bereits geimpft
INSERT IGNORE INTO ImpfempfehlungChGrundimmunisierung (id, timestampErstellt, timestampMutiert, userErstellt,
													   userMutiert, version,
													   anzahlVerabreicht, notwendigFuerChGrundimmunisierung,
													   impfstoff_id)
VALUES ('967e3f63-a2ae-4fbf-8540-fd15cb7bda67', UTC_TIMESTAMP(), UTC_TIMESTAMP(), 'flyway',
		'flyway', 0,
		1, 1, '449e41ae-b328-471f-9ed5-37c2bfc0327d');
INSERT IGNORE INTO ImpfempfehlungChGrundimmunisierung (id, timestampErstellt, timestampMutiert, userErstellt,
													   userMutiert, version,
													   anzahlVerabreicht, notwendigFuerChGrundimmunisierung,
													   impfstoff_id)
VALUES ('d1e4f8be-40c7-4833-a9ea-2d030fa6464d', UTC_TIMESTAMP(), UTC_TIMESTAMP(), 'flyway',
		'flyway', 0,
		2, 0, '449e41ae-b328-471f-9ed5-37c2bfc0327d');

/*
-- UNDO:

DELETE FROM ImpfempfehlungChGrundimmunisierung where impfstoff_id ='b159b520-742c-42c1-b6db-35664b3c2ee6';
DELETE FROM Impfstoff where id = 'b159b520-742c-42c1-b6db-35664b3c2ee6';

DELETE from flyway_schema_history where flyway_schema_history.script = 'db/migration/V18.8.5__impfstoffSpikevaxBivalentBA4.sql';

*/