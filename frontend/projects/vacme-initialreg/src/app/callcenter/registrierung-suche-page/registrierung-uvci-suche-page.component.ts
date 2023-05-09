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
import {Router} from '@angular/router';
import {Observable} from 'rxjs';
import {RegistrierungSearchResponseWithRegnummerJaxTS} from 'vacme-web-generated';
import {PersonalienSucheService} from '../../../../../vacme-web-generated/src/lib/api/personalien-suche.service';
import {BaseDestroyableComponent} from '../../../../../vacme-web-shared/src/lib/components/base-destroyable/base-destroyable.component';

@Component({
    selector: 'app-registrierung-suche-page',
    templateUrl: './registrierung-uvci-suche-page.component.html',
    styleUrls: ['./registrierung-uvci-suche-page.component.scss'],
})
export class RegistrierungUvciSuchePageComponent extends BaseDestroyableComponent implements OnInit {

    constructor(
        private sucheService: PersonalienSucheService,
        private router: Router,
    ) {
        super();
    }

    ngOnInit(): void {
    }

    getSearchFunction(): (
        geburtsdatum: Date,
        name: string,
        vorname: string,
        uvci: string,
    ) => Observable<RegistrierungSearchResponseWithRegnummerJaxTS[]> {
        return (geburtsdatum: Date, name: string, vorname: string, uvci: string) =>
            this.sucheService.personalienSucheRegResourceSuchenUvci(geburtsdatum,
                name,
                uvci,
                vorname);
    }

    getSelectionFunction(): (daten: RegistrierungSearchResponseWithRegnummerJaxTS) => void {
        return (daten: RegistrierungSearchResponseWithRegnummerJaxTS) => this.router.navigate([
            'overview',
            daten.regNummer,
        ]);
    }

}
