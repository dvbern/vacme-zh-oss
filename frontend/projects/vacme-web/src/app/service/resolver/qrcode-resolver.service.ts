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
import {ActivatedRouteSnapshot, Resolve, Router} from '@angular/router';
import {Observable, of} from 'rxjs';
import {DossierService, ImpfkontrolleJaxTS} from 'vacme-web-generated';
import {LogFactory} from 'vacme-web-shared';
import {NavigationService} from '../navigation.service';

const LOG = LogFactory.createLog('QRCodeResolverService');

@Injectable({
    providedIn: 'root',
})
export class QRCodeResolverService implements Resolve<ImpfkontrolleJaxTS | undefined> {

    constructor(protected dossierService: DossierService, private router: Router, private navigationService: NavigationService) {
    }

    public resolve(route: ActivatedRouteSnapshot): Observable<ImpfkontrolleJaxTS | undefined> {
        const nr = route.params.registrierungsnummer;

        if (nr !== 'new') {
            return this.load$(nr);
        }
        return of(undefined);
    }

    private load$(nr: string): Observable<ImpfkontrolleJaxTS | undefined> {
        if (nr) {
            this.dossierService.dossierResourceGetDashboardRegistrierung(nr).subscribe(
                dossier => {
                    this.navigationService.navigate(dossier);
                },
                error => {
                    LOG.error(error);
                    this.navigationService.notFoundResult();
                }
            );
        }
        return of(undefined);
    }
}
