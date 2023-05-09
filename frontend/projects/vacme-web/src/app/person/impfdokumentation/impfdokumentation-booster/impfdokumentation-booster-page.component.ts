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

import {DOCUMENT} from '@angular/common';
import {Component, Inject, OnInit} from '@angular/core';
import {ActivatedRoute, Data, Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {BehaviorSubject, Subject} from 'rxjs';
import {finalize} from 'rxjs/operators';
import Swal from 'sweetalert2/dist/sweetalert2.js';
import {
    DashboardJaxTS,
    DossierService,
    ImpfdokumentationJaxTS,
    ImpffolgeTS,
    ImpfstoffJaxTS,
    OrtDerImpfungDisplayNameJaxTS,
    OrtDerImpfungJaxTS,
    RegistrierungStatusTS,
    TerminbuchungService,
} from 'vacme-web-generated';
import {LogFactory} from 'vacme-web-shared';
import {ImpfdokumentationService} from '../../../../../../vacme-web-generated/src/lib/api/impfdokumentation.service';
import {
    BaseDestroyableComponent,
} from '../../../../../../vacme-web-shared/src/lib/components/base-destroyable/base-destroyable.component';
import {
    NextFreierTerminSearch,
} from '../../../../../../vacme-web-shared/src/lib/components/vacme-components/termine-bearbeiten/termine-bearbeiten.component';
import {DAILY_SELBSTZAHLER_POPUP} from '../../../../../../vacme-web-shared/src/lib/constants';
import ITermineTS from '../../../../../../vacme-web-shared/src/lib/model/ITermine';
import {ErrorMessageService} from '../../../../../../vacme-web-shared/src/lib/service/error-message.service';
import {TerminUtilService} from '../../../../../../vacme-web-shared/src/lib/service/termin-util.service';
import {TerminfindungService} from '../../../../../../vacme-web-shared/src/lib/service/terminfindung.service';
import {BlobUtil} from '../../../../../../vacme-web-shared/src/lib/util/BlobUtil';
import {BoosterUtil} from '../../../../../../vacme-web-shared/src/lib/util/booster-util';
import DateUtil from '../../../../../../vacme-web-shared/src/lib/util/DateUtil';
import TenantUtil from '../../../../../../vacme-web-shared/src/lib/util/TenantUtil';
import {ImpfdokumentationCacheService} from '../../../service/impfdokumentation.cache.service';
import {RegistrierungValidationService} from '../../../service/registrierung-validation.service';
import {TerminmanagementKontrolleImpfdokService} from '../../../service/terminmanagement-kontrolle-impfdok.service';
import {
    ImpfdokumentationFormBaseData,
    ImpfdokumentationFormSubmission,
} from '../impfdokumentation-form/impfdokumentation-form.component';

const LOG = LogFactory.createLog('ImpfdokumentationBoosterComponent');

@Component({
    selector: 'app-impfdokumentation-booster-page',
    templateUrl: './impfdokumentation-booster-page.component.html',
    styleUrls: ['./impfdokumentation-booster-page.component.scss'],
})
export class ImpfdokumentationBoosterPageComponent extends BaseDestroyableComponent implements OnInit {

    ortDerImpfungId: string | null = null;
    impffolge!: ImpffolgeTS;

    public saveInProgress = false;
    public formBaseData?: ImpfdokumentationFormBaseData;
    public odiList: OrtDerImpfungJaxTS[] = [];
    public dashboardJax?: DashboardJaxTS;
    public accessOk?: boolean; // zuerst undefined, dann true oder false
    private saved$ = new BehaviorSubject(false);
    private formDisabled$: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(false);
    private validateTrigger$: Subject<void> = new Subject<void>();
    private impfstoffe?: ImpfstoffJaxTS[];
    public impffolgeNr = 1;
    private canBeGrundimmunisierung?: boolean;
    private selbstzahlende?: boolean;
    public boosterUtil = BoosterUtil;

    // Termine umbuchen
    public modif = false;

    // ********** INIT ********************

    constructor(
        private activeRoute: ActivatedRoute,
        private router: Router,
        private impfdokumentationService: ImpfdokumentationService,
        private errorService: ErrorMessageService,
        private translationService: TranslateService,
        private impfdokumentationCacheService: ImpfdokumentationCacheService,
        private registrierungValidationService: RegistrierungValidationService,
        private terminbuchungService: TerminbuchungService,
        private dossierService: DossierService,
        private terminfindungService: TerminfindungService,
        private terminmanager: TerminmanagementKontrolleImpfdokService,
        @Inject(DOCUMENT) private document: Document,
    ) {
        super();
    }

    ngOnInit(): void {
        this.initFromActiveRoute();
    }

    private initFromActiveRoute(): void {
        this.activeRoute.data
            .pipe(this.takeUntilDestroyed())
            .subscribe(next => {
                this.onChangedParams(next);

            }, error => {
                LOG.error(error);
            });
    }

    private onChangedParams(next: Data): void {
        // Parameter laden
        this.modif = next.modif;
        this.canBeGrundimmunisierung = next.canBeGrundimmunisierung;
        this.selbstzahlende = next.selbstzahlende;

        if (next.data) {
            this.setDossier(next.data.dossier$);
            this.setImpfstoffe(next.data.impfstoffe$);
            this.odiList = next.data.odi$;
            this.setOdi();

            if (this.dashboardJax) {
                this.reinitializeFromDashboard(this.dashboardJax, false);
                this.impffolgeNr = TerminUtilService.determineImpffolgeNr(this.dashboardJax);
            }
        } else {
            this.errorService.addMesageAsError('IMPFDOK.ERROR.LOAD-DATA');
            this.accessOk = false;
        }
        this.formBaseData = {
            modif: this.modif,
            accessOk: this.accessOk,
            validate: this.validate,
            disable$: this.formDisabled$.asObservable(),
            saved$: this.saved$.asObservable(),
            validateTrigger$: this.validateTrigger$.asObservable(),
            dashboardJax: this.dashboardJax,
            impffolgeNr: this.impffolgeNr,
            impffolge: ImpffolgeTS.BOOSTER_IMPFUNG,
            impfstoffe: this.impfstoffe,
            odiList: this.odiList,
            canSelectGrundimmunisierung: !!this.canBeGrundimmunisierung,
            defaultGrundimmunisierung: false,
            selbstzahlende: this.selbstzahlende
        };
    }

    // Termine initialisieren und Validierung anstossen
    private reinitializeFromDashboard(dashboardJax: DashboardJaxTS, forceReload: boolean): void {
        this.terminmanager.reinitializeFromDashboard(dashboardJax, forceReload, this.impffolge, this);

        if (this.canShowValidierungen()) {
            this.validateTrigger$.next();
        }
    }

    private canShowValidierungen(): boolean {
        return !this.modif && !this.formDisabled$.value;
    }

    validate = (
        dashboard: DashboardJaxTS | undefined,
        impffolge: ImpffolgeTS,
        odiId: string | null,
        impfstoff: ImpfstoffJaxTS | undefined,
        odiList: OrtDerImpfungJaxTS[],
    ) => {
        // Interface ITerminTS will den Impfschutz direkt im Root
        const termineParam: ITermineTS = {...dashboard, impfschutzJax: dashboard?.impfdossier?.impfschutzJax};
        this.registrierungValidationService.validateTermineAndStatusAndOdiAndImpfstoffAndUserodi(
            termineParam,
            impffolge,
            odiId,
            impfstoff,
            odiList,
        );
    };

    // ********** DIVERSE GETTER/SETTER ********************
    private setOdi(): void {
        this.ortDerImpfungId = this.terminmanager.identifyOdiIdToSelect(this.odiList);
    }

    selectOdi(odiId: string | null): void {
        this.ortDerImpfungId = odiId;
    }

    title(): string {
        return this.translationService.instant('IMPFDOK.TITLE', {i: this.impffolgeNr});
    }

    private setImpfstoffe(data: ImpfstoffJaxTS[]): void {
        this.impfstoffe = data;
    }

    private setDossier(data: DashboardJaxTS): void {
        this.dashboardJax = data;
        if (!this.dashboardJax) {
            this.accessOk = false;
        } else {
            switch (this.dashboardJax.status) {
                case RegistrierungStatusTS.KONTROLLIERT_BOOSTER:
                    this.impffolge = ImpffolgeTS.BOOSTER_IMPFUNG;
                    this.accessOk = true;
                    break;
                default:
                    this.errorService.addMesageAsError('IMPFDOK.ERROR.WRONG-STATUS');
                    this.accessOk = false;
                    break;
            }
            // Wir nehmen den ODI was im Termin steht. Mit der Annahme, dass die Kontrolle das vorher validiert hat.
        }
    }

    private disableForm(): void {
        this.formDisabled$.next(true);
    }

    // ********** SAVE ********************

    public canSave(): boolean {
        return !this.saved$;
    }

    save(formSubmission: ImpfdokumentationFormSubmission): void {
        const impfdokumentation = formSubmission.impfdokumentation;
        if (this.saveInProgress) {
            LOG.info('saveImpfdokumentation does nothing because save is already in progress');
            return;
        }
        this.saveInProgress = true;
        this.impfdokumentationService.impfdokumentationResourceSaveImpfung(
            // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
            this.impffolge, this.ortDerImpfungId!, impfdokumentation)
            .pipe(
                finalize(() => this.saveInProgress = false),
            )
            .subscribe(() => this.onSaved(impfdokumentation),
                error => LOG.error(error));
    }

    private onSaved(impfdokumentation: ImpfdokumentationJaxTS): void {
        this.saved$.next(true);
        this.disableForm();
        this.impfdokumentationCacheService.cacheImpfdokumentation(impfdokumentation, this.ortDerImpfungId as string);
        this.print();
        this.dossierService.dossierResourceGetDashboardRegistrierung(impfdokumentation.registrierungsnummer as string)
            .subscribe(loadedDossier => {
                this.dashboardJax = loadedDossier;
                this.reinitializeFromDashboard(loadedDossier, true);
            }, error => LOG.error(error));
    }

    // ********** BACK ********************

    public back(): void {
        this.router.navigate(['']);
    }

    confirmAndResetToKontrolle(): void {
        Swal.fire({
            icon: 'question',
            text: this.translationService.instant('IMPFDOK.ZURUEK_ZU_KONTROLLE.CONFIRM'),
            showCancelButton: true,
            cancelButtonText: this.translationService.instant('IMPFDOK.ZURUEK_ZU_KONTROLLE.CONFIRM_CANCEL'),
            confirmButtonText: this.translationService.instant('IMPFDOK.ZURUEK_ZU_KONTROLLE.CONFIRM_ZURUEK'),
        }).then(r => {
            if (r.isConfirmed) {
                this.resetToKontrolle();
            }
        });
    }

    resetToKontrolle(): void {
        this.impfdokumentationService.impfdokumentationResourceImpfungVerweigert(
            this.dashboardJax?.registrierungsnummer as string)
            .subscribe(
                () => this.back(),
                error => LOG.error(error));
    }

    // ********** PRINT ********************

    print(): void {
        this.dossierService.dossierResourceDownloadImpfdokumentation(
            this.dashboardJax?.registrierungsnummer as string).subscribe(
            res => {
                BlobUtil.openInNewTab(res, this.document);
            },
            error => {
                LOG.error(error);
            });
    }

    // ********** TERMINE UMBUCHEN ********************

    exitTerminumbuchung($event: string): void {
        if ($event && this.dashboardJax) {
            this.reinitializeFromDashboard(this.dashboardJax, true);
        }
        this.reloadPage(false,  'termine-anchor');
    }

    public isUserBerechtigtForOdiOfTermin(): boolean {
        if (!this.dashboardJax?.terminNPending) {
            return true; // wenn kein TerminN vorhanden kann auf Boosterimpfungpage sicher umgebucht werden
        }

        const odiIdOfTerminN = this.dashboardJax.terminNPending.impfslot?.ortDerImpfung?.id;
        const found = this.findOdiByIdFromList(odiIdOfTerminN);
        return !!found;

    }

    private getSelectedOdi(): OrtDerImpfungDisplayNameJaxTS | undefined {
        return this.odiList?.find((k: OrtDerImpfungDisplayNameJaxTS) => k.id === this.ortDerImpfungId);
    }

    private findOdiByIdFromList(odiIdToLookFor: string | undefined): OrtDerImpfungDisplayNameJaxTS | undefined {
        return this.odiList?.find(value => value.id === odiIdToLookFor);
    }

    private setOrtDerImpfungInTerminfindungService(): void {
        if (this.ortDerImpfungId !== null && this.ortDerImpfungId !== undefined) {
            this.terminfindungService.ortDerImpfung = this.findOdiByIdFromList(this.ortDerImpfungId);
        }
    }

    public termineUmbuchen(): void {
        this.terminmanager.canProceedTermineUmbuchen$(this.isUserBerechtigtForOdiOfTermin())
            .subscribe(canProceed => {
                if (canProceed) {
                    this.reloadPage(true,  'terminbuchung-anchor');
                    this.setOrtDerImpfungInTerminfindungService();
                }
            }, error => {
                LOG.error(error);
            });
    }

    private reloadPage(
        modifFlag: boolean,
        anchor: 'terminbuchung-anchor' | 'buttons-anchor' | 'termine-anchor',
    ): void {
        this.router.navigate(
            [],
            {
                relativeTo: this.activeRoute,
                queryParams: {modif: modifFlag},
                fragment: anchor,
            });
    }

    public updateAppointment(registrierungsNummer: string): void {
        this.updateAppointmentNonAdhocTermin(registrierungsNummer);

    }



    private updateAppointmentNonAdhocTermin(registrierungsNummer: string): void {
        this.terminmanager.umbuchungRequestPut$(this.impffolge, registrierungsNummer)
            .subscribe(resultDashboard => {
                this.dashboardJax = resultDashboard;
                this.reinitializeFromDashboard(resultDashboard, true);
                this.exitTerminumbuchung(registrierungsNummer);
            }, (error => {
                LOG.error(error);
            }));
    }

    public cancelAppointment($event: string): void {
        this.terminmanager.confirmTerminAbsagen(this.impffolge).then(r => {
            if (r.isConfirmed) {
                this.terminbuchungService
                    .terminbuchungResourceTerminAbsagen($event)
                    .subscribe(resultDashboard => {
                        this.dashboardJax = resultDashboard;
                        this.reinitializeFromDashboard(resultDashboard, true);
                        this.exitTerminumbuchung($event);
                    }, (error => {
                        LOG.error(error);
                    }));
            }
        });
    }

    public gotoNextFreienTermin($event: NextFreierTerminSearch): void {
        this.terminmanager.gotoNextFreienTermin('impfdokumentation/booster', $event);
    }

    public showTerminumbuchungButtons(): boolean {
        if (this.saved$.value) {
            return false;
        }
        const selectedOdi = this.getSelectedOdi();
        if (selectedOdi) {
            return !!selectedOdi.terminverwaltung;
        }
        return true;
    }

}
