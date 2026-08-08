package com.example.app.data

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.example.app.data.db.AppDatabase

/** A real SQLite database (via SQLDelight) in the app's own sandboxed container. */
actual object LocalCache {
    private val queries by lazy {
        AppDatabase(NativeSqliteDriver(AppDatabase.Schema, "agentiz_cache.db")).cacheEntryQueries
    }

    actual fun get(key: String): String? = queries.selectValue(key).executeAsOneOrNull()

    actual fun put(key: String, value: String) {
        queries.upsert(key, value)
    }

    actual fun remove(key: String) {
        queries.deleteEntry(key)
    }
}
