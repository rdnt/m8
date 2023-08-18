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
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.watchface.CanvasComplication
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.RenderParameters
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.SystemDataSources
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImageComplicationData
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
private const val LEFT_COMPLICATION_RIGHT_BOUND = VERTICAL_COMPLICATION_OFFSET + VERTICAL_COMPLICATION_WIDTH

private const val RIGHT_COMPLICATION_LEFT_BOUND = 1f - VERTICAL_COMPLICATION_OFFSET - VERTICAL_COMPLICATION_WIDTH
private const val RIGHT_COMPLICATION_RIGHT_BOUND = 1f - VERTICAL_COMPLICATION_OFFSET

// Both left and right complications use the same top and bottom bounds.
const val HORIZONTAL_COMPLICATION_LEFT_BOUND = 102f / 384f
const val HORIZONTAL_COMPLICATION_RIGHT_BOUND = 1f - HORIZONTAL_COMPLICATION_LEFT_BOUND

// offset: 18px, height: 51px (canvas 384x384)
const val HORIZONTAL_COMPLICATION_OFFSET = 24f / 384f
const val HORIZONTAL_COMPLICATION_HEIGHT = 48f / 384f

//const val HORIZONTAL_COMPLICATION_WIDTH = HORIZONTAL_COMPLICATION_RIGHT_BOUND - HORIZONTAL_COMPLICATION_LEFT_BOUND

private const val TOP_COMPLICATION_TOP_BOUND = HORIZONTAL_COMPLICATION_OFFSET
private const val TOP_COMPLICATION_BOTTOM_BOUND = HORIZONTAL_COMPLICATION_OFFSET + HORIZONTAL_COMPLICATION_HEIGHT

private const val BOTTOM_COMPLICATION_TOP_BOUND = 1f - HORIZONTAL_COMPLICATION_OFFSET - HORIZONTAL_COMPLICATION_HEIGHT
private const val BOTTOM_COMPLICATION_BOTTOM_BOUND = 1f - HORIZONTAL_COMPLICATION_OFFSET

// Unique IDs for each complication. The settings activity that supports allowing users
// to select their complication data provider requires numbers to be >= 0.
internal const val LEFT_COMPLICATION_ID = 100
internal const val RIGHT_COMPLICATION_ID = 101
internal const val TOP_COMPLICATION_ID = 102
internal const val BOTTOM_COMPLICATION_ID = 103

/**
 * Represents the unique id associated with a complication and the complication types it supports.
 */
sealed class ComplicationConfig(val id: Int, val supportedTypes: List<ComplicationType>) {
  object Left : ComplicationConfig(
    LEFT_COMPLICATION_ID,
    listOf(
      ComplicationType.SHORT_TEXT,
//      ComplicationType.RANGED_VALUE,
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    )
  )

  object Right : ComplicationConfig(
    RIGHT_COMPLICATION_ID,
    listOf(
      ComplicationType.SHORT_TEXT,
//      ComplicationType.RANGED_VALUE,
      ComplicationType.MONOCHROMATIC_IMAGE,
      ComplicationType.SMALL_IMAGE
    )
  )

  object Top : ComplicationConfig(
    TOP_COMPLICATION_ID,
    listOf(
      ComplicationType.SHORT_TEXT,
    )
  )

  object Bottom : ComplicationConfig(
    BOTTOM_COMPLICATION_ID,
    listOf(
      ComplicationType.SHORT_TEXT,
    )
  )
}

// Utility function that initializes default complication slots (left and right).
fun createComplicationSlotManager(
  context: Context,
  currentUserStyleRepository: CurrentUserStyleRepository,
): ComplicationSlotsManager {

  val customLeftComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = ComplicationConfig.Left.id,
    canvasComplicationFactory = createVerticalComplicationFactory(context),
    supportedTypes = ComplicationConfig.Left.supportedTypes,
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
//      SystemDataSources.DATA_SOURCE_DAY_OF_WEEK,
//      ComplicationType.SHORT_TEXT
            SystemDataSources.NO_DATA_SOURCE,
            ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        LEFT_COMPLICATION_LEFT_BOUND,
        VERTICAL_COMPLICATION_TOP_BOUND,
        LEFT_COMPLICATION_RIGHT_BOUND,
        VERTICAL_COMPLICATION_BOTTOM_BOUND
      )
    )
  ).setNameResourceId(R.string.left_complication_name).setScreenReaderNameResourceId(R.string.left_complication_name).build()

  val customRightComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = ComplicationConfig.Right.id,
    canvasComplicationFactory = createVerticalComplicationFactory(context),
    supportedTypes = ComplicationConfig.Right.supportedTypes,
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
//      SystemDataSources.DATA_SOURCE_DAY_OF_WEEK,
//      ComplicationType.SHORT_TEXT
            SystemDataSources.NO_DATA_SOURCE,
            ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        RIGHT_COMPLICATION_LEFT_BOUND,
        VERTICAL_COMPLICATION_TOP_BOUND,
        RIGHT_COMPLICATION_RIGHT_BOUND,
        VERTICAL_COMPLICATION_BOTTOM_BOUND
      )
    )
  ).setNameResourceId(R.string.right_complication_name).setScreenReaderNameResourceId(R.string.right_complication_name).build()

  val customTopComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = ComplicationConfig.Top.id,
    canvasComplicationFactory = createHorizontalComplicationFactory(context),
    supportedTypes = ComplicationConfig.Top.supportedTypes,
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.DATA_SOURCE_DATE,
      ComplicationType.SHORT_TEXT
//            SystemDataSources.NO_DATA_SOURCE,
//            ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        HORIZONTAL_COMPLICATION_LEFT_BOUND,
        TOP_COMPLICATION_TOP_BOUND,
        HORIZONTAL_COMPLICATION_RIGHT_BOUND,
        TOP_COMPLICATION_BOTTOM_BOUND
      )
    )
  ).setNameResourceId(R.string.top_complication_name).setScreenReaderNameResourceId(R.string.top_complication_name).build()

  val customBottomComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = ComplicationConfig.Bottom.id,
    canvasComplicationFactory = createHorizontalComplicationFactory(context),
    supportedTypes = ComplicationConfig.Bottom.supportedTypes,
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
      SystemDataSources.DATA_SOURCE_WATCH_BATTERY,
      ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(
      RectF(
        HORIZONTAL_COMPLICATION_LEFT_BOUND,
        BOTTOM_COMPLICATION_TOP_BOUND,
        HORIZONTAL_COMPLICATION_RIGHT_BOUND,
        BOTTOM_COMPLICATION_BOTTOM_BOUND
      )
    )
  ).setNameResourceId(R.string.bottom_complication_name).setScreenReaderNameResourceId(R.string.bottom_complication_name).build()

  return ComplicationSlotsManager(
    listOf(customLeftComplication, customRightComplication, customTopComplication, customBottomComplication),
    currentUserStyleRepository
  )
}

class RectangleCanvasComplication(private val context: Context): CanvasComplication {
  override fun render(
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime,
    renderParameters: RenderParameters,
    slotId: Int
  ) {
    val start = System.currentTimeMillis()
    Log.d("RectangleCanvasComplication", "render($slotId, ${_data.type}) -- start: ${start}ms")

    val dataSource = _data.dataSource
    val isBattery = dataSource?.className == "com.google.android.clockwork.sysui.experiences.complications.providers.BatteryProviderService"

    // debug
    val dp = Paint()
    dp.color = Color.parseColor("#444444")
//    canvas.drawRect(bounds, dp)

    var text = ""
    var title: String? = null
    var icon: Bitmap? = null
    var iconRect = Rect(0, 0, 32, 32)

    when (_data.type) {
      ComplicationType.SHORT_TEXT -> {
        val dat = _data as ShortTextComplicationData
        text = dat.text.getTextAt(context.resources, Instant.now()).toString()

        if (dat.monochromaticImage != null) {
          val drawable = dat.monochromaticImage!!.image.loadDrawable(context)
          if (drawable != null) {
            icon = drawable.toBitmap(32, 32)
          }
        }

        if (dat.title != null) {
          title = dat.title!!.getTextAt(context.resources, Instant.now()).toString()
        }

      }
      else -> {
        Log.d("TIME", "start: ${start}ms, elapsed: ${System.currentTimeMillis() - start}ms")
        return
      }
    }

    if (isBattery) {
      val drawable = ContextCompat.getDrawable(context, R.drawable.battery_icon)!!
      icon = drawable.toBitmap(30, 15)
      iconRect = Rect(-1, 0, 29, 15)
    }

//    text = "1234567"

    val tp = Paint()
    tp.isAntiAlias = true
    tp.textSize = 24F/384F*canvas.width
    tp.typeface = context.resources.getFont(R.font.m8stealth57)
    tp.textAlign = Paint.Align.CENTER
    tp.color = Color.parseColor("#8888bb")

    var offsetX = iconRect.width()/2f + 6f // half icon width to the right plus 3f to the right for some spacing
    val offsetY = 10.5f

    var prefixLen = 0
    if (isBattery) {
      prefixLen = 3 - text.length
      text = text.padStart(3, ' ')
    }

    val width = 15f * text.length + 3f*(text.length-1)

    if (title != null) {
      offsetX = 0f
      text = "$title $text"
    }

    tp.color = Color.parseColor("#8888bb")

    canvas.drawText(
      text.uppercase(),
      bounds.exactCenterX()+offsetX/384F*canvas.width.toFloat(),
      bounds.exactCenterY()+offsetY/384F*canvas.height.toFloat(),
      tp
    )

    if (isBattery) {
      val prefix = "".padStart(prefixLen, '0') + " ".repeat(3 - prefixLen)

      tp.color = Color.parseColor("#343434")

      canvas.drawText(
        prefix,
        bounds.exactCenterX()+offsetX/384F*canvas.width.toFloat(),
        bounds.exactCenterY()+offsetY/384F*canvas.height.toFloat(),
        tp
      )
    }

    if (icon != null && title == null) {

      val srcRect = iconRect
      val dstRect = RectF(
        bounds.exactCenterX() - iconRect.width()/2f - width/2 - 6f,
        bounds.exactCenterY()-iconRect.height()/2f,
        bounds.exactCenterX() + iconRect.width()/2f - width/2 - 6f,
        bounds.exactCenterY()+iconRect.height()/2f,
      )

      val dp = Paint()
      dp.color = Color.parseColor("#222222")

      val iconPaint = Paint()
      iconPaint.isAntiAlias = false
      iconPaint.colorFilter = PorterDuffColorFilter(Color.parseColor("#8888bb"), PorterDuff.Mode.SRC_IN)
      canvas.drawBitmap(icon, srcRect, dstRect, iconPaint)

    }

    Log.d("TIME", "start: ${start}ms, elapsed: ${System.currentTimeMillis() - start}ms")
  }

  override fun drawHighlight(
    canvas: Canvas,
    bounds: Rect,
    boundsType: Int,
    zonedDateTime: ZonedDateTime,
    color: Int
  ) {}

  private var _data: ComplicationData = NoDataComplicationData()

  override fun getData(): ComplicationData = _data

  override fun loadData(
    complicationData: ComplicationData,
    loadDrawablesAsynchronous: Boolean
  ) {
    _data = complicationData
  }
}
