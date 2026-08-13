package com.hermes.android.ui.components.thinking

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown

private val ToolCardColors: @Composable () -> Color = {
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
}

private val ToolBorderColor: @Composable () -> Color = {
    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
}

private val ShimmerBrush = Brush.linearGradient(
    colors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f)
    )
)

/**
 * کارت نمایش ابزار (Tool Card) با افکت شیمِر
 * الهام گرفته از Aether اما با استایل Hermes
 */
@Composable
fun HxToolCard(
    toolName: String,
    status: ToolStatus = ToolStatus.Running,
    output: String? = null,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    // The card stays lightweight; status color communicates activity.

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = ToolCardColors(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // هدر ابزار
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // آیکون وضعیت
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when (status) {
                                    ToolStatus.Running -> MaterialTheme.colorScheme.primary
                                    ToolStatus.Success -> MaterialTheme.colorScheme.tertiary
                                    ToolStatus.Error -> MaterialTheme.colorScheme.error
                                }
                            )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = toolName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // دکمه انبساط اگر خروجی وجود دارد
                if (output != null) {
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
                        )
                    }
                }
            }

            // وضعیت در حال اجرا با شیمِر
            if (status == ToolStatus.Running) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.LightGray.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(4.dp)
                            .background(ShimmerBrush)
                    )
                }
            }

            // خروجی قابل انبساط
            if (output != null && (isExpanded || status != ToolStatus.Running)) {
                Spacer(modifier = Modifier.height(8.dp))
                
                if (status == ToolStatus.Running) {
                    // متن شیمِر دار
                    Text(
                        text = "در حال پردازش...",
                        fontSize = 13.sp,
                        color = Color.Transparent,
                        modifier = Modifier.background(ShimmerBrush).padding(vertical = 4.dp)
                    )
                } else {
                    // بلوک کد خروجی
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = output,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class ToolStatus {
    Running,
    Success,
    Error
}
