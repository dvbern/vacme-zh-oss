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
import { AuslandArtTS } from './ausland-art';
import { AdresseJaxTS } from './adresse-jax';
import { GeschlechtTS } from './geschlecht';
import { KrankenkasseTS } from './krankenkasse';


export interface PersonendatenKorrekturJaxTS { 
    registrierungsnummer?: string;
    geschlecht?: GeschlechtTS;
    name?: string;
    vorname?: string;
    geburtsdatum?: Date;
    verstorben?: boolean;
    adresse?: AdresseJaxTS;
    abgleichElektronischerImpfausweis?: boolean;
    contactTracing?: boolean;
    mail?: string;
    telefon?: string;
    krankenkasse?: KrankenkasseTS;
    krankenkasseKartenNr?: string;
    auslandArt?: AuslandArtTS;
    identifikationsnummer?: string;
    schutzstatus?: boolean;
    keinKontakt?: boolean;
    immunsupprimiert?: boolean;
}

