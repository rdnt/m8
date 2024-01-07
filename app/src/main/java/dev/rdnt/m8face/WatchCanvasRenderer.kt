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
import android.graphics.*
import android.util.FloatProperty
import android.view.SurfaceHolder
import android.view.animation.AnimationUtils
import androidx.annotation.Keep
import androidx.core.content.ContextCompat.getDrawable
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withScale
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.DrawMode
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
import dev.rdnt.m8face.utils.HorizontalComplication
import dev.rdnt.m8face.utils.LAYOUT_STYLE_SETTING
import dev.rdnt.m8face.utils.MILITARY_TIME_SETTING
import dev.rdnt.m8face.utils.SECONDS_STYLE_SETTING
import dev.rdnt.m8face.utils.VerticalComplication
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.*

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

  private val scope: CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val coroutineScope: CoroutineScope =
    CoroutineScope(Dispatchers.Main.immediate)

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
    isAntiAlias = true // make sure text is not anti-aliased even with this on
    typeface = context.resources.getFont(R.font.m8stealth57)
    textSize = 112f / 14f * 14f // TODO: 98f/112f
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
    textSize = 112f / 14f * 14f // TODO: 98f/112F
    color = watchFaceColors.secondaryColor
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

  private val ambientExitAnimator =
    AnimatorSet().apply {
      val linearOutSlow =
        AnimationUtils.loadInterpolator(
          context,
          android.R.interpolator.linear_out_slow_in
        )
      play(
        ObjectAnimator.ofFloat(
          drawProperties,
          DrawProperties.TIME_SCALE,
          drawProperties.timeScale,
          1.0f
        ).apply {
          duration = ambientTransitionMs
          interpolator = linearOutSlow
          setAutoCancel(false)
        },
      )
    }

  private val ambientEnterAnimator =
    AnimatorSet().apply {
      val linearOutSlow =
        AnimationUtils.loadInterpolator(
          context,
          android.R.interpolator.linear_out_slow_in
        )

      val keyframes = arrayOf(
        Keyframe.ofFloat(0f, drawProperties.timeScale),
        Keyframe.ofFloat(0.9f, 0f),
        Keyframe.ofFloat(1f, 0f)
      )

      val propertyValuesHolder = PropertyValuesHolder.ofKeyframe(
        DrawProperties.TIME_SCALE,
        *keyframes
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
      watchState.isAmbient.collect { isAmbient ->
        if (isAmbient!!) { // you call this readable? come on
          ambientExitAnimator.cancel()
          drawProperties.timeScale = 0f
        } else {
          ambientExitAnimator.setupStartValues()
          ambientExitAnimator.start()
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
      watchFaceData.secondsStyle.id == SecondsStyle.NONE.id -> 60000 // update once a second
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
          val listOption = options.value as
            UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption

          newWatchFaceData = newWatchFaceData.copy(
            layoutStyle = LayoutStyle.getLayoutStyleConfig(
              listOption.id.toString()
            ),
          )
        }

        COLOR_STYLE_SETTING -> {
          val listOption = options.value as
            UserStyleSetting.ListUserStyleSetting.ListOption

          newWatchFaceData = newWatchFaceData.copy(
            colorStyle = ColorStyle.getColorStyleConfig(
              listOption.id.toString()
            ),
          )
        }

        AMBIENT_STYLE_SETTING -> {
          val listOption = options.value as
            UserStyleSetting.ListUserStyleSetting.ListOption

          newWatchFaceData = newWatchFaceData.copy(
            ambientStyle = AmbientStyle.getAmbientStyleConfig(
              listOption.id.toString()
            ),
          )
        }

        SECONDS_STYLE_SETTING -> {
          val listOption = options.value as
            UserStyleSetting.ListUserStyleSetting.ListOption

          newWatchFaceData = newWatchFaceData.copy(
            secondsStyle = SecondsStyle.getSecondsStyleConfig(
              listOption.id.toString()
            ),
          )
        }

        MILITARY_TIME_SETTING -> {
          val booleanValue = options.value as
            UserStyleSetting.BooleanUserStyleSetting.BooleanOption

          newWatchFaceData = newWatchFaceData.copy(
            militaryTime = booleanValue.value,
          )
        }

        BIG_AMBIENT_SETTING -> {
          val booleanValue = options.value as
            UserStyleSetting.BooleanUserStyleSetting.BooleanOption

          newWatchFaceData = newWatchFaceData.copy(
            bigAmbient = booleanValue.value,
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
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime,
    sharedAssets: AnalogSharedAssets
  ) {
    canvas.drawColor(renderParameters.highlightLayer!!.backgroundTint)

    for ((_, complication) in complicationSlotsManager.complicationSlots) {
      if (complication.enabled) {
        complication.renderHighlightLayer(canvas, zonedDateTime, renderParameters)
      }
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
      var hour: Int
      if (is24Format) {
        hour = zonedDateTime.hour
      } else {
        hour = zonedDateTime.hour % 12
        if (hour == 0) {
          hour = 12
        }
      }

      if (drawProperties.timeScale == 0f) {
        var hourOffsetX = 0f
        var hourOffsetY = 0f
        var minuteOffsetX = 0f
        var minuteOffsetY = 0f

        when (watchFaceData.ambientStyle.id) {
          AmbientStyle.OUTLINE.id -> {
            if (watchFaceData.bigAmbient) {
              hourOffsetX = -99f
              hourOffsetY = -9f
              minuteOffsetX = -99f
              minuteOffsetY = 135f
            } else {
              hourOffsetX = -88f
              hourOffsetY = -8f
              minuteOffsetX = -88f
              minuteOffsetY = 120f
            }
          }

          AmbientStyle.BOLD_OUTLINE.id -> {
            if (watchFaceData.bigAmbient) {
              hourOffsetX = -99f
              hourOffsetY = -9f
              minuteOffsetX = -99f
              minuteOffsetY = 135f
            } else {
              hourOffsetX = -88f
              hourOffsetY = -8f
              minuteOffsetX = -88f
              minuteOffsetY = 120f
            }
          }

          AmbientStyle.FILLED.id -> {
            if (watchFaceData.bigAmbient) {
              hourOffsetX = -99f
              hourOffsetY = -9f
              minuteOffsetX = -99f
              minuteOffsetY = 135f
            } else {
              hourOffsetX = -88f
              hourOffsetY = -8f
              minuteOffsetX = -88f
              minuteOffsetY = 120f
            }
          }
        }

        drawTime(canvas, bounds, hour, ambientHourPaint, hourOffsetX, hourOffsetY, 0f)
        drawTime(
          canvas,
          bounds,
          zonedDateTime.minute,
          ambientMinutePaint,
          minuteOffsetX,
          minuteOffsetY,
          0f
        )
      } else {
        when (watchFaceData.secondsStyle.id) {
          SecondsStyle.NONE.id -> {

          }

          SecondsStyle.DASHES.id -> {
            drawDashes(canvas, bounds, zonedDateTime)
          }

          SecondsStyle.DOTS.id -> {
            drawDots(canvas, bounds, zonedDateTime)
          }
        }

        val scaleOffset = if (this.watchFaceData.bigAmbient) {
          18f / 14f - 1f
        } else {
          16f / 14f - 1f
        }

        drawTime(canvas, bounds, hour, hourPaint, -77f, -7f, scaleOffset) // Rect(0, 0, 152, 14))
        drawTime(
          canvas,
          bounds,
          zonedDateTime.minute,
          minutePaint,
          -77f,
          105f,
          scaleOffset
        )//Rect(0, 0, 152, -210))
      }

    }

    if (renderParameters.watchFaceLayers.contains(WatchFaceLayer.COMPLICATIONS) &&
      drawProperties.timeScale != 0f
    ) {
      drawComplications(canvas, zonedDateTime)
    }
  }

  override fun shouldAnimate(): Boolean {
    return ambientEnterAnimator.isRunning || super.shouldAnimate()
  }

  private fun animating(): Boolean {
    return ambientEnterAnimator.isRunning || ambientExitAnimator.isRunning
  }

  // ----- All drawing functions -----
  private fun drawComplications(canvas: Canvas, zonedDateTime: ZonedDateTime) {
    val opacity = this.easeInOutCirc(drawProperties.timeScale)

    for ((_, complication) in complicationSlotsManager.complicationSlots) {
      if (complication.enabled) {
        when (complication.renderer) {
          is VerticalComplication -> (complication.renderer as VerticalComplication).opacity =
            opacity

          is HorizontalComplication -> (complication.renderer as HorizontalComplication).opacity =
            opacity

          else -> {}
        }

        complication.render(canvas, zonedDateTime, renderParameters)
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
    scaleOffset: Float
  ) {
    // dx:70 dy:98
    val p = Paint(paint)
    p.textSize = p.textSize / 384F * bounds.width()

    var scale = 1f

    p.isAntiAlias = true

    scale += (1f - this.easeInOutCirc(drawProperties.timeScale)) * scaleOffset

    canvas.withScale(scale, scale, bounds.exactCenterX(), bounds.exactCenterY()) {
      canvas.drawText(
        time.toString().padStart(2, '0'),
        bounds.exactCenterX() + offsetX / 384F * bounds.width().toFloat(),
        bounds.exactCenterY() + offsetY / 384F * bounds.height().toFloat(),
        p
      )
    }
  }

  private fun drawDashes(
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime
  ) {
    // Retrieve current time to calculate location/rotation of watch arms.
    val nanoOfDate = zonedDateTime.toLocalTime().toNanoOfDay()

    val secondsPerSecondHandRotation = Duration.ofMinutes(1).seconds
    val secondsRotation =
      (nanoOfDate.toFloat() / 1000000000F).rem(secondsPerSecondHandRotation) * 360.0f /
        secondsPerSecondHandRotation

    val secondHandSize = 12f * this.easeInOutCirc(this.drawProperties.timeScale)

    val secondHandPaint = Paint(this.secondHandPaint)
    secondHandPaint.alpha =
      (this.easeInOutCirc(this.drawProperties.timeScale) * secondHandPaint.alpha).toInt()

    val transitionOffset: Float = this.easeInOutCirc(1 - this.drawProperties.timeScale) * 16f

    canvas.withRotation(secondsRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
      canvas.drawRect(
        RectF(
          (bounds.width() / 2f - 1.25f / 384F * bounds.width()),
          transitionOffset / 384F * bounds.width(),
          (bounds.width() / 2f + 1.25f / 384F * bounds.width()),
          (secondHandSize + transitionOffset * 2) / 384F * bounds.width(),
        ),
        secondHandPaint,
      )
    }

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
        maxSize = 18f
        weight = 1.75f
        minAlpha = maxAlpha / 4f
      } else if (i.mod(30f) == 0f) { // intermediates
        color = watchFaceColors.secondaryColor
        maxSize = 14f
        weight = 1.75f
        minAlpha = maxAlpha / 4f
      } else {
        color = watchFaceColors.tertiaryColor
        maxSize = 10f
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
          ),
          outerElementPaint
        )
      }

      i += 6f
    }
  }

  private fun drawDots(
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime
  ) {
    // Retrieve current time to calculate location/rotation of watch arms.
    val nanoOfDate = zonedDateTime.toLocalTime().toNanoOfDay()

    val secondsPerSecondHandRotation = Duration.ofMinutes(1).seconds
    val secondsRotation =
      (nanoOfDate.toFloat() / 1000000000F).rem(secondsPerSecondHandRotation) * 360.0f /
        secondsPerSecondHandRotation

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

  private class DrawProperties(
    var timeScale: Float = 0f
  ) {
    companion object {
      val TIME_SCALE =
        object : FloatProperty<DrawProperties>("timeScale") {
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
