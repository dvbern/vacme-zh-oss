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
import {Observable} from 'rxjs';
import {ImpfstoffJaxTS} from 'vacme-web-generated';
import {LogFactory} from 'vacme-web-shared';
import {ImpfdokumentationService} from '../../../../../vacme-web-generated/src/lib/api/impfdokumentation.service';

const LOG = LogFactory.createLog('ImpfstoffResolverService');

@Injectable({
    providedIn: 'root',
})
/**
 * Holt die Liste aller Impfstoffe, die zugelassen oder extern zugelassen sind
 */
export class ImpfstoffZugelassenAndExternZugelassenResolverService implements Resolve<Array<ImpfstoffJaxTS>> {

    constructor(protected impfdokumentationService: ImpfdokumentationService) {
    }

    public resolve(route: ActivatedRouteSnapshot): Observable<Array<ImpfstoffJaxTS>> {
        return this.impfdokumentationService.impfdokumentationResourceGetZugelasseneAndExternZugelasseneImpfstoffeList();
    }
}
