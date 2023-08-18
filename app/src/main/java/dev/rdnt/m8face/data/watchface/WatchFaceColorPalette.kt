/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.rdnt.m8face.data.watchface

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.wear.watchface.complications.rendering.ComplicationDrawable

/**
 * Color resources and drawable id needed to render the watch face. Translated from
 * [ColorStyle] constant ids to actual resources with context at run time.
 *
 * This is only needed when the watch face is active.
 *
 * Note: We do not use the context to generate a [ComplicationDrawable] from the
 * complicationStyleDrawableId (representing the style), because a new, separate
 * [ComplicationDrawable] is needed for each complication. Because the renderer will loop through
 * all the complications and there can be more than one, this also allows the renderer to create
 * as many [ComplicationDrawable]s as needed.
 */
data class WatchFaceColorPalette(
  val primaryColor: Int,
  val secondaryColor: Int,
  val tertiaryColor: Int,
) {
  companion object {
    /**
     * Converts [ColorStyle] to [WatchFaceColorPalette].
     */
    fun convertToWatchFaceColorPalette(
      context: Context,
      colorStyle: ColorStyle,
    ): WatchFaceColorPalette {
      return WatchFaceColorPalette(
        primaryColor = context.getColor(colorStyle.primaryColorId),
        secondaryColor = context.getColor(colorStyle.secondaryColorId),
        tertiaryColor = context.getColor(colorStyle.tertiaryColorId),
      )
    }
  }
}
