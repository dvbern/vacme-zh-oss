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

import {Inject, Injectable} from '@angular/core';
import {LogFactory} from '../logging';
import {APP_CONFIG, AppConfig} from '../types';
import {SettingsService} from '../../../../vacme-web-generated/src/lib/api/settings.service';
import {SettingsJaxTS} from '../../../../vacme-web-generated/src/lib/model/settings-jax';
import {Observable} from 'rxjs';
import {first, map, retry} from 'rxjs/operators';
import {TranslateService} from '@ngx-translate/core';
import {TranslatedTextJaxTS} from 'vacme-web-generated';

const LOG = LogFactory.createLog('VacmeSettingsService');

@Injectable({
    providedIn: 'root',
})
export class VacmeSettingsService {

    private _distanceImpfungenDesired = 28;
    private _distanceImpfungenToleranceAfter = 32;
    private _distanceImpfungenToleranceBefore = 0;
    private _minutesBetweenInfoUpdates = 525600;
    private _showOnboardingWelcomeText = false;
    private _emailKorrekturEnabled = false;
    private _generalInfotexts: Array<TranslatedTextJaxTS> = [];
    private _geocodingEnabled = false;
    private _boosterFreigabeNotificationDisabled = false;
    private _selbstzahlerFachapplikationEnabled = false;
    private _selbstzahlerPortalEnabled = false;
    private _minutesBetweenNumberUpdates = 1440;

    constructor(
        @Inject(APP_CONFIG) private appConfig: AppConfig,
        private translateService: TranslateService,
        private settingsService: SettingsService,
    ) {
    }

    public readSettingsFromServer$(): Observable<SettingsJaxTS> {
        // decide wether to load throgh vacme-web api or vacme api
        if (this.appConfig.appName === 'vacme-web') {
            return this.settingsService.settingsResourceGetSettings().pipe(
                first(),
                retry(1),
                map((settings: SettingsJaxTS) => {
                    this.mapSettings(settings);
                    return settings;
                })
            );
        } else {
            return this.settingsService.settingsResourceRegGetSettings().pipe(
                first(),
                retry(1),
                map((settings: SettingsJaxTS) => {
                    this.mapSettings(settings);
                    return settings;
                })
            );
        }
    }

    private mapSettings(settings: SettingsJaxTS): void {
        this._distanceImpfungenDesired = settings.distanceImpfungenDesired || 28;
        this._distanceImpfungenToleranceBefore = settings.distanceImpfungenToleranceBefore || 0;
        this._distanceImpfungenToleranceAfter = settings.distanceImpfungenToleranceAfter || 32;
        this._showOnboardingWelcomeText = settings.showOnboardingWelcomeText || false;
        this._emailKorrekturEnabled = settings.emailKorrekturEnabled || false;
        this._generalInfotexts  = settings.generalInfotexts ? settings.generalInfotexts : [];
        this._geocodingEnabled = settings.geocodingEnabled || false;
        this._boosterFreigabeNotificationDisabled = settings.boosterFreigabeNotificationDisabled || false;
        this._selbstzahlerFachapplikationEnabled = settings.selbstzahlerFachapplikationEnabled || false;
        this._selbstzahlerPortalEnabled = settings.selbstzahlerPortalEnabled || false;
        this._minutesBetweenInfoUpdates = settings.minutesBetweenInfoUpdates || 525600;
        this._minutesBetweenNumberUpdates = settings.minutesBetweenNumberUpdates || 1440;
    }

    public get minutesBetweenNumberUpdates(): number {
        return this._minutesBetweenNumberUpdates;
    }

    public get minutesBetweenInfoUpdates(): number {
        return this._minutesBetweenInfoUpdates;
    }

    public get distanceImpfungenDesired(): number {
        return this._distanceImpfungenDesired;
    }

    public get distanceImpfungenToleranceAfter(): number {
        return this._distanceImpfungenToleranceAfter;
    }

    public get distanceImpfungenToleranceBefore(): number {
        return this._distanceImpfungenToleranceBefore;
    }

    public get showOnboardingWelcomeText(): boolean {
        return this._showOnboardingWelcomeText;
    }

    public get emailKOrrekturEnabled(): boolean {
        return this._emailKorrekturEnabled;
    }

    public get geocodingEnabled(): boolean {
        return this._geocodingEnabled;
    }

    public get boosterFreigabeNotificationDisabled(): boolean {
        return this._boosterFreigabeNotificationDisabled;
    }

    public distanceImpfungenMinimal(): number {
        return this.distanceImpfungenDesired - this.distanceImpfungenToleranceBefore;
    }

    public distanceImpfungenMaximal(): number {
        return this.distanceImpfungenDesired + this.distanceImpfungenToleranceAfter;
    }

    public getInfoTextDistanceBetweenImpfungen(): string {
        const distText = this.getDistanceDesiredDistanceWithUnitText();
        return this.translateService.instant('OVERVIEW.ZWEIDREIWOCHEN', {count: 2, desiredDistance: distText});
    }

    public getDistanceDesiredDistanceWithUnitText(): string {
        const weeksValue =  this._distanceImpfungenDesired % 7 === 0;
        if (weeksValue) {
            const unit = this.translateService.instant('UNIT.WEEKS');
            return this._distanceImpfungenDesired / 7 + ' ' + unit;
        } else{
            const unit = this.translateService.instant('UNIT.DAYS');
            return this._distanceImpfungenDesired + ' ' + unit;
        }
    }

    public get showGeneralInfomessage(): boolean {
        return this.getInfomessageForcurrentLanguage() !== undefined && this.getInfomessageForcurrentLanguage()?.length !== 0;
    }

    public getInfomessageForcurrentLanguage(): string | undefined {
        const currLang = this.translateService.currentLang;
        return this._generalInfotexts.find(value => currLang.toUpperCase() === value?.sprache?.toUpperCase())?.text;
    }

    public get selbstzahlerFachapplikationEnabled(): boolean {
        return this._selbstzahlerFachapplikationEnabled;
    }

    public get selbstzahlerPortalEnabled(): boolean {
        return this._selbstzahlerPortalEnabled;
    }
}
