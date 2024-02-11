/*
 * Copyright 2020 The Android Open Source Project
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
package dev.rdnt.m8face.utils

import android.content.Context
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.text.format.DateFormat
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.style.UserStyleSchema
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.ComplicationSlotsUserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption
import androidx.wear.watchface.style.WatchFaceLayer
import dev.rdnt.m8face.R
import dev.rdnt.m8face.data.watchface.AmbientStyle
import dev.rdnt.m8face.data.watchface.AmbientStyle.Companion.ambientStyleToListOption
import dev.rdnt.m8face.data.watchface.ColorStyle
import dev.rdnt.m8face.data.watchface.ColorStyle.Companion.colorStyleToListOption
import dev.rdnt.m8face.data.watchface.LayoutStyle
import dev.rdnt.m8face.data.watchface.SecondsStyle
import dev.rdnt.m8face.data.watchface.SecondsStyle.Companion.secondsStyleToListOption

// Keys to matched content in the user style settings. We listen for changes to these
// values in the renderer and if new, we will update the database and update the watch face
// being rendered.
const val LAYOUT_STYLE_SETTING = "layout_style_setting"
const val COLOR_STYLE_SETTING = "color_style_setting"
const val AMBIENT_STYLE_SETTING = "ambient_style_setting"
const val SECONDS_STYLE_SETTING = "seconds_style_setting"
const val MILITARY_TIME_SETTING = "military_time_setting"
const val BIG_AMBIENT_SETTING = "big_ambient_setting"
const val DETAILED_AMBIENT_SETTING = "detailed_ambient_setting"

/*
 * Creates user styles in the settings activity associated with the watch face, so users can
 * edit different parts of the watch face. In the renderer (after something has changed), the
 * watch face listens for a flow from the watch face API data layer and updates the watch face.
 */
fun createUserStyleSchema(context: Context): UserStyleSchema {
  // 1. Allows user to change the color styles of the watch face (if any are available).

  val info1 = ComplicationSlotsOption(
    id = UserStyleSetting.Option.Id(LayoutStyle.INFO1.id),
    resources = context.resources,
    displayNameResourceId = R.string.info1_layout_style_name,
    icon = Icon.createWithResource(context, R.drawable.aqua_style_icon), // TODO @rdnt fix icon
    complicationSlotOverlays = listOf(
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        HOUR_COMPLICATION_ID,
        enabled = true,
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        MINUTE_COMPLICATION_ID,
        enabled = true,
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        LEFT_COMPLICATION_ID,
        enabled = true
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        TOP_COMPLICATION_ID,
        enabled = true
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        BOTTOM_COMPLICATION_ID,
        enabled = true
      ),
    )
  )

  val info2 = ComplicationSlotsOption(
    id = UserStyleSetting.Option.Id(LayoutStyle.INFO2.id),
    resources = context.resources,
    displayNameResourceId = R.string.info2_layout_style_name,
    icon = Icon.createWithResource(context, R.drawable.aqua_style_icon), // TODO @rdnt fix icon
    complicationSlotOverlays = listOf(
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        HOUR_COMPLICATION_ID,
        enabled = true,
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        MINUTE_COMPLICATION_ID,
        enabled = true,
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        LEFT_COMPLICATION_ID,
        enabled = true
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        RIGHT_COMPLICATION_ID,
        enabled = true
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        TOP_COMPLICATION_ID,
        enabled = true
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        BOTTOM_COMPLICATION_ID,
        enabled = true
      ),
    )
  )

  val info3 = ComplicationSlotsOption(
    id = UserStyleSetting.Option.Id(LayoutStyle.INFO3.id),
    resources = context.resources,
    displayNameResourceId = R.string.info3_layout_style_name,
    icon = Icon.createWithResource(context, R.drawable.aqua_style_icon), // TODO @rdnt fix icon
    complicationSlotOverlays = listOf(
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        HOUR_COMPLICATION_ID,
        enabled = true,
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        MINUTE_COMPLICATION_ID,
        enabled = true,
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        TOP_COMPLICATION_ID,
        enabled = true
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        BOTTOM_COMPLICATION_ID,
        enabled = true
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        COMPLICATIONS_TOP_LEFT_COMPLICATION_ID,
        enabled = true
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        COMPLICATIONS_BOTTOM_LEFT_COMPLICATION_ID,
        enabled = true
      ),
    )
  )

  val info4 = ComplicationSlotsOption(
    id = UserStyleSetting.Option.Id(LayoutStyle.INFO4.id),
    resources = context.resources,
    displayNameResourceId = R.string.info4_layout_style_name,
    icon = Icon.createWithResource(context, R.drawable.aqua_style_icon), // TODO @rdnt fix icon
    complicationSlotOverlays = listOf(
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        HOUR_COMPLICATION_ID,
        enabled = true,
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        MINUTE_COMPLICATION_ID,
        enabled = true,
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        TOP_COMPLICATION_ID,
        enabled = true
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        BOTTOM_COMPLICATION_ID,
        enabled = true
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        COMPLICATIONS_TOP_LEFT_COMPLICATION_ID,
        enabled = true
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        COMPLICATIONS_BOTTOM_LEFT_COMPLICATION_ID,
        enabled = true
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        COMPLICATIONS_TOP_RIGHT_COMPLICATION_ID,
        enabled = true
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        COMPLICATIONS_BOTTOM_RIGHT_COMPLICATION_ID,
        enabled = true
      ),
    )
  )

  val sport = ComplicationSlotsOption(
    id = UserStyleSetting.Option.Id(LayoutStyle.SPORT.id),
    resources = context.resources,
    displayNameResourceId = R.string.sport_layout_style_name,
    icon = Icon.createWithResource(context, R.drawable.aqua_style_icon), // TODO @rdnt fix icon
    complicationSlotOverlays = listOf(
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        HOUR_COMPLICATION_ID,
        enabled = true,
        complicationSlotBounds = ComplicationSlotBounds(RectF(
          HOUR_SPORT_COMPLICATION_LEFT_BOUND,
          HOUR_SPORT_COMPLICATION_TOP_BOUND,
          HOUR_SPORT_COMPLICATION_RIGHT_BOUND,
          HOUR_SPORT_COMPLICATION_BOTTOM_BOUND,
        )),
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        MINUTE_COMPLICATION_ID,
        enabled = true,
        complicationSlotBounds = ComplicationSlotBounds(RectF(
          MINUTE_SPORT_COMPLICATION_LEFT_BOUND,
          MINUTE_SPORT_COMPLICATION_TOP_BOUND,
          MINUTE_SPORT_COMPLICATION_RIGHT_BOUND,
          MINUTE_SPORT_COMPLICATION_BOTTOM_BOUND,
        )),
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        TOP_COMPLICATION_ID,
        enabled = true
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        BOTTOM_COMPLICATION_ID,
        enabled = true
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        RIGHT_TEXT_COMPLICATION_ID,
        enabled = true
      ),
    )
  )

  val focus = ComplicationSlotsOption(
    id = UserStyleSetting.Option.Id(LayoutStyle.FOCUS.id),
    resources = context.resources,
    displayNameResourceId = R.string.focus_layout_style_name,
    icon = Icon.createWithResource(context, R.drawable.aqua_style_icon), // TODO @rdnt fix icon
    complicationSlotOverlays = listOf(
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        HOUR_COMPLICATION_ID,
        enabled = true,
        complicationSlotBounds = ComplicationSlotBounds(RectF(
          HOUR_FOCUS_COMPLICATION_LEFT_BOUND,
          HOUR_FOCUS_COMPLICATION_TOP_BOUND,
          HOUR_FOCUS_COMPLICATION_RIGHT_BOUND,
          HOUR_FOCUS_COMPLICATION_BOTTOM_BOUND,
        )),
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        MINUTE_COMPLICATION_ID,
        enabled = true,
        complicationSlotBounds = ComplicationSlotBounds(RectF(
          MINUTE_FOCUS_COMPLICATION_LEFT_BOUND,
          MINUTE_FOCUS_COMPLICATION_TOP_BOUND,
          MINUTE_FOCUS_COMPLICATION_RIGHT_BOUND,
          MINUTE_FOCUS_COMPLICATION_BOTTOM_BOUND,
        )),
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        FOCUS_LEFT_ICON_COMPLICATION_ID,
        enabled = true
      ),
      ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        FOCUS_RIGHT_ICON_COMPLICATION_ID,
        enabled = true
      ),
    )
  )

  val layoutStyleSetting =
    ComplicationSlotsUserStyleSetting(
      id = UserStyleSetting.Id(LAYOUT_STYLE_SETTING),
      resources = context.resources,
      displayNameResourceId = R.string.layout_style_setting,
      descriptionResourceId = R.string.layout_style_setting_description,
      icon = Icon.createWithResource(context, R.drawable.mauve_style_icon), // TODO: @rdnt fix icon
      complicationConfig = listOf(
        info1,
        info2,
        info3,
        info4,
        sport,
        focus,
      ),
      listOf(WatchFaceLayer.COMPLICATIONS),
      defaultOption = info2,
    )

  val colorStyleSetting =
    UserStyleSetting.ListUserStyleSetting(
      UserStyleSetting.Id(COLOR_STYLE_SETTING),
      context.resources,
      R.string.color_style_setting,
      R.string.color_style_setting_description,
      null,
      ColorStyle.toOptionList(context),
      listOf(WatchFaceLayer.BASE),
      defaultOption = colorStyleToListOption(context, ColorStyle.LAST_DANCE)
    )

  val ambientStyleSetting =
    UserStyleSetting.ListUserStyleSetting(
      UserStyleSetting.Id(AMBIENT_STYLE_SETTING),
      context.resources,
      R.string.ambient_style_setting,
      R.string.ambient_style_setting_description,
      null,
      AmbientStyle.toOptionList(context),
      listOf(WatchFaceLayer.BASE),
      defaultOption = ambientStyleToListOption(context, AmbientStyle.OUTLINE)
    )

  val secondsStyleSetting =
    UserStyleSetting.ListUserStyleSetting(
      UserStyleSetting.Id(SECONDS_STYLE_SETTING),
      context.resources,
      R.string.seconds_style_setting,
      R.string.seconds_style_setting_description,
      null,
      SecondsStyle.toOptionList(context),
      listOf(WatchFaceLayer.BASE),
      defaultOption = secondsStyleToListOption(context, SecondsStyle.NONE)
    )

  //2. Allows user to toggle on/off military time
  val militaryTimeSetting = UserStyleSetting.BooleanUserStyleSetting(
    UserStyleSetting.Id(MILITARY_TIME_SETTING),
    context.resources,
    R.string.military_time_setting,
    R.string.military_time_setting_description,
    null,
    listOf(WatchFaceLayer.BASE),
    DateFormat.is24HourFormat(context), // default
  )

  val bigAmbientSetting = UserStyleSetting.BooleanUserStyleSetting(
    UserStyleSetting.Id(BIG_AMBIENT_SETTING),
    context.resources,
    R.string.big_ambient_setting,
    R.string.big_ambient_setting_description,
    null,
    listOf(WatchFaceLayer.BASE),
    false,
  )

  val detailedAmbientSetting = UserStyleSetting.BooleanUserStyleSetting(
    UserStyleSetting.Id(DETAILED_AMBIENT_SETTING),
    context.resources,
    R.string.detailed_ambient_setting,
    R.string.detailed_ambient_setting_description,
    null,
    listOf(WatchFaceLayer.BASE),
    false,
  )

  // 4. Create style settings to hold all options.
  return UserStyleSchema(
    listOf(
      layoutStyleSetting,
      colorStyleSetting,
      ambientStyleSetting,
      secondsStyleSetting,
      militaryTimeSetting,
      bigAmbientSetting,
      detailedAmbientSetting,
    )
  )
}
