package com.bambookit.android.data

import kotlinx.serialization.json.Json
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun routeNotFoundFromNewServer() {
        val body = """{"error":{"code":"ROUTE_NOT_FOUND","message":"This BambooKit API (1.1.0) has no GET /v1/x.","details":{"method":"GET","path":"/v1/x","apiVersion":"1.1.0","protocol":2}},"requestId":"req_1"}"""
        val e = ApiClient.errorFrom(json, "GET", "/v1/x", 404, body, null, "1.1.0")
        assertEquals("ROUTE_NOT_FOUND", e.code)
        assertEquals("req_1", e.requestId)
        assertTrue(e.isRouteMissing)
        val d = e.diagnosis
        assertEquals(2, d.protocol)
        assertTrue(Diagnostics.explain(d).why.contains("older than this app"))
        assertTrue(Diagnostics.explain(d).why.contains("API 1.1.0"))
        assertTrue(DiagAction.Retry in Diagnostics.explain(d).actions)
    }

    @Test fun routeNotFoundFromOldServerFormat() {
        val e = ApiClient.errorFrom(json, "GET", "/v1/me/stats", 404, """{"error":{"code":"NOT_FOUND","message":"Route not found"}}""", "req_h", null)
        assertTrue(e.isRouteMissing)
        assertEquals("req_h", e.requestId)
        assertTrue(Diagnostics.explain(e.diagnosis).why.contains("older than 1.1.0"))
        // A missing record is not a missing route.
        val rec = ApiClient.errorFrom(json, "GET", "/v1/sessions/s", 404, """{"error":{"code":"NOT_FOUND","message":"Session not found"}}""", null, null)
        assertFalse(rec.isRouteMissing)
    }

    @Test fun desktopUpdateRequiredCarriesVersions() {
        val body = """{"error":{"code":"DESKTOP_UPDATE_REQUIRED","message":"Update BambooKit Desktop on Desk.","details":{"device":"Desk","currentVersion":"1.0.2","requiredVersion":"1.0.3","capability":"relay.todos","reason":"Todos need it.","desktopProtocol":1,"apiProtocol":2}},"requestId":"r"}"""
        val e = ApiClient.errorFrom(json, "GET", "/v1/sessions/s/todos", 426, body, null, "1.1.0")
        val r = e.desktopRequirement
        assertNotNull(r)
        assertEquals("1.0.2", r!!.currentVersion)
        assertEquals("1.0.3", r.requiredVersion)
        assertEquals(2, r.apiProtocol)
        val why = Diagnostics.explain(e.diagnosis)
        assertTrue(why.why.contains("1.0.2") && why.why.contains("1.0.3") && why.why.contains("Todos need it."))
        assertEquals(DiagAction.UpdateDesktop, why.actions.first())
        val c = contentErrorOf(e, "x")
        assertEquals(r, c.update)
        assertTrue(c.desktopOutdated)
    }

    @Test fun eachCodeHasItsOwnExplanation() {
        val offline = Diagnostics.explain(Diagnosis("m", "DESKTOP_OFFLINE", 409))
        val timeout = Diagnostics.explain(Diagnosis("m", "DESKTOP_TIMEOUT", 504))
        val network = Diagnostics.explain(Diagnosis("m", "NETWORK", 0))
        val auth = Diagnostics.explain(Diagnosis("m", "UNAUTHORIZED", 401))
        val storage = Diagnostics.explain(Diagnosis("m", "STORAGE_NOT_CONFIGURED", 503, details = mapOf("missing" to "R2_BUCKET_NAME")))
        assertEquals(5, setOf(offline.why, timeout.why, network.why, auth.why, storage.why).size)
        assertTrue(DiagAction.Reconnect in offline.actions)
        assertTrue(DiagAction.OpenSettings in network.actions)
        assertTrue(DiagAction.Reconnect in auth.actions)
        assertTrue(storage.why.contains("R2_BUCKET_NAME"))
    }

    @Test fun storageAccessDeniedPointsAtTheServerToken() {
        val why = Diagnostics.explain(Diagnosis("m", "UPLOAD_FAILED", 403, "PUT", "storage upload", details = mapOf("storageCode" to "AccessDenied")))
        assertTrue(why.why.contains("write access"))
        val other = Diagnostics.explain(Diagnosis("m", "UPLOAD_FAILED", 403, "PUT", "storage upload"))
        assertTrue(other.why.contains("exact size"))
    }

    @Test fun technicalDetailsHaveNoQueryOrToken() {
        assertEquals("/v1/sessions/s/file?…", Diagnostics.safePath("/v1/sessions/s/file?path=secret.txt"))
        val e = ApiClient.errorFrom(json, "GET", Diagnostics.safePath("/v1/a?token=abc"), 500, "not json", null, null)
        val text = Diagnostics.technical(e.diagnosis, "1.0.6 (7)")
        assertFalse(text.contains("abc"))
        assertTrue(text.contains("HTTP status: 500"))
        assertTrue(text.contains("Android app: 1.0.6 (7)"))
        assertEquals("HTTP_500", e.code)
    }

    @Test fun detailsDropSecretLookingKeys() {
        val d = Diagnostics.flatDetails(json.parseToJsonElement("""{"missing":"R2_BUCKET_NAME","accessKey":"x","token":"y","source":"github"}"""))
        assertEquals(mapOf("missing" to "R2_BUCKET_NAME", "source" to "github"), d)
    }

    @Test fun ringBufferKeepsTheNewest() {
        Diagnostics.clear()
        repeat(25) { Diagnostics.record(Diagnosis("m$it")) }
        val recent = Diagnostics.recent()
        assertEquals(20, recent.size)
        assertEquals("m24", recent.first().message)
        Diagnostics.clear()
    }
}

class CapabilitiesTest {
    private fun pc(version: String?, caps: List<String>? = null, key: String? = null) =
        Device(id = "d", kind = "desktop", name = "Desk", platform = "win32", appVersion = version, capabilities = caps, encryptionKey = key)

    @Test fun olderDesktopLacksNewFeatures() {
        val r = DesktopCapabilities.missing(pc("1.0.2"), DesktopFeature.Todos)
        assertNotNull(r)
        assertEquals("1.0.2", r!!.currentVersion)
        assertEquals("1.0.3", r.requiredVersion)
        assertNull(DesktopCapabilities.missing(pc("1.0.2"), DesktopFeature.Tree))
        assertNull(DesktopCapabilities.missing(pc("1.0.3", key = "PEM"), DesktopFeature.Providers))
    }

    @Test fun reportedCapabilitiesWin() {
        assertNull(DesktopCapabilities.missing(pc("1.0.2", listOf("relay.todos")), DesktopFeature.Todos))
        assertNotNull(DesktopCapabilities.missing(pc("9.9.9", listOf("relay.tree")), DesktopFeature.Providers))
    }

    @Test fun encryptedKeysNeedThePublicKey() {
        assertNotNull(DesktopCapabilities.missing(pc("1.0.3"), DesktopFeature.ProviderKeys))
        assertNull(DesktopCapabilities.missing(pc("1.0.3", key = "PEM"), DesktopFeature.ProviderKeys))
        assertNotNull(DesktopCapabilities.missing(pc("1.0.3", listOf("provider-keys.encrypted")), DesktopFeature.ProviderKeys))
    }

    @Test fun phonesAreNeverGated() {
        assertNull(DesktopCapabilities.missing(Device(id = "p", kind = "mobile", name = "Phone", platform = "android"), DesktopFeature.Todos))
        assertNull(DesktopCapabilities.missing(null, DesktopFeature.Todos))
    }
}

class StatsFormatTest {
    @Test fun durations() {
        assertEquals("4h 27m", StatsFormat.duration((4 * 60 + 27) * 60_000L))
        assertEquals("2h", StatsFormat.duration(2 * 3_600_000L))
        assertEquals("12m", StatsFormat.duration(12 * 60_000L + 59_000))
        assertEquals("0m", StatsFormat.duration(0))
        assertEquals("0m", StatsFormat.duration(-5))
        assertEquals("25h 1m", StatsFormat.duration(25 * 3_600_000L + 60_000))
    }

    @Test fun counts() {
        assertEquals("1,234", StatsFormat.count(1234))
        assertEquals("0", StatsFormat.count(0))
    }

    private fun a(progress: Double, target: Double, unit: String = "count", unlocked: Boolean = false) =
        Achievement("x", progress = progress, target = target, unit = unit, unlocked = unlocked)

    @Test fun achievementProgress() {
        assertEquals("3 / 5", StatsFormat.progress(a(3.0, 5.0)))
        assertEquals(0.6f, StatsFormat.fraction(a(3.0, 5.0)), 0.0001f)
        assertEquals("0 / 1,000", StatsFormat.progress(a(0.0, 1000.0)))
        assertEquals(0f, StatsFormat.fraction(a(0.0, 1000.0)), 0f)
        // Never more than the target, never below zero.
        assertEquals(1f, StatsFormat.fraction(a(12.0, 10.0)), 0f)
        assertEquals("10 / 10", StatsFormat.progress(a(12.0, 10.0)))
        assertEquals(0f, StatsFormat.fraction(a(-1.0, 10.0)), 0f)
        // Unlocked is always full; a zero target can't divide.
        assertEquals(1f, StatsFormat.fraction(a(0.0, 3.0, unlocked = true)), 0f)
        assertEquals("3 / 3", StatsFormat.progress(a(1.0, 3.0, unlocked = true)))
        assertEquals(0f, StatsFormat.fraction(a(0.0, 0.0)), 0f)
    }

    @Test fun timeAchievementsShowHoursAndMinutes() {
        val night = a(72 * 60_000.0, 2 * 3_600_000.0, unit = "ms")
        assertEquals("1h 12m / 2h", StatsFormat.progress(night))
        assertEquals(0.6f, StatsFormat.fraction(night), 0.0001f)
    }

    @Test fun unlockedSummary() {
        assertEquals("1 of 2 unlocked", StatsFormat.unlockedSummary(listOf(a(1.0, 1.0, unlocked = true), a(0.0, 1.0))))
    }

    @Test fun projectStatusChangeUpdatesCounts() {
        val s = ProfileStats(projects = ProjectStats(total = 2, active = 2, list = listOf(ProjectStat("a"), ProjectStat("b"))))
        val t = s.withProjectStatus("a", "completed")
        assertEquals(1, t.projects.active)
        assertEquals(1, t.projects.completed)
        assertEquals("completed", t.projects.list.first { it.id == "a" }.status)
        assertEquals(s, s.withProjectStatus("zzz", "archived"))
    }

    @Test fun statsDecodeWithMissingFields() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val s = json.decodeFromString(ProfileStats.serializer(), """{"timeZone":"Asia/Kolkata","codingTime":{"totalMs":16020000},"achievements":[{"id":"marathon","title":"Marathon","progress":3600000,"target":86400000,"unit":"ms","unlocked":false,"unlockedAt":null}],"rules":{"codingTime":"busy to idle"}}""")
        assertEquals("4h 27m", StatsFormat.duration(s.codingTime.totalMs))
        assertEquals(0, s.projects.total)
        assertEquals(0L, s.code.commits)
        assertEquals("1h / 24h", StatsFormat.progress(s.achievements.single()))
        assertEquals("busy to idle", s.rules["codingTime"])
    }
}

class UpdateVersionTest {
    @Test fun numericComparison() {
        assertTrue(AppUpdater.compareVersions("1.0.10", "1.0.9") > 0)
        assertTrue(AppUpdater.compareVersions("1.0.6", "1.0.6") == 0)
        assertTrue(AppUpdater.compareVersions("v1.0.6", "1.0.6") == 0)
        assertTrue(AppUpdater.compareVersions("1.1", "1.0.9") > 0)
        assertTrue(AppUpdater.compareVersions("1.0", "1.0.0") == 0)
        assertTrue(AppUpdater.compareVersions("2.0.0", "10.0.0") < 0)
    }

    @Test fun isNewer() {
        assertTrue(AppUpdater.isNewer("1.0.7", "1.0.6"))
        assertTrue(AppUpdater.isNewer("v1.1.0", "1.0.6"))
        assertFalse(AppUpdater.isNewer("1.0.6", "1.0.6"))
        assertFalse(AppUpdater.isNewer("1.0.3", "1.0.6"))
        assertFalse(AppUpdater.isNewer("1.0.6-beta.1", "1.0.6"))
        assertFalse(AppUpdater.isNewer("", "1.0.6"))
    }
}

class AvatarUploadRequestTest {
    private val bytes = ByteArray(123_457) { (it % 251).toByte() }

    @Test fun fixedLengthBodyWithTheSignedType() {
        val req = ApiClient.signedPutRequest("https://bucket.example.com/users/u/profile/a.jpg?X-Amz-Signature=sig", "image/jpeg", bytes, mapOf("Content-Type" to "image/jpeg"))
        val body = req.body!!
        assertEquals("PUT", req.method)
        // A known length: OkHttp sends Content-Length (never chunked transfer encoding), matching the signed size.
        assertEquals(bytes.size.toLong(), body.contentLength())
        assertEquals("image/jpeg", body.contentType().toString())
        assertEquals("image/jpeg", req.header("Content-Type"))
        assertNull(req.header("Transfer-Encoding"))
        assertNull(req.header("Authorization"))
        val sink = Buffer()
        body.writeTo(sink)
        assertArrayEquals(bytes, sink.readByteArray())
        // Writing twice (a retry) sends the same bytes.
        val again = Buffer()
        body.writeTo(again)
        assertEquals(bytes.size.toLong(), again.size)
    }

    @Test fun signedHeadersAreKeptButLengthAndEncodingAreNotOverridden() {
        val req = ApiClient.signedPutRequest(
            "https://bucket.example.com/k", "image/jpeg", bytes,
            mapOf("content-type" to "image/png", "Content-Length" to "1", "Transfer-Encoding" to "chunked", "x-amz-meta-a" to "b"),
        )
        assertEquals("image/png", req.body!!.contentType().toString())
        assertEquals(bytes.size.toLong(), req.body!!.contentLength())
        assertNull(req.header("Content-Length"))
        assertNull(req.header("Transfer-Encoding"))
        assertEquals("b", req.header("x-amz-meta-a"))
    }
}
