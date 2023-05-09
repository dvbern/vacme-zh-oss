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

import {ChangeDetectorRef, Component, OnInit} from '@angular/core';
import {FormArray, FormBuilder, FormControl, FormGroup, Validators} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import * as moment from 'moment';
import {Observable, of} from 'rxjs';
import {SweetAlertOptions} from 'sweetalert2';
import Swal from 'sweetalert2/dist/sweetalert2.js';
import {ImpffolgeTS, ImpfslotDisplayDayJaxTS, ImpfslotDisplayJaxTS, ImpfslotService, TerminbuchungService, TermineAbsagenJaxTS} from 'vacme-web-generated';
import {LogFactory} from 'vacme-web-shared';
import {BaseDestroyableComponent} from '../../../../../vacme-web-shared/src/lib/components/base-destroyable/base-destroyable.component';
import {NBR_IMPFSLOT_PER_DAY, REGEX_NUMBER_INT, TERMINSLOTS_MAX_PER_DAY} from '../../../../../vacme-web-shared/src/lib/constants';
import {TSRole} from '../../../../../vacme-web-shared/src/lib/model';
import {AuthServiceRsService} from '../../../../../vacme-web-shared/src/lib/service/auth-service-rs.service';
import {VacmeSettingsService} from '../../../../../vacme-web-shared/src/lib/service/vacme-settings.service';
import DateUtil from '../../../../../vacme-web-shared/src/lib/util/DateUtil';
import FormUtil from '../../../../../vacme-web-shared/src/lib/util/FormUtil';
import LayoutUtil from '../../../../../vacme-web-shared/src/lib/util/LayoutUtil';
import TenantUtil from '../../../../../vacme-web-shared/src/lib/util/TenantUtil';
import {CanComponentDeactivate} from '../../service/termin-guard.service';

const LOG = LogFactory.createLog('TerminverwaltungPageComponent');

@Component({
    selector: 'app-terminverwaltung-page',
    templateUrl: './terminverwaltung-page.component.html',
    styleUrls: ['./terminverwaltung-page.component.scss'],
})
export class TerminverwaltungPageComponent extends BaseDestroyableComponent implements OnInit, CanComponentDeactivate {

    constructor(
        private fb: FormBuilder,
        private router: Router,
        private activeRoute: ActivatedRoute,
        private impfslotsService: ImpfslotService,
        private translate: TranslateService,
        private changeDetector: ChangeDetectorRef,
        private terminbuchungService: TerminbuchungService,
        private authService: AuthServiceRsService,
        private vacmeSettingsService: VacmeSettingsService,
        private impfslotService: ImpfslotService
    ) {
        super();
    }

    formGroup!: FormGroup;
    rows: ImpfslotDisplayDayJaxTS[] = [];
    selectedODIId: string | null = null;
    selectedJahr = 0;
    selectedMonat = 0;
    loaded = false;
    monthYearCombination: Array<{ month: number; year: number }> = new Array();
    impffolge = ImpffolgeTS;
    maxRowLength = 0; // some rows are longer than others! We need this number for the tabindex and arrow navigation
    hasMonatEveryImpfslot = true;

    routeId?: string;
    routeJahr?: number;
    routeMonat?: number;

    ERSTEIMPFUNG: ImpffolgeTS = ImpffolgeTS.ERSTE_IMPFUNG;
    ZWEITEIMPFUNG: ImpffolgeTS = ImpffolgeTS.ZWEITE_IMPFUNG;
    BOOSTERIMPFUNG: ImpffolgeTS = ImpffolgeTS.BOOSTER_IMPFUNG;

    ngOnInit(): void {
        this.activeRoute.data
            .pipe(this.takeUntilDestroyed())
            .subscribe(data => {
                this.routeId = data.data.id;
                this.routeJahr = data.data.jahr;
                this.routeMonat = data.data.monat;
                this.reload();
            }, error => {
                LOG.error(error);
            });
    }

    private reload(): void {
        if (!this.routeId || !this.routeJahr || !this.routeMonat) {
            return;
        }

        this.loaded = false;
        this.loadImpfslots$(this.routeId, this.routeJahr, this.routeMonat).pipe().subscribe(impfslots => {

            this.rows = impfslots;
            if (this.rows) {
                this.createForm();
                this.selectedODIId = this.activeRoute.snapshot.paramMap.get('ortDerImpfungId');
                this.selectedJahr = +(this.activeRoute.snapshot.paramMap.get('jahr') as string);
                this.selectedMonat = +(this.activeRoute.snapshot.paramMap.get('monat') as string);
                this.updateMonthYearCombinations();
                this.loaded = true;
            }
            this.hasMonatEveryImpfslot = this.calculateHasMonatEveryImpfslot();
        }, error => {
            LOG.error(error);
            this.loaded = true;
        });
    }

    private createForm(): void {
        this.formGroup = this.fb.group(
            {tableRowArray: this.fb.array(this.createTableRow())},
        );
        this.changeDetector.detectChanges();
    }

    private createTableRow(): FormGroup[] {
        const result = new Array();
        this.maxRowLength = 0;
        for (const row of this.rows) {
            const x = this.fb.group({
                day: this.fb.control(row.day),
                impfslotDisplayJaxList: this.fb.array(
                    this.createRowCell(row),
                ),
            });
            result.push(x);
        }
        return result;
    }

    private createRowCell(row: ImpfslotDisplayDayJaxTS): FormGroup[] {
        const result = new Array();
        if (row.impfslotDisplayJaxList) {
            this.maxRowLength = Math.max(this.maxRowLength, row.impfslotDisplayJaxList.length);
            const rowIsDisabled = !DateUtil.isAfterToday(row.day);
            for (const cell of row.impfslotDisplayJaxList) {
                const y = this.fb.group({
                    id: this.fb.control(cell.id),
                    zeitfenster: this.fb.group({
                        von: this.fb.control(cell.zeitfenster?.von),
                        bis: this.fb.control(cell.zeitfenster?.bis),
                    }),
                    kapazitaetErsteImpfung: this.fb.control(
                        {value: cell.kapazitaetErsteImpfung, disabled: rowIsDisabled},
                        [Validators.pattern(REGEX_NUMBER_INT), Validators.max(TERMINSLOTS_MAX_PER_DAY)]),
                    kapazitaetZweiteImpfung: this.fb.control(
                        {value: cell.kapazitaetZweiteImpfung, disabled: rowIsDisabled},
                        [Validators.pattern(REGEX_NUMBER_INT), Validators.max(TERMINSLOTS_MAX_PER_DAY)]),
                    kapazitaetBoosterImpfung: this.fb.control(
                        {value: cell.kapazitaetBoosterImpfung, disabled: rowIsDisabled},
                        [Validators.pattern(REGEX_NUMBER_INT), Validators.max(TERMINSLOTS_MAX_PER_DAY)]),
                });
                result.push(y);
            }
        }

        return result;
    }

    public isOfftime(row: ImpfslotDisplayDayJaxTS, vonDisplay: string): boolean {
        // weekends
        if (this.isWeekend(row.day)) {
            return true;
        }
        // night & lunchtime
        const column = this.getColumnAt(vonDisplay);
        return column ? column.offtime : true; // columns not found -> offtime as well
    }

    getColumnAt(vonDisplay: string): any {
        return this.getColumns().find(value => vonDisplay === value.von);
    }

    getColumns(): any[] {

        return [
            {von: '06:00', bis: '06:30', offtime: true},
            {von: '06:30', bis: '07:00', offtime: true},
            {von: '07:00', bis: '07:30', offtime: true},
            {von: '07:30', bis: '08:00', offtime: true},
            {von: '08:00', bis: '08:30', offtime: false},
            {von: '08:30', bis: '09:00', offtime: false},
            {von: '09:00', bis: '09:30', offtime: false},
            {von: '09:30', bis: '10:00', offtime: false},
            {von: '10:00', bis: '10:30', offtime: false},
            {von: '10:30', bis: '11:00', offtime: false},
            {von: '11:00', bis: '11:30', offtime: false},
            {von: '11:30', bis: '12:00', offtime: false},
            {von: '12:00', bis: '12:30', offtime: true},
            {von: '12:30', bis: '13:00', offtime: true},
            {von: '13:00', bis: '13:30', offtime: true},
            {von: '13:30', bis: '14:00', offtime: true},
            {von: '14:00', bis: '14:30', offtime: false},
            {von: '14:30', bis: '15:00', offtime: false},
            {von: '15:00', bis: '15:30', offtime: false},
            {von: '15:30', bis: '16:00', offtime: false},
            {von: '16:00', bis: '16:30', offtime: false},
            {von: '16:30', bis: '17:00', offtime: false},
            {von: '17:00', bis: '17:30', offtime: false},
            {von: '17:30', bis: '18:00', offtime: false},
            {von: '18:00', bis: '18:30', offtime: true},
            {von: '18:30', bis: '19:00', offtime: true},
            {von: '19:00', bis: '19:30', offtime: true},
            {von: '19:30', bis: '20:00', offtime: true},
            {von: '20:00', bis: '20:30', offtime: true},
            {von: '20:30', bis: '21:00', offtime: true},
            {von: '21:00', bis: '21:30', offtime: true},
            {von: '21:30', bis: '22:00', offtime: true},
        ];

    }

    getRows(): Array<ImpfslotDisplayDayJaxTS> {
        return this.rows;
    }

    getImpfslotArrayOfRow(rowIndex: number): Array<ImpfslotDisplayJaxTS> | undefined {
        return this.rows[rowIndex].impfslotDisplayJaxList;
    }

    isWeekend(date: Date | undefined): boolean {
        if (!date) {
            return false;
        }
        switch (date.getDay()) {
            case 0:
            case 6:
                return true;
        }
        return false;
    }

    public submitIfValid(): void {
        FormUtil.doIfValid(this.formGroup, () => {
            this.save();
        });
    }

    save(): void {
        this.loaded = false;
        const parameters: Array<ImpfslotDisplayJaxTS> = new Array();
        for (const row of this.formGroup.value.tableRowArray) {
            const rowIsDisabled = !DateUtil.isAfterToday(row.day);
            if (rowIsDisabled) {
                continue;
            }
            for (const cell of row.impfslotDisplayJaxList) {
                // Wir moechten keine leeren Zellen haben, also schreiben wir 0 hin, wenn die Zelle leer ist.
                cell.kapazitaetErsteImpfung = cell.kapazitaetErsteImpfung || 0;
                cell.kapazitaetZweiteImpfung = cell.kapazitaetZweiteImpfung || 0;
                cell.kapazitaetBoosterImpfung = cell.kapazitaetBoosterImpfung || 0;
                parameters.push(cell);
            }
        }
        this.impfslotsService.impfslotResourceUpdateImpfslot(parameters).subscribe(
            () => {
                this.reload();
                this.validateEnoughZweittermineOrOk();
            },
            error => {
                this.loaded = true;
                LOG.error(error);
            },
        );
    }

    private validateEnoughZweittermineOrOk(): void {
        if (!this.routeId || !this.routeJahr || !this.routeMonat) {
            return;
        }
        // at midday to avoid timezone issues
        const mStart = DateUtil.firstDayOfMonth(this.routeMonat).year(this.routeJahr).hour(12);
        const mEnd = DateUtil.lastDayOfMonth(this.routeMonat).year(this.routeJahr).hour(12);

        this.impfslotsService.impfslotResourceValidateImpfslotsByOdiBetween(this.routeId, mEnd.toDate(), mStart.toDate())
            .pipe().subscribe(results => {
            let swalOptions: SweetAlertOptions;
            if (results.length > 0) {
                // Zweittermin-Validierungs-Warnungen
                // noinspection MagicNumberJS
                const maxWarnings = 15;
                const message = results.slice(0, maxWarnings).reduce((text, result) =>
                    text +
                    this.translate.instant('FACH-APP.ODI.TERMINVERWALTUNG.VALIDIERUNG_ZEILE', result) +
                    '<br>', '');
                swalOptions = {
                    icon: 'warning',
                    title: this.translate.instant('FACH-APP.ODI.TERMINVERWALTUNG.VALIDIERUNG_TITEL',
                        {desiredDays: this.vacmeSettingsService.distanceImpfungenDesired}),
                    html: message,
                    showConfirmButton: true,
                };
            } else {
                // Kein Problem, alles gut
                swalOptions = {
                    icon: 'success',
                    timer: 1500,
                    showConfirmButton: false,
                };
            }

            // Nur ein Swal oeffnen, sonst hatten wir ploetzlich race condition und dann wurde das falsche gezeigt
            Swal.fire(swalOptions);

        }, error => {
            LOG.error(error);
        });
    }

    navigate(monthYear: { month: number; year: number }): void {
        this.loaded = false;
        this.router.navigate(
            ['/ortderimpfung/terminverwaltung', this.selectedODIId, monthYear.year, monthYear.month]);
        LayoutUtil.makePageBig();
    }

    updateMonthYearCombinations(): void {
        this.monthYearCombination.splice(0, this.monthYearCombination.length);
        const m: moment.Moment = DateUtil.ofMonthYear(this.selectedMonat, this.selectedJahr);
        const mm1: moment.Moment = DateUtil.substractMonths(m, 1);
        const mm2: moment.Moment = DateUtil.substractMonths(m, 2);
        const mm3: moment.Moment = DateUtil.substractMonths(m, 3);
        const mp1: moment.Moment = DateUtil.addMonths(m, 1);
        const mp2: moment.Moment = DateUtil.addMonths(m, 2);
        const mp3: moment.Moment = DateUtil.addMonths(m, 3);
        this.monthYearCombination.push({month: mm3.month(), year: mm3.year()});
        this.monthYearCombination.push({month: mm2.month(), year: mm2.year()});
        this.monthYearCombination.push({month: mm1.month(), year: mm1.year()});
        this.monthYearCombination.push({month: m.month(), year: m.year()});
        this.monthYearCombination.push({month: mp1.month(), year: mp1.year()});
        this.monthYearCombination.push({month: mp2.month(), year: mp2.year()});
        this.monthYearCombination.push({month: mp3.month(), year: mp3.year()});
    }

    backToStammdaten(): void {
        this.router.navigate(['/ortderimpfung/stammdaten/' + this.selectedODIId]);
    }

    public canDeactivate(): Observable<boolean> | Promise<boolean> | boolean {
        if (!this.formGroup.dirty) {
            LayoutUtil.makePageNormal();
            return true;
        }

        const translatedWarningText = this.translate.instant('WARN.WARN_UNSAVED_CHANGES');
        return Swal.fire({
            icon: 'warning',
            text: translatedWarningText,
            showCancelButton: true,
            cancelButtonText: this.translate.instant('WARN.WARN_UNSAVED_CHANGES_CANCEL'),
            confirmButtonText: this.translate.instant('WARN.WARN_UNSAVED_CHANGES_OK'),
        }).then(value => {
            if (value.isConfirmed) {
                LayoutUtil.makePageNormal();
            }

            return value.isConfirmed;
        });
    }

    public preventDrag($event: any): boolean {
        return false;
    }

    public onKeydown($event: KeyboardEvent): void {
        switch ($event.code) {
            case 'ArrowLeft':
                this.moveFocus(-1, $event.target as Element);
                return;
            case 'ArrowRight':
                this.moveFocus(1, $event.target as Element);
                return;
            case 'ArrowUp':
                this.moveFocus(-this.maxRowLength, $event.target as Element);
                return;
            case 'ArrowDown':
                this.moveFocus(this.maxRowLength, $event.target as Element);
                return;
        }

    }

    private moveFocus(move: number, currentElement: Element): void {
        if (!currentElement) {
            return;
        }

        const attr = currentElement.getAttribute('tabindex');
        if (!attr) {
            return;
        }
        const currentIndex = parseInt(attr, 10);
        const newIndex = move + currentIndex;

        const newElement = document.querySelector('input[tabindex="' + newIndex + '"]');
        if (newElement) {
            const newInput = newElement as HTMLInputElement;
            newInput.focus();
            // ohne das timeout wird die selection von irgendwoher wieder abgewahlt
            setTimeout(() => {
                newInput.select();
            }, 1);

        }
    }

    public getDayFormgroup(row: number): FormGroup {
        const rowArray = this.formGroup.controls.tableRowArray as FormArray;
        return rowArray.controls[row] as FormGroup;
    }

    public getDayTimeslotFormgroup(dayFormGroup: FormGroup, col: number): FormGroup {
        const colArray = dayFormGroup.controls.impfslotDisplayJaxList as FormArray;
        return colArray.controls[col] as FormGroup;
    }

    public getControl(row: number, col: number, controlName: string): FormControl {
        return this.getDayTimeslotFormgroup(this.getDayFormgroup(row), col).controls[controlName] as FormControl;
    }

    public getSum(rowIndex: number, impffolge: ImpffolgeTS): number | undefined {
        let sum = 0;

        const dayFormGroup = this.getDayFormgroup(rowIndex);

        const dayFormgroupArray = dayFormGroup.controls.impfslotDisplayJaxList as FormArray;
        for (const item of dayFormgroupArray.controls) {
            let val = 0;
            const hourFormgroup = item as FormGroup;
            let controlName: string;
            switch (impffolge) {
                case ImpffolgeTS.ERSTE_IMPFUNG:
                    controlName = 'kapazitaetErsteImpfung';
                    break;
                case ImpffolgeTS.ZWEITE_IMPFUNG:
                    controlName = 'kapazitaetZweiteImpfung';
                    break;
                case ImpffolgeTS.BOOSTER_IMPFUNG:
                    controlName = 'kapazitaetBoosterImpfung';
                    break;
            }
            const control = hourFormgroup.controls[controlName];
            try {
                val = control.value ? parseInt(control.value, 10) : 0;
            } finally {
                sum += val;
            }
        }

        return sum;
    }

    private loadImpfslots$(id: string, jahr: number, monat: number): Observable<Array<ImpfslotDisplayDayJaxTS>> {
        const mStart = DateUtil.firstDayOfMonth(monat).year(jahr).hour(12); // at midday to avoid timezone issues
        const mEnd = DateUtil.lastDayOfMonth(monat).year(jahr).hour(12); // at midday to avoid timezone issues
        if (id) {
            LayoutUtil.makePageBig();
            return this.impfslotsService.impfslotResourceGetImpfslotByODIBetween(id,
                mEnd.toDate(),
                mStart.toDate());
        }

        return of([]);
    }

    public showButtonAbsagen(row: ImpfslotDisplayDayJaxTS): boolean {
        if (!TenantUtil.hasMassenTermineAbsagen()) {
            return false;
        }
        return this.authService.hasRole(TSRole.AS_REGISTRATION_OI)
            && DateUtil.isAfterToday(row.day);
    }

    public termineAbsagen(impffolge: ImpffolgeTS, row: ImpfslotDisplayDayJaxTS): void {
        let key;
        switch (impffolge) {
            case this.ERSTEIMPFUNG:
                key =  'FACH-APP.ODI.STAMMDATEN.TERMINE_ABSAGEN_ERSTTERMIN_CONFIRM';
                break;
            case this.ZWEITEIMPFUNG:
                key = 'FACH-APP.ODI.STAMMDATEN.TERMINE_ABSAGEN_ZWEITTERMIN_CONFIRM';
                break;
            case this.BOOSTERIMPFUNG:
                key = 'FACH-APP.ODI.STAMMDATEN.TERMINE_ABSAGEN_BOOSTERTERMIN_CONFIRM';
                break;
            default:
                throw new Error('Nicht unterstuetze Impffolge ' + impffolge);
        }
        Swal.fire({
            icon: 'warning',
            text: this.translate.instant(key),
            showConfirmButton: true,
            showCancelButton: true,
        }).then(r => {
            if (r.isConfirmed && this.selectedODIId && row.day) {
                const model: TermineAbsagenJaxTS = {
                    odiId: this.selectedODIId,
                    impffolge,
                    datum: row.day
                };
                this.terminbuchungService.terminbuchungResourceTermineAbsagenForOdiAndDatum(model).subscribe(value => {
                    LOG.info(value);
                    this.reload();
                    Swal.fire({
                        icon: 'success',
                        showCancelButton: false,
                        showConfirmButton: false,
                        timer: 1500,
                    });
                }, error => LOG.error(error));
            }
        });
    }

    public generateImpfslots(): void {
        if (this.selectedODIId) {
            // Months are starting with 0 in typescript
            const monat = this.selectedMonat + 1;
            this.impfslotService.impfslotResourceGenerateImpfslot(
                monat, this.selectedODIId, this.selectedJahr
            ).subscribe(() => {
                this.reload();
            }, error => LOG.error(error));
        }
    }

    private calculateHasMonatEveryImpfslot(): boolean {
        if (!this.rows) {
            return false;
        }
        const numberOfDays = DateUtil.daysInMonth(this.selectedMonat, this.selectedJahr);
        if (this.rows.length < numberOfDays) {
            return false;
        }
        return !this.rows.find(row =>
            !row.impfslotDisplayJaxList ||
            row.impfslotDisplayJaxList?.length < NBR_IMPFSLOT_PER_DAY);
    }
}
