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

import {Injectable} from '@angular/core';
import {ActivatedRouteSnapshot, Resolve} from '@angular/router';
import {forkJoin, Observable, of} from 'rxjs';
import {DossierService} from 'vacme-web-generated';
import {ImpfdokumentationService} from '../../../../../vacme-web-generated/src/lib/api/impfdokumentation.service';

@Injectable({
    providedIn: 'root',
})
export class KontrolleResolverService implements Resolve<any> {

    constructor(
        private impfdokumentationService: ImpfdokumentationService,
        private dossierService: DossierService
    ) {
    }

    public resolve(route: ActivatedRouteSnapshot): Observable<any> {

        const registrierungsnummer = route.params.registrierungsnummer as string;
        if (registrierungsnummer !== undefined && registrierungsnummer !== 'new') {
            const fileInfoRequest$ = this.dossierService.dossierResourceGetFileInfo(registrierungsnummer);
            const dashboardRequest$ = this.dossierService.dossierResourceGetDashboardRegistrierung(registrierungsnummer);
            return forkJoin({
                fileInfo$: fileInfoRequest$,
                dashboard$: dashboardRequest$
            });
        }
        return of([]);
    }
}
