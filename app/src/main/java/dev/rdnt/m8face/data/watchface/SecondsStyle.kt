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

enum class SecondsStyle(
  val id: String,
  @StringRes val nameResourceId: Int,
  @DrawableRes val iconResourceId: Int,
) {
  NONE(
    id = "none",
    nameResourceId = R.string.none_seconds_style_name,
    iconResourceId = R.drawable.none_style_icon,
  ),
  DASHES(
    id = "dashes",
    nameResourceId = R.string.dashes_seconds_style_name,
    iconResourceId = R.drawable.dashes_style_icon,
  ),
  DOTS(
    id = "dots",
    nameResourceId = R.string.dots_seconds_style_name,
    iconResourceId = R.drawable.dots_style_icon,
  );

  companion object {
    fun getSecondsStyleConfig(id: String): SecondsStyle {
      return when (id) {
        NONE.id -> NONE
        DASHES.id -> DASHES
        DOTS.id -> DOTS
        else -> NONE
      }
    }

    fun toOptionList(context: Context): List<ListUserStyleSetting.ListOption> {
      val colorStyleIdAndResourceIdsList = enumValues<SecondsStyle>()

      return colorStyleIdAndResourceIdsList.map { style ->
        secondsStyleToListOption(context, style)
      }
    }

    fun secondsStyleToListOption(context: Context, style: SecondsStyle): ListUserStyleSetting.ListOption {
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
