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

enum class LayoutStyle(
  val id: String,
  @StringRes val nameResourceId: Int,
  @DrawableRes val iconResourceId: Int,
) {
  // TODO: @rdnt use SVGs or compress the pngs to conserve wire memory for both
  //  these and other styles (so that we don't exceed transfer limitations of
  //  watchface config over BLE)
  DEFAULT(
    id = "default",
    nameResourceId = R.string.default_layout_style_name,
    iconResourceId = R.drawable.steel_style_icon, // TODO @rdnt fix icon
  ),
  FOCUS(
    id = "focus",
    nameResourceId = R.string.focus_layout_style_name,
    iconResourceId = R.drawable.steel_style_icon, // TODO @rdnt fix icon
  ),
  SPORT(
    id = "sport",
    nameResourceId = R.string.sport_layout_style_name,
    iconResourceId = R.drawable.steel_style_icon, // TODO @rdnt fix icon
  ),
  COMPLICATIONS(
    id = "complications",
    nameResourceId = R.string.complications_layout_style_name,
    iconResourceId = R.drawable.steel_style_icon, // TODO @rdnt fix icon
  );

  companion object {
    fun getLayoutStyleConfig(id: String): LayoutStyle {
      return when (id) {
        DEFAULT.id -> DEFAULT
        FOCUS.id -> FOCUS
        SPORT.id -> SPORT
        COMPLICATIONS.id -> COMPLICATIONS
        else -> DEFAULT
      }
    }
  }
}
