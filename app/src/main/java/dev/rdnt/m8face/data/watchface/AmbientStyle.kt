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
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.ListUserStyleSetting
import dev.rdnt.m8face.R

enum class AmbientStyle(
  val id: String,
  @StringRes val nameResourceId: Int,
  @DrawableRes val iconResourceId: Int,
) {
  OUTLINE(
    id = "outline",
    nameResourceId = R.string.outline_ambient_style_name,
//    iconResourceId = R.drawable.outline_style_icon,
    iconResourceId = R.drawable.mauve_style_icon, // TODO @rdnt fix icon
  ),
  BIG_OUTLINE(
    id = "big_outline",
    nameResourceId = R.string.big_outline_ambient_style_name, // TODO
//    iconResourceId = R.drawable.outline_style_icon,
    iconResourceId = R.drawable.mauve_style_icon, // TODO @rdnt fix icon
  ),
  BOLD_OUTLINE(
    id = "bold_outline",
    nameResourceId = R.string.bold_outline_ambient_style_name,
//    iconResourceId = R.drawable.bold_outline_style_icon,
    iconResourceId = R.drawable.mauve_style_icon, // TODO @rdnt fix icon
  ),
  BIG_BOLD_OUTLINE(
    id = "big_bold_outline",
    nameResourceId = R.string.big_bold_outline_ambient_style_name, // TODO
//    iconResourceId = R.drawable.bold_outline_style_icon,
    iconResourceId = R.drawable.mauve_style_icon, // TODO @rdnt fix icon
  ),
  FILLED(
    id = "filled",
    nameResourceId = R.string.filled_ambient_style_name,
//    iconResourceId = R.drawable.filled_style_icon,
    iconResourceId = R.drawable.mauve_style_icon, // TODO @rdnt fix icon
  ),
  BIG_FILLED(
    id = "big_filled",
    nameResourceId = R.string.big_filled_ambient_style_name,
//    iconResourceId = R.drawable.filled_style_icon,
    iconResourceId = R.drawable.mauve_style_icon, // TODO @rdnt fix icon
  ),
  DETAILED(
    id = "detailed",
    nameResourceId = R.string.detailed_ambient_style_name, // TODO
//    iconResourceId = R.drawable.filled_style_icon,
    iconResourceId = R.drawable.mauve_style_icon, // TODO @rdnt fix icon
  );

  companion object {
    fun getAmbientStyleConfig(id: String): AmbientStyle {
      return when (id) {
        OUTLINE.id -> OUTLINE
        BIG_OUTLINE.id -> BIG_OUTLINE
        BOLD_OUTLINE.id -> BOLD_OUTLINE
        BIG_BOLD_OUTLINE.id -> BIG_BOLD_OUTLINE
        FILLED.id -> FILLED
        BIG_FILLED.id -> BIG_FILLED
        DETAILED.id -> DETAILED
        else -> OUTLINE
      }
    }

    fun toOptionList(context: Context): List<ListUserStyleSetting.ListOption> {
      val colorStyleIdAndResourceIdsList = enumValues<AmbientStyle>()

      return colorStyleIdAndResourceIdsList.map { style ->
        ambientStyleToListOption(context, style)
      }
    }

    fun ambientStyleToListOption(
      context: Context,
      style: AmbientStyle
    ): ListUserStyleSetting.ListOption {
      return ListUserStyleSetting.ListOption(
        UserStyleSetting.Option.Id(style.id),
        context.resources,
        style.nameResourceId,
        Icon.createWithResource(
          context,
          style.iconResourceId
        )
      )
    }
  }
}
