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
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.util.LruCache
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.SystemDataSources
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.style.CurrentUserStyleRepository
import dev.rdnt.m8face.R

// Information needed for complications.
// Creates bounds for the locations of both right and left complications. (This is the
// location from 0.0 - 1.0.)
// Both left and right complications use the same top and bottom bounds.
const val VERTICAL_COMPLICATION_TOP_BOUND = 0.328125f // 126px / 384
const val VERTICAL_COMPLICATION_BOTTOM_BOUND = 1f - VERTICAL_COMPLICATION_TOP_BOUND

// offset: 18px, width: 78px (canvas 384x384)
const val VERTICAL_COMPLICATION_OFFSET = 21f / 384f

const val VERTICAL_COMPLICATION_WIDTH = 78f / 384f
//const val VERTICAL_COMPLICATION_HEIGHT = VERTICAL_COMPLICATION_BOTTOM_BOUND - VERTICAL_COMPLICATION_TOP_BOUND

// 0.03125
 const val LEFT_COMPLICATION_LEFT_BOUND = VERTICAL_COMPLICATION_OFFSET
 const val LEFT_COMPLICATION_RIGHT_BOUND =
  VERTICAL_COMPLICATION_OFFSET + VERTICAL_COMPLICATION_WIDTH
const val LEFT_COMPLICATION_TOP_BOUND = VERTICAL_COMPLICATION_TOP_BOUND
const val LEFT_COMPLICATION_BOTTOM_BOUND = VERTICAL_COMPLICATION_BOTTOM_BOUND

 const val RIGHT_COMPLICATION_LEFT_BOUND =
  1f - VERTICAL_COMPLICATION_OFFSET - VERTICAL_COMPLICATION_WIDTH
 const val RIGHT_COMPLICATION_RIGHT_BOUND = 1f - VERTICAL_COMPLICATION_OFFSET
const val RIGHT_COMPLICATION_TOP_BOUND = VERTICAL_COMPLICATION_TOP_BOUND
const val RIGHT_COMPLICATION_BOTTOM_BOUND = VERTICAL_COMPLICATION_BOTTOM_BOUND

// Both left and right complications use the same top and bottom bounds.
const val HORIZONTAL_COMPLICATION_LEFT_BOUND = 99f / 384f
const val HORIZONTAL_COMPLICATION_RIGHT_BOUND = 1f - HORIZONTAL_COMPLICATION_LEFT_BOUND

// offset: 18px, height: 51px (canvas 384x384)
const val HORIZONTAL_COMPLICATION_OFFSET = 27f / 384f
const val HORIZONTAL_COMPLICATION_HEIGHT = 48f / 384f

//const val HORIZONTAL_COMPLICATION_WIDTH = HORIZONTAL_COMPLICATION_RIGHT_BOUND - HORIZONTAL_COMPLICATION_LEFT_BOUND

 const val TOP_COMPLICATION_TOP_BOUND = HORIZONTAL_COMPLICATION_OFFSET
 const val TOP_COMPLICATION_BOTTOM_BOUND =
  HORIZONTAL_COMPLICATION_OFFSET + HORIZONTAL_COMPLICATION_HEIGHT
const val TOP_COMPLICATION_LEFT_BOUND = HORIZONTAL_COMPLICATION_LEFT_BOUND
const val TOP_COMPLICATION_RIGHT_BOUND = HORIZONTAL_COMPLICATION_RIGHT_BOUND

 const val BOTTOM_COMPLICATION_TOP_BOUND =
  1f - HORIZONTAL_COMPLICATION_OFFSET - HORIZONTAL_COMPLICATION_HEIGHT
 const val BOTTOM_COMPLICATION_BOTTOM_BOUND = 1f - HORIZONTAL_COMPLICATION_OFFSET
const val BOTTOM_COMPLICATION_LEFT_BOUND = HORIZONTAL_COMPLICATION_LEFT_BOUND
const val BOTTOM_COMPLICATION_RIGHT_BOUND = HORIZONTAL_COMPLICATION_RIGHT_BOUND

// Unique IDs for each complication. The settings activity that supports allowing users
// to select their complication data provider requires numbers to be >= 0.
internal const val LEFT_COMPLICATION_ID = 100
internal const val RIGHT_COMPLICATION_ID = 101
internal const val TOP_COMPLICATION_ID = 102
internal const val BOTTOM_COMPLICATION_ID = 103
internal const val HOUR_COMPLICATION_ID = 104
internal const val MINUTE_COMPLICATION_ID = 105

internal const val COMPLICATIONS_TOP_LEFT_COMPLICATION_ID = 106
internal const val COMPLICATIONS_BOTTOM_LEFT_COMPLICATION_ID = 107
internal const val COMPLICATIONS_TOP_RIGHT_COMPLICATION_ID = 108
internal const val COMPLICATIONS_BOTTOM_RIGHT_COMPLICATION_ID = 109
internal const val COMPLICATIONS_TOP_COMPLICATION_ID = 110
internal const val COMPLICATIONS_BOTTOM_COMPLICATION_ID = 111
internal const val COMPLICATIONS_HOUR_COMPLICATION_ID = 112
internal const val COMPLICATIONS_MINUTE_COMPLICATION_ID = 113

internal const val FOCUS_LEFT_ICON_COMPLICATION_ID = 114
internal const val FOCUS_RIGHT_ICON_COMPLICATION_ID = 115
internal const val FOCUS_HOUR_COMPLICATION_ID = 116
internal const val FOCUS_MINUTE_COMPLICATION_ID = 117

internal const val RIGHT_TEXT_COMPLICATION_ID = 118
internal const val SPORT_HOUR_COMPLICATION_ID = 119
internal const val SPORT_MINUTE_COMPLICATION_ID = 120

const val TOP_LEFT_COMPLICATION_LEFT_BOUND = 33f / 384f
const val TOP_LEFT_COMPLICATION_TOP_BOUND = 93f / 384f
const val TOP_LEFT_COMPLICATION_RIGHT_BOUND = 33f / 384f + 60f / 384f
const val TOP_LEFT_COMPLICATION_BOTTOM_BOUND = 93f / 384f + 90f / 384f

const val BOTTOM_LEFT_COMPLICATION_LEFT_BOUND = 33f / 384f
const val BOTTOM_LEFT_COMPLICATION_TOP_BOUND = 201f / 384f
const val BOTTOM_LEFT_COMPLICATION_RIGHT_BOUND = 33f / 384f + 60f / 384f
const val BOTTOM_LEFT_COMPLICATION_BOTTOM_BOUND = 201f / 384f + 90f / 384f

const val TOP_RIGHT_COMPLICATION_LEFT_BOUND = 285f / 384f
const val TOP_RIGHT_COMPLICATION_TOP_BOUND = 93f / 384f
const val TOP_RIGHT_COMPLICATION_RIGHT_BOUND = 285f / 384f + 60f / 384f
const val TOP_RIGHT_COMPLICATION_BOTTOM_BOUND = 93f / 384f + 90f / 384f

const val BOTTOM_RIGHT_COMPLICATION_LEFT_BOUND = 285f / 384f
const val BOTTOM_RIGHT_COMPLICATION_TOP_BOUND = 201f / 384f
const val BOTTOM_RIGHT_COMPLICATION_RIGHT_BOUND = 285f / 384f + 60f / 384f
const val BOTTOM_RIGHT_COMPLICATION_BOTTOM_BOUND = 201f / 384f + 90f / 384f

const val LEFT_ICON_COMPLICATION_LEFT_BOUND = 24f / 384f
const val LEFT_ICON_COMPLICATION_TOP_BOUND = 126f / 384f
const val LEFT_ICON_COMPLICATION_RIGHT_BOUND = 24f / 384f + 54f / 384f
const val LEFT_ICON_COMPLICATION_BOTTOM_BOUND = 126f / 384f + 132f / 384f

const val RIGHT_ICON_COMPLICATION_LEFT_BOUND = 306f / 384f
const val RIGHT_ICON_COMPLICATION_TOP_BOUND = 126f / 384f
const val RIGHT_ICON_COMPLICATION_RIGHT_BOUND = 306f / 384f + 54f / 384f
const val RIGHT_ICON_COMPLICATION_BOTTOM_BOUND = 126f / 384f + 132f / 384f

const val RIGHT_TEXT_COMPLICATION_LEFT_BOUND = 249f / 384f - 14f / 384f
const val RIGHT_TEXT_COMPLICATION_TOP_BOUND = 246f / 384f - 14f / 384f + 2f / 384f
const val RIGHT_TEXT_COMPLICATION_RIGHT_BOUND = 249f / 384f + 82f / 384f + 14f / 384f
const val RIGHT_TEXT_COMPLICATION_BOTTOM_BOUND = 246f / 384f + 14f / 384f + 14f / 384f + 2f / 384f

const val HOUR_COMPLICATION_LEFT_BOUND = 114f / 384f
const val HOUR_COMPLICATION_TOP_BOUND = 87f / 384f
const val HOUR_COMPLICATION_RIGHT_BOUND = 114f / 384f + 156f / 384f
const val HOUR_COMPLICATION_BOTTOM_BOUND = 87f / 384f + 99f / 384f

const val HOUR_SPORT_COMPLICATION_LEFT_BOUND = 81f / 384f
const val HOUR_SPORT_COMPLICATION_TOP_BOUND = 87f / 384f
const val HOUR_SPORT_COMPLICATION_RIGHT_BOUND = 81f / 384f + 156f / 384f
const val HOUR_SPORT_COMPLICATION_BOTTOM_BOUND = 87f / 384f + 102f / 384f

const val HOUR_FOCUS_COMPLICATION_LEFT_BOUND = 93f / 384f
const val HOUR_FOCUS_COMPLICATION_TOP_BOUND = 57f / 384f
const val HOUR_FOCUS_COMPLICATION_RIGHT_BOUND = 93f / 384f + 198f / 384f
const val HOUR_FOCUS_COMPLICATION_BOTTOM_BOUND = 57f / 384f + 126f / 384f

const val MINUTE_COMPLICATION_LEFT_BOUND = 114f / 384f
const val MINUTE_COMPLICATION_TOP_BOUND = 198f / 384f
const val MINUTE_COMPLICATION_RIGHT_BOUND = 114f / 384f + 156f / 384f
const val MINUTE_COMPLICATION_BOTTOM_BOUND = 198f / 384f + 99f / 384f

const val MINUTE_SPORT_COMPLICATION_LEFT_BOUND = 81f / 384f
const val MINUTE_SPORT_COMPLICATION_TOP_BOUND = 198f / 384f
const val MINUTE_SPORT_COMPLICATION_RIGHT_BOUND = 81f / 384f + 156f / 384f
const val MINUTE_SPORT_COMPLICATION_BOTTOM_BOUND = 198f / 384f + 102f / 384f

const val MINUTE_FOCUS_COMPLICATION_LEFT_BOUND = 93f / 384f
const val MINUTE_FOCUS_COMPLICATION_TOP_BOUND = 201f / 384f
const val MINUTE_FOCUS_COMPLICATION_RIGHT_BOUND = 93f / 384f + 198f / 384f
const val MINUTE_FOCUS_COMPLICATION_BOTTOM_BOUND = 201f / 384f + 126f / 384f

///**
// * Represents the unique id associated with a complication and the complication types it supports.
// */
//sealed class ComplicationConfigDelete(
//  val id: Int,
//  val supportedTypes: List<ComplicationType>,
//  val renderBounds: RectF = RectF(),
//  val slotBounds: RectF = RectF(),
//  val nameResourceId: Int?,
//) {
//  object Hour : ComplicationConfigDelete(
//    HOUR_COMPLICATION_ID, listOf(
//      ComplicationType.SHORT_TEXT,
//      ComplicationType.MONOCHROMATIC_IMAGE,
//      ComplicationType.SMALL_IMAGE
//    ),
//    slotBounds = RectF(
//      HOUR_COMPLICATION_LEFT_BOUND,
//      HOUR_COMPLICATION_TOP_BOUND,
//      HOUR_COMPLICATION_RIGHT_BOUND,
//      HOUR_COMPLICATION_BOTTOM_BOUND,
//    ),
//    renderBounds = RectF(
//      HOUR_COMPLICATION_LEFT_BOUND,
//      HOUR_COMPLICATION_TOP_BOUND,
//      HOUR_COMPLICATION_RIGHT_BOUND,
//      HOUR_COMPLICATION_BOTTOM_BOUND,
//    ),
//    nameResourceId = R.string.hour_complication_name,
//  )
//
//  object Minute : ComplicationConfigDelete(
//    MINUTE_COMPLICATION_ID, listOf(
//      ComplicationType.SHORT_TEXT,
//      ComplicationType.MONOCHROMATIC_IMAGE,
//      ComplicationType.SMALL_IMAGE
//    ),
//    slotBounds = RectF(
//      MINUTE_COMPLICATION_LEFT_BOUND,
//      MINUTE_COMPLICATION_TOP_BOUND,
//      MINUTE_COMPLICATION_RIGHT_BOUND,
//      MINUTE_COMPLICATION_BOTTOM_BOUND,
//    ),
//    renderBounds = RectF(
//      MINUTE_COMPLICATION_LEFT_BOUND,
//      MINUTE_COMPLICATION_TOP_BOUND,
//      MINUTE_COMPLICATION_RIGHT_BOUND,
//      MINUTE_COMPLICATION_BOTTOM_BOUND,
//    ),
//    nameResourceId = R.string.minute_complication_name,
//  )
//
//  object Left : ComplicationConfigDelete(
//    LEFT_COMPLICATION_ID, listOf(
//      ComplicationType.SHORT_TEXT,
//      ComplicationType.MONOCHROMATIC_IMAGE,
//      ComplicationType.SMALL_IMAGE
//    ),
//    renderBounds = RectF(
//      LEFT_COMPLICATION_LEFT_BOUND,
//      LEFT_COMPLICATION_TOP_BOUND,
//      LEFT_COMPLICATION_RIGHT_BOUND,
//      LEFT_COMPLICATION_BOTTOM_BOUND,
//    ),
//    slotBounds = RectF(
//      0f / 384f,
//      87f / 384f,
//      0f / 384f + 108f / 384f,
//      87f / 384f + 210f / 384f,
//    ),
//    nameResourceId = R.string.left_complication_name,
//  )
//
//  object Right : ComplicationConfigDelete(
//    RIGHT_COMPLICATION_ID, listOf(
//      ComplicationType.SHORT_TEXT,
//      ComplicationType.MONOCHROMATIC_IMAGE,
//      ComplicationType.SMALL_IMAGE
//    ),
//    renderBounds = RectF(
//      RIGHT_COMPLICATION_LEFT_BOUND,
//      RIGHT_COMPLICATION_TOP_BOUND,
//      RIGHT_COMPLICATION_RIGHT_BOUND,
//      RIGHT_COMPLICATION_BOTTOM_BOUND,
//    ),
//    slotBounds = RectF(
//      276f / 384f,
//      87f / 384f,
//      276f / 384f + 108f / 384f,
//      87f / 384f + 210f / 384f
//    ),
//    nameResourceId = R.string.right_complication_name,
//  )
//
//  object Top : ComplicationConfigDelete(
//    TOP_COMPLICATION_ID, listOf(
//      ComplicationType.SHORT_TEXT,
//    ),
//    renderBounds = RectF(
//      TOP_COMPLICATION_LEFT_BOUND,
//      TOP_COMPLICATION_TOP_BOUND,
//      TOP_COMPLICATION_RIGHT_BOUND,
//      TOP_COMPLICATION_BOTTOM_BOUND,
//    ),
//    slotBounds = RectF(
//      0f / 384f,
//      0f / 384f,
//      0f / 384f + 384f / 384f,
//      0f / 384f + 81f / 384f,
//    ),
//    nameResourceId = R.string.top_complication_name,
//  )
//
//  object Bottom : ComplicationConfigDelete(
//    BOTTOM_COMPLICATION_ID, listOf(
//      ComplicationType.SHORT_TEXT,
//    ),
//    renderBounds = RectF(
//      BOTTOM_COMPLICATION_LEFT_BOUND,
//      BOTTOM_COMPLICATION_TOP_BOUND,
//      BOTTOM_COMPLICATION_RIGHT_BOUND,
//      BOTTOM_COMPLICATION_BOTTOM_BOUND,
//    ),
//    slotBounds = RectF(
//      0f / 384f,
//      303f / 384f,
//      0f / 384f + 384f / 384f,
//      303f / 384f + 81f / 384f,
//    ),
//    nameResourceId = R.string.bottom_complication_name,
//  )
//
//  // ==========================================================================
//
//  object TopLeft : ComplicationConfigDelete(
//    COMPLICATIONS_TOP_LEFT_COMPLICATION_ID, listOf(
//      ComplicationType.SHORT_TEXT,
//      ComplicationType.MONOCHROMATIC_IMAGE,
//      ComplicationType.SMALL_IMAGE
//    ),
//    renderBounds = RectF(
//      TOP_LEFT_COMPLICATION_LEFT_BOUND,
//      TOP_LEFT_COMPLICATION_TOP_BOUND-6f/384f,
//      TOP_LEFT_COMPLICATION_RIGHT_BOUND,
//      TOP_LEFT_COMPLICATION_BOTTOM_BOUND+6f/384f,
//    ),
//    slotBounds = RectF(
//      0f / 384f,
//      87f / 384f,
//      0f / 384f + 108f / 384f,
//      87f / 384f + 102f / 384f,
//    ),
//    nameResourceId = R.string.top_left_complication_name,
//  )
//
//  object BottomLeft : ComplicationConfigDelete(
//    COMPLICATIONS_BOTTOM_LEFT_COMPLICATION_ID, listOf(
//      ComplicationType.SHORT_TEXT,
//      ComplicationType.MONOCHROMATIC_IMAGE,
//      ComplicationType.SMALL_IMAGE
//    ),
//    renderBounds = RectF(
//      BOTTOM_LEFT_COMPLICATION_LEFT_BOUND,
//      BOTTOM_LEFT_COMPLICATION_TOP_BOUND-6f/384f,
//      BOTTOM_LEFT_COMPLICATION_RIGHT_BOUND,
//      BOTTOM_LEFT_COMPLICATION_BOTTOM_BOUND+6f/384f,
//    ),
//    slotBounds = RectF(
//      0f / 384f,
//      195f / 384f,
//      0f / 384f + 108f / 384f,
//      195f / 384f + 102f / 384f,
//    ),
//    nameResourceId = R.string.bottom_left_complication_name,
//  )
//
//  object TopRight : ComplicationConfigDelete(
//    COMPLICATIONS_TOP_RIGHT_COMPLICATION_ID, listOf(
//      ComplicationType.SHORT_TEXT,
//      ComplicationType.MONOCHROMATIC_IMAGE,
//      ComplicationType.SMALL_IMAGE
//    ),
//    renderBounds = RectF(
//      TOP_RIGHT_COMPLICATION_LEFT_BOUND,
//      TOP_RIGHT_COMPLICATION_TOP_BOUND-6f/384f,
//      TOP_RIGHT_COMPLICATION_RIGHT_BOUND,
//      TOP_RIGHT_COMPLICATION_BOTTOM_BOUND+6f/384f,
//    ),
//    slotBounds = RectF(
//      276f / 384f,
//      87f / 384f,
//      276f / 384f + 108f / 384f,
//      87f / 384f + 102f / 384f,
//    ),
//    nameResourceId = R.string.top_right_complication_name,
//  )
//
//  object BottomRight : ComplicationConfigDelete(
//    COMPLICATIONS_BOTTOM_RIGHT_COMPLICATION_ID, listOf(
//      ComplicationType.SHORT_TEXT,
//      ComplicationType.MONOCHROMATIC_IMAGE,
//      ComplicationType.SMALL_IMAGE
//    ),
//    renderBounds = RectF(
//      BOTTOM_RIGHT_COMPLICATION_LEFT_BOUND,
//      BOTTOM_RIGHT_COMPLICATION_TOP_BOUND-6f/384f,
//      BOTTOM_RIGHT_COMPLICATION_RIGHT_BOUND,
//      BOTTOM_RIGHT_COMPLICATION_BOTTOM_BOUND+6f/384f,
//    ),
//    slotBounds = RectF(
//      276f / 384f,
//      195f / 384f,
//      276f / 384f + 108f / 384f,
//      195f / 384f + 102f / 384f,
//    ),
//    nameResourceId = R.string.bottom_right_complication_name,
//  )
//
////  object ComplicationsTop : ComplicationConfigDelete(
////    COMPLICATIONS_TOP_COMPLICATION_ID, listOf(
////      ComplicationType.SHORT_TEXT,
////    ),
////    renderBounds = RectF(
////      TOP_COMPLICATION_LEFT_BOUND,
////      TOP_COMPLICATION_TOP_BOUND,
////      TOP_COMPLICATION_RIGHT_BOUND,
////      TOP_COMPLICATION_BOTTOM_BOUND,
////    ),
////    slotBounds = RectF(
////      0f / 384f,
////      0f / 384f,
////      0f / 384f + 384f / 384f,
////      0f / 384f + 81f / 384f,
////    ),
////  )
//
////  object ComplicationsBottom : ComplicationConfigDelete(
////    COMPLICATIONS_BOTTOM_COMPLICATION_ID, listOf(
////      ComplicationType.SHORT_TEXT,
////    ),
////    renderBounds = RectF(
////      BOTTOM_COMPLICATION_LEFT_BOUND,
////      BOTTOM_COMPLICATION_TOP_BOUND,
////      BOTTOM_COMPLICATION_RIGHT_BOUND,
////      BOTTOM_COMPLICATION_BOTTOM_BOUND,
////    ),
////    slotBounds = RectF(
////      0f / 384f,
////      303f / 384f,
////      0f / 384f + 384f / 384f,
////      303f / 384f + 81f / 384f,
////    ),
////  )
//
//  // ==========================================================================
//
//  object LeftIcon : ComplicationConfigDelete(
//    FOCUS_LEFT_ICON_COMPLICATION_ID, listOf(
//      ComplicationType.MONOCHROMATIC_IMAGE,
//      ComplicationType.SMALL_IMAGE
//    ),
//    renderBounds = RectF(
//      LEFT_ICON_COMPLICATION_LEFT_BOUND,
//      LEFT_ICON_COMPLICATION_TOP_BOUND,
//      LEFT_ICON_COMPLICATION_RIGHT_BOUND,
//      LEFT_ICON_COMPLICATION_BOTTOM_BOUND
//    ),
//    slotBounds = RectF(
//      0f / 384f,
//      0f / 384f,
//      0f / 384f + 93f / 384f,
//      0f / 384f + 384f / 384f
//    ),
//    nameResourceId = R.string.left_complication_name,
//  )
//
//  object RightIcon : ComplicationConfigDelete(
//    FOCUS_RIGHT_ICON_COMPLICATION_ID, listOf(
//      ComplicationType.MONOCHROMATIC_IMAGE,
//      ComplicationType.SMALL_IMAGE
//    ),
//    renderBounds = RectF(
//      RIGHT_ICON_COMPLICATION_LEFT_BOUND,
//      RIGHT_ICON_COMPLICATION_TOP_BOUND,
//      RIGHT_ICON_COMPLICATION_RIGHT_BOUND,
//      RIGHT_ICON_COMPLICATION_BOTTOM_BOUND
//    ),
//    slotBounds = RectF(
//      291f / 384f,
//      0f / 384f,
//      291f / 384f + 93f / 384f,
//      0f / 384f + 384f / 384f
//    ),
//    nameResourceId = R.string.right_complication_name,
//  )
//
//  object Text : ComplicationConfigDelete(
//    RIGHT_TEXT_COMPLICATION_ID, listOf(
//      ComplicationType.SHORT_TEXT,
//    ),
//    renderBounds = RectF(
//      RIGHT_TEXT_COMPLICATION_LEFT_BOUND,
//      RIGHT_TEXT_COMPLICATION_TOP_BOUND,
//      RIGHT_TEXT_COMPLICATION_RIGHT_BOUND,
//      RIGHT_TEXT_COMPLICATION_BOTTOM_BOUND
//    ),
//    slotBounds = RectF(
//      RIGHT_TEXT_COMPLICATION_LEFT_BOUND,
//      RIGHT_TEXT_COMPLICATION_TOP_BOUND,
//      RIGHT_TEXT_COMPLICATION_RIGHT_BOUND,
//      RIGHT_TEXT_COMPLICATION_BOTTOM_BOUND
//    ),
//    nameResourceId = R.string.right_complication_name,
//  )
//}

// Utility function that initializes default complication slots (left and right).
fun createComplicationSlotManager(
  context: Context,
  currentUserStyleRepository: CurrentUserStyleRepository,
): ComplicationSlotsManager {
  val verticalComplicationFactory = createVerticalComplicationFactory(context)
  val horizontalComplicationFactory = createHorizontalComplicationFactory(context)
  val invisibleComplicationFactory = createInvisibleComplicationFactory(context)
  val horizontalTextComplicationFactory = createHorizontalTextComplicationFactory(context)

  val hourComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = HOUR_COMPLICATION_ID,
    canvasComplicationFactory = invisibleComplicationFactory,
    supportedTypes = listOf(
      ComplicationType.SHORT_TEXT,
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    ),
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        HOUR_COMPLICATION_LEFT_BOUND,
        HOUR_COMPLICATION_TOP_BOUND,
        HOUR_COMPLICATION_RIGHT_BOUND,
        HOUR_COMPLICATION_BOTTOM_BOUND,
      ),
    )
  ).setNameResourceId(R.string.hour_complication_name)
    .setScreenReaderNameResourceId(R.string.hour_complication_name)
    .setEnabled(false)
    .build()

  val minuteComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = MINUTE_COMPLICATION_ID,
    canvasComplicationFactory = invisibleComplicationFactory,
    supportedTypes = listOf(
      ComplicationType.SHORT_TEXT,
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    ),
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        MINUTE_COMPLICATION_LEFT_BOUND,
        MINUTE_COMPLICATION_TOP_BOUND,
        MINUTE_COMPLICATION_RIGHT_BOUND,
        MINUTE_COMPLICATION_BOTTOM_BOUND,
      )
    )
  ).setNameResourceId(R.string.minute_complication_name)
    .setScreenReaderNameResourceId(R.string.minute_complication_name)
    .setEnabled(false)
    .build()

  val leftComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = LEFT_COMPLICATION_ID,
    canvasComplicationFactory = verticalComplicationFactory,
    supportedTypes = listOf(
      ComplicationType.SHORT_TEXT,
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    ),
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        LEFT_COMPLICATION_LEFT_BOUND,
        LEFT_COMPLICATION_TOP_BOUND,
        LEFT_COMPLICATION_RIGHT_BOUND,
        LEFT_COMPLICATION_BOTTOM_BOUND,
      )
    )
  ).setNameResourceId(R.string.left_complication_name)
    .setScreenReaderNameResourceId(R.string.left_complication_name)
    .setEnabled(false)
    .build()

  val rightComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = RIGHT_COMPLICATION_ID,
    canvasComplicationFactory = verticalComplicationFactory,
    supportedTypes = listOf(
      ComplicationType.SHORT_TEXT,
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    ),
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        RIGHT_COMPLICATION_LEFT_BOUND,
        RIGHT_COMPLICATION_TOP_BOUND,
        RIGHT_COMPLICATION_RIGHT_BOUND,
        RIGHT_COMPLICATION_BOTTOM_BOUND,
      )
    )
  ).setNameResourceId(R.string.right_complication_name)
    .setScreenReaderNameResourceId(R.string.right_complication_name)
    .setEnabled(false)
    .build()

  val topComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = TOP_COMPLICATION_ID,
    canvasComplicationFactory = horizontalComplicationFactory,
    supportedTypes = listOf(
      ComplicationType.SHORT_TEXT,
    ),
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.DATA_SOURCE_DATE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        TOP_COMPLICATION_LEFT_BOUND,
        TOP_COMPLICATION_TOP_BOUND,
        TOP_COMPLICATION_RIGHT_BOUND,
        TOP_COMPLICATION_BOTTOM_BOUND,
      )
    )
  ).setNameResourceId(R.string.top_complication_name)
    .setScreenReaderNameResourceId(R.string.top_complication_name)
    .setEnabled(false)
    .build()

  val bottomComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = BOTTOM_COMPLICATION_ID,
    canvasComplicationFactory = horizontalComplicationFactory,
    supportedTypes = listOf(
      ComplicationType.SHORT_TEXT,
    ),
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.DATA_SOURCE_WATCH_BATTERY, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        BOTTOM_COMPLICATION_LEFT_BOUND,
        BOTTOM_COMPLICATION_TOP_BOUND,
        BOTTOM_COMPLICATION_RIGHT_BOUND,
        BOTTOM_COMPLICATION_BOTTOM_BOUND,
      )
    )
  ).setNameResourceId(R.string.bottom_complication_name)
    .setScreenReaderNameResourceId(R.string.bottom_complication_name)
    .setEnabled(false)
    .build()

  val topLeftComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = COMPLICATIONS_TOP_LEFT_COMPLICATION_ID,
    canvasComplicationFactory = verticalComplicationFactory,
    supportedTypes = listOf(
      ComplicationType.SHORT_TEXT,
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    ),
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        TOP_LEFT_COMPLICATION_LEFT_BOUND,
        TOP_LEFT_COMPLICATION_TOP_BOUND-6f/384f,
        TOP_LEFT_COMPLICATION_RIGHT_BOUND,
        TOP_LEFT_COMPLICATION_BOTTOM_BOUND+6f/384f,
      )
    )
  ).setNameResourceId(R.string.top_left_complication_name)
    .setScreenReaderNameResourceId(R.string.top_left_complication_name)
    .setEnabled(false)
    .build()

  val bottomLeftComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = COMPLICATIONS_BOTTOM_LEFT_COMPLICATION_ID,
    canvasComplicationFactory = verticalComplicationFactory,
    supportedTypes = listOf(
      ComplicationType.SHORT_TEXT,
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    ),
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        BOTTOM_LEFT_COMPLICATION_LEFT_BOUND,
        BOTTOM_LEFT_COMPLICATION_TOP_BOUND-6f/384f,
        BOTTOM_LEFT_COMPLICATION_RIGHT_BOUND,
        BOTTOM_LEFT_COMPLICATION_BOTTOM_BOUND+6f/384f,
      )
    )
  ).setNameResourceId(R.string.bottom_left_complication_name)
    .setScreenReaderNameResourceId(R.string.bottom_left_complication_name)
    .setEnabled(false)
    .build()

  val topRightComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = COMPLICATIONS_TOP_RIGHT_COMPLICATION_ID,
    canvasComplicationFactory = verticalComplicationFactory,
    supportedTypes = listOf(
      ComplicationType.SHORT_TEXT,
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    ),
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        TOP_RIGHT_COMPLICATION_LEFT_BOUND,
        TOP_RIGHT_COMPLICATION_TOP_BOUND-6f/384f,
        TOP_RIGHT_COMPLICATION_RIGHT_BOUND,
        TOP_RIGHT_COMPLICATION_BOTTOM_BOUND+6f/384f,
      )
    )
  ).setNameResourceId(R.string.top_right_complication_name)
    .setScreenReaderNameResourceId(R.string.top_right_complication_name)
    .setEnabled(false)
    .build()

  val bottomRightComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = COMPLICATIONS_BOTTOM_RIGHT_COMPLICATION_ID,
    canvasComplicationFactory = verticalComplicationFactory,
    supportedTypes = listOf(
      ComplicationType.SHORT_TEXT,
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    ),
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        BOTTOM_RIGHT_COMPLICATION_LEFT_BOUND,
        BOTTOM_RIGHT_COMPLICATION_TOP_BOUND-6f/384f,
        BOTTOM_RIGHT_COMPLICATION_RIGHT_BOUND,
        BOTTOM_RIGHT_COMPLICATION_BOTTOM_BOUND+6f/384f,
      )
    )
  ).setNameResourceId(R.string.bottom_right_complication_name)
    .setScreenReaderNameResourceId(R.string.bottom_right_complication_name)
    .setEnabled(false)
    .build()

  val leftIconComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = FOCUS_LEFT_ICON_COMPLICATION_ID,
    canvasComplicationFactory = verticalComplicationFactory,
    supportedTypes = listOf(
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    ),
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
  ).setNameResourceId(R.string.left_complication_name)
    .setScreenReaderNameResourceId(R.string.left_complication_name)
    .setEnabled(false)
    .build()

  val rightIconComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = FOCUS_RIGHT_ICON_COMPLICATION_ID,
    canvasComplicationFactory = verticalComplicationFactory,
    supportedTypes = listOf(
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    ),
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
  ).setNameResourceId(R.string.right_complication_name)
    .setScreenReaderNameResourceId(R.string.right_complication_name)
    .setEnabled(false)
    .build()

  val textComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = RIGHT_TEXT_COMPLICATION_ID,
    canvasComplicationFactory = horizontalTextComplicationFactory,
    supportedTypes = listOf(
      ComplicationType.SHORT_TEXT,
    ),
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.NO_DATA_SOURCE, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        RIGHT_TEXT_COMPLICATION_LEFT_BOUND,
        RIGHT_TEXT_COMPLICATION_TOP_BOUND,
        RIGHT_TEXT_COMPLICATION_RIGHT_BOUND,
        RIGHT_TEXT_COMPLICATION_BOTTOM_BOUND
      )
    )
  ).setNameResourceId(R.string.right_complication_name)
    .setScreenReaderNameResourceId(R.string.right_complication_name)
    .setEnabled(false)
    .build()

  return ComplicationSlotsManager(
    listOf(
      hourComplication,
      minuteComplication,

      leftComplication,
      rightComplication,

      topComplication,
      bottomComplication,

      topLeftComplication,
      bottomLeftComplication,
      topRightComplication,
      bottomRightComplication,

      leftIconComplication,
      rightIconComplication,

      textComplication,
    ), currentUserStyleRepository
  )
}

class ComplicationRenderer {
  val bmpCache = LruCache<String, Bitmap>(1)
  val complCache = LruCache<String, Bitmap>(1)

  fun reset() {
    complCache.evictAll()
  }

  inline fun <reified T : ComplicationData> render(
    bounds: Rect,
    data: ComplicationData,
    renderer: (canvas: Canvas, bounds: Rect, data: T) -> Unit
  ): Bitmap {
    val cacheKey = "${bounds.hashCode()},${data.hashCode()}"

    val cached = complCache.get(cacheKey)
    if (cached != null) {
      return cached
    }

    val bmpKey = "${bounds.hashCode()}"

    var bitmap = bmpCache.get(bmpKey)
    if (bitmap != null) {
      bitmap.eraseColor(Color.TRANSPARENT)
    } else {
      bitmap = Bitmap.createBitmap(
        bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888
      )
      bmpCache.put(bmpKey, bitmap)
    }

    val bitmapCanvas = Canvas(bitmap)
    val rect = Rect(0, 0, bitmap.width, bitmap.height)

    (data as? T)?.let {
      renderer(bitmapCanvas, rect, it)
    }

    complCache.put(cacheKey, bitmap)

    return bitmap
  }

}
