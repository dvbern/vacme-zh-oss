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
import { ImpfstoffJaxTS } from './impfstoff-jax';
import { OrtDerImpfungTypTS } from './ort-der-impfung-typ';
import { AdresseJaxTS } from './adresse-jax';


export interface OrtDerImpfungJaxTS { 
    version?: number;
    timestampErstellt?: Date;
    timestampMutiert?: Date;
    userErstellt?: string;
    userMutiert?: string;
    id?: string;
    name?: string;
    adresse?: AdresseJaxTS;
    typ?: OrtDerImpfungTypTS;
    mobilerOrtDerImpfung?: boolean;
    oeffentlich?: boolean;
    terminverwaltung?: boolean;
    externerBuchungslink?: string;
    personalisierterImpfReport?: boolean;
    deaktiviert?: boolean;
    booster?: boolean;
    impfstoffe?: Array<ImpfstoffJaxTS>;
    zsrNummer?: string;
    glnNummer?: string;
    kommentar?: string;
    identifier?: string;
    organisationsverantwortung?: string;
    fachverantwortungbab?: string;
    impfungGegenBezahlung?: boolean;
}

