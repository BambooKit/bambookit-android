package com.bambookit.android.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Test
    fun `free plan parses`() {
        val text = """{"data":{"plan":"free","source":null,"proUntil":null,
            "limits":{"phoneMessagesPerDay":20,"phoneSessionsPerDay":3,"desktops":1},
            "usage":{"phoneMessagesToday":7,"phoneSessionsToday":3,"desktops":1},
            "resetsAt":"2026-10-06T00:00:00.000Z","ads":true,"rewards":{"todayCount":1,"maxPerDay":3,"hours":24}}}"""
        val p = json.decodeFromString(Envelope.serializer(Plan.serializer()), text).data
        assertFalse(p.isPro)
        assertTrue(p.ads)
        assertEquals(20, p.limits.phoneMessagesPerDay)
        assertEquals(3, p.limits.phoneSessionsPerDay)
        assertEquals(7, p.usage.phoneMessagesToday)
        assertEquals(2, p.rewardsLeft)
        assertTrue(p.canWatchReward)
        assertEquals("2026-10-06T00:00:00.000Z", p.resetsAt)
    }

    @Test
    fun `pro plan with unlimited limits parses`() {
        val text = """{"plan":"pro","source":"reward","proUntil":"2026-10-06T12:00:00Z",
            "limits":{"phoneMessagesPerDay":null,"phoneSessionsPerDay":null,"desktops":5},
            "usage":{"phoneMessagesToday":40,"phoneSessionsToday":9,"desktops":2},"ads":false,
            "rewards":{"todayCount":3,"maxPerDay":3,"hours":24},"extra":"ignored"}"""
        val p = json.decodeFromString(Plan.serializer(), text)
        assertTrue(p.isPro)
        assertEquals("reward", p.source)
        assertNull(p.limits.phoneMessagesPerDay)
        assertEquals(5, p.limits.desktops)
        assertFalse(p.ads)
        assertEquals(0, p.rewardsLeft)
        assertFalse(p.canWatchReward)
    }

    @Test
    fun `missing fields mean no ads`() {
        val p = json.decodeFromString(Plan.serializer(), """{"plan":"free"}""")
        assertFalse(p.ads)
        assertFalse(p.canWatchReward)
    }

    @Test
    fun `billing plans and reward token parse`() {
        val b = json.decodeFromString(
            Envelope.serializer(BillingPlans.serializer()),
            """{"data":{"products":[{"id":"pro_month","name":"Pro monthly","amount":499,"currency":"usd","period":"month","days":30}],
                "limits":{"free":{"phoneMessagesPerDay":20},"pro":{}},"payments":{"configured":true,"environment":"sandbox"},
                "rewards":{"hours":24,"maxPerDay":3}}}""",
        ).data
        assertEquals(1, b.products.size)
        assertEquals(499L, b.products[0].amount)
        assertTrue(b.payments.configured)
        assertEquals(3, b.rewards.maxPerDay)
        val t = json.decodeFromString(Envelope.serializer(RewardToken.serializer()), """{"data":{"customData":"abc.def","userId":"u1"}}""").data
        assertEquals("abc.def", t.customData)
        assertEquals("u1", t.userId)
    }

    @Test
    fun `PLAN_LIMIT error parses`() {
        val body = """{"error":{"code":"PLAN_LIMIT","message":"Daily free limit reached",
            "details":{"limit":"phoneMessagesPerDay","max":20,"used":20,"resetsAt":"2026-10-06T00:00:00Z","upgradeUrl":"https://bambookit-web.onrender.com/pricing/"}},"requestId":"r1"}"""
        val e = ApiClient.errorFrom(json, "POST", "/v1/sessions/s1/commands", 402, body, null, null)
        assertEquals(402, e.status)
        val limit = PlanLimitError.from(e)!!
        assertEquals("phoneMessagesPerDay", limit.limit)
        assertEquals(20, limit.max)
        assertEquals(20, limit.used)
        assertEquals("2026-10-06T00:00:00Z", limit.resetsAt)
        assertEquals("https://bambookit-web.onrender.com/pricing/", limit.upgradeUrl)
        assertFalse(limit.isSessions)
    }

    @Test
    fun `PLAN_LIMIT for sessions and without details`() {
        val s = ApiClient.errorFrom(
            json, "POST", "/v1/projects/p/sessions", 402,
            """{"error":{"code":"PLAN_LIMIT","message":"Limit","details":{"limit":"phoneSessionsPerDay","max":"3","used":3}}}""", null, null,
        )
        val l = PlanLimitError.from(s)!!
        assertTrue(l.isSessions)
        assertEquals(3, l.max)
        val bare = PlanLimitError.from(ApiClient.errorFrom(json, "POST", "/x", 402, """{"error":{"code":"PLAN_LIMIT","message":"Limit"}}""", null, null))!!
        assertNull(bare.max)
        assertEquals("Limit", bare.message)
    }

    @Test
    fun `other errors are not PLAN_LIMIT`() {
        assertNull(PlanLimitError.from(ApiClient.errorFrom(json, "POST", "/x", 503, """{"error":{"code":"DESKTOP_OFFLINE","message":"x"}}""", null, null)))
        assertNull(PlanLimitError.from(IllegalStateException("x")))
        assertNull(PlanLimitError.from(null))
    }
}
