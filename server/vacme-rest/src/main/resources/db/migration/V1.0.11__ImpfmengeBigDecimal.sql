ALTER TABLE Impfung
	MODIFY menge DECIMAL(19, 2) NOT NULL;

ALTER TABLE Impfung_AUD
	MODIFY menge DECIMAL(19, 2) NULL;