package com.hermes.android.data

import com.hermes.android.domain.messaging.MessagingPairing.Request
import com.hermes.android.domain.messaging.MessagingPairing.User
import com.hermes.android.domain.messaging.PairingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingParseTest {

    @Test
    fun `pending and approved from hermes pairing list`() {
        // Captured from Hermes 0.21.7, with an update notice before it as cli.exec can return.
        val output = """
            hint: `python-telegram-bot` (v22.8) was included — run `hermes update` to finish it

              Pending Pairing Requests (3):
              Platform     Request ID         User ID              Name                 Age
              --------     ----------         -------              ----                 ---
              discord      70cb5fb877c568e7   998877                                    1m ago
              telegram     961b5263d6833d58   123456789            Ali Reza             12m ago
              whatsapp     -                  4477@s.whatsapp.net  Old                  300m ago

              Approve with: hermes pairing approve <platform> <request-id>
              The code the bot DM'd the user also works if they relay it.

              Approved Users (2):
              Platform     User ID              Name
              --------     -------              ----
              slack        U07AB
              telegram     555                  Sara K

        """.trimIndent()
        val list = parsePairingList(output)
        assertEquals(
            listOf(
                Request("discord", "70cb5fb877c568e7", "998877", "", 1),
                Request("telegram", "961b5263d6833d58", "123456789", "Ali Reza", 12),
                Request("whatsapp", null, "4477@s.whatsapp.net", "Old", 300),
            ),
            list.pending,
        )
        assertEquals(listOf(User("slack", "U07AB", ""), User("telegram", "555", "Sara K")), list.approved)
    }

    @Test
    fun `nothing yet`() {
        val list = parsePairingList("No pairing data found. No one has tried to pair yet~\n")
        assertTrue(list.pending.isEmpty() && list.approved.isEmpty())
        val onlyApproved = parsePairingList(
            "\n  No pending pairing requests.\n\n  No approved users.\n\n",
        )
        assertTrue(onlyApproved.pending.isEmpty() && onlyApproved.approved.isEmpty())
    }

    @Test
    fun `approve outcome reads the CLI verdict`() {
        assertEquals(
            PairingResult.Done,
            pairingOutcome("\n  Approved! User Ali (123) on telegram can now use the bot~\n  They'll be recognized.\n", "Approved!"),
        )
        assertEquals(
            PairingResult.Failed(
                "Pairing request or code 'abc' not found or expired for platform 'telegram'. " +
                    "Run 'hermes pairing list' to see pending requests.",
            ),
            pairingOutcome(
                "notice line\n\n  Pairing request or code 'abc' not found or expired for platform 'telegram'.\n" +
                    "  Run 'hermes pairing list' to see pending requests.\n\n",
                "Approved!",
            ),
        )
    }
}
