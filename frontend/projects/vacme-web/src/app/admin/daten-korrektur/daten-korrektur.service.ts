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

import {formatDate} from '@angular/common';
import {Injectable} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {DashboardJaxTS, ImpffolgeTS} from 'vacme-web-generated';
import {Option} from '../../../../../vacme-web-shared/src/lib/components/form-controls/input-select/option';
import {ErrorMessageService} from '../../../../../vacme-web-shared/src/lib/service/error-message.service';
import {TerminUtilService} from '../../../../../vacme-web-shared/src/lib/service/termin-util.service';
import DateUtil from '../../../../../vacme-web-shared/src/lib/util/DateUtil';
import {ImpfungListItem} from './ImpfungListItem';

@Injectable({
    providedIn: 'root',
})
export default class DatenKorrekturService {

    constructor(
        private translateService: TranslateService,
        private errorMessageService: ErrorMessageService
    ) {
    }

    public availableImpfungAndImpffolgeOptions(dashboardJax: DashboardJaxTS | undefined): Option[] {
        if (!dashboardJax) {
            return [];
        }

        const result: Option[] = [];

        if (dashboardJax.impfung1) {
            const impffolgeNr1 =  TerminUtilService.determineImpffolgeNrForImpfung1Or2(
                ImpffolgeTS.ERSTE_IMPFUNG,
                dashboardJax);
            const val1: ImpfungListItem = {
                impfung: dashboardJax.impfung1,
                impffolge: ImpffolgeTS.ERSTE_IMPFUNG,
                impffolgeNr: impffolgeNr1
            };
            result.push({
                label: this.getLabelForImpfung(impffolgeNr1, dashboardJax.impfung1.timestampImpfung),
                value: val1
            });
        }
        if (dashboardJax.impfung2) {
            const impffolgeNr2 = TerminUtilService.determineImpffolgeNrForImpfung1Or2(
                ImpffolgeTS.ZWEITE_IMPFUNG,
                dashboardJax);
            const val2: ImpfungListItem = {
                impfung: dashboardJax.impfung2,
                impffolge: ImpffolgeTS.ZWEITE_IMPFUNG,
                impffolgeNr: impffolgeNr2
            };
            result.push({
                label: this.getLabelForImpfung(impffolgeNr2, dashboardJax.impfung2.timestampImpfung),
                value: val2
            });
        }
        if (dashboardJax.impfdossier?.impfdossiereintraege) {
            dashboardJax.impfdossier?.impfdossiereintraege
                .filter(eintrag => (eintrag.impfung))
                .forEach(value => {

                    if (value.impffolgeNr === undefined) {
                        this.errorMessageService.addMesageAsError('Impffolge-Nr der Impfung muss gesetzt sein');
                        throw new Error('value must be defined');
                    }
                    const valN: ImpfungListItem = {
                        impfung: value,
                        impffolge: ImpffolgeTS.BOOSTER_IMPFUNG,
                        impffolgeNr: value.impffolgeNr
                    };
                    result.push({
                        // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
                        label: this.getLabelForImpfung(value.impffolgeNr!, value.impfung?.timestampImpfung!),
                        value: valN
                    });
                });
        }

        return result;
    }

    private getLabelForImpfung(n: number, date: Date | undefined): string {
        return this.translateService.instant('FACH-ADMIN.DATEN_KORREKTUR.IMPFUNG.IMPFUNG_NR',
            {
                num: '' + n,
                date: date
                    ? formatDate(date, DateUtil.dateFormatLong(this.translateService.currentLang), this.translateService.currentLang)
                    : '?'
            });
    }

}
