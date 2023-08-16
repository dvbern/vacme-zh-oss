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

import {Component} from '@angular/core';
import {FormBuilder} from '@angular/forms';
import {TranslateService} from '@ngx-translate/core';
import {FileSystemFileEntry, NgxFileDropEntry} from 'ngx-file-drop';
// falsch: import Swal from 'sweetalert2'; // Hier wird nicht nur das JS, sondern auch das CSS importiert
import Swal from 'sweetalert2/dist/sweetalert2.js'; // nur das JS importieren
import {OdiImportService} from 'vacme-web-generated';
import {LogFactory} from 'vacme-web-shared';
import {BaseDestroyableComponent} from '../../../../vacme-web-shared/src/lib/components/base-destroyable/base-destroyable.component';

const LOG = LogFactory.createLog('OdiImportComponent');

@Component({
    selector: 'app-odi-import-page',
    templateUrl: './odi-import-page.component.html',
    styleUrls: ['./odi-import-page.component.scss'],
})
export class OdiImportPageComponent extends BaseDestroyableComponent {

    selectedFile!: FileSystemFileEntry | undefined;

    constructor(
        private formBuilder: FormBuilder,
        private odiImportService: OdiImportService,
        private translateService: TranslateService,
    ) {
        super();
    }

    upload(): void {
        if (this.selectedFile === undefined) {
            return;
        }

        this.selectedFile.file(file => {
            this.odiImportService.odiImportResourceUploadAsync('import-odis', file).subscribe(value => {
                Swal.fire({
                    icon: 'info',
                    text: this.translateService.instant('FACH-APP.ODI-IMPORT.IMPORT_UPLOAD_CONFIRMATION'),
                    showCloseButton: false,
                    showConfirmButton: true,
                }).then(() => {
                    this.selectedFile = undefined;
                });
            }, err => {
                // Die Fehlermeldung wird vom ErrorInterceptor schon angezeigt
            });
        });
    }

    dropped(ngxFilesToUpload: NgxFileDropEntry[]): void {
        const droppedFiles: FileSystemFileEntry[] =
            ngxFilesToUpload
                .map(value => value.fileEntry as FileSystemFileEntry)
                .filter(droppedFile => droppedFile.isFile);

        if (droppedFiles.length >= 0 && droppedFiles[0].isFile) {
            this.selectedFile = droppedFiles[0] as FileSystemFileEntry;
        }
    }

    linkToTemplate(): string {
        return 'templates/ImportOdi.xlsx?v=' + __VERSION__;
    }
}
