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
import {Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import * as moment from 'moment';
// falsch: import Swal from 'sweetalert2'; // Hier wird nicht nur das JS, sondern auch das CSS importiert
import Swal from 'sweetalert2/dist/sweetalert2.js';
import {DashboardJaxTS, RegistrierungStatusTS} from 'vacme-web-generated';
import {LogFactory} from 'vacme-web-shared';
import {NBR_HOURS_TO_SHOW_IMPFDETAILS} from '../../../../vacme-web-shared/src/lib/constants';
import {TSRole} from '../../../../vacme-web-shared/src/lib/model';
import {AuthServiceRsService} from '../../../../vacme-web-shared/src/lib/service/auth-service-rs.service';
import DateUtil from '../../../../vacme-web-shared/src/lib/util/DateUtil'; // nur das JS importieren

const LOG = LogFactory.createLog('NavigationService');

@Injectable({
    providedIn: 'root',
})
export class NavigationService {

    constructor(
        private router: Router,
        private authServiceRS: AuthServiceRsService,
        private translationService: TranslateService,
    ) {
    }

    public navigate(dossier: DashboardJaxTS): void {
        if (!dossier) {
            this.notFoundResult();
        }
        switch (dossier.status) {
            case RegistrierungStatusTS.ABGESCHLOSSEN_OHNE_ZWEITE_IMPFUNG:
            case RegistrierungStatusTS.AUTOMATISCH_ABGESCHLOSSEN:
            case RegistrierungStatusTS.ABGESCHLOSSEN:
            case RegistrierungStatusTS.IMPFUNG_2_DURCHGEFUEHRT:
            case RegistrierungStatusTS.IMMUNISIERT:
                this.toGeimpft(dossier.registrierungsnummer as string);
                break;
            case RegistrierungStatusTS.IMPFUNG_1_DURCHGEFUEHRT:
                if (this.isNachdokumentationWithoutImpfungRole()) {
                    this.toGeimpft(dossier.registrierungsnummer as string);
                    break;
                }
                if (dossier.impfung1?.timestampImpfung) {
                    // Wenn die Impfung weniger als 24h her ist:  Impfdetails anzeigen
                    const hoursDiff = DateUtil.getHoursDiff(moment(dossier.impfung1?.timestampImpfung), DateUtil.now());
                    if (hoursDiff < NBR_HOURS_TO_SHOW_IMPFDETAILS) {
                        this.toGeimpft(dossier.registrierungsnummer as string);
                        break;
                    }
                }
                // Sonst normal auf die Kontrolle
                this.toKontrolle2(dossier.registrierungsnummer as string);
                break;
            case RegistrierungStatusTS.IMPFUNG_1_KONTROLLIERT:
                if (!this.isBerechtigtForImpfdokumentation()) {
                    this.showInfoBereitsKontrolliert(dossier);
                } else {
                    this.toImpfung1(dossier.registrierungsnummer as string);
                }
                break;
            case RegistrierungStatusTS.IMPFUNG_2_KONTROLLIERT:
                if (this.isNachdokumentationWithoutImpfungRole()) {
                    this.toGeimpft(dossier.registrierungsnummer as string);
                    break;
                }
                if (!this.isBerechtigtForImpfdokumentation()) {
                    this.showInfoBereitsKontrolliert(dossier);
                } else {
                    this.toImpfung2(dossier.registrierungsnummer as string);
                }
                break;
            case RegistrierungStatusTS.ODI_GEWAEHLT:
            case RegistrierungStatusTS.REGISTRIERT:
            case RegistrierungStatusTS.FREIGEGEBEN:
            case RegistrierungStatusTS.GEBUCHT:
                if (this.isNachdokumentationWithoutImpfungRole()) {
                    this.toGeimpft(dossier.registrierungsnummer as string);
                    break;
                }
                this.toKontrolle1(dossier.registrierungsnummer as string);
                break;
            case RegistrierungStatusTS.GEBUCHT_BOOSTER:
            case RegistrierungStatusTS.ODI_GEWAEHLT_BOOSTER:
                if (this.isNachdokumentationWithoutImpfungRole()) {
                    this.toGeimpft(dossier.registrierungsnummer as string);
                    break;
                }
                this.toKontrolleBooster(dossier.registrierungsnummer as string, dossier);
                break;
            case RegistrierungStatusTS.FREIGEGEBEN_BOOSTER:
                this.toGeimpft(dossier.registrierungsnummer as string);
                break;
            case RegistrierungStatusTS.KONTROLLIERT_BOOSTER:

                if (this.isNachdokumentationWithoutImpfungRole()) {
                    this.toGeimpft(dossier.registrierungsnummer as string);
                    break;
                }
                if (!this.isBerechtigtForImpfdokumentation()) {
                    this.showInfoBereitsKontrolliert(dossier);
                } else {
                    this.toImpfungBooster(dossier.registrierungsnummer as string, dossier);
                }
                break;
            default:
                throw Error('Nicht behandelter Status: ' + dossier.status);
        }
    }

    private isNachdokumentationWithoutImpfungRole(): boolean {
        return this.authServiceRS.isOneOfRoles([
                TSRole.KT_NACHDOKUMENTATION, TSRole.KT_MEDIZINISCHE_NACHDOKUMENTATION]) &&
            !this.authServiceRS.isOneOfRoles([
                TSRole.OI_IMPFVERANTWORTUNG, TSRole.OI_DOKUMENTATION, TSRole.OI_KONTROLLE, TSRole.KT_IMPFDOKUMENTATION]);
    }

    public notFoundResult(): void {
        this.router.navigate(['startseite'], {queryParams: {err: 'ERR_NO_IMPFABLE_PERS'}});
    }

    private toImpfung1(registrierungsnummer: string): void {
        this.redirectToImpfdokumentation(registrierungsnummer);
    }

    private toImpfung2(registrierungsnummer: string): void {
        this.redirectToImpfdokumentation(registrierungsnummer);
    }

    private toImpfungBooster(registrierungsnummer: string, dashboardJax: DashboardJaxTS): void {
        this.redirectToImpfdokumentation(registrierungsnummer, true);
    }

    /**
     * Diese Funktion forciert ein navigieren auf eine eige andere Seite wenn man von
     * impfdokumentation wieder auf impfdokumentation navigiert (e.g. wenn man von dort eine Person sucht
     * die ebenfalls grad ans impfen kommt wie bspw beim Massenupload). Grund dafuer ist, dass wir
     * die Page in diesem Fall neu aufbauen wollen (damit sie wieder enabled ist etc)
     *
     * Damit wir das aber nicht machen wenn nicht noetig pruefen wir wo wir hergekommen sind
     *
     * @param registrierungsnummer die reg auf deren impfdokumentation navigiert wird
     */
    private redirectToImpfdokumentation(registrierungsnummer: string, booster?: boolean): void {
        if (this.router.url.includes('/impfdokumentation')) {
            this.router.navigateByUrl('/', {skipLocationChange: true}).then(() => {
                LOG.info('forced reload of component during navigation');
                this.redirectToImpfdokumentationBasic(registrierungsnummer, booster);
            });
        } else {
            this.redirectToImpfdokumentationBasic(registrierungsnummer, booster);
        }
    }

    private redirectToImpfdokumentationBasic(registrierungsnummer: string, booster?: boolean): void {
        if (booster) {
            this.router.navigate(['person', registrierungsnummer, 'impfdokumentation', 'booster']);
        } else {
            this.router.navigate(['person', registrierungsnummer, 'impfdokumentation']);
        }
    }

    private toKontrolle1(registrierungsnummer: string): void {
        this.router.navigate(['person', registrierungsnummer, 'kontrolle']);
    }

    private toKontrolle2(registrierungsnummer: string): void {
        this.router.navigate(['person', registrierungsnummer, 'kontrolle']);
    }

    private toKontrolleBooster(registrierungsnummer: string, dashboardJax: DashboardJaxTS): void {
        this.router.navigate(['person', registrierungsnummer, 'kontrolle']);
    }

    private toGeimpft(registrierungsnummer: string): void {
        this.router.navigate(['person', registrierungsnummer, 'geimpft']);
    }

    private showInfoBereitsKontrolliert(dossier: DashboardJaxTS): void {
        Swal.fire({
            icon: 'info',
            text: this.translationService.instant('NAVIGATION_SERVICE.PERSON_BEREITS_KONTROLLIERT',
                {person: this.getPersonInfos(dossier)}),
            showConfirmButton: true
        });
    }

    private getPersonInfos(dashboardJax: DashboardJaxTS): string {
        const result = dashboardJax.registrierungsnummer
            + ' - ' + dashboardJax.name
            + ' ' + dashboardJax.vorname
            + ' (' + this.geburtsdatumAsString(dashboardJax) + ') ';

        return result;
    }

    private geburtsdatumAsString(dashboardJax: DashboardJaxTS): string {
        if (dashboardJax && dashboardJax.geburtsdatum) {
            const moment1 = moment(dashboardJax.geburtsdatum);
            if (moment1) {
                const gebDat = DateUtil.momentToLocalDateFormat(moment1, 'DD.MM.YYYY');
                if (gebDat) {
                    return gebDat;
                }
            }
        }
        return '';
    }

    private isBerechtigtForImpfdokumentation(): boolean {
        return this.authServiceRS.isOneOfRoles([TSRole.OI_DOKUMENTATION, TSRole.KT_IMPFDOKUMENTATION]);
    }
}
