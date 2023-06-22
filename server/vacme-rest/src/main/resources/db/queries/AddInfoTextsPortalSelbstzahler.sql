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

UPDATE ApplicationMessage set htmlContent = '<p><b>Wichtiger Hinweis</b></p><li>Covid-Impfungen müssen selber bezahlt werden, ausser in medizinisch indizierten Ausnahmefällen. Bitte beachten Sie die aktuellen Impfempfehlungen <a target=_blank href=https://www.zh.ch/de/gesundheit/coronavirus/coronavirus-impfung.html>HIER</a></li>'
                          where title = 'GENERAL_INFOTEXT_DE';
UPDATE ApplicationMessage set htmlContent = '<p><b>Remarque importante</b></p><li>Les vaccins COVID sont à votre charge, sauf exceptionnellement pour des raisons médicales. Veuillez consulter les recommandations actuelles en matière de vaccination <a target=_blank href=https://www.zh.ch/en/gesundheit/coronavirus/coronavirus-impfung.html>ICI</a></li>'
                          where title = 'GENERAL_INFOTEXT_FR';
UPDATE ApplicationMessage set htmlContent = '<p><b>Important notice</b></p><li>COVID vaccinations must be paid for by yourself, except in medically indicated exceptional cases. Please note the current vaccination recommendations <a target=_blank href=https://www.zh.ch/en/gesundheit/coronavirus/coronavirus-impfung.html>HERE</a></li>'
                          where title = 'GENERAL_INFOTEXT_EN';


