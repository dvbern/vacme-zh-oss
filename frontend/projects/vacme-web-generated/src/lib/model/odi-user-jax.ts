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
import { FachRolleTS } from './fach-rolle';


export interface OdiUserJaxTS { 
    id?: string;
    username: string;
    enabled?: boolean;
    firstName: string;
    lastName: string;
    email: string;
    phone: string;
    glnNummer?: string;
    fachRolle?: FachRolleTS;
}

