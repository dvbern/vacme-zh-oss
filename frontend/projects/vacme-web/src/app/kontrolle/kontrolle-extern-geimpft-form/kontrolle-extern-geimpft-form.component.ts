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

import {DatePipe} from '@angular/common';
import {ChangeDetectorRef, Component, Input, OnChanges, OnDestroy, OnInit} from '@angular/core';
import {FormBuilder, FormGroup} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {Subject} from 'rxjs';
// falsch: import Swal from 'sweetalert2'; // Hier wird nicht nur das JS, sondern auch das CSS importiert
import {DossierService, ExternGeimpftJaxTS, KontrolleService, StammdatenService} from 'vacme-web-generated';
import {LogFactory} from 'vacme-web-shared';
import {
    MissingForGrundimmunisiertTS,
} from '../../../../../vacme-web-generated/src/lib/model/missing-for-grundimmunisiert';
import {Option} from '../../../../../vacme-web-shared/src/lib/components/form-controls/input-select/option';
import {AuthServiceRsService} from '../../../../../vacme-web-shared/src/lib/service/auth-service-rs.service';
import {ExternGeimpftUtil} from '../../../../../vacme-web-shared/src/lib/util/externgeimpft-util';

const LOG = LogFactory.createLog('KontrolleExternGeimpftFormComponent');

@Component({
    selector: 'app-kontrolle-extern-geimpft-form',
    templateUrl: './kontrolle-extern-geimpft-form.component.html',
    styleUrls: ['./kontrolle-extern-geimpft-form.component.scss'],
})
export class KontrolleExternGeimpftFormComponent implements OnInit, OnChanges, OnDestroy {

    private ngUnsubscribe$ = new Subject();

    @Input()
    public formGroup!: FormGroup;

    @Input()
    impfstoffOptions: Option[] = [];

    @Input()
    public externGeimpftOriginal?: ExternGeimpftJaxTS;


    constructor(
        private fb: FormBuilder,
        private router: Router,
        private route: ActivatedRoute,
        private stammdatenService: StammdatenService,
        private kontrolleService: KontrolleService,
        private authService: AuthServiceRsService,
        public translate: TranslateService,
        private datePipe: DatePipe,
        private cdRef: ChangeDetectorRef,
        public dossierService: DossierService,
    ) {
    }

    public myExternGeimpft(): ExternGeimpftJaxTS | null | undefined {
        return this.externGeimpftOriginal;
    }

    ngOnInit(): void {
        this.updateFormFromModelAndDetectChanges();
    }

    ngOnChanges(): void {
        if (!!this.formGroup) { // ngOnChanges kann vor und nach ngOnInit aufgerufen werden
            this.updateFormFromModelAndDetectChanges();
        }
    }

    private updateFormFromModelAndDetectChanges(): void {
        this.updateFormFromModel();
        this.cdRef.detectChanges();
    }

    private updateFormFromModel(): void {
        const model = this.myExternGeimpft();
        if (model) {
            ExternGeimpftUtil.updateFormFromModel(this.formGroup, model, this.impfstoffOptions, this.datePipe);
        }
    }

    public hasBeenGeimpft(): boolean {
        return this.formGroup?.get('externGeimpft')?.value;
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe$.next();
        this.ngUnsubscribe$.complete();
    }

    public showGenesen(): boolean {
        return ExternGeimpftUtil.showGenesen();
    }
    public showTrotzdemVollstaendigGrundimmunisieren(): boolean {
        return ExternGeimpftUtil.showTrotzdemVollstaendigGrundimmunisieren(this.formGroup);
    }

    public showPositivGetestetDatum(): boolean {
        return ExternGeimpftUtil.showPositivGetestetDatum(this.formGroup, this.impfstoffOptions);
    }

    public getAnzahlMissingImpfungen(): MissingForGrundimmunisiertTS | undefined {
        return ExternGeimpftUtil.calculateAnzahlMissingImpfungen(this.formGroup);
    }

}
