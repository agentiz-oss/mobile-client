package com.example.app.screens

import com.example.app.data.RunUsageDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.math.roundToLong

/**
 * Token spend rendered the way the dashboard's `tokenUsage.ts` renders it, so the two clients
 * spell one number one way: "12 340" → "12.3k", "1 234 567" → "1.2M" — a run card is scanned,
 * not read.
 */

internal fun formatTokens(value: Long): String {
    if (value <= 0) return "0"
    if (value < 1_000) return value.toString()
    if (value < 10_000) {
        val tenths = (value / 100.0).roundToLong()
        return "${tenths / 10}.${tenths % 10}k"
    }
    if (value < 1_000_000) return "${(value / 1_000.0).roundToLong()}k"
    val tenths = (value / 100_000.0).roundToLong()
    return "${tenths / 10}.${tenths % 10}M"
}

/** Total even when the sender only provided components (an older worker build). */
internal fun totalTokens(usage: RunUsageDto): Long =
    if (usage.totalTokens > 0) usage.totalTokens
    else usage.inputTokens + usage.outputTokens + usage.cacheReadTokens + usage.cacheWriteTokens

/** "· 110k ткн" for a run card, or null when there is nothing worth a badge. */
internal fun tokensBadge(usage: RunUsageDto?): String? =
    usage?.let(::totalTokens)?.takeIf { it > 0 }?.let { "${formatTokens(it)} ткн" }

/** "≈ $0.2779" under a dime, "≈ $0.28" above — the dashboard's precision rule. */
internal fun formatCostUsd(cost: Double): String {
    val digits = if (cost < 0.1) 4 else 2
    var factor = 1L
    repeat(digits) { factor *= 10 }
    val scaled = (cost * factor).roundToLong()
    return "≈ $${scaled / factor}.${(scaled % factor).toString().padStart(digits, '0')}"
}

/** The worker ships stage usage inside the stage's output JSON rather than as a typed field. */
private val StageOutputJson = Json { ignoreUnknownKeys = true }

/**
 * The `usage` block of one stage's output, or null when the stage reported none. Decoded
 * tolerantly: the block carries fields this build does not model (`reasoningTokens`,
 * `contextWindow`), and a shape it cannot read at all must cost nothing but the badge.
 */
internal fun stageUsage(output: JsonElement?): RunUsageDto? {
    val block = (output as? JsonObject)?.get("usage") as? JsonObject ?: return null
    return runCatching { StageOutputJson.decodeFromJsonElement<RunUsageDto>(block) }.getOrNull()
}
