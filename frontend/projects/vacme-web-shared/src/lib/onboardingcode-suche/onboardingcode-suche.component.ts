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

import {Component, Input, OnInit} from '@angular/core';
import {FormBuilder, FormGroup, Validators} from '@angular/forms';
import {Observable} from 'rxjs';
import {LogFactory} from '../logging';
import FormUtil from '../util/FormUtil';
import {TSRole} from '../model';
import {DashboardJaxTS} from 'vacme-web-generated';
import {Router} from '@angular/router';
import {AuthServiceRsService} from '../service/auth-service-rs.service';
import {ErrorMessageService} from '../service/error-message.service';
import {TranslateService} from '@ngx-translate/core';

const LOG = LogFactory.createLog('OnboardingcodeSucheComponent');

@Component({
    selector: 'lib-onboardingcode-suche',
    templateUrl: './onboardingcode-suche.component.html',
    styleUrls: ['./onboardingcode-suche.component.scss']
})
export class OnboardingcodeSucheComponent implements OnInit {

    public formGroup!: FormGroup;

    @Input()
    public searchFunction!: (onboardingcode: string) => Observable<DashboardJaxTS>;


    constructor(private fb: FormBuilder,
                private router: Router,
                private translationService: TranslateService,
                private authService: AuthServiceRsService,
                private errorService: ErrorMessageService) {
    }

    ngOnInit(): void {
        if (!this.hasCallcenterRole()) {
            this.errorService.addMesageAsError('ERROR_UNAUTHORIZED');
            this.router.navigate(['start']);
        }
        const minLength = 10;
        const maxLength = 11;
        this.formGroup = this.fb.group({
            code: this.fb.control(undefined,
                [Validators.minLength(minLength), Validators.maxLength(maxLength), Validators.required]),
        });
    }

    public onboardingcodeSuche(): void {
        FormUtil.doIfValid(this.formGroup, () => {
            const valueToSearch = this.formGroup.controls.code.value;
            this.searchFunction(valueToSearch).subscribe((next: DashboardJaxTS) => {
                if (next) {
                    this.router.navigate(['/overview/', next.registrierungsnummer]);
                } else {
                    this.showNoResult();
                }
            }, error => LOG.error(error));
        });
    }

    private hasCallcenterRole(): boolean {
        return this.authService.isOneOfRoles([TSRole.CC_AGENT, TSRole.CC_BENUTZER_VERWALTER]);
    }

    private showNoResult(): void {
        this.errorService.addMesageAsError(this.translationService.instant('CALLCENTER.ONBOARDINGCODE-SUCHE.NO-RESULT'));
    }
}
