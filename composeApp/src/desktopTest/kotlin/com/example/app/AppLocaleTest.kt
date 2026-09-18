package com.example.app

import com.example.app.data.AppSettings
import com.example.app.i18n.AppLocale
import com.example.app.i18n.Lang
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which language the app decides to speak, and — more to the point — which of the four possible
 * answers wins when several of them are available at once.
 *
 * The order is a decision, not an accident: the setting is `UserAP.locale`, the panel reads the same
 * column, and everything below it in the chain exists only to have something to say before anybody
 * is signed in. The failure this suite is here to prevent is the mirror image of the timezone one —
 * a profile field nobody has filled in must read as "nobody said" and let the device answer, rather
 * than as a language, which would leave a reader staring at English with no switch in sight.
 *
 * Deliberately not extending [RussianUiTest]: pinning a language is exactly what these tests are
 * about, so they undo the pin themselves and watch the resolution happen.
 */
class AppLocaleTest {

    @BeforeTest
    fun freshProcess() {
        AppSettings.useInMemoryForTesting()
        AppLocale.resetForTesting()
    }

    @Test
    fun `a stored choice is what the login screen speaks`() {
        // Nobody is signed in yet, so there is no profile to ask — and the language the reader chose
        // last time is the one they chose, not a default to fall back through.
        AppSettings.language = "es"
        AppLocale.start()
        assertEquals(Lang.Es, AppLocale.lang)
    }

    @Test
    fun `the profile overrules whatever this device had decided`() {
        AppSettings.language = "es"
        AppLocale.start()
        AppLocale.applyProfile("ru")
        assertEquals(Lang.Ru, AppLocale.lang)
        // And it is cached, so the next cold start opens in it rather than opening in Spanish and
        // flipping the moment `/auth/me` answers.
        assertEquals("ru", AppSettings.language)
    }

    @Test
    fun `an empty profile field means nobody said, not English`() {
        AppSettings.language = "ru"
        AppLocale.start()
        // The state of every account on a deployment whose panel never offered the field. It must
        // not erase the answer the app already had.
        AppLocale.applyProfile(null)
        assertEquals(Lang.Ru, AppLocale.lang)
        AppLocale.applyProfile("")
        assertEquals(Lang.Ru, AppLocale.lang)
        assertEquals("ru", AppSettings.language)
    }

    @Test
    fun `a language this app does not have is not an answer either`() {
        AppSettings.language = "ru"
        AppLocale.start()
        AppLocale.applyProfile("de")
        assertEquals(Lang.Ru, AppLocale.lang)
    }

    @Test
    fun `a fresh install with nothing stored and nothing in the profile lands somewhere`() {
        // Whatever the device says — and English when it says something this app has no table for.
        // The assertion is deliberately weak: the point is that a decision is always reached, since
        // the alternative is a screen with no words on it.
        AppLocale.start()
        AppLocale.applyProfile(null)
        assertEquals(true, AppLocale.lang in Lang.entries)
    }

    @Test
    fun `picking a language is not undone by the profile it has not reached yet`() {
        // The bug this exists for: the picker applies the choice locally and writes it to the
        // profile over the network. Until that write lands the session still says the old language,
        // and the screen used to snap back to it one recomposition after the tap.
        AppLocale.start()
        AppLocale.applyProfile("ru")
        AppLocale.choose(Lang.Es)
        AppLocale.applyProfile("ru")
        assertEquals(Lang.Es, AppLocale.lang)
        assertEquals("es", AppSettings.language)

        // Once the write lands and the stored session carries it, nothing changes — but a *different*
        // answer from the profile (the panel, another phone) is still allowed to win.
        AppLocale.applyProfile("es")
        assertEquals(Lang.Es, AppLocale.lang)
        AppLocale.applyProfile("en")
        assertEquals(Lang.En, AppLocale.lang)
    }

    @Test
    fun `a tag is matched on its language, whatever the region`() {
        assertEquals(Lang.Ru, Lang.fromTag("ru-RU"))
        assertEquals(Lang.Ru, Lang.fromTag("ru_RU"))
        assertEquals(Lang.Es, Lang.fromTag("es-419"))
        assertEquals(Lang.En, Lang.fromTag("EN-gb"))
        assertEquals(null, Lang.fromTag("de"))
        assertEquals(null, Lang.fromTag(""))
        assertEquals(null, Lang.fromTag(null))
    }
}
