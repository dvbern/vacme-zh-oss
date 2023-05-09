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

import {MOBILE_VORWAHLEN, REGEX_TELEFON} from '../constants';

export default class ValidationUtil {

    public static hasSwissMobileVorwahl(telToCheck: string): boolean {
        if (telToCheck) {
            const capturedGroups = telToCheck.match(REGEX_TELEFON);
            // const regex = new RegExp(REGEX_TELEFON);
            if (capturedGroups && capturedGroups.length >= 3) {
                const vorwahlGroup = capturedGroups[2];
                const matchesVorwahl: boolean = MOBILE_VORWAHLEN.some(value => value === vorwahlGroup);
                return matchesVorwahl;
            }
        }
        return false;
    }
}
