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

import {Component, OnInit} from '@angular/core';
import {FormBuilder, FormGroup, Validators} from '@angular/forms';
import {Router} from '@angular/router';
import {DashboardJaxTS, DossierService} from 'vacme-web-generated';
import {LogFactory} from 'vacme-web-shared';
import {BaseDestroyableComponent} from '../../../../../vacme-web-shared/src/lib/components/base-destroyable/base-destroyable.component';
import {REGISTRIERUNGSNUMMER_LENGTH} from '../../../../../vacme-web-shared/src/lib/constants';
import {TSRole} from '../../../../../vacme-web-shared/src/lib/model';
import {AuthServiceRsService} from '../../../../../vacme-web-shared/src/lib/service/auth-service-rs.service';
import {ErrorMessageService} from '../../../../../vacme-web-shared/src/lib/service/error-message.service';
import {TerminfindungResetService} from '../../../../../vacme-web-shared/src/lib/service/terminfindung-reset.service';
import FormUtil from '../../../../../vacme-web-shared/src/lib/util/FormUtil';
import TenantUtil from '../../../../../vacme-web-shared/src/lib/util/TenantUtil';

const LOG = LogFactory.createLog('RegistrierungSuchenPageComponent');

@Component({
    selector: 'app-registrierung-suchen-page',
    templateUrl: './registrierung-suchen-page.component.html',
    styleUrls: ['./registrierung-suchen-page.component.scss'],
})
export class RegistrierungSuchenPageComponent extends BaseDestroyableComponent implements OnInit {

    public formGroup!: FormGroup;

    constructor(
        private fb: FormBuilder,
        private dossierService: DossierService,
        private router: Router,
        private terminfindungResetService: TerminfindungResetService,
        private authService: AuthServiceRsService,
        private errorService: ErrorMessageService,
    ) {
        super();
    }

    ngOnInit(): void {
        this.formGroup = this.fb.group({
            code: this.fb.control(undefined, [
                Validators.minLength(REGISTRIERUNGSNUMMER_LENGTH),
                Validators.maxLength(REGISTRIERUNGSNUMMER_LENGTH),
                Validators.required]),
        });
        if (!this.hasCallcenterRole()) {
            this.errorService.addMesageAsError('ERROR_UNAUTHORIZED');
            this.router.navigate(['start']);
        }
    }

    public submitIfValid(): void {
        FormUtil.doIfValid(this.formGroup, () => {
            this.suchen();
        });
    }

    public onboardingEnabled(): boolean {
        return TenantUtil.hasOnboarding();
    }

    suchen(): void {
        this.terminfindungResetService.resetData();
        this.dossierService.dossierResourceRegGetDashboardRegistrierung(this.formGroup.controls.code.value)
            .subscribe(
                (next: DashboardJaxTS) => {
                    this.router.navigate(['/overview/', next.registrierungsnummer]);
                },
                error => {
                    LOG.error('Could not find Reg', error);
                },
            );
    }

    erfassen(): void {
        this.router.navigate(['personendaten']);
    }

    private hasCallcenterRole(): boolean {
        return this.authService.isOneOfRoles([TSRole.CC_AGENT, TSRole.CC_BENUTZER_VERWALTER]);
    }
}
