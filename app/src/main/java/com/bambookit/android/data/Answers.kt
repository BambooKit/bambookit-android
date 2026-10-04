package com.bambookit.android.data

/** What the user picked for one question: option labels and/or typed text. */
data class QuestionDraft(val selected: Set<String> = emptySet(), val custom: String = "")

/** API limit for one answer string. */
const val MAX_ANSWER_CHARS = 2000

/**
 * Builds the body of POST /v1/approvals/:id/answer: one list of answers per question, in question order.
 *
 * - Chosen options are sent by label, in the order the agent listed them.
 * - Single-choice questions send one answer: typed text (when allowed and filled in) wins over a chosen option.
 * - Multiple-choice questions send every chosen option, plus typed text when allowed and filled in.
 * - Typed text is ignored when the question does not allow it (custom == false).
 *
 * Returns null while any question is still unanswered (Submit stays disabled).
 */
fun buildAnswers(questions: List<QuestionSpec>, drafts: List<QuestionDraft>): List<List<String>>? {
    if (questions.isEmpty()) return null
    return questions.mapIndexed { i, q ->
        val draft = drafts.getOrNull(i) ?: QuestionDraft()
        val labels = q.options.map { it.label }.filter { it.isNotBlank() && it in draft.selected }.distinct()
        val custom = if (q.allowsCustom) draft.custom.trim().take(MAX_ANSWER_CHARS) else ""
        val answer = if (q.allowsMultiple) {
            labels + listOfNotNull(custom.takeIf { it.isNotEmpty() && it !in labels })
        } else {
            if (custom.isNotEmpty()) listOf(custom) else labels.take(1)
        }
        if (answer.isEmpty()) return null
        answer
    }
}

/** Updates a draft after the user taps an option. Single choice: picking an option clears typed text. */
fun QuestionDraft.toggle(q: QuestionSpec, label: String): QuestionDraft =
    if (q.allowsMultiple) copy(selected = if (label in selected) selected - label else selected + label)
    else QuestionDraft(selected = if (label in selected) emptySet() else setOf(label), custom = "")

/** Updates a draft after the user types. Single choice: typing replaces the chosen option. */
fun QuestionDraft.typed(q: QuestionSpec, text: String): QuestionDraft =
    if (q.allowsMultiple) copy(custom = text) else QuestionDraft(selected = if (text.isBlank()) selected else emptySet(), custom = text)

// ================================================================== session title

const val SESSION_TITLE_MAX = 200

/** Session titles are 1-200 characters after trimming, on one line (same rules as RENAME_SESSION). */
fun validateSessionTitle(raw: String): NicknameCheck {
    val title = raw.trim()
    return when {
        title.isEmpty() -> NicknameCheck.Invalid("Enter a title")
        title.length > SESSION_TITLE_MAX -> NicknameCheck.Invalid("Titles can be up to $SESSION_TITLE_MAX characters")
        title.any { it == '\n' || it == '\r' } -> NicknameCheck.Invalid("Use a single line")
        else -> NicknameCheck.Ok(title)
    }
}

/** Applies a like/unlike to every copy of a session in a list (optimistic update or rollback). */
fun List<Session>.withStarred(id: String, starred: Boolean): List<Session> = map { if (it.id == id) it.copy(starred = starred) else it }

// ================================================================== nickname

const val NICKNAME_MAX = 40

sealed interface NicknameCheck {
    data class Ok(val value: String) : NicknameCheck
    data class Invalid(val reason: String) : NicknameCheck
}

/** Nicknames are 1-40 characters after trimming, without control characters (same rules as PATCH /v1/me). */
fun validateNickname(raw: String): NicknameCheck {
    val name = raw.trim()
    return when {
        name.isEmpty() -> NicknameCheck.Invalid("Enter a nickname")
        // Same unit as the API's check (JavaScript string length = UTF-16 code units).
        name.length > NICKNAME_MAX -> NicknameCheck.Invalid("Nicknames can be up to $NICKNAME_MAX characters")
        name.any { it.isISOControl() } -> NicknameCheck.Invalid("Nickname contains invalid characters")
        else -> NicknameCheck.Ok(name)
    }
}
