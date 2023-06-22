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
import {Inject, Injectable} from '@angular/core';
import {Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {KeycloakService} from 'keycloak-angular';
import {from, Observable, of} from 'rxjs';
import {catchError, concatMap, first, map, takeWhile, tap} from 'rxjs/operators';
import {PropertiesService, StammdatenService} from 'vacme-web-generated';
import {RegistrierungValidationService} from '../../../../vacme-web/src/app/service/registrierung-validation.service';
import {API_URL} from '../constants';
import {LogFactory} from '../logging';
import {APP_CONFIG, AppConfig} from '../types';
import {parsePrio} from '../util/parsePrio';
import {AuthServiceRsService} from './auth-service-rs.service';
import {VacmeSettingsService} from './vacme-settings.service';
import Swal from 'sweetalert2/dist/sweetalert2.js';

const LOG = LogFactory.createLog('AppLoadService');

@Injectable({
    providedIn:
        'root',
})
export class AppLoadService {

    private readonly serviceURL: string;

    constructor(
        private authServiceRsService: AuthServiceRsService,
        private propertyService: PropertiesService,
        private stammdatenService: StammdatenService,
        private registrierungValidation: RegistrierungValidationService,
        private keycloakService: KeycloakService,
        private vacmeSettingsService: VacmeSettingsService,
        private router: Router,
        private translateService: TranslateService,
        @Inject(APP_CONFIG) private appConfig: AppConfig,
        @Inject(DOCUMENT) private document: any,
    ) {
        this.serviceURL = `${API_URL}`;
    }

    public initializeApp(): Promise<any> {

        return this.initializeUsingKeycloak$();
    }

    private initializeUsingKeycloak$(): Promise<any> {
        return new Promise((resolve, reject): void => {

            LOG.info(`initialize using keycloak login...`);

            const checkAuthMethodObs$ = this.checkAuthMethod$();

            const xsrfObs$ = this.authServiceRsService.refreshXSRFToken$();
            const ensureUserUpdatedInBackend$ = this.authServiceRsService.ensureUserUpdatedInBackend$();
            const ensureUserActive$ = this.authServiceRsService.ensureUserActive$();

            let userActive: boolean;

            checkAuthMethodObs$.pipe(
                concatMap((dummyMode: boolean) => {
                    if (dummyMode) {
                        LOG.warn('running in dummy-login mode, keycloak will be disabled');

                        // this could be used in case the keycloak service cant get the token correctly
                        // this.propertyService.apiV1PropertiesPublicKeycloakconfigGet().subscribe(value =>
                        //         LOG.info('loaded the keycloak config via properties service
                        //         (can be removed later only here to test if loading works on dev)', value),
                        //     error => LOG.error(error));

                        LOG.debug(`init with cookie login...`);

                        // dummy login mode is enabled, no keycloak
                        return of(this.authServiceRsService.initWithCookie());
                    }
                    const initKeycloakObs$ = from(this.initKeycloakService$());

                    // keycloak is enabled, after initializing keycloak service init principal using keycloak
                    return initKeycloakObs$.pipe(
                        concatMap(() => {
                            return this.authServiceRsService.initPrincipalFromKeycloak$();
                        }),
                        concatMap(() => {
                            LOG.debug('Ensure user is not deactivated by 30 day rule.');

                            return ensureUserActive$.pipe(tap(active => {
                                userActive = active;
                                if (!active) {
                                    Swal.fire({icon: 'info', text: this.translateService.instant('ACCOUNT_DEAKTIVIERT')})
                                        .then(() => this.authServiceRsService.logout(this.router, '/start'));
                                }
                            }));
                        }),
                        // Stop further initialization as to not create a db entry.
                        takeWhile(() => userActive),
                        concatMap(() => {
                            LOG.debug('ensure user updated in backend');

                            return ensureUserUpdatedInBackend$;
                        }),
                        concatMap(() => {
                            LOG.debug('reading xsfr token');

                            return xsrfObs$;
                        }),
                        concatMap(() => {
                            // only load prio in vacme-web
                            if (this.appConfig.appName === 'vacme-web') {
                                return this.stammdatenService.stammdatenResourceGetFreigegebenePrio().pipe(
                                    first(),
                                    map(
                                        prios => {
                                            return this.registrierungValidation.setFreigegebenePrio(prios);
                                        },
                                    ),
                                    catchError(err => of(parsePrio('A'))),// bei fehlern mal A annehmen, ist ja nur fuer
                                    // client validierung
                                );
                            }
                            return Promise.resolve(); // return fulfilled promise
                        }),
                        concatMap(() => {
                            return this.vacmeSettingsService.readSettingsFromServer$().pipe(
                                catchError(err => {
                                    LOG.error('could not load vacme settings through vacme-settigns-service');
                                    throw  err;
                                }),
                            );

                        }),
                    );

                }),
            ).subscribe(() => {
                resolve(undefined);
            }, err => {
                LOG.error('could not perform complete app initialization', err);
                reject();
            });

        });
    }

    private async initKeycloakService$(): Promise<boolean> {
        LOG.debug('initializing keycloak service');

        const url = this.getKeycloakConfigURL(); // load config from server
        let success = false;
        try {
            // if you want keycloak to ensure login on init set the on load option to login-required
            success = await this.keycloakService.init({
                config: url,
                loadUserProfileAtStartUp: false,
                initOptions: {
                    // eslint-disable-next-line max-len
                    onLoad: this.decideLoginMethodOnLoad(),
                    checkLoginIframe: true,
                    checkLoginIframeInterval: 30,
                    pkceMethod: 'S256',
                },
                bearerExcludedUrls: [
                    '/assets',
                    '/api/v1/public/keycloakconfig/*',
                    '/api/v1/web/auth/refreshXSRFToken', // this request triggers login
                ],
            });
        } catch (e) {
            LOG.error('error during keycloak init', e);
            success = false;
        }

        return Promise.resolve(success);

    }

    private checkAuthMethod$(): Observable<boolean> {
        // if this observable is true then keycloak ist disabled and we are using dummy login, currently only one or
        // the other can be active
        if (!this.appConfig.keycloakServiceEnabled) {
            return of(false);
        } else {
            return of(false);
        }

    }

    public getKeycloakConfigURL(): string {
        return this.serviceURL + '/public/keycloakconfig/' + this.appConfig.appName;
    }

    private decideLoginMethodOnLoad(): 'login-required' | 'check-sso' {
        return this.appConfig.appName === 'vacme-web' ? 'login-required' : 'check-sso';

    }
}
