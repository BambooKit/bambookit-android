package com.bambookit.android.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionAnswerPayloadTest {
    private val single = QuestionSpec(
        header = "Database", question = "Which database?",
        options = listOf(QuestionOption("Postgres"), QuestionOption("SQLite")),
    )
    private val multiple = QuestionSpec(
        question = "Which platforms?", multiple = true,
        options = listOf(QuestionOption("Android"), QuestionOption("iOS"), QuestionOption("Web")),
    )
    private val noCustom = QuestionSpec(question = "Proceed?", custom = false, options = listOf(QuestionOption("Yes"), QuestionOption("No")))

    @Test fun singleChoiceSendsTheChosenLabel() {
        assertEquals(listOf(listOf("SQLite")), buildAnswers(listOf(single), listOf(QuestionDraft(setOf("SQLite")))))
    }

    @Test fun multipleChoiceKeepsOptionOrderAndAddsTypedText() {
        val d = QuestionDraft(setOf("Web", "Android"), custom = "  Desktop ")
        assertEquals(listOf(listOf("Android", "Web", "Desktop")), buildAnswers(listOf(multiple), listOf(d)))
    }

    @Test fun typedTextWinsForSingleChoice() {
        assertEquals(listOf(listOf("MySQL")), buildAnswers(listOf(single), listOf(QuestionDraft(setOf("Postgres"), "MySQL"))))
    }

    @Test fun typedTextIgnoredWhenCustomIsNotAllowed() {
        assertNull(buildAnswers(listOf(noCustom), listOf(QuestionDraft(custom = "Maybe"))))
        assertEquals(listOf(listOf("No")), buildAnswers(listOf(noCustom), listOf(QuestionDraft(setOf("No"), "Maybe"))))
    }

    @Test fun unansweredQuestionBlocksSubmit() {
        assertNull(buildAnswers(listOf(single, multiple), listOf(QuestionDraft(setOf("Postgres")))))
        assertNull(buildAnswers(listOf(single), listOf(QuestionDraft(custom = "   "))))
        assertNull(buildAnswers(emptyList(), emptyList()))
    }

    @Test fun oneAnswerListPerQuestionInOrder() {
        val answers = buildAnswers(listOf(single, multiple), listOf(QuestionDraft(setOf("Postgres")), QuestionDraft(setOf("iOS"))))
        assertEquals(listOf(listOf("Postgres"), listOf("iOS")), answers)
    }

    @Test fun unknownLabelsAreNotSent() {
        assertNull(buildAnswers(listOf(single), listOf(QuestionDraft(setOf("Oracle")))))
    }

    @Test fun togglingAndTypingFollowSingleAndMultipleRules() {
        val picked = QuestionDraft(custom = "x").toggle(single, "Postgres")
        assertEquals(QuestionDraft(setOf("Postgres"), ""), picked)
        assertEquals(QuestionDraft(setOf("SQLite"), ""), picked.toggle(single, "SQLite"))
        assertEquals(QuestionDraft(emptySet(), "My own"), picked.typed(single, "My own"))
        val multi = QuestionDraft().toggle(multiple, "Web").toggle(multiple, "iOS").toggle(multiple, "Web")
        assertEquals(setOf("iOS"), multi.selected)
        assertEquals(QuestionDraft(setOf("iOS"), "Linux"), multi.typed(multiple, "Linux"))
    }

    @Test fun requestBodyIsAnswersArrayOfArrays() {
        val body = ApiClient.answerPayload(listOf(listOf("Postgres"), listOf("Android", "Web")))
        assertEquals("""{"answers":[["Postgres"],["Android","Web"]]}""", body.toString())
    }

    @Test fun questionApprovalDecodesFromTheApi() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val a = json.decodeFromString(
            Approval.serializer(),
            """{"id":"apr_1","sessionId":"s1","permission":"question","kind":"question","status":"PENDING",
               "questions":[{"header":"DB","question":"Which?","options":[{"label":"A","description":"first"},{"label":"B"}],"multiple":true,"custom":false}],
               "answers":null,"patterns":[]}""",
        )
        assertTrue(a.isQuestion)
        val q = a.questions!!.single()
        assertTrue(q.allowsMultiple)
        assertFalse(q.allowsCustom)
        assertEquals("first", q.options[0].description)
        val p = json.decodeFromString(Approval.serializer(), """{"id":"apr_2","permission":"bash","status":"PENDING"}""")
        assertFalse(p.isQuestion)
    }
}

class NotificationDedupeTest {
    @Test fun eachIdRingsOnce() {
        val gate = NotificationGate()
        assertTrue(gate.shouldNotify("ntf_1", eventSeq = 11, connectionStartSeq = 10))
        assertFalse(gate.shouldNotify("ntf_1", eventSeq = 11, connectionStartSeq = 10))
        assertTrue(gate.shouldNotify("ntf_2", eventSeq = 12, connectionStartSeq = 10))
    }

    @Test fun eventsFromBeforeTheConnectionStartAreSilent() {
        val gate = NotificationGate()
        assertFalse(gate.shouldNotify("ntf_old", eventSeq = 9, connectionStartSeq = 10))
        assertFalse(gate.shouldNotify("ntf_edge", eventSeq = 10, connectionStartSeq = 10))
        // Replayed again after another reconnect: still silent (already seen).
        assertFalse(gate.shouldNotify("ntf_old", eventSeq = 9, connectionStartSeq = 20))
    }

    @Test fun liveOnlyEventsAndUnknownStartRing() {
        val gate = NotificationGate()
        assertTrue(gate.shouldNotify("ntf_live", eventSeq = -1, connectionStartSeq = 10))
        assertTrue(gate.shouldNotify("ntf_x", eventSeq = 5, connectionStartSeq = null))
        assertFalse(gate.shouldNotify("", eventSeq = 50, connectionStartSeq = 10))
    }

    @Test fun memoryIsBounded() {
        val gate = NotificationGate(capacity = 3)
        listOf("a", "b", "c", "d").forEach { assertTrue(gate.shouldNotify(it, 100, 10)) }
        // "a" was evicted, so a later duplicate would ring again; the most recent ones are still remembered.
        assertFalse(gate.shouldNotify("d", 100, 10))
        assertTrue(gate.shouldNotify("a", 100, 10))
    }

    @Test fun channelsByType() {
        assertEquals(NotifyKind.Request, notifyKindOf("approval.required"))
        assertEquals(NotifyKind.Request, notifyKindOf("question.asked"))
        assertEquals(NotifyKind.SessionUpdate, notifyKindOf("session.completed"))
        assertEquals(NotifyKind.SessionUpdate, notifyKindOf("session.failed"))
    }
}

class NicknameValidationTest {
    @Test fun trimsAndAccepts() {
        assertEquals(NicknameCheck.Ok("Satyam"), validateNickname("  Satyam "))
        assertEquals(NicknameCheck.Ok("A"), validateNickname("A"))
        assertEquals(NicknameCheck.Ok("x".repeat(40)), validateNickname("x".repeat(40)))
    }

    @Test fun rejectsEmptyTooLongAndControlCharacters() {
        assertTrue(validateNickname("") is NicknameCheck.Invalid)
        assertTrue(validateNickname("    ") is NicknameCheck.Invalid)
        assertTrue(validateNickname("x".repeat(41)) is NicknameCheck.Invalid)
        assertTrue(validateNickname("bad\u0007name") is NicknameCheck.Invalid)
        assertTrue(validateNickname("two\nlines") is NicknameCheck.Invalid)
    }

    @Test fun lengthCountsLikeTheApi() {
        // 20 emoji are 40 UTF-16 code units (the API's limit), 21 are too many.
        assertTrue(validateNickname("\uD83D\uDE00".repeat(20)) is NicknameCheck.Ok)
        assertTrue(validateNickname("\uD83D\uDE00".repeat(21)) is NicknameCheck.Invalid)
    }
}

class RelayRequestTest {
    @Test fun pathsUseForwardSlashes() {
        assertEquals("src/main/App.kt", ApiClient.relayPath("src\\main\\App.kt"))
        assertEquals("src/a b/file name.ts", ApiClient.relayPath("./src/a b/file name.ts"))
        assertEquals("C:/Users/me/proj/x.ts", ApiClient.relayPath("C:\\Users\\me\\proj\\x.ts"))
        assertEquals("/home/me/app/x.ts", ApiClient.relayPath("/home/me/app/x.ts"))
        assertEquals("src", ApiClient.relayPath("src/"))
        assertEquals("", ApiClient.relayPath(""))
    }

    @Test fun olderDesktopIsRecognised() {
        val e = contentErrorOf(ApiException("Unsupported request tree", 422, "DESKTOP_ERROR"), "x")
        assertTrue(e.desktopOutdated)
        val other = contentErrorOf(ApiException("Binary files cannot be shown", 422, "DESKTOP_ERROR"), "x")
        assertEquals("DESKTOP_ERROR", other.code)
        assertTrue(contentErrorOf(ApiException("slow", 503, "DESKTOP_TIMEOUT"), "x").timedOut)
    }
}
