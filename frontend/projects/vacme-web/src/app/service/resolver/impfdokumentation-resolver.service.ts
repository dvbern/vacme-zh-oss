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
import {DossierService, KontrolleService, OrtderimpfungService} from 'vacme-web-generated';
import {ImpfdokumentationService} from '../../../../../vacme-web-generated/src/lib/api/impfdokumentation.service';

@Injectable({
    providedIn: 'root',
})
export class ImpfdokumentationResolverService implements Resolve<any> {

    constructor(
        private kontrolleService: KontrolleService,
        private impfdokumentationService: ImpfdokumentationService,
        private dossierService: DossierService,
        private odiService: OrtderimpfungService
    ) {
    }

    public resolve(route: ActivatedRouteSnapshot): Observable<any> {
        const registrierungsnummer = route.params.registrierungsnummer as string;
        if (registrierungsnummer) {
            const dossierRequest$ = this.dossierService.dossierResourceGetDashboardRegistrierung(registrierungsnummer);
            const impfstoffeRequest$ = this.impfdokumentationService
                .impfdokumentationResourceGetZugelasseneAndExternZugelasseneImpfstoffeList();
            const odiRequest$ = this.odiService.ortDerImpfungResourceGetAllOrtDerImpfungJax();
            return forkJoin({
                dossier$: dossierRequest$,
                impfstoffe$: impfstoffeRequest$,
                odi$: odiRequest$
            });
        }
        return of([]);
    }
}
