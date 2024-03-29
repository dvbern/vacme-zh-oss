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

import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';
import {TermineNResolverService} from '../../../../vacme-web-shared/src/lib/service/resolver/termin-N-resolver.service';
import {DossierResolverService} from '../service/resolver/dossier-resolver.service';
import {FreieImpfslotsResolverService} from '../service/resolver/freie-impfslots-resolver.service';
import {ImpffolgeResolverService} from '../../../../vacme-web-shared/src/lib/service/resolver/impffolge-resolver.service';
import {ErkrankungPageComponent} from './erkrankung-page/erkrankung-page.component';
import {TerminfindungPageComponent} from './terminfindung-page/terminfindung-page.component';
import {FreieTermineResolverService} from '../../../../vacme-web-shared/src/lib/service/resolver/freie-termine-resolver.service';
import {OrtDerImpfungIdResolverService} from '../../../../vacme-web-shared/src/lib/service/resolver/ort-der-impfung-id-resolver.service';
// eslint-disable-next-line max-len
import {RegistrierungsnummerResolverService} from '../../../../vacme-web-shared/src/lib/service/resolver/registrierungsnummer-resolver.service';
import {TermineBearbeitenResolverService} from '../../../../vacme-web-shared/src/lib/service/resolver/termin-bearbeiten-resolver.service';
import {Termine1ResolverService} from '../../../../vacme-web-shared/src/lib/service/resolver/termin-1-resolver.service';
import {Termine2ResolverService} from '../../../../vacme-web-shared/src/lib/service/resolver/termin-2-resolver.service';
// eslint-disable-next-line max-len
import {RegistrierungsStatusResolverService} from '../../../../vacme-web-shared/src/lib/service/resolver/registrierungsstatus-resolver.service';
import {
    UnsavedChangesGuard
} from '../../../../vacme-web/src/app/service/unsaved-changes-guard.service';

const routes: Routes = [

    {
        path: 'terminfindung/:ortDerImpfungId/:impffolge/:date',
        component: TerminfindungPageComponent,
        runGuardsAndResolvers: 'paramsOrQueryParamsChange',
        resolve: {
            freieSlots: FreieImpfslotsResolverService,
            impffolge: ImpffolgeResolverService,
            datum: FreieTermineResolverService,
            odi: OrtDerImpfungIdResolverService,
            registrierungsnummer: RegistrierungsnummerResolverService,
            modif: TermineBearbeitenResolverService,
            termin1: Termine1ResolverService,
            termin2: Termine2ResolverService,
            terminN: TermineNResolverService,
            status: RegistrierungsStatusResolverService
        },
    },
    {
        path: 'erkrankungen',
        component: ErkrankungPageComponent,
        canDeactivate: [UnsavedChangesGuard],
        resolve: {
            dossier: DossierResolverService,
        }
    },
    {
        path: '',
        redirectTo: 'start',
        pathMatch: 'full',
    },
    {
        path: '**',
        redirectTo: '/start',
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class RegistrierungRoutingModule {
}
