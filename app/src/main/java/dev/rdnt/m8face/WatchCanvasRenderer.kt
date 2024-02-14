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
package dev.rdnt.m8face

import android.animation.AnimatorSet
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.FloatProperty
import android.view.SurfaceHolder
import android.view.animation.AnimationUtils
import androidx.annotation.Keep
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.get
import androidx.core.graphics.withRotation
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyle
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.WatchFaceLayer
import dev.rdnt.m8face.data.watchface.AmbientStyle
import dev.rdnt.m8face.data.watchface.ColorStyle
import dev.rdnt.m8face.data.watchface.LayoutStyle
import dev.rdnt.m8face.data.watchface.SecondsStyle
import dev.rdnt.m8face.data.watchface.WatchFaceColorPalette.Companion.convertToWatchFaceColorPalette
import dev.rdnt.m8face.data.watchface.WatchFaceData
import dev.rdnt.m8face.utils.AMBIENT_STYLE_SETTING
import dev.rdnt.m8face.utils.BIG_AMBIENT_SETTING
import dev.rdnt.m8face.utils.COLOR_STYLE_SETTING
import dev.rdnt.m8face.utils.DETAILED_AMBIENT_SETTING
import dev.rdnt.m8face.utils.HorizontalComplication
import dev.rdnt.m8face.utils.HorizontalTextComplication
import dev.rdnt.m8face.utils.IconComplication
import dev.rdnt.m8face.utils.LAYOUT_STYLE_SETTING
import dev.rdnt.m8face.utils.MILITARY_TIME_SETTING
import dev.rdnt.m8face.utils.SECONDS_STYLE_SETTING
import dev.rdnt.m8face.utils.VerticalComplication
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


// Default for how long each frame is displayed at expected frame rate.
private const val DEFAULT_INTERACTIVE_DRAW_MODE_UPDATE_DELAY_MILLIS: Long = 16



/**
 * Renders watch face via data in Room database. Also, updates watch face state based on setting
 * changes by user via [userStyleRepository.addUserStyleListener()].
 */
@Keep
class WatchCanvasRenderer(
  private val context: Context,
  surfaceHolder: SurfaceHolder,
  watchState: WatchState,
  private val complicationSlotsManager: ComplicationSlotsManager,
  currentUserStyleRepository: CurrentUserStyleRepository,
  canvasType: Int
) : Renderer.CanvasRenderer2<WatchCanvasRenderer.AnalogSharedAssets>(
  surfaceHolder,
  currentUserStyleRepository,
  watchState,
  canvasType,
  DEFAULT_INTERACTIVE_DRAW_MODE_UPDATE_DELAY_MILLIS,
  clearWithBackgroundTintBeforeRenderingHighlightLayer = false
) {
  class AnalogSharedAssets : SharedAssets {
    override fun onDestroy() {
    }
  }

  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)

  // Represents all data needed to render the watch face. All value defaults are constants. Only
  // three values are changeable by the user (color scheme, ticks being rendered, and length of
  // the minute arm). Those dynamic values are saved in the watch face APIs and we update those
  // here (in the renderer) through a Kotlin Flow.
  private var watchFaceData: WatchFaceData = WatchFaceData(
    colorStyle = ColorStyle.LAST_DANCE,
    ambientStyle = AmbientStyle.OUTLINE,
    secondsStyle = SecondsStyle.NONE,
    militaryTime = true,
    bigAmbient = false,
    layoutStyle = LayoutStyle.INFO1,
  )

  // Converts resource ids into Colors and ComplicationDrawable.
  private var watchFaceColors = convertToWatchFaceColorPalette(
    context,
    watchFaceData.colorStyle,
  )

  private val outerElementPaint = Paint().apply {
    isAntiAlias = true
    color = watchFaceColors.tertiaryColor
  }

  private val secondHandPaint = Paint().apply {
    isAntiAlias = true
    color = Color.parseColor("#45455C")
  }

  private val datePaint = Paint().apply {
    isAntiAlias = true
    typeface = context.resources.getFont(R.font.m8stealth57)
    textSize = 24F
    textAlign = Paint.Align.CENTER
    color = watchFaceColors.tertiaryColor
  }

  private val hourPaint = Paint().apply {
    this.isSubpixelText = true
    isAntiAlias = true // make sure text is not anti-aliased even with this on
    typeface = context.resources.getFont(R.font.m8stealth57)
//    textSize = 112f / 14f * 14f // TODO: 98f/112f
    textSize = 112f / 14f
    color = watchFaceColors.primaryColor
  }

  private val ambientHourPaint = Paint().apply {
    isAntiAlias = true
    val big = watchFaceData.bigAmbient

    if (watchFaceData.ambientStyle.id == AmbientStyle.OUTLINE.id) {
      typeface = context.resources.getFont(R.font.m8stealth57thin)
      textSize = 8F
      if (big) {
        typeface = context.resources.getFont(R.font.m8stealth57thinbig)
        textSize = 8F
      }

    } else if (watchFaceData.ambientStyle.id == AmbientStyle.BOLD_OUTLINE.id) {
      typeface = context.resources.getFont(R.font.m8stealth57thick)
      textSize = 8F // 90px high
      if (big) {
        typeface = context.resources.getFont(R.font.m8stealth57thickbig)
        textSize = 8F
      }
    } else if (watchFaceData.ambientStyle.id == AmbientStyle.FILLED.id) {
      typeface = context.resources.getFont(R.font.m8stealth57)
      textSize = 112F / 14f * 16f
      if (big) {
        textSize = 112F / 14f * 18f
      }
    }

    color = watchFaceColors.primaryColor
  }

  private val minutePaint = Paint().apply {
    isAntiAlias = true // make sure text is not anti-aliased even with this on
    typeface = context.resources.getFont(R.font.m8stealth57)
//    textSize = 112f / 14f * 14f // TODO: 98f/112F
    textSize = 112f / 14f
    color = watchFaceColors.secondaryColor
  }

  private val secondPaint = Paint().apply {
    isAntiAlias = true // make sure text is not anti-aliased even with this on
    typeface = context.resources.getFont(R.font.m8stealth57)
    textSize = 112f / 14f
    color = watchFaceColors.tertiaryColor
  }

  private val ambientMinutePaint = Paint().apply {
    isAntiAlias = true
    val big = watchFaceData.bigAmbient

    if (watchFaceData.ambientStyle.id == AmbientStyle.OUTLINE.id) {
      typeface = context.resources.getFont(R.font.m8stealth57thin)
      textSize = 8F
      if (big) {
        typeface = context.resources.getFont(R.font.m8stealth57thinbig)
        textSize = 8F
      }
    } else if (watchFaceData.ambientStyle.id == AmbientStyle.BOLD_OUTLINE.id) {
      typeface = context.resources.getFont(R.font.m8stealth57thick)
      textSize = 8F
      if (big) {
        typeface = context.resources.getFont(R.font.m8stealth57thickbig)
        textSize = 8F
      }
    } else if (watchFaceData.ambientStyle.id == AmbientStyle.FILLED.id) {
      typeface = context.resources.getFont(R.font.m8stealth57)
      textSize = 112F / 14f * 16f
      if (big) {
        textSize = 112F / 14f * 18f
      }
    }

    color = watchFaceColors.secondaryColor
  }

  private val batteryPaint = Paint().apply {
    isAntiAlias = true
    typeface = context.resources.getFont(R.font.m8stealth57)
    textSize = 24F
    color = watchFaceColors.tertiaryColor
  }

  private val batteryPrefixPaint = Paint().apply {
    isAntiAlias = true
    typeface = context.resources.getFont(R.font.m8stealth57)
    textSize = 24F
    color = Color.parseColor("#343434") // TODO: either store on selected theme or calculate
  }

  private val batteryIconPaint = Paint().apply {
    isAntiAlias = true
    color = watchFaceColors.tertiaryColor
  }

  private var is24Format: Boolean = watchFaceData.militaryTime

  private val ambientTransitionMs = 1000L
  private var drawProperties = DrawProperties()
  private var isHeadless = false
  private var isAmbient = false

  private val bitmapCache: BitmapCache = BitmapCache()

  private val ambientExitAnimator = AnimatorSet().apply {
    val linearOutSlow = AnimationUtils.loadInterpolator(
      context, android.R.interpolator.linear_out_slow_in
    )
    play(
      ObjectAnimator.ofFloat(
        drawProperties, DrawProperties.TIME_SCALE, drawProperties.timeScale, 1.0f
      ).apply {
        duration = ambientTransitionMs
        interpolator = linearOutSlow
        setAutoCancel(false)
      },
    )
  }

  private val ambientEnterAnimator = AnimatorSet().apply {
    val linearOutSlow = AnimationUtils.loadInterpolator(
      context, android.R.interpolator.linear_out_slow_in
    )

    val keyframes = arrayOf(
      Keyframe.ofFloat(0f, drawProperties.timeScale),
      Keyframe.ofFloat(0.9f, 0f),
      Keyframe.ofFloat(1f, 0f)
    )

    val propertyValuesHolder = PropertyValuesHolder.ofKeyframe(
      DrawProperties.TIME_SCALE, *keyframes
    )

    play(
      ObjectAnimator.ofPropertyValuesHolder(drawProperties, propertyValuesHolder).apply {
        duration = ambientTransitionMs * 5 / 9
        interpolator = linearOutSlow
        setAutoCancel(false)
      },
    )
  }

  init {
    scope.launch {
      currentUserStyleRepository.userStyle.collect { userStyle ->
        updateWatchFaceData(userStyle)
      }
    }

    coroutineScope.launch {
      watchState.isAmbient.collect { ambient ->
        isHeadless = watchState.isHeadless
        isAmbient = ambient!!

        if (!watchState.isHeadless) {
          if (isAmbient) {
            ambientEnterAnimator.setupStartValues()
            ambientEnterAnimator.start()
//            ambientExitAnimator.cancel()
//            drawProperties.timeScale = 0f
          } else {
            ambientExitAnimator.setupStartValues()
            ambientExitAnimator.start()
          }
        } else {
          ambientExitAnimator.setupStartValues()
          drawProperties.timeScale = 0f
        }
      }
    }
  }

  override suspend fun createSharedAssets(): AnalogSharedAssets {
    return AnalogSharedAssets()
  }

  private fun updateRefreshRate() {
    interactiveDrawModeUpdateDelayMillis = when {
      animating() -> 16 // 60 fps cause animating
//      watchFaceData.secondsStyle.id == SecondsStyle.NONE.id -> 60000 // update once a second
      watchFaceData.secondsStyle.id == SecondsStyle.NONE.id -> 1000 // update once a second // TODO: handle digital seconds
      watchFaceData.secondsStyle.id == SecondsStyle.DASHES.id -> 16 // 60 fps
      watchFaceData.secondsStyle.id == SecondsStyle.DOTS.id -> 16 // 60 fps
      else -> 60000 // safe default
    }
  }

  /*
   * Triggered when the user makes changes to the watch face through the settings activity. The
   * function is called by a flow.
   */
  private fun updateWatchFaceData(userStyle: UserStyle) {
//    Log.d(TAG, "updateWatchFace(): $userStyle")

    var newWatchFaceData: WatchFaceData = watchFaceData

    // Loops through user style and applies new values to watchFaceData.
    for (options in userStyle) {
      when (options.key.id.toString()) {
        LAYOUT_STYLE_SETTING -> {
          val listOption =
            options.value as UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption

          newWatchFaceData = newWatchFaceData.copy(
            layoutStyle = LayoutStyle.getLayoutStyleConfig(
              listOption.id.toString()
            ),
          )
        }

        COLOR_STYLE_SETTING -> {
          val listOption = options.value as UserStyleSetting.ListUserStyleSetting.ListOption

          newWatchFaceData = newWatchFaceData.copy(
            colorStyle = ColorStyle.getColorStyleConfig(
              listOption.id.toString()
            ),
          )
        }

        AMBIENT_STYLE_SETTING -> {
          val listOption = options.value as UserStyleSetting.ListUserStyleSetting.ListOption

          newWatchFaceData = newWatchFaceData.copy(
            ambientStyle = AmbientStyle.getAmbientStyleConfig(
              listOption.id.toString()
            ),
          )
        }

        SECONDS_STYLE_SETTING -> {
          val listOption = options.value as UserStyleSetting.ListUserStyleSetting.ListOption

          newWatchFaceData = newWatchFaceData.copy(
            secondsStyle = SecondsStyle.getSecondsStyleConfig(
              listOption.id.toString()
            ),
          )
        }

        MILITARY_TIME_SETTING -> {
          val booleanValue = options.value as UserStyleSetting.BooleanUserStyleSetting.BooleanOption

          newWatchFaceData = newWatchFaceData.copy(
            militaryTime = booleanValue.value,
          )
        }

        BIG_AMBIENT_SETTING -> {
          val booleanValue = options.value as UserStyleSetting.BooleanUserStyleSetting.BooleanOption

          newWatchFaceData = newWatchFaceData.copy(
            bigAmbient = booleanValue.value,
          )
        }

        DETAILED_AMBIENT_SETTING -> {
          val booleanValue = options.value as UserStyleSetting.BooleanUserStyleSetting.BooleanOption

          newWatchFaceData = newWatchFaceData.copy(
            detailedAmbient = booleanValue.value,
          )
        }
      }
    }

    // Only updates if something changed.
    if (watchFaceData != newWatchFaceData) {
      watchFaceData = newWatchFaceData

      // Recreates Color and ComplicationDrawable from resource ids.
      watchFaceColors = convertToWatchFaceColorPalette(
        context,
        watchFaceData.colorStyle,
      )

      outerElementPaint.color = watchFaceColors.tertiaryColor
      datePaint.color = watchFaceColors.tertiaryColor

      hourPaint.color = watchFaceColors.primaryColor
      ambientHourPaint.color = watchFaceColors.primaryColor

      minutePaint.color = watchFaceColors.secondaryColor
      ambientMinutePaint.color = watchFaceColors.secondaryColor

      secondPaint.color = watchFaceColors.tertiaryColor

      batteryPaint.color = watchFaceColors.tertiaryColor
      batteryIconPaint.color = watchFaceColors.tertiaryColor

      if (watchFaceData.ambientStyle.id == AmbientStyle.OUTLINE.id) {
        ambientHourPaint.typeface = context.resources.getFont(R.font.m8stealth57thin)
        ambientHourPaint.textSize = 8F

        if (watchFaceData.bigAmbient) {
          ambientHourPaint.typeface = context.resources.getFont(R.font.m8stealth57thinbig)
          ambientHourPaint.textSize = 8F
        }

        ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57thin)
        ambientMinutePaint.textSize = 8F

        if (watchFaceData.bigAmbient) {
          ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57thinbig)
          ambientMinutePaint.textSize = 8F
        }
      } else if (watchFaceData.ambientStyle.id == AmbientStyle.BOLD_OUTLINE.id) {
        ambientHourPaint.typeface = context.resources.getFont(R.font.m8stealth57thick)
        ambientHourPaint.textSize = 8F
        if (watchFaceData.bigAmbient) {
          ambientHourPaint.typeface = context.resources.getFont(R.font.m8stealth57thickbig)
          ambientHourPaint.textSize = 8F
        }

        ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57thick)
        ambientMinutePaint.textSize = 8F
        if (watchFaceData.bigAmbient) {
          ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57thickbig)
          ambientMinutePaint.textSize = 8F
        }
      } else if (watchFaceData.ambientStyle.id == AmbientStyle.FILLED.id) {
        ambientHourPaint.typeface = context.resources.getFont(R.font.m8stealth57)
        ambientHourPaint.textSize = 112F / 14f * 16f
        if (watchFaceData.bigAmbient) {
          ambientHourPaint.textSize = 112F / 14f * 18f
        }

        ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57)
        ambientMinutePaint.textSize = 112F / 14f * 16f
        if (watchFaceData.bigAmbient) {
          ambientMinutePaint.textSize = 112F / 14f * 18f
        }

      }

      is24Format = watchFaceData.militaryTime

      // TODO: update colors for all elements here

      // Applies the user chosen complication color scheme changes. ComplicationDrawables for
      // each of the styles are defined in XML so we need to replace the complication's
      // drawables.
      for ((_, complication) in complicationSlotsManager.complicationSlots) {

        when (complication.renderer) {
          is VerticalComplication -> (complication.renderer as VerticalComplication).tertiaryColor =
            watchFaceColors.tertiaryColor

          is HorizontalComplication -> (complication.renderer as HorizontalComplication).tertiaryColor =
            watchFaceColors.tertiaryColor

          is HorizontalTextComplication -> (complication.renderer as HorizontalTextComplication).tertiaryColor =
            watchFaceColors.tertiaryColor

          is IconComplication -> (complication.renderer as IconComplication).tertiaryColor =
            watchFaceColors.tertiaryColor

          else -> {}
        }
      }
    }
  }

  override fun onDestroy() {
//        Log.d(TAG, "onDestroy()")
    scope.cancel("WatchCanvasRenderer scope clear() request")
    super.onDestroy()
  }

  override fun renderHighlightLayer(
    canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: AnalogSharedAssets
  ) {
    canvas.drawColor(renderParameters.highlightLayer!!.backgroundTint)

    for ((_, complication) in complicationSlotsManager.complicationSlots) {
      if (complication.enabled) {
        complication.renderHighlightLayer(canvas, zonedDateTime, renderParameters)
      }
    }
  }

//        val scaleOffset = if (this.watchFaceData.bigAmbient) {
//          18f / 14f - 1f
//        } else {
//          16f / 14f - 1f
//        }

  private val scale
    get() = if (!isHeadless) drawProperties.timeScale else 1f

  fun interpolate(start: Float, end: Float): Float {
    return start + easeInOutCubic(scale) * (end - start)
  }

  val timeTextSize: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.FOCUS.id -> {
        18f
      }

      else -> {
        if (watchFaceData.detailedAmbient) {
          14f
        } else {
//          interpolate(if (watchFaceData.bigAmbient) 18f else 16f, 14f)
          14f
        }
      }
    }

  val timeTextScale: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.FOCUS.id -> {
        18f / 18f
      }

      else -> {
        if (watchFaceData.detailedAmbient) {
          14f / 14f
        } else {
          interpolate(if (watchFaceData.bigAmbient) 18f / 14f else 16f / 14f, 14f / 14f)
        }
      }
    }

  val timeOffsetX: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.SPORT.id -> {
        if (watchFaceData.detailedAmbient) {
          -34f
        } else {
          interpolate(0f, -34f)
        }
      }

      else -> {
        0f
      }
    }

  val minutesOffsetX: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.SPORT.id -> {
        if (watchFaceData.detailedAmbient) {
          -34f - 11f
        } else {
          interpolate(0f, -34f - 11f)
        }
      }

      else -> {
        0f
      }
    }

  val hourOffsetY: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.FOCUS.id -> {
//        183f - 192f
        -58f - 10f - 4f
//        -7f - 49f
      }

      else -> {
        -56f
//        185f - 192f
//        -7f - 49f
//        interpolate(183f, 185f)
      }
    }

//  val minuteOffsetX: Float
//    get() = when (watchFaceData.layoutStyle.id) {
//      LayoutStyle.SPORT.id -> {
//        81f
//      }
//
//      LayoutStyle.FOCUS.id -> {
//        93f;
//      }
//
//      else -> {
////        115f;
//        interpolate(93f, 115f)
//      }
//    }

  val minuteOffsetY: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.FOCUS.id -> {
        58f + 10f + 4f
      }

      else -> {
        56f
//        interpolate(327f, 297f)
      }
    }

  val shouldDrawSeconds: Boolean
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.INFO1.id -> true
      LayoutStyle.INFO3.id -> true
      LayoutStyle.SPORT.id -> true
      else -> false
    }

//  val secondsOffsetX: Float
//    get() = when(watchFaceData.layoutStyle.id) {
//      LayoutStyle.SPORT.id -> 95f
//      else -> 0f
//    }

  val secondsOffsetX: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.SPORT.id -> {
        if (watchFaceData.detailedAmbient) {
          95f
        } else {
          interpolate(129f, 95f)
        }
      }

      else -> {
        129f
      }
    }

  val secondsOffsetY: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.SPORT.id -> -31f
      else -> 0f
    }

  val shouldDrawAmPm: Boolean
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.SPORT.id -> true
      else -> false
    }

  val secondsTextSize: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.INFO1.id -> 6f
      LayoutStyle.INFO3.id -> 6f
      LayoutStyle.SPORT.id -> 7f
      else -> 0f
    }

  val secondsTextScale: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.FOCUS.id -> {
        18f / 14f
      }

      else -> {
        if (watchFaceData.detailedAmbient) {
          14f / 14f
        } else {
          interpolate(if (watchFaceData.bigAmbient) 18f / 14f else 16f / 14f, 14f / 14f)
        }
      }
    }

  val ampmTextScale: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.FOCUS.id -> {
        18f / 14f
      }

      else -> {
        if (watchFaceData.detailedAmbient) {
          14f / 14f
        } else {
          interpolate(if (watchFaceData.bigAmbient) 18f / 14f else 16f / 14f, 14f / 14f)
        }
      }
    }

  fun getHour(zonedDateTime: ZonedDateTime): Int {
    if (is24Format) {
      return zonedDateTime.hour
    } else {
      val hour = zonedDateTime.hour % 12
      if (hour == 0) {
        return 12
      }
      return hour
    }
  }

  fun getAmPm(zonedDateTime: ZonedDateTime): String {
    return if (zonedDateTime.hour < 12) {
      "am"
    } else {
      "pm"
    }
  }

  override fun render(
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime,
    sharedAssets: AnalogSharedAssets,
  ) {
    updateRefreshRate()

    canvas.drawColor(Color.parseColor("#ff000000"))

    if (renderParameters.watchFaceLayers.contains(WatchFaceLayer.BASE)) {
      drawTime(
        canvas,
        bounds,
        getHour(zonedDateTime),
        hourPaint,
        timeOffsetX,
        hourOffsetY,
        timeTextSize,
        timeTextScale,
        HOURS_BITMAP_KEY
      )

      drawTime(
        canvas,
        bounds,
        zonedDateTime.minute,
        minutePaint,
        timeOffsetX,
        minuteOffsetY,
        timeTextSize,
        timeTextScale,
        MINUTES_BITMAP_KEY
      )

      if (shouldDrawSeconds) {
        drawSeconds(
          canvas,
          bounds,
          zonedDateTime.second,
          secondPaint,
          secondsOffsetX,
          secondsOffsetY,
          secondsTextSize,
          secondsTextScale,
        )
      }


      if (shouldDrawAmPm && false) {
        // TODO
        drawAmPm(
          canvas,
          bounds,
          getAmPm(zonedDateTime),
          secondPaint,
          timeOffsetX,
          25f,
          ampmTextScale,
        )
      }


//      if (drawProperties.timeScale == 0f) {
//        var hourOffsetX = 0f
//        var hourOffsetY = 0f
//        var minuteOffsetX = 0f
//        var minuteOffsetY = 0f
//
//        when (watchFaceData.ambientStyle.id) {
//          AmbientStyle.OUTLINE.id -> {
//            if (watchFaceData.bigAmbient) {
//              hourOffsetX = -99f
//              hourOffsetY = -9f
//              minuteOffsetX = -99f
//              minuteOffsetY = 135f
//            } else {
//              hourOffsetX = -88f
//              hourOffsetY = -8f
//              minuteOffsetX = -88f
//              minuteOffsetY = 120f
//            }
//          }
//
//          AmbientStyle.BOLD_OUTLINE.id -> {
//            if (watchFaceData.bigAmbient) {
//              hourOffsetX = -99f
//              hourOffsetY = -9f
//              minuteOffsetX = -99f
//              minuteOffsetY = 135f
//            } else {
//              hourOffsetX = -88f
//              hourOffsetY = -8f
//              minuteOffsetX = -88f
//              minuteOffsetY = 120f
//            }
//          }
//
//          AmbientStyle.FILLED.id -> {
//            if (watchFaceData.bigAmbient) {
//              hourOffsetX = -99f
//              hourOffsetY = -9f
//              minuteOffsetX = -99f
//              minuteOffsetY = 135f
//            } else {
//              hourOffsetX = -88f
//              hourOffsetY = -8f
//              minuteOffsetX = -88f
//              minuteOffsetY = 120f
//            }
//          }
//        }
//
////        drawTimeOld(canvas, bounds, hour, ambientHourPaint, hourOffsetX, hourOffsetY, 0f)
////        drawTimeOld(
////          canvas,
////          bounds,
////          zonedDateTime.minute,
////          ambientMinutePaint,
////          minuteOffsetX,
////          minuteOffsetY,
////          0f
////        )
//      } else {
//        when (watchFaceData.secondsStyle.id) {
//          SecondsStyle.NONE.id -> {
//
//          }
//
//          SecondsStyle.DASHES.id -> {
//            drawDashes(canvas, bounds, zonedDateTime)
//          }
//
//          SecondsStyle.DOTS.id -> {
//            drawDots(canvas, bounds, zonedDateTime)
//          }
//        }
//
//        val scaleOffset = if (this.watchFaceData.bigAmbient) {
//          18f / 14f - 1f
//        } else {
//          16f / 14f - 1f
//        }
//
////        drawTimeOld(canvas, bounds, hour, hourPaint, -77f, -7f, scaleOffset) // Rect(0, 0, 152, 14))
////        drawTimeOld(
////          canvas,
////          bounds,
////          zonedDateTime.minute,
////          minutePaint,
////          -77f,
////          105f,
////          scaleOffset
////        )//Rect(0, 0, 152, -210))
//      }

    }

//    if (renderParameters.watchFaceLayers.contains(WatchFaceLayer.COMPLICATIONS) &&
//      watchFaceData.detailedAmbient || drawProperties.timeScale != 0f
//      ) {
    drawComplications(canvas, zonedDateTime)
//    }

//    if (renderParameters.watchFaceLayesr.contains(WatchFaceLayer.COMPLICATIONS) &&
//      drawProperties.timeScale != 0f
//    ) {
//      drawComplications(canvas, zonedDateTime)
//    }
  }

  override fun shouldAnimate(): Boolean {
    return ambientEnterAnimator.isRunning || super.shouldAnimate()
  }

  private fun animating(): Boolean {
    return ambientEnterAnimator.isRunning || ambientExitAnimator.isRunning
  }

  // ----- All drawing functions -----
  private fun drawComplications(canvas: Canvas, zonedDateTime: ZonedDateTime) {
    var opacity =
      if (watchFaceData.detailedAmbient) .75f + this.easeInOutCirc(drawProperties.timeScale) / 4 else this.easeInOutCirc(
        drawProperties.timeScale
      )
    opacity = 1f
    val offsetX =
      if (watchFaceData.layoutStyle.id == LayoutStyle.SPORT.id) interpolate(34f, 0f) else 0f
    val scale = interpolate(16f / 14f, 1f)

    for ((_, complication) in complicationSlotsManager.complicationSlots) {
      if (complication.enabled) {
        when (complication.renderer) {
          is VerticalComplication -> {
            (complication.renderer as VerticalComplication).opacity = opacity
            (complication.renderer as VerticalComplication).scale = scale
          }

          is HorizontalComplication -> {
            (complication.renderer as HorizontalComplication).opacity = opacity
            (complication.renderer as HorizontalComplication).offsetX = offsetX
            (complication.renderer as HorizontalComplication).scale = scale
          }

          is HorizontalTextComplication -> {
            (complication.renderer as HorizontalTextComplication).opacity = opacity
            (complication.renderer as HorizontalTextComplication).offsetX = offsetX
            (complication.renderer as HorizontalTextComplication).scale = scale
          }

          is IconComplication -> (complication.renderer as IconComplication).opacity = opacity

          else -> {}
        }

        complication.render(canvas, zonedDateTime, renderParameters)
      }
    }
  }

  private fun drawSeconds(
    canvas: Canvas,
    bounds: Rect,
    time: Int,
    paint: Paint,
    offsetX: Float,
    offsetY: Float,
    textSize: Float,
    textScale: Float,
  ) {
    val text = time.toString().padStart(2, '0')

    var opacity =
      if (watchFaceData.detailedAmbient) .75f + this.easeInOutCirc(drawProperties.timeScale) / 4 else this.easeInOutCirc(
        drawProperties.timeScale
      )
    opacity = 1f

    val bitmap = renderSeconds(text, paint, textSize, watchFaceColors.tertiaryColor, opacity)

    canvas.withScale(textScale, textScale, bounds.exactCenterX(), bounds.exactCenterY()) {
      canvas.drawBitmap(
        bitmap,
        192f - bitmap.width / 2 + offsetX,
        192f - bitmap.height / 2 + offsetY,
        Paint(),
      )
    }
  }

  private fun renderSeconds(
    text: String,
    paint: Paint,
    textSize: Float,
    color: Int,
    opacity: Float,
  ): Bitmap {
    val hash = "${text},${textSize},${color},${opacity}"

    val cached = bitmapCache.get(SECONDS_BITMAP_KEY, hash)
    if (cached != null) {
      return cached
    }

    val p = Paint(paint)
    p.textSize *= textSize
    p.color = ColorUtils.blendARGB(Color.TRANSPARENT, color, opacity)

    val textBounds = Rect()
    p.getTextBounds(text, 0, text.length, textBounds)
    val bounds = Rect(0, 0, textBounds.width(), textBounds.height())

    val bitmap = Bitmap.createBitmap(
      bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)

    canvas.drawText(
      text,
      0f,
      bounds.height().toFloat(),
      p,
    )

    // DEBUG --------------------
    canvas.drawRect(bounds, Paint().apply {
      this.color = Color.parseColor("#22ffffff")
    })
    val p2 = Paint()
    p2.color = Color.WHITE
    canvas.drawText(
      "r ${bitmapCache.loads(SECONDS_BITMAP_KEY)} w ${bitmapCache.renders(SECONDS_BITMAP_KEY)}",
      0f,
      bounds.height().toFloat(),
      p2,
    )
    // ---------------------------

    bitmapCache.set(SECONDS_BITMAP_KEY, hash, bitmap)

    return bitmap
  }

  private fun drawAmPm(
    canvas: Canvas,
    bounds: Rect,
    ampm: String,
    paint: Paint,
    offsetX: Float,
    offsetY: Float,
    _scale: Float,
  ) {
    val text = ampm.uppercase()

    var opacity =
      if (watchFaceData.detailedAmbient) .75f + this.easeInOutCirc(drawProperties.timeScale) / 4 else this.easeInOutCirc(
        drawProperties.timeScale
      )
    opacity = 1f

    val p = Paint(paint)
    p.textSize *= 5f
    p.isAntiAlias = true
    p.isDither = true
    p.isFilterBitmap = true

    var scale = _scale / 14f
//    scale = 1f

    val cacheBitmap = Bitmap.createBitmap(
      bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888
    )
    val bitmapCanvas = Canvas(cacheBitmap)

    val textBounds = Rect()
    p.getTextBounds(text, 0, text.length, textBounds)
    p.color = ColorUtils.blendARGB(Color.TRANSPARENT, watchFaceColors.tertiaryColor, opacity)

    bitmapCanvas.drawText(
      text,
      192f,
      192f + textBounds.height() / 2,
      p,
    )

    canvas.withScale(scale, scale, bounds.exactCenterX(), bounds.exactCenterY()) {
      canvas.withTranslation(offsetX, offsetY) {
        canvas.drawBitmap(
          cacheBitmap,
          0f,
          0f,
          Paint(),
        )
      }
    }
  }

  private fun drawTime(
    canvas: Canvas,
    bounds: Rect,
    time: Int,
    paint: Paint,
    offsetX: Float,
    offsetY: Float,
    textSize: Float,
    textScale: Float,
    cacheKey: String,
  ) {
    val text = time.toString().padStart(2, '0')

    val p = Paint(paint)
    p.textSize *= 14f

    val bitmap = renderTime(text, paint, textSize, cacheKey)

    canvas.withScale(textScale, textScale, bounds.exactCenterX(), bounds.exactCenterY()) {
      canvas.drawBitmap(
        bitmap,
        192f - bitmap.width / 2 + offsetX,
        192f - bitmap.height / 2 + offsetY,
        Paint(),
      )
    }
  }

  private fun renderTime(
    text: String,
    paint: Paint,
    textSize: Float,
    cacheKey: String,
  ): Bitmap {
    val hash = "${text},${textSize}"

    val cached = bitmapCache.get(cacheKey, hash)
    if (cached != null) {
      return cached
    }

    val p = Paint(paint)
    p.textSize *= textSize

    val textBounds = Rect()
    p.getTextBounds(text, 0, text.length, textBounds)
    val bounds = Rect(0, 0, textBounds.width(), textBounds.height())

    val bitmap = Bitmap.createBitmap(
      bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)

    canvas.drawText(
      text,
      0f,
      bounds.height().toFloat(),
      p,
    )

    // DEBUG --------------------
    canvas.drawRect(bounds, Paint().apply {
      this.color = Color.parseColor("#22ffffff")
    })
    val p2 = Paint()
    p2.color = Color.WHITE
    canvas.drawText(
      "r ${bitmapCache.loads(cacheKey)} w ${bitmapCache.renders(cacheKey)}",
      0f,
      bounds.height().toFloat(),
      p2,
    )
    // ---------------------------

    bitmapCache.set(cacheKey, hash, bitmap)

    return bitmap
  }

  private fun drawDashes(
    canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime
  ) {
    // Retrieve current time to calculate location/rotation of watch arms.
    val nanoOfDate = zonedDateTime.toLocalTime().toNanoOfDay()

    val secondsPerSecondHandRotation = Duration.ofMinutes(1).seconds
    val secondsRotation =
      (nanoOfDate.toFloat() / 1000000000F).rem(secondsPerSecondHandRotation) * 360.0f / secondsPerSecondHandRotation

    val secondHandSize = 12f * this.easeInOutCirc(this.drawProperties.timeScale)

    val secondHandPaint = Paint(this.secondHandPaint)
    secondHandPaint.alpha =
      (this.easeInOutCirc(this.drawProperties.timeScale) * secondHandPaint.alpha).toInt()

    val transitionOffset: Float = this.easeInOutCirc(1 - this.drawProperties.timeScale) * 16f

//    canvas.withRotation(secondsRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
//      canvas.drawRect(
//        RectF(
//          (bounds.width() / 2f - 1.25f / 384F * bounds.width()),
//          transitionOffset / 384F * bounds.width(),
//          (bounds.width() / 2f + 1.25f / 384F * bounds.width()),
//          (secondHandSize + transitionOffset * 2) / 384F * bounds.width(),
//        ),
//        secondHandPaint,
//      )
//    }

    val maxAlpha = 255f

    var i = 0f
    while (i < 360f) {
      var color: Int
      var maxSize: Float
      val weight: Float
      val minAlpha: Float

      var size: Float
      var alpha: Float

      if (i.mod(90f) == 0f) { // cardinals
        color = watchFaceColors.primaryColor
        maxSize = 12f
        weight = 1.75f
        minAlpha = maxAlpha / 4f
      } else if (i.mod(30f) == 0f) { // intermediates
        color = watchFaceColors.secondaryColor
        maxSize = 10f
        weight = 1.75f
        minAlpha = maxAlpha / 4f
      } else {
        color = watchFaceColors.tertiaryColor
        maxSize = 8f
        weight = 1.5f
        minAlpha = maxAlpha / 8f
      }

      val minSize = maxSize - 2f

      if (i == 0f) {
        if (secondsRotation > 354f) { // last second animation
          val ratio = (secondsRotation - 354f) / 6f

          size = minSize + (maxSize - minSize) * ratio
          alpha = minAlpha + (maxAlpha - minAlpha) * ratio
        } else {
          val ratio = (354f - secondsRotation) / 354f

          if (secondsRotation < 6f) {
            val ratio2 = 1f - secondsRotation / 6f
            color = ColorUtils.blendARGB(color, Color.WHITE, ratio2)
          }

          size = minSize + (maxSize - minSize) * ratio
          alpha = minAlpha + (maxAlpha - minAlpha) * ratio
        }
      } else {
        if (secondsRotation > 354f) { // last second animation
          val clipAngle = this.easeInOutCirc((secondsRotation - 354f) / 6f) * 360f

          if (i > clipAngle) {
            size = maxSize
            alpha = maxAlpha
          } else {
            size = minSize
            alpha = minAlpha
          }

          if (i == 354f) {
            color = ColorUtils.blendARGB(color, Color.WHITE, (6f - secondsRotation + 354) / 6f)
          }
        } else {
          val diff = i - secondsRotation

          if (diff < -6f) { // elapsed second, draw fully
            size = maxSize
            alpha = maxAlpha
          } else if (diff >= -6f && diff < 0) {
            color = ColorUtils.blendARGB(color, Color.WHITE, (6f + diff) / 6f)
            alpha = maxAlpha
            size = maxSize
          } else if (diff >= 0 && diff < 6f) { // fade in next second indicator
            alpha = minAlpha + (maxAlpha - minAlpha) * (6f - diff) / 6f
            size = maxSize - 2f + (6f - diff) / 3f
          } else {
            size = minSize
            alpha = minAlpha
          }
        }
      }

      size *= this.easeInOutCirc(this.drawProperties.timeScale)
      alpha *= this.easeInOutCirc(this.drawProperties.timeScale)

      outerElementPaint.color = color
      outerElementPaint.alpha = alpha.toInt()

      canvas.withRotation(i, bounds.exactCenterX(), bounds.exactCenterY()) {
        canvas.drawRect(
          RectF(
            bounds.exactCenterX() - weight / 384F * bounds.width(),
            transitionOffset / 384F * bounds.width(),
            bounds.exactCenterX() + weight / 384F * bounds.width(),
            (size + transitionOffset * 2) / 384F * bounds.width(),
          ), outerElementPaint
        )
      }

      i += 6f
    }
  }

  private fun drawDots(
    canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime
  ) {
    // Retrieve current time to calculate location/rotation of watch arms.
    val nanoOfDate = zonedDateTime.toLocalTime().toNanoOfDay()

    val secondsPerSecondHandRotation = Duration.ofMinutes(1).seconds
    val secondsRotation =
      (nanoOfDate.toFloat() / 1000000000F).rem(secondsPerSecondHandRotation) * 360.0f / secondsPerSecondHandRotation

    val div = secondsRotation.div(6f).toInt()
    val mod = secondsRotation.mod(6f)

    val rotation = div * 6f + this.easeInOutCirc(mod / 6f) * 6f

    var centerY = 8f * this.easeInOutCirc(this.drawProperties.timeScale)
    val secondHandSize = 3.5f * this.easeInOutCirc(this.drawProperties.timeScale)
    val transitionOffset = this.easeInOutCirc(1 - this.drawProperties.timeScale) * 24f

    val secondHandPaint = Paint(this.secondHandPaint)
    secondHandPaint.alpha =
      (this.easeInOutCirc(this.drawProperties.timeScale) * secondHandPaint.alpha).toInt()

    canvas.withRotation(rotation, bounds.exactCenterX(), bounds.exactCenterY()) {
      canvas.drawCircle(
        bounds.centerX().toFloat(),
        (centerY + transitionOffset),
        secondHandSize / 384F * bounds.width(),
        secondHandPaint
      )
    }

    val maxAlpha = 255f

    var i = 0f
    while (i < 360f) {
      var color: Int
      val maxSize: Float
      var minAlpha: Float

      var size: Float
      var alpha: Float

      if (i.mod(90f) == 0f) { // cardinals
        color = watchFaceColors.primaryColor
        maxSize = 5f
        minAlpha = maxAlpha / 4f
      } else if (i.mod(30f) == 0f) { // intermediates
        color = watchFaceColors.secondaryColor
        maxSize = 4f
        minAlpha = maxAlpha / 4f
      } else {
        color = watchFaceColors.tertiaryColor
        maxSize = 3.5f
        minAlpha = maxAlpha / 8f
      }

      val minSize = maxSize - 0.5f

      if (i == 0f) {
        if (secondsRotation > 354f) { // last second animation
          val ratio = (secondsRotation - 354f) / 6f

          size = minSize + (maxSize - minSize) * ratio
          alpha = minAlpha + (maxAlpha - minAlpha) * ratio
        } else {
          val ratio = (354f - secondsRotation) / 354f

          if (secondsRotation < 6f) {
            val ratio2 = 1f - secondsRotation / 6f
            color = ColorUtils.blendARGB(color, Color.WHITE, ratio2)
          }

          size = minSize + (maxSize - minSize) * ratio
          alpha = minAlpha + (maxAlpha - minAlpha) * ratio
        }
      } else {
        if (secondsRotation > 354f) { // last second animation
          val clipAngle = this.easeInOutCirc((secondsRotation - 354f) / 6f) * 360f

          if (i > clipAngle) {
            size = maxSize
            alpha = maxAlpha
          } else {
            size = minSize
            alpha = minAlpha
          }

          if (i == 354f) {
            color = ColorUtils.blendARGB(color, Color.WHITE, (6f - secondsRotation + 354) / 6f)
          }
        } else {
          val diff = i - secondsRotation

          if (diff < -6f) { // elapsed second, draw fully
            size = maxSize
            alpha = maxAlpha
          } else if (diff >= -6f && diff < 0) {
            color = ColorUtils.blendARGB(color, Color.WHITE, (6f + diff) / 6f)
            alpha = maxAlpha
            size = maxSize
          } else if (diff >= 0 && diff < 6f) { // fade in next second indicator
            alpha = minAlpha + (maxAlpha - minAlpha) * (6f - diff) / 6f
            size = minSize + (maxSize - minSize) * (6f - diff) / 6f
          } else {
            size = minSize
            alpha = minAlpha
          }
        }
      }

      centerY = 8f * this.easeInOutCirc(this.drawProperties.timeScale)
      size = 0.5f * this.easeInOutCirc(this.drawProperties.timeScale) * size + 0.5f * size
      alpha *= this.easeInOutCirc(this.drawProperties.timeScale)

      outerElementPaint.color = color
      outerElementPaint.alpha = alpha.toInt()

      canvas.withRotation(i, bounds.exactCenterX(), bounds.exactCenterY()) {
        canvas.drawCircle(
          bounds.centerX().toFloat(),
          centerY + transitionOffset,
          size / 384F * bounds.width(),
          outerElementPaint
        )
      }

      i += 6f
    }
  }

  private fun easeInOutQuint(x: Float): Float {
    return if (x < 0.5f) {
      16 * x * x * x * x * x
    } else {
      1 - (-2f * x + 2f).pow(5f) / 2
    }
  }

  private fun easeInOutCirc(x: Float): Float {
    return if (x < 0.5f) {
      (1f - sqrt(1f - (2f * x).pow(2f))) / 2f
    } else {
      (sqrt(1f - (-2f * x + 2f).pow(2f)) + 1f) / 2f
    }
  }

  private fun easeInOutCubic(x: Float): Float {
    return if (x < 0.5f) {
      4 * x * x * x
    } else {
      1 - (-2f * x + 2f).pow(3f) / 2
    }
  }

  private class DrawProperties(
    var timeScale: Float = 0f
  ) {
    companion object {
      val TIME_SCALE = object : FloatProperty<DrawProperties>("timeScale") {
        override fun setValue(obj: DrawProperties, value: Float) {
          obj.timeScale = value
        }

        override fun get(obj: DrawProperties): Float {
          return obj.timeScale
        }
      }
    }
  }

  companion object {
    private const val TAG = "WatchCanvasRenderer"
  }
}

