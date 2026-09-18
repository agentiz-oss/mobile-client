package com.example.app.data

/**
 * The handful of preferences that belong to this device rather than to the account.
 *
 * Backed by [LocalCache] under a `pref:` prefix, which buys four platform implementations for
 * nothing — but the prefix is not decoration: everything else in that store is a copy of a server
 * response and may be dropped at any time, so a reader has to be able to tell a preference from a
 * cache entry before deciding what is safe to evict.
 *
 * A preference here is a **cache of a decision, never the decision itself**: the language is
 * `UserAP.locale` on the server, and this copy exists so the login screen and an offline start have
 * something to speak. Losing the whole store therefore costs a person nothing they cannot get back
 * by signing in.
 */
object AppSettings {

    /**
     * The BCP-47 tag last chosen on this device, or null when nobody has chosen. Null and "the
     * device's language" are different answers — see [com.example.app.i18n.AppLocale].
     */
    var language: String?
        get() = read(LANGUAGE)
        set(value) = write(LANGUAGE, value)

    private const val LANGUAGE = "pref:language"

    /**
     * Swapped in by tests so a test never reads — or writes — the developer's real preferences.
     * Same shape and the same reason as [useInMemorySessionStorageForTesting]; null means "use the
     * platform store", which is every non-test run.
     */
    private var override: MutableMap<String, String>? = null

    /** Redirects preferences to memory for the duration of a test, and empties them. */
    fun useInMemoryForTesting() {
        override = mutableMapOf()
    }

    private fun read(key: String): String? = try {
        (override?.get(key) ?: LocalCache.get(key))?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        // A browser with storage blocked throws on the first read. A preference nobody can store
        // is a preference nobody set.
        null
    }

    private fun write(key: String, value: String?) {
        try {
            val slot = override
            when {
                value == null -> if (slot != null) slot.remove(key) else LocalCache.remove(key)
                slot != null -> slot[key] = value
                else -> LocalCache.put(key, value)
            }
        } catch (_: Throwable) {
        }
    }
}
