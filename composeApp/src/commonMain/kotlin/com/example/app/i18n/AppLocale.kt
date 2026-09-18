package com.example.app.i18n

import androidx.compose.runtime.mutableStateOf
import com.example.app.data.AppSettings
import com.example.app.platform.deviceLanguageTag

/**
 * Which language the app is speaking right now, and where that decision comes from.
 *
 * The answer has one source of truth and three fallbacks behind it, resolved in this order:
 *
 * 1. **`UserAP.locale`** — the signed-in person's own profile field, handed over by the mobile API
 *    with the rest of the user on login and on every `/auth/me`. This is the setting: the same one
 *    the admin panel reads, so a person who has said "я по-русски" once has said it everywhere.
 * 2. **the last choice made on this device**, cached in [AppSettings]. It is what the login screen
 *    speaks (there is no user yet to ask), what an offline start speaks before `/auth/me` answers,
 *    and what the picker writes locally so the UI turns over before the server round-trip does.
 * 3. **the device's own language** ([deviceLanguageTag]) — the same fallback shape as the timezone,
 *    which is here for the same reason: a profile field nobody has filled in must not mean "no
 *    answer", it means "nobody said, so use what the phone says".
 * 4. **English**, for a device speaking a language this app does not have.
 *
 * ### Why a plain global rather than a CompositionLocal
 *
 * Half the text is assembled outside composition — an age (`formatWaiting`), a byte count, a
 * duration, the plural of "окно" — by pure functions that every screen and several tests call
 * directly. A CompositionLocal cannot answer those without every one of them becoming
 * `@Composable`. [current] is Compose snapshot state all the same, so reading [strings] *during*
 * composition still subscribes that composition to it and switching the language repaints the whole
 * app with no screen having to observe anything.
 */
object AppLocale {

    private val current = mutableStateOf(Lang.En)

    /** True once [start] has run, so a test that sets a language outright is not overruled by it. */
    private var started = false

    /**
     * The profile value [applyProfile] last acted on. Without it that call would undo the picker:
     * choosing a language re-runs the composition that applies the profile, and the profile still
     * says the old one until the server answers — so the app would snap back a frame after the tap.
     */
    private var appliedProfile: String? = null

    /** The language in effect. Reading this inside a composition re-runs it on a change. */
    val lang: Lang get() = current.value

    /** The table every string in the app is read out of. */
    val strings: Strings get() = current.value.table()

    /**
     * Resolves the language from what is known before anyone is signed in — the stored choice, then
     * the device — and does it once per process. Called as the app starts, ahead of the first
     * composition, so the first frame is already in the right language rather than flipping to it.
     */
    fun start() {
        if (started) return
        started = true
        current.value = Lang.fromTag(AppSettings.language)
            ?: Lang.fromTag(deviceLanguageTag())
            ?: Lang.En
    }

    /**
     * Applies the language of the signed-in profile. A null or unrecognised `UserAP.locale` leaves
     * whatever [start] resolved standing — an empty profile field is "nobody said", never "speak
     * English" — which is what keeps this from overwriting a device language on every launch.
     *
     * The stored copy is refreshed with it — but only when the profile actually said something, so
     * an empty profile field can never erase a choice made on this device. That write is what keeps
     * the *next* cold start from opening in one language and flipping to another once `/auth/me`
     * answers: [start] then already reads the language this call would have chosen.
     */
    fun applyProfile(locale: String?) {
        if (locale == appliedProfile) return
        appliedProfile = locale
        val lang = Lang.fromTag(locale) ?: return
        current.value = lang
        AppSettings.language = lang.tag
    }

    /**
     * The person picked a language. Takes effect immediately and is remembered on this device;
     * writing it to the profile is the caller's job, because that is a network call that can fail
     * while the UI must not.
     */
    fun choose(lang: Lang) {
        current.value = lang
        AppSettings.language = lang.tag
    }

    /** Pins a language for a test, bypassing both the device and the stored choice. */
    fun useForTesting(lang: Lang) {
        started = true
        appliedProfile = null
        current.value = lang
    }

    /**
     * Puts the resolver back to how it is in a process that has just launched, so a test can watch
     * [start] and [applyProfile] make their decision instead of asserting against whatever the
     * previous test left behind. [AppLocale] is a global — which is the point of it — so undoing it
     * has to be possible or every test after the first is testing a used one.
     */
    fun resetForTesting() {
        started = false
        appliedProfile = null
        current.value = Lang.En
    }
}

/**
 * Every string the app writes for itself. Short on purpose — it is read at hundreds of call sites,
 * `strings.tasksTitle` rather than `AppLocale.strings.tasksTitle`.
 *
 * Not a `@Composable` getter: see the note in [AppLocale] about the formatting helpers.
 */
val strings: Strings get() = AppLocale.strings
