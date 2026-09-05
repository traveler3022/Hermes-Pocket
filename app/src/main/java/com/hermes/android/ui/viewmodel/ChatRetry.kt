package com.hermes.android.ui.viewmodel

/**
 * What a "retry last message" resubmits, and where the transcript has to be
 * cut for it.
 *
 * [ordinal] is the position of that message among the user TURNS of the
 * session — the numbering `prompt.submit`'s `truncate_before_user_ordinal`
 * speaks. Steer lines are echoed into the transcript for the reader but were
 * never user turns on the server, so they are excluded from both the ordinal
 * and the choice of which message to resend; counting them shifted every
 * ordinal after a steer by one and rewound the wrong turn.
 *
 * [index] is the position in the transcript list, so the caller can drop
 * everything that came after it before resending.
 */
internal data class RetryTarget(
    val message: ChatMessage.User,
    val ordinal: Int,
    val index: Int,
)

/** The last real user turn in this transcript, or null when there is none. */
internal fun List<ChatMessage>.retryTarget(): RetryTarget? {
    var ordinal = -1
    var found: RetryTarget? = null
    forEachIndexed { index, msg ->
        if (msg is ChatMessage.User && !msg.isSteer) {
            ordinal += 1
            found = RetryTarget(message = msg, ordinal = ordinal, index = index)
        }
    }
    return found
}
