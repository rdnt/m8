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
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.FloatProperty
import android.view.SurfaceHolder
import android.view.animation.AnimationUtils
import androidx.annotation.Keep
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.graphics.ColorUtils
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
import dev.rdnt.m8face.utils.DEBUG_SETTING
import dev.rdnt.m8face.utils.DETAILED_AMBIENT_SETTING
import dev.rdnt.m8face.utils.HorizontalComplication
import dev.rdnt.m8face.utils.HorizontalTextComplication
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
    debug = false,
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
    textSize = 8f
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
    textSize = 8f
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

  private var debug: Boolean = watchFaceData.debug

  private val ambientTransitionMs = 1000L
  private var drawProperties = DrawProperties()
  private var isHeadless = false
  private var isAmbient = false

  private val bitmapCache: BitmapCache = BitmapCache()

  private var interactiveFrameDelay: Long = 16

  private val ambientExitAnimator = AnimatorSet().apply {
    interpolator = AnimationUtils.loadInterpolator(
      context, android.R.interpolator.accelerate_decelerate
    )
    play(
      ObjectAnimator.ofFloat(
        drawProperties, DrawProperties.TIME_SCALE, 0f, 1.0f
      ).apply {
        duration = ambientTransitionMs
        setAutoCancel(true)
        doOnStart {
          interactiveDrawModeUpdateDelayMillis = 16
        }
        doOnEnd {
          interactiveDrawModeUpdateDelayMillis = interactiveFrameDelay
        }
      },
    )
  }

  private val ambientEnterAnimator = AnimatorSet().apply {
    interpolator = AnimationUtils.loadInterpolator(
      context, android.R.interpolator.accelerate_decelerate
    )

    val keyframes = arrayOf(
      Keyframe.ofFloat(0f, 1f),
      Keyframe.ofFloat(0.9f, 0f),
      Keyframe.ofFloat(1f, 0f),
    )

    val propertyValuesHolder = PropertyValuesHolder.ofKeyframe(
      DrawProperties.TIME_SCALE, *keyframes
    )

    play(
      ObjectAnimator.ofPropertyValuesHolder(drawProperties, propertyValuesHolder).apply {
        duration = ambientTransitionMs
        setAutoCancel(true)
        doOnStart {
          interactiveDrawModeUpdateDelayMillis = 16 // TODO: consider 33 for better battery life
        }
        doOnEnd {
          interactiveDrawModeUpdateDelayMillis = interactiveFrameDelay
        }
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

            ambientExitAnimator.cancel()
            ambientEnterAnimator.cancel()

            ambientEnterAnimator.setupStartValues()
            ambientEnterAnimator.start()
          } else {

            ambientExitAnimator.cancel()
            ambientEnterAnimator.cancel()

            ambientExitAnimator.setupStartValues()
            ambientExitAnimator.start()
          }
        } else {
          drawProperties.timeScale = 0f
        }
      }
    }
  }

  override suspend fun createSharedAssets(): AnalogSharedAssets {
    return AnalogSharedAssets()
  }

//  private fun updateRefreshRate() {
//    interactiveDrawModeUpdateDelayMillis = when {
//      ambientEnterAnimator.isRunning || ambientExitAnimator.isRunning -> 19 // TODO // 60 fps cause animating
////      watchFaceData.secondsStyle.id == SecondsStyle.NONE.id -> 60000 // update once a second
//      watchFaceData.secondsStyle.id == SecondsStyle.NONE.id -> 1000 // update once a second // TODO: handle digital seconds
//      watchFaceData.secondsStyle.id == SecondsStyle.DASHES.id -> 17 // 60 fps TODO
//      watchFaceData.secondsStyle.id == SecondsStyle.DOTS.id -> 18 // 60 fps TODO
//      else -> 60000 // safe default
//    }
//  }

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

        DEBUG_SETTING -> {
          val booleanValue = options.value as UserStyleSetting.BooleanUserStyleSetting.BooleanOption

          newWatchFaceData = newWatchFaceData.copy(
            debug = booleanValue.value,
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
      debug = watchFaceData.debug

      interactiveFrameDelay = when (watchFaceData.secondsStyle.id) {
        SecondsStyle.NONE.id -> if (shouldDrawSeconds) 1000 else 60000
        SecondsStyle.DASHES.id -> 16
        SecondsStyle.DOTS.id -> 16
        else -> 1000
      }
      interactiveDrawModeUpdateDelayMillis = interactiveFrameDelay

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

  val timeTextSizeThick: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.FOCUS.id -> {
//        22f / 16f
        0f
      }

      else -> {
        if (watchFaceData.detailedAmbient) {
//          18f / 16f
          0f
        } else {
//          interpolate(if (watchFaceData.bigAmbient) 18f else 16f, 14f)
          18f / 16f
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

  val timeTextScaleThick: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.FOCUS.id -> {
        18f / 18f
      }

      else -> {
        if (watchFaceData.detailedAmbient) {
          14f / 18f
        } else {
          interpolate(if (watchFaceData.bigAmbient) 18f / 18f else 16f / 18f, 14f / 18f)
        }
      }
    }

  val timeOffsetX: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.SPORT.id -> {
        if (watchFaceData.detailedAmbient) {
          -35f
        } else {
          -35f
//          interpolate(0f, -34f)
        }

      }

      else -> {
        0f
      }
    }

  val timeOffsetXThick: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.SPORT.id -> {
        if (watchFaceData.detailedAmbient) {
          -35f/14f*18f
        } else {
          -35f/14f*18f
//          interpolate(0f, -34f)
        }

      }

      else -> {
        0f
      }
    }

//  val minutesOffsetX: Float
//    get() = when (watchFaceData.layoutStyle.id) {
//      LayoutStyle.SPORT.id -> {
//        if (watchFaceData.detailedAmbient) {
//          -34f - 11f
//        } else {
//          interpolate(0f, -34f - 11f)
//        }
//      }
//
//      else -> {
//        0f
//      }
//    }

  val hourOffsetY: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.FOCUS.id -> {
//        183f - 192f
        -72f
//        -7f - 49f
      }

      else -> {
        -56f
//        185f - 192f
//        -7f - 49f
//        interpolate(183f, 185f)
      }
    }

  val hourOffsetYThick: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.FOCUS.id -> {
//        183f - 192f
        -72f
//        -7f - 49f
      }

      else -> {
        -72f
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

  val minuteOffsetYThick: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.FOCUS.id -> {
        72f
      }

      else -> {
        72f
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
          95f
//          interpolate(129f, 95f)
        }
      }

      else -> {
        129f
      }
    }

  val secondsOffsetY: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.SPORT.id -> -32f
      else -> 0f
    }

  val shouldDrawAmPm: Boolean
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.SPORT.id -> true
      else -> false
    }

  val ampmOffsetX: Float
    get() = if (watchFaceData.detailedAmbient) {
      84f
    } else {
      84f
//      interpolate(84f+34f, 84f)
    }

  val secondsTextSize: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.INFO1.id -> 6f
      LayoutStyle.INFO3.id -> 6f
      LayoutStyle.SPORT.id -> 7f
      else -> 0f
    }

//  val secondsTextScale: Float
//    get() = when (watchFaceData.layoutStyle.id) {
//      LayoutStyle.FOCUS.id -> {
//        18f / 14f
//      }
//
//      else -> {
//        if (watchFaceData.detailedAmbient) {
//          14f / 14f
//        } else {
//          interpolate(if (watchFaceData.bigAmbient) 18f / 14f else 16f / 14f, 14f / 14f)
//        }
//      }
//    }

//  val ampmTextScale: Float
//    get() = if (watchFaceData.detailedAmbient) {
//      14f / 14f
//    } else {
//      interpolate(if (watchFaceData.bigAmbient) 18f / 14f else 16f / 14f, 14f / 14f)
//    }

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

  private var frame: Int = 0

  override fun render(
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime,
    sharedAssets: AnalogSharedAssets,
  ) {
    frame++

    canvas.drawColor(Color.parseColor("#ff000000"))

    if (debug) {
      val p2 = Paint()
      p2.color = Color.parseColor("#aaff1111")
      p2.typeface = context.resources.getFont(R.font.m8stealth57)
      p2.textSize = 8f

      val text = "f $frame s ${"%.2f".format(drawProperties.timeScale)}"

      val textBounds = Rect()
      p2.getTextBounds(text, 0, text.length, textBounds)

      canvas.drawText(
        text,
        192f - textBounds.width() / 2,
        192f + textBounds.height() / 2,
        p2,
      )
    }

    var offsetX =
      if (watchFaceData.layoutStyle.id == LayoutStyle.SPORT.id && !watchFaceData.detailedAmbient) interpolate(
        35f/14f*18f,
        0f
      ) else 0f
//    val offsetX = 0f

    val text14 = Paint(hourPaint).apply {
      isAntiAlias = false
      typeface = context.resources.getFont(R.font.m8stealth57)
      textSize = 144f
    }

    val text16 = Paint(hourPaint).apply {
      isAntiAlias = false
      typeface = context.resources.getFont(R.font.m8stealth57thin)
      textSize = 9f
    }

    val text18 = Paint(hourPaint).apply {
      isAntiAlias = false
      typeface = context.resources.getFont(R.font.m8stealth57thick)
      textSize = 9f
    }

    val hourText = when  {
      (watchFaceData.ambientStyle.id == AmbientStyle.OUTLINE.id && drawProperties.timeScale == 0f) -> {
        Paint(text16)
      }
      (watchFaceData.ambientStyle.id == AmbientStyle.BOLD_OUTLINE.id && drawProperties.timeScale == 0f) -> {
        Paint(text18)
      }
      else -> {
        Paint(text14)
      }
    }

    val minuteText14 = Paint(minutePaint).apply {
      isAntiAlias = false
      typeface = context.resources.getFont(R.font.m8stealth57)
      textSize = 144f
    }

    val minuteText16 = Paint(minutePaint).apply {
      isAntiAlias = false
      typeface = context.resources.getFont(R.font.m8stealth57thin)
      textSize = 9f
    }

    val minuteText18 = Paint(minutePaint).apply {
      isAntiAlias = false
      typeface = context.resources.getFont(R.font.m8stealth57thick)
      textSize = 9f
    }


    val minuteText = when  {
      (watchFaceData.ambientStyle.id == AmbientStyle.OUTLINE.id && drawProperties.timeScale == 0f) -> {
        Paint(minuteText16)
      }
      (watchFaceData.ambientStyle.id == AmbientStyle.BOLD_OUTLINE.id && drawProperties.timeScale == 0f) -> {
        Paint(minuteText18)
      }
      else -> {
        Paint(minuteText14)
      }
    }

      canvas.withScale(timeTextScaleThick, timeTextScaleThick, bounds.exactCenterX(), bounds.exactCenterY()) {

        canvas.withTranslation(offsetX, 0f) {
        if (renderParameters.watchFaceLayers.contains(WatchFaceLayer.BASE)) {
          drawText(
            canvas,
            bounds,
            getHour(zonedDateTime).toString().padStart(2, '0'),
            hourText,
            1f,
            timeOffsetXThick,
            hourOffsetYThick,
            1f,
            HOURS_BITMAP_KEY
          )

          drawText(
            canvas,
            bounds,
            zonedDateTime.minute.toString().padStart(2, '0'),
            minuteText,
            1f,
            timeOffsetXThick,
            minuteOffsetYThick,
            1f,
            MINUTES_BITMAP_KEY
          )
        }
      }
    }

    offsetX =
      if (watchFaceData.layoutStyle.id == LayoutStyle.SPORT.id && !watchFaceData.detailedAmbient) interpolate(
        35f,
        0f
      ) else 0f



    canvas.withScale(timeTextScale, timeTextScale, bounds.exactCenterX(), bounds.exactCenterY()) {
      canvas.withTranslation(offsetX, 0f) {

        val compBmp = Bitmap.createBitmap(
          bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888
        )

        val compCanvas = Canvas(compBmp)

        if (renderParameters.watchFaceLayers.contains(WatchFaceLayer.BASE)) {
//          drawText(
//            canvas,
//            bounds,
//            getHour(zonedDateTime).toString().padStart(2, '0'),
//            hourPaint,
//            1f,
//            timeOffsetX,
//            hourOffsetY,
//            timeTextSize,
//            HOURS_BITMAP_KEY
//          )



//          drawText(
//            canvas,
//            bounds,
//            zonedDateTime.minute.toString().padStart(2, '0'),
//            minutePaint,
//            1f,
//            timeOffsetX,
//            minuteOffsetY,
//            timeTextSize,
//            MINUTES_BITMAP_KEY
//          )

          if (shouldDrawSeconds) {
            drawText(
              compCanvas,
              bounds,
              if (watchFaceData.detailedAmbient && isAmbient) "M8" else zonedDateTime.second.toString()
                .padStart(2, '0'),
              secondPaint,
              1f,
              secondsOffsetX,
              secondsOffsetY,
              secondsTextSize,
              SECONDS_BITMAP_KEY,
            )
          }

          if (shouldDrawAmPm) {
            drawText(
              compCanvas,
              bounds,
              getAmPm(zonedDateTime).uppercase(),
              secondPaint,
              1f,
              ampmOffsetX,
              24f,
              5f,
              AMPM_BITMAP_KEY,
            )
          }

          when (watchFaceData.secondsStyle.id) {
            SecondsStyle.DASHES.id -> {
              drawDashes(compCanvas, bounds, zonedDateTime)
            }

            SecondsStyle.DOTS.id -> {
              drawDots(compCanvas, bounds, zonedDateTime)
            }
          }

        }

        if (renderParameters.watchFaceLayers.contains(WatchFaceLayer.COMPLICATIONS) &&
          (watchFaceData.detailedAmbient || drawProperties.timeScale != 0f)
        ) {
          drawComplications(compCanvas, zonedDateTime)
        }

        val scale = if (isAmbient) .75f else 1f
//        val scale = interpolate(.75f, 1f) // TODO scaling opacity looks weird due to automatic brightness kicking in
        val opacity = if (watchFaceData.detailedAmbient) scale else interpolate(0f, 1f)

        canvas.drawBitmap(
          compBmp,
          bounds.left.toFloat(),
          bounds.top.toFloat(),
          Paint().apply { alpha = (opacity * 255).toInt() },
        )

      }
    }

  }

  override fun shouldAnimate(): Boolean {
    return super.shouldAnimate() || ambientEnterAnimator.isRunning || ambientExitAnimator.isRunning
  }

  // ----- All drawing functions -----
  private fun drawComplications(canvas: Canvas, zonedDateTime: ZonedDateTime) {
//    var opacity =
//      if (watchFaceData.detailedAmbient) .75f + this.easeInOutCirc(drawProperties.timeScale) / 4 else this.easeInOutCirc(
//        drawProperties.timeScale
//      )
//    opacity = 1f

//    val offsetX =
//      if (watchFaceData.layoutStyle.id == LayoutStyle.SPORT.id && !watchFaceData.detailedAmbient) interpolate(34f, 0f) else 0f
//    val offsetX = 0f

//    val maxScale = if (watchFaceData.bigAmbient) 18f / 14f else 16f / 14f
//    var scale = if (watchFaceData.detailedAmbient) 1f else interpolate(maxScale, 1f)
//    if (watchFaceData.layoutStyle.id == LayoutStyle.FOCUS.id) {
//      scale = 1f
//    }

    for ((_, complication) in complicationSlotsManager.complicationSlots) {
      if (complication.enabled) {
        when (complication.renderer) {
          is VerticalComplication -> {
//            (complication.renderer as VerticalComplication).opacity = opacity
//            (complication.renderer as VerticalComplication).scale = scale
            (complication.renderer as VerticalComplication).debug = debug
          }

          is HorizontalComplication -> {
//            (complication.renderer as HorizontalComplication).opacity = opacity
//            (complication.renderer as HorizontalComplication).offsetX = offsetX
//            (complication.renderer as HorizontalComplication).scale = scale
            (complication.renderer as HorizontalComplication).debug = debug
          }

          is HorizontalTextComplication -> {
//            (complication.renderer as HorizontalTextComplication).opacity = opacity
//            (complication.renderer as HorizontalTextComplication).offsetX = offsetX
//            (complication.renderer as HorizontalTextComplication).scale = scale
            (complication.renderer as HorizontalTextComplication).debug = debug
          }

          else -> {}
        }

        complication.render(canvas, zonedDateTime, renderParameters)
      }
    }
  }

  private fun drawText(
    canvas: Canvas,
    bounds: Rect,
    text: String,
    paint: Paint,
    opacity: Float,
    offsetX: Float,
    offsetY: Float,
    textSize: Float,
    cacheKey: String,
  ) {
    val bitmap = renderText(text, paint, textSize, paint.color, cacheKey)

    canvas.drawBitmap(
      bitmap,
      192f - bitmap.width / 2 + offsetX,
      192f - bitmap.height / 2 + offsetY,
      Paint().apply {
        alpha = (opacity * 255).toInt()
      },
    )
  }

  private fun renderText(
    text: String,
    paint: Paint,
    textSize: Float,
    color: Int,
    cacheKey: String,
  ): Bitmap {
    val hash = "${text},${textSize},${color},${debug}"

//    val cached = bitmapCache.get(cacheKey, hash)
//    if (cached != null) {
//      return cached
//    }

    val p = Paint(paint)
    p.textSize *= textSize
    p.color = color

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

    if (debug) {
      canvas.drawRect(bounds, Paint().apply {
        this.color = ColorUtils.blendARGB(Color.TRANSPARENT, Color.parseColor("#aaf2e900"), 1f)
        style = Paint.Style.STROKE
        strokeWidth = 2f
      })
      val p2 = Paint()
      p2.color = ColorUtils.blendARGB(Color.TRANSPARENT, Color.parseColor("#aaf2e900"), 1f)
      p2.typeface = context.resources.getFont(R.font.m8stealth57)
      p2.textSize = 8f
      canvas.drawText(
        "r ${bitmapCache.loads(cacheKey)}",
        3f,
        bounds.height().toFloat() - 12f,
        p2,
      )

      canvas.drawText(
        "w ${bitmapCache.renders(cacheKey)}",
        3f,
        bounds.height().toFloat() - 3f,
        p2,
      )
    }

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

    val transitionOffset: Float = this.easeInOutCirc(1 - this.drawProperties.timeScale) * -16f * 0

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
        weight = 2f
        minAlpha = maxAlpha / 4f
      } else if (i.mod(30f) == 0f) { // intermediates
        color = watchFaceColors.secondaryColor
        maxSize = 10f
        weight = 2f
        minAlpha = maxAlpha / 4f
      } else {
        color = watchFaceColors.tertiaryColor
        maxSize = 8f
        weight = 1.75f
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
    val transitionOffset = this.easeInOutCirc(1 - this.drawProperties.timeScale) * 24f * 0

    val secondHandPaint = Paint(this.secondHandPaint)
    secondHandPaint.alpha =
      (this.easeInOutCirc(this.drawProperties.timeScale) * secondHandPaint.alpha).toInt()

//    canvas.withRotation(rotation, bounds.exactCenterX(), bounds.exactCenterY()) {
//      canvas.drawCircle(
//        bounds.centerX().toFloat(),
//        (centerY + transitionOffset),
//        secondHandSize / 384F * bounds.width(),
//        secondHandPaint
//      )
//    }

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

