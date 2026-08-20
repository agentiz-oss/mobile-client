package com.example.app

import com.example.app.data.RunUsageDto
import com.example.app.screens.formatCostUsd
import com.example.app.screens.formatTokens
import com.example.app.screens.stageUsage
import com.example.app.screens.tokensBadge
import com.example.app.screens.totalTokens
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The spend-display contract, pinned to the dashboard's `tokenUsage.ts` so the two clients spell
 * one number one way; plus the tolerance rules — an absent report is nothing (not zero), and a
 * stage-usage block with fields this build does not model still yields its badge.
 */
class TokenFormatTest {

    @Test
    fun formatsLikeTheDashboard() {
        assertEquals("0", formatTokens(0))
        assertEquals("742", formatTokens(742))
        assertEquals("1.2k", formatTokens(1_234))
        assertEquals("9.9k", formatTokens(9_940))
        assertEquals("12k", formatTokens(12_340))
        assertEquals("110k", formatTokens(110_316))
        assertEquals("1.2M", formatTokens(1_234_567))
    }

    @Test
    fun costPrecisionFollowsTheDimeRule() {
        assertEquals("≈ $0.0999", formatCostUsd(0.09989))
        assertEquals("≈ $0.28", formatCostUsd(0.277888))
        assertEquals("≈ $1.23", formatCostUsd(1.234))
    }

    @Test
    fun totalFallsBackToComponentsForOlderWorkers() {
        val componentsOnly = RunUsageDto(inputTokens = 10, outputTokens = 20, cacheReadTokens = 30, cacheWriteTokens = 40)
        assertEquals(100, totalTokens(componentsOnly))
        assertEquals(7, totalTokens(RunUsageDto(totalTokens = 7)))
    }

    @Test
    fun badgeIsAbsentNotZero() {
        assertNull(tokensBadge(null))
        assertNull(tokensBadge(RunUsageDto()))
        assertEquals("110k ткн", tokensBadge(RunUsageDto(totalTokens = 110_316)))
    }

    @Test
    fun stageUsageSurvivesUnknownFieldsAndForeignShapes() {
        val output = Json.parseToJsonElement(
            """{"agentResponse":"done","usage":{"totalTokens":110316,"inputTokens":10,"outputTokens":2465,
                "cacheReadTokens":90823,"cacheWriteTokens":17018,"reasoningTokens":5,"contextWindow":200000,
                "estimatedCostUsd":0.277888,"model":"claude-sonnet-5"}}""",
        )
        val usage = stageUsage(output)!!
        assertEquals(110_316, usage.totalTokens)
        assertEquals("claude-sonnet-5", usage.model)
        assertEquals(0.277888, usage.estimatedCostUsd)

        assertNull(stageUsage(null))
        assertNull(stageUsage(Json.parseToJsonElement("""{"agentResponse":"no usage here"}""")))
        assertNull(stageUsage(Json.parseToJsonElement("""{"usage":"not an object"}""")))
        assertNull(stageUsage(Json.parseToJsonElement("""{"usage":{"totalTokens":"not a number"}}""")))
    }
}
