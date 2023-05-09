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
import EnumUtil from '../../../../vacme-web-shared/src/lib/util/EnumUtil';
import {ImpfdokumentationCache, LotAndMenge} from '../model/impfdokumentation.cache';
import {ImpfdokumentationJaxTS} from 'vacme-web-generated';

@Injectable({
    providedIn: 'root'
})
export class ImpfdokumentationCacheService {

    private impfdokumentation: ImpfdokumentationCache | undefined;

    constructor() {
    }

    public cacheImpfdokumentation(data: ImpfdokumentationJaxTS, odiId: string): void {
        const cacheLotAndMenge: {[impfstoff: string]: LotAndMenge} = this.createLotAndMenge(data);
        this.impfdokumentation = {
            ortDerImpfungId: odiId,
            impfstoff: data.impfstoff?.id,
            lotAndMengeByImpfstoff: cacheLotAndMenge,
            verantwortlicherBenutzerId: data.verantwortlicherBenutzerId,
            durchfuehrenderBenutzerId: data.durchfuehrenderBenutzerId,
            verarbreichungsart: data.verarbreichungsart,
            verarbreichungsort: data.verarbreichungsort,
            verarbreichungsseite: data.verarbreichungsseite
        } as ImpfdokumentationCache;
        localStorage.setItem('impfdokumentation', JSON.stringify(this.impfdokumentation));
    }

    private createLotAndMenge(data: ImpfdokumentationJaxTS): { [impfstoff: string]: LotAndMenge } {
        const lotAndMenge = {lot: data.lot, menge: data.menge};
        const impfdokumentation = this.getImpfdokumentation();
        const cached = impfdokumentation?.lotAndMengeByImpfstoff;
        if (cached && data.impfstoff?.id) {
            cached[data.impfstoff?.id] = lotAndMenge;
            return cached;
        }
        if (data.impfstoff?.id) {
            return {[data.impfstoff?.id]: lotAndMenge};
        }
        return {};
    }

    public getLotAndMengeForImpfstoff(impfstoff?: string | null): LotAndMenge | undefined {
        const impfdokumentationCache = this.getImpfdokumentation();
        if (impfstoff && impfdokumentationCache?.lotAndMengeByImpfstoff) {
            return impfdokumentationCache.lotAndMengeByImpfstoff[impfstoff];
        }
        return undefined;
    }

    public getImpfdokumentation(): ImpfdokumentationCache | undefined {
        if (!this.impfdokumentation) {
            let item = localStorage.getItem('impfdokumentation');
            if (item) {
                item = EnumUtil.ensureBackwardsCompatibleCache(item);
                this.impfdokumentation = JSON.parse(item) as ImpfdokumentationCache;
            }
        }
        return this.impfdokumentation;
    }

    public cacheSelectedOdi(newOdi: string | undefined): void {
        let localStoredImpfdoku = this.getImpfdokumentation();

        if (!localStoredImpfdoku) {
            localStoredImpfdoku = {} as ImpfdokumentationCache;
        }

        if (localStoredImpfdoku.ortDerImpfungId !== newOdi && newOdi) {
            localStoredImpfdoku.ortDerImpfungId = newOdi;
            localStoredImpfdoku.durchfuehrenderBenutzerId = undefined;
            localStoredImpfdoku.verantwortlicherBenutzerId = undefined;

            localStorage.setItem('impfdokumentation', JSON.stringify(localStoredImpfdoku));
        }

    }

    public removeOdiFromCache(): void {
        let localStoredImpfdoku = this.getImpfdokumentation();

        if (!localStoredImpfdoku) {
            localStoredImpfdoku = {} as ImpfdokumentationCache;
        }

        localStoredImpfdoku.ortDerImpfungId = undefined;
        localStoredImpfdoku.durchfuehrenderBenutzerId = undefined;
        localStoredImpfdoku.verantwortlicherBenutzerId = undefined;

        localStorage.setItem('impfdokumentation', JSON.stringify(localStoredImpfdoku));
    }
}
