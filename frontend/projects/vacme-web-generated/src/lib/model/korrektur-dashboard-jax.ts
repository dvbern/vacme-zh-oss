/**
 * Generated VacMe API
 * Generated using custom templates to be found under vacme-web-generated/src/templates.
 *
 * The version of the OpenAPI document: 999.0.0
 *
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */
import { ExternGeimpftJaxTS } from './extern-geimpft-jax';
import { ImpfungJaxTS } from './impfung-jax';
import { OrtDerImpfungDisplayNameJaxTS } from './ort-der-impfung-display-name-jax';
import { KontrolleKommentarJaxTS } from './kontrolle-kommentar-jax';
import { AbgesagteTermineJaxTS } from './abgesagte-termine-jax';
import { ImpfterminJaxTS } from './impftermin-jax';
import { GeschlechtTS } from './geschlecht';
import { RegistrierungStatusTS } from './registrierung-status';
import { PrioritaetTS } from './prioritaet';
import { RegistrierungsEingangTS } from './registrierungs-eingang';
import { ImpfdossierJaxTS } from './impfdossier-jax';
import { CurrentZertifikatInfoTS } from './current-zertifikat-info';


export interface KorrekturDashboardJaxTS { 
    registrierungsnummer?: string;
    registrierungId?: string;
    status?: RegistrierungStatusTS;
    prioritaet?: PrioritaetTS;
    gewuenschterOrtDerImpfung?: OrtDerImpfungDisplayNameJaxTS;
    nichtVerwalteterOdiSelected?: boolean;
    termin1?: ImpfterminJaxTS;
    termin2?: ImpfterminJaxTS;
    terminNPending?: ImpfterminJaxTS;
    vorname?: string;
    name?: string;
    geschlecht?: GeschlechtTS;
    geburtsdatum?: Date;
    immobil?: boolean;
    impfung1?: ImpfungJaxTS;
    impfung2?: ImpfungJaxTS;
    eingang?: RegistrierungsEingangTS;
    abgesagteTermine?: AbgesagteTermineJaxTS;
    elektronischerImpfausweis?: boolean;
    vollstaendigerImpfschutz?: boolean;
    timestampLetzterPostversand?: Date;
    gueltigeSchweizerAdresse?: boolean;
    hasBenutzer?: boolean;
    benutzerId?: string;
    impfdossier?: ImpfdossierJaxTS;
    externGeimpft?: ExternGeimpftJaxTS;
    selbstzahler?: boolean;
    immunsupprimiert?: boolean;
    zweiteImpfungVerzichtetGrund?: string;
    positivGetestetDatum?: Date;
    zweiteImpfungVerzichtetZeit?: Date;
    timestampZuletztAbgeschlossen?: Date;
    timestampArchiviert?: Date;
    registrierungsEingang?: RegistrierungsEingangTS;
    boosterImpfungen?: Array<ImpfungJaxTS>;
    abgleichElektronischerImpfausweis?: boolean;
    bemerkungenRegistrierung?: string;
    kommentare?: Array<KontrolleKommentarJaxTS>;
    currentZertifikatInfo?: CurrentZertifikatInfoTS;
    timestampPhonenumberUpdate?: Date;
    durchfuehrendePerson1?: string;
    durchfuehrendePerson2?: string;
    mail?: string;
    telefon?: string;
}

