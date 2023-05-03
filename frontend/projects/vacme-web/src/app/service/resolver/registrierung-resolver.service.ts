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
import {Observable, of} from 'rxjs';
import {ImpfkontrolleJaxTS, KontrolleService} from 'vacme-web-generated';

@Injectable({
    providedIn: 'root',
})
export class RegistrierungResolverService implements Resolve<ImpfkontrolleJaxTS | undefined> {

    constructor(protected kontrolleService: KontrolleService) {
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
            return this.kontrolleService.impfkontrolleResourceFind(nr);
        }

        return of(undefined);
    }
}
