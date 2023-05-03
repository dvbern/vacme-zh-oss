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

import {ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnDestroy, OnInit} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {Subscription} from 'rxjs';
import {LogFactory} from 'vacme-web-shared';
import IPersonalienTS from '../../../../../vacme-web-shared/src/lib/model/IPersonalienTS';
import {TSRegistrierungViolationType} from '../../../../../vacme-web-shared/src/lib/model/TSRegistrierungViolationType';
import {RegistrierungValidationService} from '../../service/registrierung-validation.service';

const LOG = LogFactory.createLog('PersonInfosComponent');

@Component({
    selector: 'app-person-infos',
    templateUrl: './person-infos.component.html',
    styleUrls: ['./person-infos.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PersonInfosComponent implements OnInit, OnDestroy {

    @Input() public personalien!: IPersonalienTS;

    private regValSub!: Subscription;

    public hasImpfgruppeError = false;

    constructor(
        private cdRef: ChangeDetectorRef,
        private registrierungValidationService: RegistrierungValidationService,
        public translateService: TranslateService,
    ) {
    }

    ngOnInit(): void {
        // detektiert events die uns intressieren
        this.regValSub = this.registrierungValidationService.registrierungValidationEventStrdeam$
            .subscribe(validations => {
                if (validations) {
                    for (const validation of validations) {
                        switch (validation.type) {
                            case TSRegistrierungViolationType.WRONG_IMPFGRUPPE:
                                this.hasImpfgruppeError = !validation.clearEvent;
                                break;

                        }
                    }
                    this.cdRef.detectChanges();
                }
            }, error => LOG.error(error));
    }

    ngOnDestroy(): void {
        this.regValSub.unsubscribe();
    }

}
