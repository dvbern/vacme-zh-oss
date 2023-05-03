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
import {FormBuilder} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {ApplicationhealthService, RegistrierungTermineImpfungJaxTS} from 'vacme-web-generated';

// falsch: import Swal from 'sweetalert2'; // Hier wird nicht nur das JS, sondern auch das CSS importiert
import {LogFactory} from 'vacme-web-shared';
import {BaseDestroyableComponent} from '../../../../../vacme-web-shared/src/lib/components/base-destroyable/base-destroyable.component';
import {TSRole} from '../../../../../vacme-web-shared/src/lib/model';
import {AuthServiceRsService} from '../../../../../vacme-web-shared/src/lib/service/auth-service-rs.service';

const LOG = LogFactory.createLog('ApplicationHealthPageComponent');

@Component({
    selector: 'app-application-health-page',
    templateUrl: './application-health-page.component.html',
    styleUrls: ['./application-health-page.component.scss'],
})
export class ApplicationHealthPageComponent extends BaseDestroyableComponent implements OnInit {

    constructor(
        private fb: FormBuilder,
        private router: Router,
        private activeRoute: ActivatedRoute,
        private authService: AuthServiceRsService,
        private applicationhealthService: ApplicationhealthService,
    ) {
        super();
    }

    public autokorrektur = false;

    public resultInkonsistenzenTermine: Array<RegistrierungTermineImpfungJaxTS> | undefined;
    public resultInkonsistenzenStatus: Array<RegistrierungTermineImpfungJaxTS> | undefined;
    public resultRegistrierungenMitUnterschiedlichenOdi: Array<RegistrierungTermineImpfungJaxTS> | undefined;
    public resultAusstehendeImpfungenZwei: Array<RegistrierungTermineImpfungJaxTS> | undefined;

    ngOnInit(): void {
    }

    public isUserInroleAsRegistrationOi(): boolean {
        return this.authService.hasRole(TSRole.AS_REGISTRATION_OI);
    }

    public getInkonsistenzenTermine(): void {
        this.applicationhealthService.applicationHealthResourceGetInkonsistenzenTermine(this.autokorrektur)
            .subscribe((res: RegistrierungTermineImpfungJaxTS[]) => {
                this.autokorrektur = false;
                this.resultInkonsistenzenTermine = res;
            }, (error: any) => LOG.error('ERROR getInkonsistenzenTermine', error));
    }

    public getInkonsistenzenStatus(): void {
        this.applicationhealthService.applicationHealthResourceGetInkonsistenzenStatus()
            .subscribe((res: RegistrierungTermineImpfungJaxTS[]) => {
                this.autokorrektur = false;
                this.resultInkonsistenzenStatus = res;
            }, (error: any) => LOG.error('ERROR getInkonsistenzenStatus', error));
    }

    public getRegistrierungenMitUnterschiedlichenOdi(): void {
        this.applicationhealthService.applicationHealthResourceGetRegistrierungenMitUnterschiedlichenOdi()
            .subscribe((res: RegistrierungTermineImpfungJaxTS[]) => {
                this.autokorrektur = false;
                this.resultRegistrierungenMitUnterschiedlichenOdi = res;
            }, (error: any) => LOG.error('ERROR getRegistrierungenMitUnterschiedlichenOdi', error));
    }

    public getAusstehendeImpfungenZwei(): void {
        this.applicationhealthService.applicationHealthResourceGetAusstehendeImpfungenZwei()
            .subscribe((res: RegistrierungTermineImpfungJaxTS[]) => {
                this.autokorrektur = false;
                this.resultAusstehendeImpfungenZwei = res;
            }, (error: any) => LOG.error('ERROR getAusstehendeImpfungenZwei', error));
    }
}

