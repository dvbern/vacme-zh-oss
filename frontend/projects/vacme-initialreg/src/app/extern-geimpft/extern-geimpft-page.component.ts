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

import {DatePipe} from '@angular/common';
import {Component, OnInit} from '@angular/core';
import {FormBuilder, FormGroup} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {Observable, of} from 'rxjs';
import {first} from 'rxjs/operators';
// falsch: import Swal from 'sweetalert2'; // Hier wird nicht nur das JS, sondern auch das CSS importiert
import Swal from 'sweetalert2/dist/sweetalert2.js'; // nur das JS importieren
import {
    DashboardJaxTS,
    ExternGeimpftJaxTS,
    ImpfstoffJaxTS,
    RegistrierungService,
    StammdatenService,
} from 'vacme-web-generated';
import {LogFactory} from 'vacme-web-shared';
import {MissingForGrundimmunisiertTS} from '../../../../vacme-web-generated/src/lib/model/missing-for-grundimmunisiert';
import {
    BaseDestroyableComponent,
} from '../../../../vacme-web-shared/src/lib/components/base-destroyable/base-destroyable.component';
import {Option} from '../../../../vacme-web-shared/src/lib/components/form-controls/input-select/option';
import {AuthServiceRsService} from '../../../../vacme-web-shared/src/lib/service/auth-service-rs.service';
import {TerminfindungResetService} from '../../../../vacme-web-shared/src/lib/service/terminfindung-reset.service';
import {ConfirmUtil} from '../../../../vacme-web-shared/src/lib/util/confirm-util';
import {ExternGeimpftUtil} from '../../../../vacme-web-shared/src/lib/util/externgeimpft-util';
import FormUtil from '../../../../vacme-web-shared/src/lib/util/FormUtil';
import {FreigabeEntzugUtil} from '../../../../vacme-web-shared/src/lib/util/freigabe-entzug-util';

const LOG = LogFactory.createLog('ExternGeimpftComponent');

@Component({
    templateUrl: './extern-geimpft-page.component.html',
    styleUrls: ['./extern-geimpft-page.component.scss'],
})
export class ExternGeimpftPageComponent extends BaseDestroyableComponent implements OnInit {

    public registrierungsnummer!: string;
    public dashboardJax?: DashboardJaxTS;
    private saveRequestPending = false;

    public formGroup!: FormGroup;

    impfstoffOptions: Option[] = [];

    constructor(
        private fb: FormBuilder,
        private router: Router,
        private route: ActivatedRoute,
        private stammdatenService: StammdatenService,
        private registrierungsService: RegistrierungService,
        private authService: AuthServiceRsService,
        private translate: TranslateService,
        private datePipe: DatePipe,
        private terminfindungResetService: TerminfindungResetService,
    ) {
        super();
    }

    ngOnInit(): void {

        this.route.params
            .pipe(this.takeUntilDestroyed())
            .subscribe(params => {
                this.registrierungsnummer = params.registrierungsnummer as string;
            }, error => {
                LOG.error('Registrierungsnummer fehlt', error);
            });

        this.stammdatenService.stammdatenResourceRegGetAlleImpfstoffeForExternGeimpft().pipe(first())
            .subscribe((list: ImpfstoffJaxTS[]) => {

            // @ts-ignore
            this.impfstoffOptions = list.map(impfstoff => {
                return {
                    label: impfstoff.displayName,
                    value: impfstoff,
                };
            });

            this.route.data
                .pipe(this.takeUntilDestroyed())
                .subscribe(next => {
                    this.dashboardJax = next.dossier;
                    this.setupForm();

                    if (this.dashboardJax?.externGeimpft) {
                        ExternGeimpftUtil.updateFormFromModel(this.formGroup, this.dashboardJax?.externGeimpft,
                            this.impfstoffOptions, this.datePipe);
                    }
                }, error => {
                    LOG.error(error);
                });

        }, error => {
            LOG.error(error);
        });
    }

    private setupForm(): void {
        this.formGroup = ExternGeimpftUtil.createFormgroup(
            this.fb, false, this.destroyedEmitter$, this.impfstoffOptions);
    }

    public submitIfValid(): void {
        FormUtil.doIfValid(this.formGroup, () => {
            this.save();
        });
    }

    private save(): void {
        if (this.saveRequestPending) {
            return;
        }
        this.saveBasic();
    }

    private saveBasic(): void {
        if (!!this.dashboardJax) {
            this.saveRequestPending = true; // can only run one save request at a time
            const dashboardVorher = this.dashboardJax;
            const load = this.prepareLoadToSave();
            if (!!load) {
                // Testweise ausfuehren und schauen, was passieren wuerde
                this.registrierungsService.registrierungResourceUpdateExternGeimpft(load.regNr, true, load.data)
                    .subscribe(
                        dashboardReloadedTest => {
                            // vergleichen, ob beim Speichern der Termin oder die Odiwahl geloescht werden wuerde
                            const canProceed$ = this.warnBeforeFinalSaveIfNeeded$(dashboardVorher, dashboardReloadedTest);
                            canProceed$.subscribe(canProceed => {
                                if (canProceed) {
                                    // Wirklich speichern
                                    this.registrierungsService
                                        .registrierungResourceUpdateExternGeimpft(load.regNr, false, load.data)
                                        .subscribe(
                                            dashboardReloaded => {
                                                this.doAfterSaved(dashboardVorher, dashboardReloaded, load);
                                            }, error => this.onSaveError(error));
                                } else {
                                    this.saveRequestPending = false;
                                }
                            }, error => this.onSaveError(error));
                        }, error => this.onSaveError(error));
            }
        }
    }

    private prepareLoadToSave(): { data: ExternGeimpftJaxTS; regNr: string } | undefined {
        if (this.dashboardJax?.registrierungsnummer) {
            const data: ExternGeimpftJaxTS = ExternGeimpftUtil.formToModel(this.formGroup, this.impfstoffOptions);
            const regNr = this.dashboardJax?.registrierungsnummer;
            return {data, regNr};
        }
        return undefined;
    }

    private doAfterSaved(
        dashboardVorher: DashboardJaxTS,
        dashboardReloaded: DashboardJaxTS,
        load: { data: ExternGeimpftJaxTS; regNr: string },
    ): void {
        // Abschlussdialog, wenn noetig
        this.informUserAboutLostFreigabe(dashboardVorher, dashboardReloaded);
        // aufraeumen
        this.saveRequestPending = false;
        this.formGroup = this.fb.group({}); // damit man keine unsaved changes warnung bekommt
        this.terminfindungResetService.resetData();

        // zurueck zum Start
        this.router.navigate(['overview', load.regNr]);
    }

    private onSaveError(err: any): void {
        LOG.error('HTTP Error', err);
        this.saveRequestPending = false;
    }

    public hasBeenGeimpft(): boolean {
        return this.formGroup?.get('externGeimpft')?.value;
    }

    public showGenesen(): boolean {
        return ExternGeimpftUtil.showGenesen();
    }

    public showPositivGetestetDatum(): boolean {
        return ExternGeimpftUtil.showPositivGetestetDatum(this.formGroup, this.impfstoffOptions);
    }

    public getAnzahlMissingImpfungen(): MissingForGrundimmunisiertTS | undefined {
        return ExternGeimpftUtil.calculateAnzahlMissingImpfungen(this.formGroup);
    }

    private warnBeforeFinalSaveIfNeeded$(
        dashboardVorher: DashboardJaxTS,
        dashboardReloadedTest: DashboardJaxTS,
    ): Observable<any> {
        // vergleichen, ob beim Speichern der Termin oder die Odiwahl geloescht werden wuerde
        const willLoseOdi = FreigabeEntzugUtil.lostOdi(dashboardVorher, dashboardReloadedTest);
        const willLoseNichtVerwalteterOdi = FreigabeEntzugUtil.lostNichtVerwalteterOdi(dashboardVorher, dashboardReloadedTest);
        const willLoseTermine = FreigabeEntzugUtil.lostTermin(dashboardVorher, dashboardReloadedTest);

        if ((willLoseOdi || willLoseNichtVerwalteterOdi || willLoseTermine)) {
            // Bestaetigungsdialog oeffnen, wenn Termine/Odiwahl geloescht werden
            let message = '';
            if (willLoseOdi) {
                message = 'WILL_LOSE_ODI';
            } else if (willLoseTermine) {
                message = 'WILL_LOSE_TERMINE';
            } else if (willLoseNichtVerwalteterOdi) {
                message = 'WILL_LOSE_ODI_NICHT_VERWALTET';
            }
            const msgPrefix = 'ERKRANKUNGEN.' + message + '.';
            return ConfirmUtil.swalAsObservable$(
                Swal.fire({
                    icon: 'warning',
                    text: this.translate.instant(msgPrefix + 'CONFIRM_QUESTION', {
                        odi: dashboardVorher.gewuenschterOrtDerImpfung?.name,
                    }),
                    showCancelButton: true,
                    cancelButtonText: this.translate.instant(msgPrefix + 'CANCEL'),
                    confirmButtonText: this.translate.instant(msgPrefix + 'CONFIRM'),
                }));
        } else {
            // es passiert nichts Schlimmes, kein Dialog noetig
            return of(true);
        }
    }

    private informUserAboutLostFreigabe(dashboardVorher: DashboardJaxTS, dashboardReloaded: DashboardJaxTS): void {

        // Termin wurde annulliert
        if (FreigabeEntzugUtil.lostTermin(dashboardVorher, dashboardReloaded)) {
            this.informUser('ERKRANKUNGEN.TERMIN_ENTFERNT', undefined);
            return;
        }
        // Odiwahl wurde annulliert
        if (FreigabeEntzugUtil.lostOdi(dashboardVorher, dashboardReloaded)) {
            this.informUser('ERKRANKUNGEN.ODIWAHL_ENTFERNT', {
                odi: dashboardVorher.gewuenschterOrtDerImpfung?.name,
            });
            return;
        }
        // Nicht-verwalteter ODI wurde annuliert
        if (FreigabeEntzugUtil.lostNichtVerwalteterOdi(dashboardVorher, dashboardReloaded)) {
            this.informUser('ERKRANKUNGEN.ODIWAHL_ENTFERNT_NICHT_VERWALTET', undefined);
            return;
        }

        // Freigabe wurde entfernt, ohne dass bereits Termin oder Odi gewaehlt war
        if (FreigabeEntzugUtil.lostFreigabe(dashboardVorher, dashboardReloaded)) {
            this.informUser('ERKRANKUNGEN.FREIGABE_ENTFERNT', undefined);
            return;
        }
    }

    private informUser(messageKey: string, translateParams: object | undefined): void {
        const message = this.translate.instant(messageKey, translateParams);

        Swal.fire({
            icon: 'info',
            text: message,
            showConfirmButton: true,
            showCancelButton: false,
        });
        return;
    }
}
