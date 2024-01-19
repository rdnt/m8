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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.watchface.CanvasComplication
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.RenderParameters
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.SystemDataSources
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.style.CurrentUserStyleRepository
import dev.rdnt.m8face.R
import java.time.Instant
import java.time.ZonedDateTime

// Information needed for complications.
// Creates bounds for the locations of both right and left complications. (This is the
// location from 0.0 - 1.0.)
// Both left and right complications use the same top and bottom bounds.
const val VERTICAL_COMPLICATION_TOP_BOUND = 0.328125f // 126px / 384
const val VERTICAL_COMPLICATION_BOTTOM_BOUND = 1f - VERTICAL_COMPLICATION_TOP_BOUND

// offset: 18px, width: 78px (canvas 384x384)
const val VERTICAL_COMPLICATION_OFFSET = 24f / 384f

const val VERTICAL_COMPLICATION_WIDTH = 78f / 384f
//const val VERTICAL_COMPLICATION_HEIGHT = VERTICAL_COMPLICATION_BOTTOM_BOUND - VERTICAL_COMPLICATION_TOP_BOUND

// 0.03125
private const val LEFT_COMPLICATION_LEFT_BOUND = VERTICAL_COMPLICATION_OFFSET
private const val LEFT_COMPLICATION_RIGHT_BOUND =
  VERTICAL_COMPLICATION_OFFSET + VERTICAL_COMPLICATION_WIDTH

private const val RIGHT_COMPLICATION_LEFT_BOUND =
  1f - VERTICAL_COMPLICATION_OFFSET - VERTICAL_COMPLICATION_WIDTH
private const val RIGHT_COMPLICATION_RIGHT_BOUND = 1f - VERTICAL_COMPLICATION_OFFSET

// Both left and right complications use the same top and bottom bounds.
const val HORIZONTAL_COMPLICATION_LEFT_BOUND = 102f / 384f
const val HORIZONTAL_COMPLICATION_RIGHT_BOUND = 1f - HORIZONTAL_COMPLICATION_LEFT_BOUND

// offset: 18px, height: 51px (canvas 384x384)
const val HORIZONTAL_COMPLICATION_OFFSET = 27f / 384f
const val HORIZONTAL_COMPLICATION_HEIGHT = 48f / 384f

//const val HORIZONTAL_COMPLICATION_WIDTH = HORIZONTAL_COMPLICATION_RIGHT_BOUND - HORIZONTAL_COMPLICATION_LEFT_BOUND

private const val TOP_COMPLICATION_TOP_BOUND = HORIZONTAL_COMPLICATION_OFFSET
private const val TOP_COMPLICATION_BOTTOM_BOUND =
  HORIZONTAL_COMPLICATION_OFFSET + HORIZONTAL_COMPLICATION_HEIGHT

private const val BOTTOM_COMPLICATION_TOP_BOUND =
  1f - HORIZONTAL_COMPLICATION_OFFSET - HORIZONTAL_COMPLICATION_HEIGHT
private const val BOTTOM_COMPLICATION_BOTTOM_BOUND = 1f - HORIZONTAL_COMPLICATION_OFFSET

// Unique IDs for each complication. The settings activity that supports allowing users
// to select their complication data provider requires numbers to be >= 0.
internal const val LEFT_COMPLICATION_ID = 100
internal const val RIGHT_COMPLICATION_ID = 101
internal const val TOP_COMPLICATION_ID = 102
internal const val BOTTOM_COMPLICATION_ID = 103
internal const val TOP_LEFT_COMPLICATION_ID = 104
internal const val BOTTOM_LEFT_COMPLICATION_ID = 105
internal const val TOP_RIGHT_COMPLICATION_ID = 106
internal const val BOTTOM_RIGHT_COMPLICATION_ID = 107
internal const val LEFT_ICON_COMPLICATION_ID = 108
internal const val RIGHT_ICON_COMPLICATION_ID = 109
internal const val RIGHT_TEXT_COMPLICATION_ID = 110

private const val TOP_LEFT_COMPLICATION_LEFT_BOUND = 33f / 384f
private const val TOP_LEFT_COMPLICATION_TOP_BOUND = 99f / 384f
private const val TOP_LEFT_COMPLICATION_RIGHT_BOUND = 33f / 384f + 72f / 384f
private const val TOP_LEFT_COMPLICATION_BOTTOM_BOUND = 99f / 384f + 90f / 384f

private const val BOTTOM_LEFT_COMPLICATION_LEFT_BOUND = 33f / 384f
private const val BOTTOM_LEFT_COMPLICATION_TOP_BOUND = 195f / 384f
private const val BOTTOM_LEFT_COMPLICATION_RIGHT_BOUND = 33f / 384f + 72f / 384f
private const val BOTTOM_LEFT_COMPLICATION_BOTTOM_BOUND = 195f / 384f + 90f / 384f

private const val TOP_RIGHT_COMPLICATION_LEFT_BOUND = 279f / 384f
private const val TOP_RIGHT_COMPLICATION_TOP_BOUND = 99f / 384f
private const val TOP_RIGHT_COMPLICATION_RIGHT_BOUND = 279f / 384f + 72f / 384f
private const val TOP_RIGHT_COMPLICATION_BOTTOM_BOUND = 99f / 384f + 90f / 384f

private const val BOTTOM_RIGHT_COMPLICATION_LEFT_BOUND = 279f / 384f
private const val BOTTOM_RIGHT_COMPLICATION_TOP_BOUND = 195f / 384f
private const val BOTTOM_RIGHT_COMPLICATION_RIGHT_BOUND = 279f / 384f + 72f / 384f
private const val BOTTOM_RIGHT_COMPLICATION_BOTTOM_BOUND = 195f / 384f + 90f / 384f

private const val LEFT_ICON_COMPLICATION_LEFT_BOUND = 33f / 384f
private const val LEFT_ICON_COMPLICATION_TOP_BOUND = 126f / 384f
private const val LEFT_ICON_COMPLICATION_RIGHT_BOUND = 33f / 384f + 54f / 384f
private const val LEFT_ICON_COMPLICATION_BOTTOM_BOUND = 126f / 384f + 132f / 384f

private const val RIGHT_ICON_COMPLICATION_LEFT_BOUND = 297f / 384f
private const val RIGHT_ICON_COMPLICATION_TOP_BOUND = 126f / 384f
private const val RIGHT_ICON_COMPLICATION_RIGHT_BOUND = 297f / 384f + 54f / 384f
private const val RIGHT_ICON_COMPLICATION_BOTTOM_BOUND = 126f / 384f + 132f / 384f

/**
 * Represents the unique id associated with a complication and the complication types it supports.
 */
sealed class ComplicationConfig(val id: Int, val supportedTypes: List<ComplicationType>) {
  object Left : ComplicationConfig(
    LEFT_COMPLICATION_ID, listOf(
      ComplicationType.SHORT_TEXT,
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    )
  )

  object Right : ComplicationConfig(
    RIGHT_COMPLICATION_ID, listOf(
      ComplicationType.SHORT_TEXT,
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    )
  )

  object Top : ComplicationConfig(
    TOP_COMPLICATION_ID, listOf(
      ComplicationType.SHORT_TEXT,
    )
  )

  object Bottom : ComplicationConfig(
    BOTTOM_COMPLICATION_ID, listOf(
      ComplicationType.SHORT_TEXT,
    )
  )

  object TopLeft : ComplicationConfig(
    TOP_LEFT_COMPLICATION_ID, listOf(
      ComplicationType.SHORT_TEXT,
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    )
  )

  object BottomLeft : ComplicationConfig(
    BOTTOM_LEFT_COMPLICATION_ID, listOf(
      ComplicationType.SHORT_TEXT,
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    )
  )

  object TopRight : ComplicationConfig(
    TOP_RIGHT_COMPLICATION_ID, listOf(
      ComplicationType.SHORT_TEXT,
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    )
  )

  object BottomRight : ComplicationConfig(
    BOTTOM_RIGHT_COMPLICATION_ID, listOf(
      ComplicationType.SHORT_TEXT,
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    )
  )

  object LeftIcon : ComplicationConfig(
    LEFT_ICON_COMPLICATION_ID, listOf(
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    )
  )

  object RightIcon : ComplicationConfig(
    RIGHT_ICON_COMPLICATION_ID, listOf(
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    )
  )

  object RightText : ComplicationConfig(
    RIGHT_TEXT_COMPLICATION_ID, listOf(
      ComplicationType.SHORT_TEXT,
    )
  )
}

// Utility function that initializes default complication slots (left and right).
fun createComplicationSlotManager(
  context: Context,
  currentUserStyleRepository: CurrentUserStyleRepository,
): ComplicationSlotsManager {

  // TODO @rdnt setNameResourceId setScreenReaderNameResourceId use proper name for all

  val customLeftComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = ComplicationConfig.Left.id,
    canvasComplicationFactory = createVerticalComplicationFactory(context),
    supportedTypes = ComplicationConfig.Left.supportedTypes,
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        LEFT_COMPLICATION_LEFT_BOUND,
        VERTICAL_COMPLICATION_TOP_BOUND,
        LEFT_COMPLICATION_RIGHT_BOUND,
        VERTICAL_COMPLICATION_BOTTOM_BOUND
      )
    )
  ).setNameResourceId(R.string.left_complication_name)
    .setScreenReaderNameResourceId(R.string.left_complication_name).setEnabled(false).build()

  val customRightComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = ComplicationConfig.Right.id,
    canvasComplicationFactory = createVerticalComplicationFactory(context),
    supportedTypes = ComplicationConfig.Right.supportedTypes,
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        RIGHT_COMPLICATION_LEFT_BOUND,
        VERTICAL_COMPLICATION_TOP_BOUND,
        RIGHT_COMPLICATION_RIGHT_BOUND,
        VERTICAL_COMPLICATION_BOTTOM_BOUND
      )
    )
  ).setNameResourceId(R.string.right_complication_name)
    .setScreenReaderNameResourceId(R.string.right_complication_name).setEnabled(false).build()

  val customTopComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = ComplicationConfig.Top.id,
    canvasComplicationFactory = createHorizontalComplicationFactory(context),
    supportedTypes = ComplicationConfig.Top.supportedTypes,
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.DATA_SOURCE_DATE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        HORIZONTAL_COMPLICATION_LEFT_BOUND,
        TOP_COMPLICATION_TOP_BOUND,
        HORIZONTAL_COMPLICATION_RIGHT_BOUND,
        TOP_COMPLICATION_BOTTOM_BOUND
      )
    )
  ).setNameResourceId(R.string.top_complication_name)
    .setScreenReaderNameResourceId(R.string.top_complication_name).setEnabled(false).build()

  val customBottomComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = ComplicationConfig.Bottom.id,
    canvasComplicationFactory = createHorizontalComplicationFactory(context),
    supportedTypes = ComplicationConfig.Bottom.supportedTypes,
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.DATA_SOURCE_WATCH_BATTERY, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        HORIZONTAL_COMPLICATION_LEFT_BOUND,
        BOTTOM_COMPLICATION_TOP_BOUND,
        HORIZONTAL_COMPLICATION_RIGHT_BOUND,
        BOTTOM_COMPLICATION_BOTTOM_BOUND
      )
    )
  ).setNameResourceId(R.string.bottom_complication_name)
    .setScreenReaderNameResourceId(R.string.bottom_complication_name).setEnabled(false).build()


  val customTopLeftComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = ComplicationConfig.TopLeft.id,
    canvasComplicationFactory = createVerticalComplicationFactory(context),
    supportedTypes = ComplicationConfig.TopLeft.supportedTypes,
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        TOP_LEFT_COMPLICATION_LEFT_BOUND,
        TOP_LEFT_COMPLICATION_TOP_BOUND,
        TOP_LEFT_COMPLICATION_RIGHT_BOUND,
        TOP_LEFT_COMPLICATION_BOTTOM_BOUND
      )
    )
  ).setNameResourceId(R.string.top_left_complication_name)
    .setScreenReaderNameResourceId(R.string.left_complication_name).setEnabled(false).build()

  val customBottomLeftComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = ComplicationConfig.BottomLeft.id,
    canvasComplicationFactory = createVerticalComplicationFactory(context),
    supportedTypes = ComplicationConfig.BottomLeft.supportedTypes,
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        BOTTOM_LEFT_COMPLICATION_LEFT_BOUND,
        BOTTOM_LEFT_COMPLICATION_TOP_BOUND,
        BOTTOM_LEFT_COMPLICATION_RIGHT_BOUND,
        BOTTOM_LEFT_COMPLICATION_BOTTOM_BOUND
      )
    )
  ).setNameResourceId(R.string.bottom_left_complication_name)
    .setScreenReaderNameResourceId(R.string.left_complication_name).setEnabled(false).build()

  val customTopRightComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = ComplicationConfig.TopRight.id,
    canvasComplicationFactory = createVerticalComplicationFactory(context),
    supportedTypes = ComplicationConfig.TopRight.supportedTypes,
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        TOP_RIGHT_COMPLICATION_LEFT_BOUND,
        TOP_RIGHT_COMPLICATION_TOP_BOUND,
        TOP_RIGHT_COMPLICATION_RIGHT_BOUND,
        TOP_RIGHT_COMPLICATION_BOTTOM_BOUND
      )
    )
  ).setNameResourceId(R.string.top_right_complication_name)
    .setScreenReaderNameResourceId(R.string.left_complication_name).setEnabled(false).build()

  val customBottomRightComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = ComplicationConfig.BottomRight.id,
    canvasComplicationFactory = createVerticalComplicationFactory(context),
    supportedTypes = ComplicationConfig.BottomRight.supportedTypes,
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        BOTTOM_RIGHT_COMPLICATION_LEFT_BOUND,
        BOTTOM_RIGHT_COMPLICATION_TOP_BOUND,
        BOTTOM_RIGHT_COMPLICATION_RIGHT_BOUND,
        BOTTOM_RIGHT_COMPLICATION_BOTTOM_BOUND
      )
    )
  ).setNameResourceId(R.string.bottom_right_complication_name)
    .setScreenReaderNameResourceId(R.string.left_complication_name).setEnabled(false).build()

  val customLeftIconComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = ComplicationConfig.LeftIcon.id,
    canvasComplicationFactory = createIconComplicationFactory(context),
    supportedTypes = ComplicationConfig.LeftIcon.supportedTypes,
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.MONOCHROMATIC_IMAGE
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        LEFT_ICON_COMPLICATION_LEFT_BOUND,
        LEFT_ICON_COMPLICATION_TOP_BOUND,
        LEFT_ICON_COMPLICATION_RIGHT_BOUND,
        LEFT_ICON_COMPLICATION_BOTTOM_BOUND
      )
    )
  ).setNameResourceId(R.string.left_icon_complication_name)
    .setScreenReaderNameResourceId(R.string.left_complication_name).setEnabled(false).build()

  val customRightIconComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = ComplicationConfig.RightIcon.id,
    canvasComplicationFactory = createIconComplicationFactory(context),
    supportedTypes = ComplicationConfig.RightIcon.supportedTypes,
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.MONOCHROMATIC_IMAGE
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        RIGHT_ICON_COMPLICATION_LEFT_BOUND,
        RIGHT_ICON_COMPLICATION_TOP_BOUND,
        RIGHT_ICON_COMPLICATION_RIGHT_BOUND,
        RIGHT_ICON_COMPLICATION_BOTTOM_BOUND
      )
    )
  ).setNameResourceId(R.string.right_icon_complication_name)
    .setScreenReaderNameResourceId(R.string.left_complication_name).setEnabled(false).build()

  val customRightTextComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = ComplicationConfig.RightText.id,
    canvasComplicationFactory = createHorizontalComplicationFactory(context),
    supportedTypes = ComplicationConfig.RightText.supportedTypes,
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        LEFT_COMPLICATION_LEFT_BOUND,
        VERTICAL_COMPLICATION_TOP_BOUND,
        LEFT_COMPLICATION_RIGHT_BOUND,
        VERTICAL_COMPLICATION_BOTTOM_BOUND
      )
    )
  ).setNameResourceId(R.string.right_text_complication_name)
    .setScreenReaderNameResourceId(R.string.left_complication_name).setEnabled(false).build()

  return ComplicationSlotsManager(
    listOf(
      customLeftComplication,
      customRightComplication,
      customTopComplication,
      customBottomComplication,
      customTopLeftComplication,
      customBottomLeftComplication,
      customTopRightComplication,
      customBottomRightComplication,
      customLeftIconComplication,
      customRightIconComplication,
      customRightTextComplication
    ), currentUserStyleRepository
  )
}
