/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hermes.android.ui.icons.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes

public val Icons.Outlined.PermMedia: ImageVector
    get() {
        _permMedia?.let { return it }
        return materialIcon(name = "Outlined.PermMedia") {
            addPath(
                pathData = addPathNodes(
                    "M2,6H0v5h0.01L0,20c0,1.1 0.9,2 2,2h18v-2H2V6z" +
                        "M7,15h14l-3.5,-4.5l-2.5,3.01L11.5,9L7,15z" +
                        "M22,4h-8l-2,-2H6C4.9,2 4.01,2.9 4.01,4L4,16c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6C24,4.9 23.1,4 22,4z" +
                        "M22,16H6V4h5.17l1.41,1.41L13.17,6H22V16z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.also { _permMedia = it }
    }

private var _permMedia: ImageVector? = null
