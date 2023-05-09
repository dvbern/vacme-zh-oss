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

import {CommonModule, registerLocaleData} from '@angular/common';
import {HTTP_INTERCEPTORS, HttpClientModule} from '@angular/common/http';
import localeDeCH from '@angular/common/locales/de-CH';
import localeDeCHExtra from '@angular/common/locales/extra/de-CH';
import localeFrCHExtra from '@angular/common/locales/extra/fr-CH';
import localeFrCH from '@angular/common/locales/fr-CH';
import {LOCALE_ID, NgModule} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {RouterModule} from '@angular/router';
import {NgbTooltipModule, NgbTypeaheadModule} from '@ng-bootstrap/ng-bootstrap';
import {TranslateModule} from '@ngx-translate/core';
import {SweetAlert2Module} from '@sweetalert2/ngx-sweetalert2';
import {Configuration} from 'vacme-web-generated';
import {ApplicationMessageWindowComponent} from './application-message-window/application-message-window.component';
import {CollapsibleComponent} from './components/collapsible-content/collapsible.component';
import {AccordionComponent} from './components/accordion/accordion.component';
import {ButtonNavigateComponent} from './components/button-navigate/button-navigate.component';
import {ButtonComponent} from './components/button/button.component';
import {DvErrorMessagesComponent} from './components/dv-error-messages/dv-error-messages.component';
import {DvMenuComponent} from './components/dv-menu/dv-menu.component';
import {AmpelComponent} from './components/form-controls/ampel/ampel.component';
import {FormControlAmpelComponent} from './components/form-controls/form-control-ampel/form-control-ampel.component';
import {InputCheckboxComponent} from './components/form-controls/input-checkbox/input-checkbox.component';
import {InputMultiSelectComponent} from './components/form-controls/input-multi-select/input-multi-select.component';
import {InputRadioComponent} from './components/form-controls/input-radio/input-radio.component';
import {InputSelectComponent} from './components/form-controls/input-select/input-select.component';
import {InputTextComponent} from './components/form-controls/input-text/input-text.component';
import {InputTrimDirective} from './components/form-controls/input-text/input-trim.directive';
import {InputTextareaComponent} from './components/form-controls/input-textarea/input-textarea.component';
import {InputTypeaheadFormComponent} from './components/form-controls/input-typeahead-form/input-typeahead-form.component';
import {ValidationmessagesComponent} from './components/form-controls/validationmessages/validationmessages.component';
import {InputTypeaheadComponent} from './components/input-typeahead/input-typeahead.component';
import {AccountLinksComponent} from './components/account-links/account-links.component';
import {AddressboxComponent} from './components/vacme-components/addressbox/addressbox.component';
import {DateSpinnerComponent} from './components/vacme-components/date-spinner/date-spinner.component';
// eslint-disable-next-line max-len
import {ExternGeimpftInfoComponentComponent} from './components/vacme-components/extern-geimpft-info-component/extern-geimpft-info-component.component';
import {TerminOverviewItemComponent} from './components/vacme-components/termin-overview-item/termin-overview-item.component';
import {TermineBearbeitenComponent} from './components/vacme-components/termine-bearbeiten/termine-bearbeiten.component';
import {TerminfindungComponent} from './components/vacme-components/terminfindung/terminfindung.component';
import {ZertifikatListComponent} from './components/vacme-components/zertifikat-list-component/zertifikat-list.component';
import {ZertifikatPerPostComponent} from './components/vacme-components/zertifikat-per-post/zertifikat-per-post.component';
import {WarningboxComponent} from './components/warningbox/warningbox.component';
import {TabbingClickDirective} from './directives/tabbing-click-directive';
import {FooterComponent} from './footer/footer.component';
import {GreetingComponentComponent} from './greeting-component/greeting-component.component';
import {HttpLocaleInterceptorService} from './interceptors/http-locale-interceptor.service';
import {NgxJsonDatetimeInterceptor} from './interceptors/json-datetime-interceptor';
import {OnboardingcodeSucheComponent} from './onboardingcode-suche/onboardingcode-suche.component';
import {PersonalienSucheComponent} from './personalien-suche/personalien-suche.component';
import {WINDOW_PROVIDERS} from './providers/window-provider';
import {RegistrierungUvciSucheComponent} from './registrierung-suche-uvci/registrierung-uvci-suche.component';
import {SimplePagerComponent} from './simple-pager/simple-pager.component';
import {SortByPipe} from './util/sort-by-pipe';

import {VacmeWebSharedComponent} from './vacme-web-shared.component';

registerLocaleData(localeDeCH, 'de-CH', localeDeCHExtra);
registerLocaleData(localeDeCH, 'de', localeDeCHExtra);
registerLocaleData(localeFrCH, 'fr-CH', localeFrCHExtra);
registerLocaleData(localeFrCH, 'fr', localeFrCHExtra);

@NgModule({
    declarations: [
        VacmeWebSharedComponent,
        GreetingComponentComponent,
        FooterComponent,
        DvMenuComponent,
        AccountLinksComponent,
        DvErrorMessagesComponent,
        InputTextComponent,
        ValidationmessagesComponent,
        InputCheckboxComponent,
        InputRadioComponent,
        InputSelectComponent,

        InputTextareaComponent,
        InputTypeaheadComponent,
        InputTypeaheadFormComponent,
        AmpelComponent,
        FormControlAmpelComponent,
        WarningboxComponent,
        AccordionComponent,
        CollapsibleComponent,
        AddressboxComponent,
        TabbingClickDirective,
        TerminfindungComponent,
        TermineBearbeitenComponent,
        TerminOverviewItemComponent,
        ButtonComponent,
        ButtonNavigateComponent,
        DateSpinnerComponent,
        SortByPipe,
        ApplicationMessageWindowComponent,
        SimplePagerComponent,
        InputMultiSelectComponent,
        PersonalienSucheComponent,
        RegistrierungUvciSucheComponent,
        OnboardingcodeSucheComponent,
        ZertifikatPerPostComponent,
        ExternGeimpftInfoComponentComponent,
        InputTrimDirective,
        ZertifikatListComponent,
    ],
    imports: [
        CommonModule,
        HttpClientModule,
        ReactiveFormsModule,
        FormsModule,
        TranslateModule.forChild(),
        RouterModule,
        NgbTooltipModule,
        NgbTypeaheadModule,
    ],
    exports: [
        VacmeWebSharedComponent,
        GreetingComponentComponent,
        FooterComponent,
        DvMenuComponent,
        AccountLinksComponent,
        DvErrorMessagesComponent,
        ValidationmessagesComponent,
        InputTextComponent,
        InputCheckboxComponent,
        InputRadioComponent,
        InputSelectComponent,
        InputTextareaComponent,
        InputTypeaheadComponent,
        InputTypeaheadFormComponent,
        AmpelComponent,
        FormControlAmpelComponent,
        WarningboxComponent,
        AccordionComponent,
        CollapsibleComponent,
        AddressboxComponent,
        TranslateModule,
        SweetAlert2Module,
        NgbTooltipModule,
        TabbingClickDirective, // to make anchors accessible
        TerminfindungComponent,
        TermineBearbeitenComponent,
        TerminOverviewItemComponent,
        DateSpinnerComponent,
        ButtonComponent,
        ButtonNavigateComponent,
        ApplicationMessageWindowComponent,
        SortByPipe,
        NgbTypeaheadModule,
        SimplePagerComponent,
        InputMultiSelectComponent,
        PersonalienSucheComponent,
        RegistrierungUvciSucheComponent,
        OnboardingcodeSucheComponent,
        ZertifikatPerPostComponent,
        ExternGeimpftInfoComponentComponent,
        ZertifikatListComponent,
    ],
    providers: [
        {
            provide: Configuration,
            useFactory: () => new Configuration(
                {
                    basePath: '',
                },
            ),
            multi: false,
        },
        {provide: LOCALE_ID, useValue: 'de-CH'},
        {provide: HTTP_INTERCEPTORS, useClass: HttpLocaleInterceptorService, multi: true},
        {
            provide: HTTP_INTERCEPTORS,
            useClass: NgxJsonDatetimeInterceptor,
            multi: true,
        },
        WINDOW_PROVIDERS,
    ],
})
export class VacmeWebSharedModule {
}
