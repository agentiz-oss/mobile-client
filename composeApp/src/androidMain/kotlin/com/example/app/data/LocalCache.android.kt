package com.example.app.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.app.data.db.AppDatabase
import com.example.app.data.db.CacheEntryQueries

/**
 * A real SQLite database (via SQLDelight), separate from [SessionStorage]'s prefs file, so clearing
 * cached server data on logout never risks touching the session and vice versa. [LocalCache.init]
 * must run before the first access; [com.example.app.MainActivity] does it alongside
 * [initSessionStorage].
 */
actual object LocalCache {
    private var queries: CacheEntryQueries? = null

    fun init(context: Context) {
        val driver = AndroidSqliteDriver(AppDatabase.Schema, context.applicationContext, "agentiz_cache.db")
        queries = AppDatabase(driver).cacheEntryQueries
    }

    actual fun get(key: String): String? = queries?.selectValue(key)?.executeAsOneOrNull()

    actual fun put(key: String, value: String) {
        queries?.upsert(key, value)
    }

    actual fun remove(key: String) {
        queries?.deleteEntry(key)
    }
}

/** Wires the cache to a context. Call once, before [com.example.app.App] first composes. */
fun initLocalCache(context: Context) = LocalCache.init(context)
