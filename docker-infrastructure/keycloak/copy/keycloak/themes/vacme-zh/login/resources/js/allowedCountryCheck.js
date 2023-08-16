function isCountryAllowed(parsedNumber) {
    //These countries are also in env config. It is important to change the list in two places when changing it.
    //It is also important to change the text: sms-auth.help, ALLOWED_COUNTRIES_TEXT
    const allowedCountries = ['CH', 'DE', 'IT', 'FR', 'AT', 'LI'];
    return allowedCountries.includes(parsedNumber.country);
}