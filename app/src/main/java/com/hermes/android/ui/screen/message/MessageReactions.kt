package com.hermes.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.MessageReaction
import com.hermes.android.ui.viewmodel.QUICK_REACTIONS

/**
 * The six quick reactions (the desktop's picker), on top of a message's touch-and-hold
 * menu where iOS puts its Tapbacks. The one already picked is marked; picking it again
 * takes it back.
 */
@Composable
internal fun ReactionQuickRow(selected: String?, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        QUICK_REACTIONS.forEach { emoji ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        if (emoji == selected) {
                            Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        } else {
                            Modifier
                        },
                    )
                    .clickable(role = Role.Button) { onSelect(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = 22.sp)
            }
        }
    }
}

/**
 * The reactions a message carries, flat: no pill and no border, just the emoji in the
 * register of the action row (the desktop's ReactionBadge). With [onRetract] the
 * user's own takes itself back when tapped; the agent's only shows.
 */
@Composable
internal fun ReactionBadge(
    reactions: List<MessageReaction>,
    modifier: Modifier = Modifier,
    onRetract: (() -> Unit)? = null,
) {
    if (reactions.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        reactions.forEach { reaction ->
            Text(
                text = reaction.emoji,
                fontSize = 15.sp,
                modifier = if (reaction.isMine && onRetract != null) {
                    Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onRetract() }
                        .padding(4.dp)
                } else {
                    Modifier.padding(4.dp)
                },
            )
        }
    }
}

/**
 * A reply's one reaction slot at the end of its action row, as on the desktop: the
 * add-reaction glyph while it has none, the reactions themselves once it does. Either
 * way a tap opens the quick row, to pick, switch or take back.
 */
@Composable
internal fun ReactionSlot(
    reactions: List<MessageReaction>,
    enabled: Boolean,
    onReact: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        if (reactions.isEmpty()) {
            MessageActionIcon(
                icon = HxIcons.SmilePlus,
                contentDescription = t("React", "واکنش"),
                onClick = { open = true },
            )
        } else {
            ReactionBadge(
                reactions = reactions,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled, role = Role.Button) { open = true }
                    .padding(horizontal = 2.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ReactionQuickRow(
                selected = reactions.firstOrNull { it.isMine }?.emoji,
                onSelect = { open = false; onReact(it) },
            )
        }
    }
}
