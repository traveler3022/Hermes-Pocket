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

package com.hermes.android.ui.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Filled.DataUsage: ImageVector
    get() {
        if (_dataUsage != null) {
            return _dataUsage!!
        }
        _dataUsage = materialIcon(name = "Filled.DataUsage") {
            materialPath {
                moveTo(13.0f, 2.05f)
                verticalLineToRelative(3.03f)
                curveToRelative(3.39f, 0.49f, 6.0f, 3.39f, 6.0f, 6.92f)
                curveToRelative(0.0f, 0.9f, -0.18f, 1.75f, -0.48f, 2.54f)
                lineToRelative(2.6f, 1.53f)
                curveToRelative(0.56f, -1.24f, 0.88f, -2.62f, 0.88f, -4.07f)
                curveToRelative(0.0f, -5.18f, -3.95f, -9.45f, -9.0f, -9.95f)
                close()
                moveTo(12.0f, 19.0f)
                curveToRelative(-3.87f, 0.0f, -7.0f, -3.13f, -7.0f, -7.0f)
                curveToRelative(0.0f, -3.53f, 2.61f, -6.43f, 6.0f, -6.92f)
                verticalLineTo(2.05f)
                curveToRelative(-5.06f, 0.5f, -9.0f, 4.76f, -9.0f, 9.95f)
                curveToRelative(0.0f, 5.52f, 4.47f, 10.0f, 9.99f, 10.0f)
                curveToRelative(3.31f, 0.0f, 6.24f, -1.61f, 8.06f, -4.09f)
                lineToRelative(-2.6f, -1.53f)
                curveTo(16.17f, 17.98f, 14.21f, 19.0f, 12.0f, 19.0f)
                close()
            }
        }
        return _dataUsage!!
    }

private var _dataUsage: ImageVector? = null
