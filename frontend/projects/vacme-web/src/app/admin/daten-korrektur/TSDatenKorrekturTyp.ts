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

import {TSRole} from '../../../../../vacme-web-shared/src/lib/model';

export enum TSDatenKorrekturTyp {
    DELETE_ACCOUNT = 'DELETE_ACCOUNT',
    EMAIL_TELEPHONE = 'EMAIL_TELEPHONE',
    IMPFUNG_DATEN = 'IMPFUNG_DATEN',
    IMPFUNG_ORT = 'IMPFUNG_ORT',
    IMPFUNG_VERABREICHUNG = 'IMPFUNG_VERABREICHUNG',
    IMPFUNG_DATUM = 'IMPFUNG_DATUM',
    DELETE_IMPFUNG = 'DELETE_IMPFUNG',
    PERSONENDATEN = 'PERSONENDATEN',
    ZERTIFIKAT= 'ZERTIFIKAT',
    ZERTIFIKAT_REVOKE_AND_RECREATE = 'ZERTIFIKAT_REVOKE_AND_RECREATE',
    SELBSTZAHLENDE = 'SELBSTZAHLENDE',
}

export function getAllowdRoles(korrektur: TSDatenKorrekturTyp): Array<TSRole> {
    const roles: Array<TSRole> = [];
    switch (korrektur) {
        case TSDatenKorrekturTyp.DELETE_ACCOUNT:
            roles.push(TSRole.AS_BENUTZER_VERWALTER);
            roles.push(TSRole.KT_NACHDOKUMENTATION);
            roles.push(TSRole.KT_MEDIZINISCHE_NACHDOKUMENTATION);
            break;
        case TSDatenKorrekturTyp.EMAIL_TELEPHONE:
            roles.push(TSRole.KT_NACHDOKUMENTATION);
            break;
        case TSDatenKorrekturTyp.IMPFUNG_DATEN:
            roles.push(TSRole.OI_DOKUMENTATION);
            roles.push(TSRole.OI_IMPFVERANTWORTUNG);
            roles.push(TSRole.KT_MEDIZINISCHE_NACHDOKUMENTATION);
            break;
        case TSDatenKorrekturTyp.IMPFUNG_ORT:
            roles.push(TSRole.OI_IMPFVERANTWORTUNG);
            roles.push(TSRole.KT_NACHDOKUMENTATION);
            roles.push(TSRole.KT_MEDIZINISCHE_NACHDOKUMENTATION);
            break;
        case TSDatenKorrekturTyp.DELETE_IMPFUNG:
            roles.push(TSRole.OI_IMPFVERANTWORTUNG);
            roles.push(TSRole.KT_MEDIZINISCHE_NACHDOKUMENTATION);
            break;
        case TSDatenKorrekturTyp.IMPFUNG_VERABREICHUNG:
            roles.push(TSRole.OI_IMPFVERANTWORTUNG);
            roles.push(TSRole.KT_MEDIZINISCHE_NACHDOKUMENTATION);
            break;
        case TSDatenKorrekturTyp.IMPFUNG_DATUM:
            roles.push(TSRole.OI_IMPFVERANTWORTUNG);
            roles.push(TSRole.KT_NACHDOKUMENTATION);
            roles.push(TSRole.KT_MEDIZINISCHE_NACHDOKUMENTATION);
            break;
        case TSDatenKorrekturTyp.PERSONENDATEN:
            roles.push(TSRole.OI_IMPFVERANTWORTUNG);
            roles.push(TSRole.KT_NACHDOKUMENTATION);
            roles.push(TSRole.KT_MEDIZINISCHE_NACHDOKUMENTATION);
            break;
        case TSDatenKorrekturTyp.ZERTIFIKAT:
            roles.push(TSRole.AS_BENUTZER_VERWALTER);
            break;
        case TSDatenKorrekturTyp.ZERTIFIKAT_REVOKE_AND_RECREATE:
            roles.push(TSRole.KT_NACHDOKUMENTATION);
            roles.push(TSRole.KT_MEDIZINISCHE_NACHDOKUMENTATION);
            break;
        case TSDatenKorrekturTyp.SELBSTZAHLENDE:
            roles.push(TSRole.OI_IMPFVERANTWORTUNG);
            roles.push(TSRole.KT_NACHDOKUMENTATION);
            roles.push(TSRole.KT_MEDIZINISCHE_NACHDOKUMENTATION);
            break;
    }
    return roles;
}

export function getAllowedKorrekturTypen(roles: Array<TSRole> | undefined): Array<TSDatenKorrekturTyp> {
    const typen: Array<TSDatenKorrekturTyp> = [];
    if (!roles) {
        return typen;
    }
    for (const value of Object.values(TSDatenKorrekturTyp)) {
        const intersection = roles.filter(x => getAllowdRoles(value).includes(x));
        if (intersection.length > 0) {
            typen.push(value);
        }
    }
    return typen;
}
