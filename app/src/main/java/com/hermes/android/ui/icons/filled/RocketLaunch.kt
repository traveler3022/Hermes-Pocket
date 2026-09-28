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

public val Icons.Filled.RocketLaunch: ImageVector
    get() {
        if (_rocketLaunch != null) {
            return _rocketLaunch!!
        }
        _rocketLaunch = materialIcon(name = "Filled.RocketLaunch") {
            materialPath {
                moveTo(9.19f, 6.35f)
                curveToRelative(-2.04f, 2.29f, -3.44f, 5.58f, -3.57f, 5.89f)
                lineTo(2.0f, 10.69f)
                lineToRelative(4.05f, -4.05f)
                curveToRelative(0.47f, -0.47f, 1.15f, -0.68f, 1.81f, -0.55f)
                lineTo(9.19f, 6.35f)
                lineTo(9.19f, 6.35f)
                close()
                moveTo(11.17f, 17.0f)
                curveToRelative(0.0f, 0.0f, 3.74f, -1.55f, 5.89f, -3.7f)
                curveToRelative(5.4f, -5.4f, 4.5f, -9.62f, 4.21f, -10.57f)
                curveToRelative(-0.95f, -0.3f, -5.17f, -1.19f, -10.57f, 4.21f)
                curveTo(8.55f, 9.09f, 7.0f, 12.83f, 7.0f, 12.83f)
                lineTo(11.17f, 17.0f)
                close()
                moveTo(17.65f, 14.81f)
                curveToRelative(-2.29f, 2.04f, -5.58f, 3.44f, -5.89f, 3.57f)
                lineTo(13.31f, 22.0f)
                lineToRelative(4.05f, -4.05f)
                curveToRelative(0.47f, -0.47f, 0.68f, -1.15f, 0.55f, -1.81f)
                lineTo(17.65f, 14.81f)
                lineTo(17.65f, 14.81f)
                close()
                moveTo(9.0f, 18.0f)
                curveToRelative(0.0f, 0.83f, -0.34f, 1.58f, -0.88f, 2.12f)
                curveTo(6.94f, 21.3f, 2.0f, 22.0f, 2.0f, 22.0f)
                reflectiveCurveToRelative(0.7f, -4.94f, 1.88f, -6.12f)
                curveTo(4.42f, 15.34f, 5.17f, 15.0f, 6.0f, 15.0f)
                curveTo(7.66f, 15.0f, 9.0f, 16.34f, 9.0f, 18.0f)
                close()
                moveTo(13.0f, 9.0f)
                curveToRelative(0.0f, -1.1f, 0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                reflectiveCurveTo(13.0f, 10.1f, 13.0f, 9.0f)
                close()
            }
        }
        return _rocketLaunch!!
    }

private var _rocketLaunch: ImageVector? = null
