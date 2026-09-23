package com.hermes.android.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.hermes.android.ui.design.HxIcons
import com.hermes.android.ui.design.HxGradientActionPill
import com.hermes.android.ui.design.HxHeaderCircleButton
import com.hermes.android.ui.design.hxSoftShadow
import com.hermes.android.ui.i18n.t
import com.hermes.android.ui.viewmodel.SessionActivity
import com.hermes.android.ui.viewmodel.SessionItem
import com.hermes.android.ui.viewmodel.TodoItemUi
import com.hermes.android.ui.viewmodel.TodoStatus

@Composable
internal fun AgentTodoCard(todos: List<TodoItemUi>) {
    var expanded by remember { mutableStateOf(false) }
    val done = todos.count { it.status == TodoStatus.COMPLETED }
    val current = todos.firstOrNull { it.status == TodoStatus.IN_PROGRESS }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = t("Tasks", "کارها") + " $done/${todos.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!expanded && current != null) {
                    Text(
                        text = current.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                todos.forEach { todo ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = when (todo.status) {
                                TodoStatus.COMPLETED -> "✓"
                                TodoStatus.IN_PROGRESS -> "▸"
                                TodoStatus.CANCELLED -> "✕"
                                TodoStatus.PENDING -> "○"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (todo.status) {
                                TodoStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                                TodoStatus.CANCELLED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = todo.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (todo.status == TodoStatus.COMPLETED ||
                                todo.status == TodoStatus.CANCELLED
                            ) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textDecoration = if (todo.status == TodoStatus.COMPLETED) {
                                TextDecoration.LineThrough
                            } else null,
                        )
                    }
                }
            }
        }
    }
}
