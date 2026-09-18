package com.example.app

import com.example.app.i18n.AppLocale
import com.example.app.i18n.Lang
import kotlin.test.BeforeTest

/**
 * Pins the language for a test class.
 *
 * Every assertion in this suite that reads a word off the screen reads a Russian one, because
 * Russian is the language these strings were written in and the table the other two are translated
 * from — so a diff in the wording is visible here rather than only after a translator's pass.
 *
 * It has to be *pinned* and not merely defaulted: without this the app resolves its language from
 * the machine's own locale, and the same test would then pass on a developer's laptop and fail on
 * a CI runner whose JVM reports `en_US` — which is the kind of failure that gets a suite disabled.
 *
 * Implemented as a class to extend rather than a function to remember to call, for the same reason.
 */
abstract class RussianUiTest {

    @BeforeTest
    fun pinLanguage() = AppLocale.useForTesting(Lang.Ru)
}
