package com.example.app.platform

import java.util.Locale

/** The JVM's default locale, which it takes from the OS (or from `-Duser.language`). */
actual fun deviceLanguageTag(): String = Locale.getDefault().toLanguageTag()
