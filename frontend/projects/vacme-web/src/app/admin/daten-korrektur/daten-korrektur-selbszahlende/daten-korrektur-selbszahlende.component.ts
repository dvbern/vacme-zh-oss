import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {FormBuilder, FormGroup, Validators} from '@angular/forms';
import {TranslateService} from '@ngx-translate/core';
import Swal from 'sweetalert2/dist/sweetalert2.js';
import {DashboardJaxTS, ImpfungSelbstzahlendeKorrekturJaxTS, KorrekturService} from 'vacme-web-generated';
import {LogFactory} from 'vacme-web-shared';
import {Option} from '../../../../../../vacme-web-shared/src/lib/components/form-controls/input-select/option';
import {AuthServiceRsService} from '../../../../../../vacme-web-shared/src/lib/service/auth-service-rs.service';
import FormUtil from '../../../../../../vacme-web-shared/src/lib/util/FormUtil';
import {isAtLeastOnceGeimpft} from '../../../../../../vacme-web-shared/src/lib/util/registrierung-status-utils';
import DatenKorrekturService from '../daten-korrektur.service';
import {getAllowdRoles, TSDatenKorrekturTyp} from '../TSDatenKorrekturTyp';

const LOG = LogFactory.createLog('DatenKorrekturSelbszahlendeComponent');

@Component({
    selector: 'app-daten-korrektur-selbszahlende',
    templateUrl: './daten-korrektur-selbszahlende.component.html',
})
export class DatenKorrekturSelbszahlendeComponent implements OnInit {
    @Input()
    dashboardJax!: DashboardJaxTS | undefined;

    @Output()
    public finished = new EventEmitter<boolean>();

    public impfungenListOptions: Option[] = [];
    public selbstzahlendeOptions: Option[] = [
        {label: 'SELBSTZAHLENDE', value: true},
        {label: 'IMPFKAMPAGNE', value: false},
    ];
    public formGroup!: FormGroup;

    constructor(
        private authService: AuthServiceRsService,
        private datenKorrekturUtil: DatenKorrekturService,
        private fb: FormBuilder,
        private korrekturService: KorrekturService,
        private translationService: TranslateService,
    ) {
    }

    ngOnInit(): void {
        this.formGroup = this.fb.group({
            impfung: this.fb.control(null, [Validators.required]),
            selbstzahlende: this.fb.control(null, Validators.required),
        });
    }

    public correctIfValid(): void {
        if (this.hasRequiredRole()) {
            FormUtil.doIfValid(this.formGroup, () => {
                this.selbstzahlendeKorrigieren();
            });
        }
    }

    public availableImpfungAndImpffolgeOptions(): Option[] {
        if (!this.impfungenListOptions?.length) {
            this.impfungenListOptions = this.datenKorrekturUtil.availableImpfungAndImpffolgeOptions(this.dashboardJax);
        }
        return this.impfungenListOptions;
    }

    public hasRequiredRole(): boolean {
        return this.authService.isOneOfRoles(getAllowdRoles(TSDatenKorrekturTyp.SELBSTZAHLENDE));
    }

    public reset(): void {
        this.dashboardJax = undefined;
        this.formGroup.reset();
        this.finished.emit(false);
    }

    private selbstzahlendeKorrigieren() {
        const data: ImpfungSelbstzahlendeKorrekturJaxTS = {
            impffolge: this.formGroup.get('impfung')?.value.impffolge,
            impffolgeNr: this.formGroup.get('impfung')?.value.impffolgeNr,
            selbstzahlende: this.formGroup.get('selbstzahlende')?.value,
        };
        const regNummer = this.dashboardJax?.registrierungsnummer;
        if (!regNummer || !data) {
            return;
        }
        this.korrekturService.korrekturResourceImpfungSelbstzahlendeKorrigieren(regNummer, data).subscribe(() => {
            Swal.fire({
                icon: 'success',
                text: this.translationService.instant('FACH-ADMIN.DATEN_KORREKTUR.SUCCESS'),
                showConfirmButton: true,
            }).then(() => {
                this.dashboardJax = undefined;
                this.formGroup.reset();
                this.finished.emit(true);
            });
        }, err => {
            LOG.error('Could not update Date of Impfung', err);
        });
    }

    public enabled(): boolean {
        if (this.dashboardJax?.registrierungsnummer) {
            return isAtLeastOnceGeimpft(this.dashboardJax.status);
        }
        return false;
    }
}
