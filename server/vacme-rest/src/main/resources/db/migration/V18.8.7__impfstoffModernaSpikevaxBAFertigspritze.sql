#Impfstoff Spikevax Bivalent Original / Omicron BA.4-5 Fertigspritzen
INSERT IGNORE INTO Impfstoff (id, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version,
							  anzahlDosenBenoetigt, code, name, hersteller, covidCertProdCode, hexFarbe,
							  zulassungsStatus,
							  informationsLink, impfstofftyp, zulassungsStatusBooster, wHoch2Code)
VALUES ('478da06e-a729-4118-afdf-dd3337ce6741', UTC_TIMESTAMP(), UTC_TIMESTAMP(), 'flyway', 'flyway', 0,
		2, '7680692110015', 'Spikevax® Bivalent Original / Omicron BA.4-5 Fertigspritzen', 'Moderna', 'EU/1/20/1507', '#0000DD',
		'EMPFOHLEN',
		'https://www.swissmedic.ch/swissmedic/de/home/news/coronavirus-covid-19/smc-laesst-bivalente-covid-19-origial-omicron-ba4-5-auffrischimpfung-moderna-zu.html',
		'MRNA', 'EMPFOHLEN', 'Spikevax®');

# Impfempfehlung wenn bereits geimpft
INSERT IGNORE INTO ImpfempfehlungChGrundimmunisierung (id, timestampErstellt, timestampMutiert, userErstellt,
													   userMutiert, version,
													   anzahlVerabreicht, notwendigFuerChGrundimmunisierung,
													   impfstoff_id)
VALUES ('811f0bc2-cfa1-475d-b477-f5307dea7cac', UTC_TIMESTAMP(), UTC_TIMESTAMP(), 'flyway',
		'flyway', 0,
		1, 1, '478da06e-a729-4118-afdf-dd3337ce6741');

INSERT IGNORE INTO ImpfempfehlungChGrundimmunisierung (id, timestampErstellt, timestampMutiert, userErstellt,
													   userMutiert, version,
													   anzahlVerabreicht, notwendigFuerChGrundimmunisierung,
													   impfstoff_id)
VALUES ('c98dc40a-3844-47c8-b498-ea3c0908c7d0', UTC_TIMESTAMP(), UTC_TIMESTAMP(), 'flyway',
		'flyway', 0,
		2, 0, '478da06e-a729-4118-afdf-dd3337ce6741');

INSERT IGNORE INTO OrtDerImpfung_Impfstoff (impfstoff_id, ortDerImpfung_id)
SELECT '478da06e-a729-4118-afdf-dd3337ce6741', odi.id
FROM OrtDerImpfung odi;

/*
-- UNDO:
DELETE FROM OrtDerImpfung_Impfstoff WHERE impfstoff_id = '478da06e-a729-4118-afdf-dd3337ce6741';
DELETE FROM ImpfempfehlungChGrundimmunisierung where impfstoff_id ='478da06e-a729-4118-afdf-dd3337ce6741';
DELETE FROM Impfstoff where id = '478da06e-a729-4118-afdf-dd3337ce6741';

DELETE from flyway_schema_history where flyway_schema_history.script = 'db/migration/V18.8.7__impfstoffModernaSpikevaxBAFertigspritze.sql';

*/