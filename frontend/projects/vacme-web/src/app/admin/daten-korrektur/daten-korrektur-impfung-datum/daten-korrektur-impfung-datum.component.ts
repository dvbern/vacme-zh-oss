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

import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {FormBuilder, FormGroup, Validators} from '@angular/forms';
import {NgbTimeStruct} from '@ng-bootstrap/ng-bootstrap';
import {TranslateService} from '@ngx-translate/core';
import * as moment from 'moment';
// falsch: import Swal from 'sweetalert2'; // Hier wird nicht nur das JS, sondern auch das CSS importiert
import Swal from 'sweetalert2/dist/sweetalert2.js';
import {DashboardJaxTS, ImpfungDatumKorrekturJaxTS, KorrekturService} from 'vacme-web-generated';
import {LogFactory} from 'vacme-web-shared';
import {Option} from '../../../../../../vacme-web-shared/src/lib/components/form-controls/input-select/option';
import {
    DATE_FORMAT,
    DATE_PATTERN,
    DB_DEFAULT_MAX_LENGTH,
    MIN_DATE_FOR_IMPFUNGEN,
} from '../../../../../../vacme-web-shared/src/lib/constants';
import {AuthServiceRsService} from '../../../../../../vacme-web-shared/src/lib/service/auth-service-rs.service';
import {
    datumInPastValidator,
} from '../../../../../../vacme-web-shared/src/lib/util/customvalidator/datum-in-past-validator';
import {minDateValidator} from '../../../../../../vacme-web-shared/src/lib/util/customvalidator/min-date-validator';
import {
    parsableDateValidator,
} from '../../../../../../vacme-web-shared/src/lib/util/customvalidator/parsable-date-validator';
import FormUtil from '../../../../../../vacme-web-shared/src/lib/util/FormUtil';
import {isAtLeastOnceGeimpft} from '../../../../../../vacme-web-shared/src/lib/util/registrierung-status-utils';
import {SortByPipe} from '../../../../../../vacme-web-shared/src/lib/util/sort-by-pipe';
import DatenKorrekturService from '../daten-korrektur.service';
import {getAllowdRoles, TSDatenKorrekturTyp} from '../TSDatenKorrekturTyp';

const LOG = LogFactory.createLog('DatenKorrekturImpfungDatumComponent');

@Component({
    selector: 'app-daten-korrektur-impfung-datum',
    templateUrl: './daten-korrektur-impfung-datum.component.html',
    styleUrls: ['./daten-korrektur-impfung-datum.component.scss'],
    providers: [SortByPipe],
})
export class DatenKorrekturImpfungDatumComponent implements OnInit {

    @Input()
    dashboardJax!: DashboardJaxTS | undefined;

    @Output()
    public finished = new EventEmitter<boolean>();

    formGroup!: FormGroup;

    hourStep = 1;
    minuteStep = 30;
    public impfungenListOptions: Option[] = [];

    constructor(
        private authService: AuthServiceRsService,
        private fb: FormBuilder,
        private translationService: TranslateService,
        private korrekturService: KorrekturService,
        private datenKorrekturUtil: DatenKorrekturService
    ) {
    }

    ngOnInit(): void {
        this.formGroup = this.fb.group({
            impfung: this.fb.control(null, [Validators.required]),
            datum: this.fb.control(null,
                [
                    Validators.minLength(5), Validators.maxLength(DB_DEFAULT_MAX_LENGTH),
                    Validators.pattern(DATE_PATTERN), Validators.required, parsableDateValidator(), datumInPastValidator(),
                    minDateValidator(moment(MIN_DATE_FOR_IMPFUNGEN, 'DD.MM.YYYY').toDate()),
                ]),
            zeit: this.fb.control(null, Validators.required),
        });
    }

    availableImpfungAndImpffolgeOptions(): Option[] {
        if (!this.impfungenListOptions?.length) {
            this.impfungenListOptions = this.datenKorrekturUtil.availableImpfungAndImpffolgeOptions(this.dashboardJax);
        }
        return this.impfungenListOptions;
    }

    public hasRequiredRole(): boolean {
        return this.authService.isOneOfRoles(getAllowdRoles(TSDatenKorrekturTyp.IMPFUNG_DATUM));
    }

    public enabled(): boolean {
        if (this.dashboardJax?.registrierungsnummer) {
            return isAtLeastOnceGeimpft(this.dashboardJax.status);
        }
        return false;
    }

    public correctIfValid(): void {
        if (this.hasRequiredRole()) {
            FormUtil.doIfValid(this.formGroup, () => {
                this.correctData();
            });
        }
    }

    private correctData(): void {
        const data: ImpfungDatumKorrekturJaxTS = {
            impffolge: this.formGroup.get('impfung')?.value.impffolge,
            impffolgeNr: this.formGroup.get('impfung')?.value.impffolgeNr,
            terminTime: this.getFormControlTermin(),
        };
        const regNummer = this.dashboardJax?.registrierungsnummer;
        if (!regNummer || !data) {
            return;
        }
        this.korrekturService.korrekturResourceImpfungDatumKorrigieren(regNummer, data).subscribe(() => {
            Swal.fire({
                icon: 'success',
                text: this.translationService.instant('FACH-ADMIN.DATEN_KORREKTUR.SUCCESS'),
                showConfirmButton: true,
            }).then(() => {
                this.dashboardJax = undefined;
                this.formGroup.reset();
                this.finished.emit(true);
            });
        }, err => {
            LOG.error('Could not update Date of Impfung', err);
        });
    }

    private getFormControlTermin(): Date {
        const datum = moment(this.formGroup.get('datum')?.value, DATE_FORMAT);
        const time: NgbTimeStruct = this.formGroup.get('zeit')?.value;
        datum.hour(time.hour).minute(time.minute);
        return datum.toDate();
    }

    public reset(): void {
        this.dashboardJax = undefined;
        this.formGroup.reset();
        this.finished.emit(false);
    }
}

