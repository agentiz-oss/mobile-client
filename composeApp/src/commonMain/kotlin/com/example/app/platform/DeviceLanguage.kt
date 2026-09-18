package com.example.app.platform

/**
 * The BCP-47 tag of the language this device is set to — `ru`, `en-GB`, `es-419`.
 *
 * Only ever a *fallback*: which language the app speaks is the signed-in person's own setting
 * (`UserAP.locale`, see [com.example.app.i18n.AppLocale]), and this answers the question only while
 * nobody has said anything — on the login screen, and for an account whose profile leaves the field
 * empty. Same shape as [deviceUtcOffsetMinutes] and for the same reason: the platform already knows,
 * and the app carries no data of its own to work it out.
 *
 * An empty string is a legal answer and means "could not tell", which reads as "no opinion" rather
 * than as a language.
 */
expect fun deviceLanguageTag(): String
