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
import { ChronischeKrankheitenTS } from './chronische-krankheiten';
import { AuslandArtTS } from './ausland-art';
import { AdresseJaxTS } from './adresse-jax';
import { LebensumstaendeTS } from './lebensumstaende';
import { BeruflicheTaetigkeitTS } from './berufliche-taetigkeit';
import { KrankenkasseTS } from './krankenkasse';


export interface SelfserviceEditJaxTS { 
    adresse?: AdresseJaxTS;
    krankenkasse?: KrankenkasseTS;
    krankenkasseKartenNr?: string;
    auslandArt?: AuslandArtTS;
    chronischeKrankheiten?: ChronischeKrankheitenTS;
    beruflicheTaetigkeit?: BeruflicheTaetigkeitTS;
    lebensumstaende?: LebensumstaendeTS;
    bemerkung?: string;
    keinKontakt?: boolean;
    timestampInfoUpdate?: Date;
    registrationTimestamp?: Date;
}

