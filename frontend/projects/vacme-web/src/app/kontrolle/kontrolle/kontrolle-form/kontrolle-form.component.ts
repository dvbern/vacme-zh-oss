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
import {
    ChangeDetectorRef,
    Component,
    EventEmitter,
    Input,
    OnChanges,
    OnDestroy,
    OnInit,
    Output,
    SimpleChanges,
} from '@angular/core';
import {FormBuilder, FormControl, FormGroup, Validators} from '@angular/forms';
import {TranslateService} from '@ngx-translate/core';
import {Observable, of, Subject} from 'rxjs';
/* eslint-disable-next-line */
import {concatMap, first, tap} from 'rxjs/operators';
// falsch: import Swal from 'sweetalert2'; // Hier wird nicht nur das JS, sondern auch das CSS importiert
import Swal from 'sweetalert2/dist/sweetalert2.js'; // nur das JS importieren
import {
    ChronischeKrankheitenTS,
    ExternGeimpftJaxTS,
    GeschlechtTS, ImpfdossierJaxTS,
    ImpfkontrolleJaxTS,
    ImpfstoffJaxTS,
    KrankenkasseTS,
    LebensumstaendeTS,
    OrtDerImpfungDisplayNameJaxTS,
    StammdatenService,
} from 'vacme-web-generated';
import {LogFactory} from 'vacme-web-shared';
import {ImpfdokumentationService} from '../../../../../../vacme-web-generated/src/lib/api/impfdokumentation.service';
import {Option} from '../../../../../../vacme-web-shared/src/lib/components/form-controls/input-select/option';
import {
    DATE_PATTERN,
    DB_DEFAULT_MAX_LENGTH,
    EMAIL_PATTERN,
    MAX_ALTER_IMPFLING,
    MAX_LENGTH_TEXTAREA,
    MIN_ALTER_IMPFLING,
} from '../../../../../../vacme-web-shared/src/lib/constants';
import {TSRole} from '../../../../../../vacme-web-shared/src/lib/model';
import {AuthServiceRsService} from '../../../../../../vacme-web-shared/src/lib/service/auth-service-rs.service';
import {BerufUtil} from '../../../../../../vacme-web-shared/src/lib/util/beruf-util';
import {ConfirmUtil} from '../../../../../../vacme-web-shared/src/lib/util/confirm-util';
import {
    certifiableNamestringValidator,
} from '../../../../../../vacme-web-shared/src/lib/util/customvalidator/certifiable-namestring-validator';
import {
    datumInPastValidator,
} from '../../../../../../vacme-web-shared/src/lib/util/customvalidator/datum-in-past-validator';
import {
    parsableDateValidator,
} from '../../../../../../vacme-web-shared/src/lib/util/customvalidator/parsable-date-validator';
import {
    validPhoneNumberValidator,
} from '../../../../../../vacme-web-shared/src/lib/util/customvalidator/phone-number-validator';
import DateUtil from '../../../../../../vacme-web-shared/src/lib/util/DateUtil';
import {ExternGeimpftUtil} from '../../../../../../vacme-web-shared/src/lib/util/externgeimpft-util';
import FormUtil from '../../../../../../vacme-web-shared/src/lib/util/FormUtil';
import {
    KrankenkasseEnumInterface,
    KrankenkasseUtil,
} from '../../../../../../vacme-web-shared/src/lib/util/krankenkasse-util';
import {isAnyStatusOfBooster} from '../../../../../../vacme-web-shared/src/lib/util/registrierung-status-utils';
import TenantUtil from '../../../../../../vacme-web-shared/src/lib/util/TenantUtil';

const LOG = LogFactory.createLog('KontrolleFormComponent');

@Component({
    selector: 'app-kontrolle-form',
    templateUrl: './kontrolle-form.component.html',
    styleUrls: ['./kontrolle-form.component.scss'],
})
export class KontrolleFormComponent implements OnInit, OnChanges, OnDestroy {

    private ngUnsubscribe$ = new Subject();
    @Input() newPerson?: boolean;
    @Input() showExternGeimpft = true;
    @Input() public ortDerImpfungList: OrtDerImpfungDisplayNameJaxTS[] = [];

    public formGroup!: FormGroup;
    public impfkontrolle: ImpfkontrolleJaxTS | undefined;

    @Input() canSave = false;
    @Output() saveEvent = new EventEmitter<void>();
    @Output() saveFalschePersonEvent = new EventEmitter<void>();

    @Output() navigateToImpfdokumentationEvent = new EventEmitter<void>();

    @Output() navigateBackEvent = new EventEmitter<void>();

    @Output() finishedLoading = new EventEmitter<void>();

    @Input() public showTelefonHinweis = false;

    public krankenkassen!: KrankenkasseEnumInterface[];
    public krankenkassenSelected = false;
    public krankheitenOptions = Object.values(ChronischeKrankheitenTS).map(t => {
        return {label: t, value: t};
    });
    public beruflicheTaetigkeit = TenantUtil.getBeruflicheTaetigkeit().map(t => {
        return {label: t, value: t};
    });
    public lebensumstaende = Object.values(LebensumstaendeTS).map(t => {
        return {label: t, value: t};
    });

    public kkUtil = KrankenkasseUtil;
    public geschlechtOptions = Object.values(GeschlechtTS).map(t => {
        return {label: t, value: t};
    });
    public impfstoffOptions!: Option[];

    public selbstzahlendeOptions: Option[] = [
        {label: 'SELBSTZAHLENDE', value: true},
        {label: 'IMPFKAMPAGNE', value: false},
    ];

    constructor(
        private fb: FormBuilder,
        private datePipe: DatePipe,
        private translationService: TranslateService,
        private stammdatenService: StammdatenService,
        private impfdokumentationService: ImpfdokumentationService,
        private authServiceRS: AuthServiceRsService,
        private cdRef: ChangeDetectorRef,
    ) {
    }

    ngOnInit(): void {
        this.loadStammdatenAndSetupForm();
    }

    public ngOnChanges(changes: SimpleChanges): void {
        this.cdRef.detectChanges();
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe$.next();
        this.ngUnsubscribe$.complete();
    }

    private loadStammdaten$(): Observable<any> {
        return this.loadKrankenkassen$().pipe(
            concatMap(() => this.loadImpfstoffe$()));
    }

    private loadStammdatenAndSetupForm(): void {
        this.loadStammdaten$()
            .subscribe(
                () => {
                    this.formGroup = this.setupForm(this.impfstoffOptions);
                    LOG.info('finished loading');
                    this.finishedLoading.emit();
                    this.cdRef.detectChanges(); // sonst wird externes Zertifikat-Formular nicht angezeigt
                }, error => {
                    LOG.info('error during stammdaten loading', error);
                    this.disableAllFields();
                });
    }

    private loadKrankenkassen$(): Observable<KrankenkasseTS> {
        // Krankenkassenliste
        LOG.info('loading krankenkassen');
        return this.stammdatenService.stammdatenResourceGetKrankenkassen()
            .pipe(
                first(),
                tap(
                    (krankenkasseList: any) => {
                        LOG.info('loaded krankenkassen');
                        this.krankenkassen = krankenkasseList;
                    },
                    (error: any) => {
                        LOG.error(error);
                        Swal.fire({
                            icon: 'warning',
                            text: this.translationService.instant('FACH-APP.KONTROLLE.ERROR_LOADING_KK'),
                            showCancelButton: false,
                        });
                    }));
    }

    private loadImpfstoffe$(): Observable<ImpfstoffJaxTS[]> {
        // Impfstoffliste (nur fuer externesZertifikat)
        if (!this.impfstoffOptions) {
            LOG.info('loading impfstoffe');
            return this.stammdatenService.stammdatenResourceGetAlleImpfstoffeForExternGeimpft()
                .pipe(
                    first(),
                    tap((list: ImpfstoffJaxTS[]) => {
                        LOG.info('loaded impfstoffe');
                        this.impfstoffOptions = list.map(impfstoff => {
                            const impfstoffOption: Option =
                                {
                                    label: impfstoff.displayName,
                                    value: impfstoff,
                                };
                            return impfstoffOption;
                        });
                    }, error => {
                        LOG.error(error);
                        Swal.fire({
                            icon: 'warning',
                            text: this.translationService.instant('FACH-APP.KONTROLLE.ERROR_LOADING_IMPFSTOFFE'),
                            showCancelButton: false,
                        });
                    }));
        } else {
            return of([]);
        }
    }

    public patchValue(impfkontrolle: ImpfkontrolleJaxTS, impfdossier?: ImpfdossierJaxTS | undefined): void {
        this.impfkontrolle = impfkontrolle;
        this.formGroup.patchValue(impfkontrolle);
        // @ts-ignore
        this.formGroup.get('krankenkasse')?.setValue(impfkontrolle.krankenkasse.name);
        // Die Krankenkassen Nummer-Box korrekt enabled/disabled
        this.krankenkasseDisableIfOhnePrefix();
        this.formGroup.get('geburtsdatum')?.setValue(
            // at midday to avoid timezone issues
            this.datePipe.transform(impfkontrolle.geburtsdatum?.setHours(12), 'dd.MM.yyyy'),
        );
        // verstecktes ExternGeimpft: disablen, sonst kann man nicht mehr speichern
        if (!this.showExternGeimpft) {
            this.externGeimpftFormGroup()?.disable();
        }
        // Auf Kontrolle soll auf keinen Fall etwas vorausgefuellt werden, auch wenn es im Server noch gespeichert ist
        this.formGroup.get('impfungkontrolleTermin')?.get('selbstzahlende')?.setValue(undefined);
    }

    private setupForm(impfstoffOptions: Option[]): FormGroup {
        const minLength = 2;
        const theFormGroup = this.fb.group({
            geschlecht: this.fb.control(undefined,
                [Validators.minLength(minLength), Validators.maxLength(DB_DEFAULT_MAX_LENGTH), Validators.required]),
            name: this.fb.control(undefined,
                [
                    Validators.minLength(minLength), Validators.maxLength(DB_DEFAULT_MAX_LENGTH), Validators.required,
                    certifiableNamestringValidator(),
                ]),
            vorname: this.fb.control(undefined,
                [
                    Validators.minLength(minLength), Validators.maxLength(DB_DEFAULT_MAX_LENGTH), Validators.required,
                    certifiableNamestringValidator(),
                ]),

            adresse: this.fb.group({
                    adresse1: this.fb.control(undefined,
                        [
                            Validators.minLength(minLength),
                            Validators.maxLength(DB_DEFAULT_MAX_LENGTH),
                            Validators.required,
                        ]),
                    plz: this.fb.control(undefined, Validators.required),
                    ort: this.fb.control(undefined,
                        [
                            Validators.minLength(minLength),
                            Validators.maxLength(DB_DEFAULT_MAX_LENGTH),
                            Validators.required,
                        ]),

                },
            ),
            impfungkontrolleTermin: this.fb.group({
                bemerkung: this.fb.control(undefined, [Validators.maxLength(MAX_LENGTH_TEXTAREA)]),
                identitaetGeprueft: this.fb.control(undefined),
                selbstzahlende: this.fb.control(undefined, Validators.required),
            }),
            immobil: this.fb.control(false),
            mail: this.fb.control(undefined,
                [
                    Validators.minLength(minLength),
                    Validators.maxLength(DB_DEFAULT_MAX_LENGTH),
                    Validators.pattern(EMAIL_PATTERN),
                ]),
            telefon: this.fb.control(undefined,
                [
                    Validators.minLength(minLength),
                    Validators.maxLength(DB_DEFAULT_MAX_LENGTH),
                    Validators.required,
                    validPhoneNumberValidator(),
                ]),
            identifikationsnummer: this.fb.control(undefined,
                [Validators.minLength(minLength), Validators.maxLength(DB_DEFAULT_MAX_LENGTH)]),
            krankenkasse: this.fb.control(undefined,
                [Validators.minLength(minLength), Validators.maxLength(DB_DEFAULT_MAX_LENGTH), Validators.required]),
            krankenkasseKartenNr: this.fb.control(undefined,
                [Validators.minLength(20), Validators.maxLength(20), Validators.required]),
            auslandArt: this.fb.control(undefined,
                [KrankenkasseUtil.createAuslandArtValidator(this, 'krankenkasse')]),
            schutzstatus: this.fb.control(undefined),
            geburtsdatum: this.fb.control(undefined,
                [
                    Validators.minLength(minLength),
                    Validators.maxLength(DB_DEFAULT_MAX_LENGTH),
                    Validators.pattern(DATE_PATTERN),
                    Validators.required,
                    parsableDateValidator(),
                    datumInPastValidator(),
                ]),
            verstorben: this.fb.control(undefined),
            bemerkung: this.fb.control(undefined, Validators.maxLength(MAX_LENGTH_TEXTAREA)),
            chronischeKrankheiten: this.fb.control(undefined, Validators.required),
            lebensumstaende: this.fb.control(undefined, Validators.required),
            beruflicheTaetigkeit: this.fb.control(undefined, Validators.required),
            abgleichElektronischerImpfausweis: this.fb.control(false),
            contactTracing: this.fb.control(false),
            ampelColor: this.fb.control(undefined, Validators.required),
            externGeimpft: ExternGeimpftUtil.createFormgroup(
                this.fb, true, this.ngUnsubscribe$, impfstoffOptions),
            keinKontakt: this.fb.control(undefined),
        });

        BerufUtil.addBerufAutoselectForChildren(theFormGroup, 'geburtsdatum', 'beruflicheTaetigkeit');
        ConfirmUtil.addCheckboxAreYouSureWarning(theFormGroup,
            'verstorben',
            this.translationService,
            'FACH-APP.KONTROLLE.VERSTORBEN');
        return theFormGroup;
    }

    krankenkasseSelectChange($event: any): void {
        KrankenkasseUtil.onKrankenkasseChange(this, 'krankenkasse', 'krankenkasseKartenNr', 'auslandArt');
    }

    krankenkasseDisableIfOhnePrefix(): void {
        KrankenkasseUtil.krankenkasseDisableIfOhnePrefix(this, 'krankenkasse', 'krankenkasseKartenNr');
    }

    disableFields(): void {
        this.formGroup.get('lebensumstaende')?.disable();
        this.formGroup.get('beruflicheTaetigkeit')?.disable();
        this.formGroup.get('ampelColor')?.disable();
        this.formGroup.get('chronischeKrankheiten')?.disable();
    }

    disablePhoneNumber(): void {
        this.formGroup.get('telefon')?.disable();
        this.showTelefonHinweis = true;
    }

    disableAllFields(): void {
        this.formGroup?.disable();
    }

    adresseFormGroup(): FormGroup {
        return this.formGroup.get('adresse') as FormGroup;
    }

    ampelFormGroup(): FormControl {
        return this.formGroup.get('ampelColor') as FormControl;
    }

    externGeimpftFormGroup(): FormGroup {
        return this.formGroup.get('externGeimpft') as FormGroup;
    }

    getKontrolleGroup(name: string): FormGroup {
        return this.formGroup.get(name) as FormGroup;
    }

    hasMobileOrtDerImpfung(): boolean {
        return TenantUtil.hasMobilerOrtDerImpfungAdministration();
    }

    hasContactTracingEnabled(): boolean {
        return TenantUtil.hasContactTracing();
    }

    // TODO das sollte ein Validator auf der Ampel sein, damit es als Validierung angezeigt wird
    isAmpelRed(): boolean {
        return this.ampelFormGroup().value === 'RED';
    }

    public showMobilConfirmation(): void {
        const immobilControl = this.formGroup.controls.immobil;

        if (!immobilControl.value) {
            Swal.fire({
                icon: 'question',
                text: this.translationService.instant('FACH-APP.KONTROLLE.MOBIL_CONFIRMATION'),
                showCancelButton: true,
                confirmButtonText: this.translationService.instant('CONFIRMATION.YES'),
                cancelButtonText: this.translationService.instant('CONFIRMATION.NO'),
            }).then(r => {
                if (!r.isConfirmed) {
                    immobilControl.setValue(true);
                }
            });
        }
        // warnung wenn auf Kontrolle jemand neu auf immobil gesetzt wird
        if (immobilControl.value) {
            Swal.fire({
                icon: 'question',
                text: this.translationService.instant('FACH-APP.KONTROLLE.MOBIL_CONFIRMATION_DEP'),
                showCancelButton: true,
                confirmButtonText: this.translationService.instant('CONFIRMATION.YES'),
                cancelButtonText: this.translationService.instant('CONFIRMATION.NO'),
            }).then(r => {
                if (r.isConfirmed) {
                    immobilControl.setValue(true);
                } else {
                    immobilControl.setValue(false);
                }
            });
        }
    }

    presave(): ImpfkontrolleJaxTS {
        this.formGroup.enable();
        const value: ImpfkontrolleJaxTS = this.formGroup.value;

        // Geburtsdatum
        value.geburtsdatum = DateUtil.parseDateAsMidday(this.formGroup.value.geburtsdatum);

        // externes Zertifikat
        value.externGeimpft = this.mapExternGeimpftToModel(
            this.externGeimpftFormGroup(),
            this.impfkontrolle?.externGeimpft);

        // values from the original impfkontrolle object
        value.registrierungsnummer = this.impfkontrolle?.registrierungsnummer;
        value.impffolgeNr = this.impfkontrolle?.impffolgeNr || 1;
        value.impfdossierEintragId = this.impfkontrolle?.impfdossierEintragId;

        return value;
    }

    private mapExternGeimpftToModel(
        externGeimpftFormGroup?: FormGroup,
        externGeimpftOriginal?: ExternGeimpftJaxTS,
    ): undefined | ExternGeimpftJaxTS {
        let externGeimpftJax: ExternGeimpftJaxTS | undefined;
        if (!this.showExternGeimpft) {
            // wenn nicht editierbar: den Originalwert aus dem urspruenglichen Jax zurueckgeben oder,
            // wenn dieser null ist dann einen defaultwert mit false
            externGeimpftJax = externGeimpftOriginal;
            if (!externGeimpftJax) {
                externGeimpftJax = {externGeimpft: false} as ExternGeimpftJaxTS;
            }
        } else {
            // wenn editierbar: den Wert aus dem Formular lesen
            externGeimpftJax = ExternGeimpftUtil.formToModel(externGeimpftFormGroup, this.impfstoffOptions);
        }
        return externGeimpftJax;

    }

    public saveIfValid(): void {
        this.confirmGeburtsdatumIfNotInAgeRangeAndPerformAction(
            () => {
                FormUtil.doIfValid(this.formGroup, () => {
                    this.save();
                });
            },
        );
    }

    private confirmGeburtsdatumIfNotInAgeRangeAndPerformAction(action: () => void): void {
        const gebdate = DateUtil.parseDateAsMidday(this.formGroup.value.geburtsdatum);
        const age = DateUtil.age(gebdate);
        if (age < MIN_ALTER_IMPFLING || age > MAX_ALTER_IMPFLING) {
            Swal.fire({
                icon: 'warning',
                text: this.translationService.instant('FACH-APP.KONTROLLE.DIALOG_CONFIRM_BIRTHDATE', {
                    age,
                }),
                showConfirmButton: true,
                showCancelButton: true,
                input: 'text',
                inputValue: '',
                inputPlaceholder: this.translationService.instant(
                    'FACH-APP.KONTROLLE.DIALOG_CONFIRM_BIRTHDATE_PLACEHOLDER'),
            }).then(r => {
                if (r.isConfirmed) {
                    // Wir uebernehmen immer das neu eingegebene Datum und schreiben es zurueck ins Form, wo es dann
                    // validiert wird. Dies, damit nicht im Dialog validiert werden muss
                    this.formGroup.get('geburtsdatum')?.setValue(r.value);
                    action();
                }
            });
        } else {
            action();
        }
    }

    save(): void {
        this.saveEvent.emit();
    }

    public saveFalschePersonIfValid(): void {
        FormUtil.doIfValid(this.formGroup, () => {
            this.saveFalschePerson();
        });
    }

    saveFalschePerson(): void {
        this.saveFalschePersonEvent.emit();
    }

    public isBerechtigtForImpfdokumentation(): boolean {
        return this.authServiceRS.isOneOfRoles([TSRole.OI_DOKUMENTATION, TSRole.KT_IMPFDOKUMENTATION]);
    }

    // Nur für Zürich aktiviert
    hasKeinKontaktEnabled(): boolean {
        return TenantUtil.hasKeinKontakt();
    }
}
