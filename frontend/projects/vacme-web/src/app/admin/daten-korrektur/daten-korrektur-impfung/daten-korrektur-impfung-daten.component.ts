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
import {ActivatedRoute} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
// falsch: import Swal from 'sweetalert2'; // Hier wird nicht nur das JS, sondern auch das CSS importiert
import Swal from 'sweetalert2/dist/sweetalert2.js';
import {DashboardJaxTS, ImpfstoffJaxTS, ImpfungKorrekturJaxTS, KorrekturService} from 'vacme-web-generated';
import {LogFactory} from 'vacme-web-shared';
import {
    BaseDestroyableComponent,
} from '../../../../../../vacme-web-shared/src/lib/components/base-destroyable/base-destroyable.component';
import {Option} from '../../../../../../vacme-web-shared/src/lib/components/form-controls/input-select/option';
import {DB_DEFAULT_MAX_LENGTH, REGEX_IMPFMENGE} from '../../../../../../vacme-web-shared/src/lib/constants';
import {AuthServiceRsService} from '../../../../../../vacme-web-shared/src/lib/service/auth-service-rs.service';
import FormUtil from '../../../../../../vacme-web-shared/src/lib/util/FormUtil';
import {ImpfstoffUtil} from '../../../../../../vacme-web-shared/src/lib/util/impfstoff-util';
import {isAtLeastOnceGeimpft} from '../../../../../../vacme-web-shared/src/lib/util/registrierung-status-utils';
import DatenKorrekturService from '../daten-korrektur.service';
import {getAllowdRoles, TSDatenKorrekturTyp} from '../TSDatenKorrekturTyp';

const LOG = LogFactory.createLog('DatenKorrekturImpfungDatenComponent');

@Component({
    selector: 'app-daten-korrektur-impfung-daten',
    templateUrl: './daten-korrektur-impfung-daten.component.html',
    styleUrls: ['./daten-korrektur-impfung-daten.component.scss']
})
export class DatenKorrekturImpfungDatenComponent extends BaseDestroyableComponent implements OnInit {

    @Input()
    dashboardJax!: DashboardJaxTS | undefined;

    @Output()
    public finished = new EventEmitter<boolean>();

    formGroup!: FormGroup;

    impfstoffe: ImpfstoffJaxTS[] = [];
    impfstoffOptions: Option[] = [];
    public impfungenListOptions: Option[] = [];
    public impfstoffInformationsLink: string | undefined;

    constructor(
        private authService: AuthServiceRsService,
        private fb: FormBuilder,
        private activatedRoute: ActivatedRoute,
        private translationService: TranslateService,
        private korrekturService: KorrekturService,
        private datenKorrekturUtil: DatenKorrekturService
    ) {
        super();
    }

    ngOnInit(): void {
        this.activatedRoute.data
            .pipe(this.takeUntilDestroyed())
            .subscribe(next => {
                this.impfstoffe = next.impfstoffList;
                this.impfstoffOptions = this.impfstoffe.map(impfstoff => {
                    return {
                        label: ImpfstoffUtil.createImpfstoffLabel(impfstoff, this.translationService),
                        value: impfstoff.id,
                    };
                });
            }, error => LOG.error(error));

        this.formGroup = this.fb.group({
            impfung: this.fb.control(null, [Validators.required]),
            impfstoff: this.fb.control(null, [Validators.required]),
            lot: this.fb.control(null, [Validators.required]),
            menge: this.fb.control(null,
                [Validators.maxLength(DB_DEFAULT_MAX_LENGTH),
                    Validators.required, Validators.pattern(REGEX_IMPFMENGE)])
        });
    }

    availableImpfungAndImpffolgeOptions(): Option[] {
        if (!this.impfungenListOptions?.length) {
            this.impfungenListOptions = this.datenKorrekturUtil.availableImpfungAndImpffolgeOptions(this.dashboardJax);
        }
        return this.impfungenListOptions;
    }

    public hasRequiredRole(): boolean {
        return this.authService.isOneOfRoles(getAllowdRoles(TSDatenKorrekturTyp.IMPFUNG_DATEN));
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
                this.impffolgeKorrigieren();
            });
        }
    }

    private impffolgeKorrigieren(): void {
        const data: ImpfungKorrekturJaxTS = {
            impffolge: this.formGroup.get('impfung')?.value.impffolge,
            impffolgeNr: this.formGroup.get('impfung')?.value.impffolgeNr,
            impfstoff: this.formGroup.get('impfstoff')?.value,
            lot: this.formGroup.get('lot')?.value,
            menge: this.formGroup.get('menge')?.value,
        };
        const regNummer = this.dashboardJax?.registrierungsnummer;
        if (!regNummer) {
            return;
        }
        this.korrekturService.korrekturResourceImpfungKorrigieren(regNummer, data).subscribe(res => {
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
            LOG.error('Could not update Impffung', err);
        });
    }

    public reset(): void {
        this.dashboardJax = undefined;
        this.formGroup.reset();
        this.finished.emit(false);
    }

    public impfstoffSelectChange() {
        const impfstoffId = this.formGroup.get('impfstoff')?.value;
        const impfstoff = this.impfstoffe.find(currImpfstoff => currImpfstoff.id === impfstoffId);
        if (impfstoff) {
            this.impfstoffInformationsLink = impfstoff.informationsLink;
            if (impfstoff.eingestellt) {
                Swal.fire({
                    icon: 'warning',
                    text: this.translationService.instant('FACH-ADMIN.DATEN_KORREKTUR.IMPFSTOFF_EINGESTELLT_WARNUNG',
                        {impfstoff: impfstoff.displayName}),
                    showConfirmButton: true,
                });
            }
        }
    }

    public openImpfstoffInformation() {
        window.open(this.impfstoffInformationsLink, '_blank');
    }
}
