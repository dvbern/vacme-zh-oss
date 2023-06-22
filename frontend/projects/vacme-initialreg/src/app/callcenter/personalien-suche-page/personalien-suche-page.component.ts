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
import {PersonalienJaxTS} from 'vacme-web-generated';
import {PersonalienSucheService} from '../../../../../vacme-web-generated/src/lib/api/personalien-suche.service';
import {BaseDestroyableComponent} from '../../../../../vacme-web-shared/src/lib/components/base-destroyable/base-destroyable.component';

@Component({
    selector: 'app-personalien-suche-page',
    templateUrl: './personalien-suche-page.component.html',
    styleUrls: ['./personalien-suche-page.component.scss'],
})
export class PersonalienSuchePageComponent extends BaseDestroyableComponent implements OnInit {

    constructor(
        private sucheService: PersonalienSucheService,
        private router: Router,
    ) {
        super();
    }

    ngOnInit(): void {
    }

    getSearchFunction(): (geburtsdatum: Date, name: string, vorname: string) => Observable<PersonalienJaxTS[]> {
        return (geburtsdatum: Date, name: string, vorname: string) =>
            this.sucheService.personalienSucheRegResourceSuchen(geburtsdatum, name, vorname);
    }

    getSelectionFunction(): (daten: any) => void {
        return (daten: any) => this.router.navigate(['personalien-edit', daten.registrierungId]);
    }

}
