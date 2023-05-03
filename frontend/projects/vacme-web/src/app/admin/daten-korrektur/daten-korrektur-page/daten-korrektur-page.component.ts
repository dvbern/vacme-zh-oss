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

import {formatDate} from '@angular/common';
import {Component, OnInit} from '@angular/core';
import {FormBuilder, FormGroup, Validators} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {KorrekturDashboardJaxTS, KorrekturService} from 'vacme-web-generated';
import {LogFactory} from 'vacme-web-shared';
import {
    BaseDestroyableComponent,
} from '../../../../../../vacme-web-shared/src/lib/components/base-destroyable/base-destroyable.component';
import {Option} from '../../../../../../vacme-web-shared/src/lib/components/form-controls/input-select/option';
import {REGISTRIERUNGSNUMMER_LENGTH} from '../../../../../../vacme-web-shared/src/lib/constants';
import {TSRole} from '../../../../../../vacme-web-shared/src/lib/model';
import {AuthServiceRsService} from '../../../../../../vacme-web-shared/src/lib/service/auth-service-rs.service';
import {VacmeSettingsService} from '../../../../../../vacme-web-shared/src/lib/service/vacme-settings.service';
import DateUtil from '../../../../../../vacme-web-shared/src/lib/util/DateUtil';
import FormUtil from '../../../../../../vacme-web-shared/src/lib/util/FormUtil';
import {getAllowdRoles, getAllowedKorrekturTypen, TSDatenKorrekturTyp} from '../TSDatenKorrekturTyp';

const LOG = LogFactory.createLog('DatenKorrekturComponent');

@Component({
    selector: 'app-daten-korrektur-page',
    templateUrl: './daten-korrektur-page.component.html',
    styleUrls: ['./daten-korrektur-page.component.scss'],
})
export class DatenKorrekturPageComponent extends BaseDestroyableComponent implements OnInit {

    searchFormGroup!: FormGroup;

    korrekturDashboard!: KorrekturDashboardJaxTS | undefined;

    formGroup!: FormGroup;

    public datenKorrekturTypen: Option[] = [];

    selectedKorrekturTyp: TSDatenKorrekturTyp | undefined;

    queryRegNummer?: string;

    constructor(
        private authService: AuthServiceRsService,
        private fb: FormBuilder,
        private korrekturService: KorrekturService,
        private router: Router,
        private route: ActivatedRoute,
        private vacmeSettingsService: VacmeSettingsService,
        private translate: TranslateService,
    ) {
        super();
    }

    ngOnInit(): void {
        this.route.queryParamMap.pipe(this.takeUntilDestroyed()).subscribe(map => {
            const registrierungNummer = map.get('registrierungNummer');
            if (registrierungNummer) {
                this.queryRegNummer = registrierungNummer;
                this.router.navigate(
                    ['.'],
                    {relativeTo: this.route, queryParams: {registrierungNummer: null}});
            }
        }, error => LOG.error(error));
        // Korrektur-Typ Liste fuellen aufgrund meiner Berechtigungen
        this.datenKorrekturTypen = getAllowedKorrekturTypen(this.authService.getPrincipalRoles())
            .filter(t => t !== TSDatenKorrekturTyp.EMAIL_TELEPHONE)
            .map(t => {
                return {label: t, value: t};
            });

        this.searchFormGroup = this.fb.group({
            registrierungsNummer: this.fb.control(this.queryRegNummer, [
                Validators.required,
                Validators.minLength(REGISTRIERUNGSNUMMER_LENGTH),
                Validators.maxLength(REGISTRIERUNGSNUMMER_LENGTH)]),
            datenKorrekturTyp: this.fb.control(null, [Validators.required]),
        });
        if (this.authService.isOneOfRoles(getAllowdRoles(TSDatenKorrekturTyp.EMAIL_TELEPHONE))
            && this.vacmeSettingsService.emailKOrrekturEnabled) {
            this.datenKorrekturTypen.push({
                label: TSDatenKorrekturTyp.EMAIL_TELEPHONE,
                value: TSDatenKorrekturTyp.EMAIL_TELEPHONE,
            });
        }
    }

    public getDateString(date: Date | undefined | null): string {
        if (date == null) {
            return '';
        }
        return formatDate(date, DateUtil.dateFormatMedium(this.translate.currentLang), this.translate.currentLang);
    }

    public hasRequiredRole(): boolean {
        // Nur anzeigen, wenn ich fuer irgendetwas berechtigt bin
        return this.datenKorrekturTypen.length > 0;
    }

    public showImpfungDatenKorrektur(): boolean {
        return this.selectedKorrekturTyp === TSDatenKorrekturTyp.IMPFUNG_DATEN;
    }

    public showImpfungOrtKorrektur(): boolean {
        return this.selectedKorrekturTyp === TSDatenKorrekturTyp.IMPFUNG_ORT;
    }

    public showImpfungVerabreichungKorrektur(): boolean {
        return this.selectedKorrekturTyp === TSDatenKorrekturTyp.IMPFUNG_VERABREICHUNG;
    }

    public showImpfungDatumKorrektur(): boolean {
        return this.selectedKorrekturTyp === TSDatenKorrekturTyp.IMPFUNG_DATUM;
    }

    public showImpfungLoeschen(): boolean {
        return this.selectedKorrekturTyp === TSDatenKorrekturTyp.DELETE_IMPFUNG;
    }

    public showAccountLoeschen(): boolean {
        return this.selectedKorrekturTyp === TSDatenKorrekturTyp.DELETE_ACCOUNT;
    }

    public showPersonendatenKorrektur(): boolean {
        return this.selectedKorrekturTyp === TSDatenKorrekturTyp.PERSONENDATEN;
    }

    public showZertifikatKorrektur(): boolean {
        return this.selectedKorrekturTyp === TSDatenKorrekturTyp.ZERTIFIKAT;
    }

    public showZertifikatRevokeAndRecreateKorrektur(): boolean {
        return this.selectedKorrekturTyp === TSDatenKorrekturTyp.ZERTIFIKAT_REVOKE_AND_RECREATE;
    }

    public showEmailKorrektur(): boolean {
        return this.selectedKorrekturTyp === TSDatenKorrekturTyp.EMAIL_TELEPHONE;
    }

    public showPersonalienSearch(): boolean {
        return this.authService.isOneOfRoles([TSRole.KT_NACHDOKUMENTATION, TSRole.KT_IMPFVERANTWORTUNG]);
    }

    public showImpfungSelbstzahlendKorrektur(): boolean {
        return this.selectedKorrekturTyp === TSDatenKorrekturTyp.SELBSTZAHLENDE;
    }

    public searchIfValid(): void {
        FormUtil.doIfValid(this.searchFormGroup, () => {
            this.search();
        });
    }

    private search(): void {
        const code = this.searchFormGroup.get('registrierungsNummer')?.value;
        this.selectedKorrekturTyp = this.searchFormGroup.get('datenKorrekturTyp')?.value;

        this.korrekturService.korrekturResourceGetDashboardRegistrierung(code).subscribe(
            (res: KorrekturDashboardJaxTS) => {
                this.prepareDossier(res);
            },
            error => {
                LOG.error(`Could not find Registrierung with code ${code}`, error);
            },
        );
    }

    private prepareDossier(dashboardJaxTS: KorrekturDashboardJaxTS): void {
        this.korrekturDashboard = dashboardJaxTS;
    }

    public finished(navigate: boolean): void {
        const registrierungsnummer = this.korrekturDashboard?.registrierungsnummer as string;
        this.korrekturDashboard = undefined;
        this.searchFormGroup.reset();
        if (navigate) {
            this.navigateToGeimpftPage(registrierungsnummer);
        }
    }

    public navigateToSearch(): void {
        this.router.navigate(['admin', 'datenkorrektur', 'suche-registrierung']);
    }

    public navigateToGeimpftPage(registrierungsnummer: string): void {
        this.router.navigate(['person', registrierungsnummer, 'geimpft']);
    }
}
