package com.hermes.android.ui.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

object HxShape {
    val bubble: Shape = RoundedCornerShape(18.dp)
    val card: Shape = RoundedCornerShape(16.dp)
    val chip: Shape = RoundedCornerShape(18.dp)
    val button: Shape = RoundedCornerShape(24.dp)
    val fab: Shape = CircleShape
    val topBar: Shape = RoundedCornerShape(0.dp)
    val bottomSheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val searchField: Shape = RoundedCornerShape(24.dp)
    val drawer: Shape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp)
    val avatar: Shape = CircleShape
}
