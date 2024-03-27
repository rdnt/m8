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
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.FloatProperty
import android.util.Log
import android.util.LruCache
import android.view.SurfaceHolder
import android.view.animation.AnimationUtils
import androidx.annotation.Keep
import androidx.compose.runtime.saveable.autoSaver
import androidx.core.animation.doOnEnd
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.set
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
import dev.rdnt.m8face.utils.COLOR_STYLE_SETTING
import dev.rdnt.m8face.utils.HorizontalComplication
import dev.rdnt.m8face.utils.HorizontalTextComplication
import dev.rdnt.m8face.utils.LAYOUT_STYLE_SETTING
import dev.rdnt.m8face.utils.MILITARY_TIME_SETTING
import dev.rdnt.m8face.utils.SECONDS_STYLE_SETTING
import dev.rdnt.m8face.utils.VerticalComplication
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.system.measureNanoTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val debug = false
private const val debugTiming = false

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
  60000,
  clearWithBackgroundTintBeforeRenderingHighlightLayer = false
) {
  class AnalogSharedAssets : SharedAssets {
    override fun onDestroy() {
    }
  }

  // https://stackoverflow.com/a/32948752
  private val ambientTransitionMs = context.resources.getInteger(android.R.integer.config_longAnimTime).toLong()

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
    textSize = 8f
    color = watchFaceColors.primaryColor
  }

  private val ambientHourPaint = Paint().apply {
    isAntiAlias = true
//    val big = watchFaceData.bigAmbient
    var big = false

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
    color = watchFaceColors.tertiaryColor
    textSize = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.SPORT.id -> 56f
      else -> 48f
    }
  }

  private val ampmPaint = Paint().apply {
    isAntiAlias = true // make sure text is not anti-aliased even with this on
    typeface = context.resources.getFont(R.font.m8stealth57)
    textSize = 112f / 14f * 5f
    color = watchFaceColors.tertiaryColor
  }

  private val ambientMinutePaint = Paint().apply {
    isAntiAlias = true
//    val big = watchFaceData.bigAmbient
    var big = false

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

  private var drawProperties = DrawProperties() // timeScale = 0f
  private var isHeadless = false
  private var isAmbient = false

  private var interactiveFrameDelay: Long = 16

//  private var animating: Boolean = false

  private var enteringAmbient = false
  private var enteringInteractive = false
//  private var enteringAmbientFrame = 0
//  private var enteringInteractiveFrame = 0

  private val ambientExitAnimatorOld =
    AnimatorSet().apply {
      play(
        ObjectAnimator.ofFloat(drawProperties, DrawProperties.TIME_SCALE, 1.0f).apply {
          interpolator =
            AnimationUtils.loadInterpolator(
              context,
              android.R.interpolator.accelerate_decelerate
            )
          setAutoCancel(true)
        },
      )
    }

  // Animation played when entering ambient mode.

  private val ambientEnterAnimatorOld =
    AnimatorSet().apply {
//        interpolator = AnimationUtils.loadInterpolator(
//          context,
//          android.R.interpolator.decelerate_cubic
//        )

//      val keyframes = arrayOf(
//        Keyframe.ofFloat(0f, 1f),
//        Keyframe.ofFloat(1f, 0f),
//      )
//
//      val propertyValuesHolder = PropertyValuesHolder.ofKeyframe(
//        DrawProperties.TIME_SCALE, *keyframes, Keyframe.ofFloat(1f, 0f)
//      )

      play(
//        ObjectAnimator.ofFloat(drawProperties, DrawProperties.TIME_SCALE, drawProperties.timeScale).apply {
//
//          duration = ambientTransitionMs/4
//        },
        ObjectAnimator.ofFloat(drawProperties, DrawProperties.TIME_SCALE, 1f).apply {
          interpolator = AnimationUtils.loadInterpolator(
            context,
            android.R.interpolator.accelerate_decelerate
          )
//          setAutoCancel(true)
//          duration = ambientTransitionMs
        },
      )
    }

  private val ambientAnimator =
//    AnimatorSet().apply {
//        interpolator = AnimationUtils.loadInterpolator(
//          context,
//          android.R.interpolator.decelerate_cubic
//        )

//      val keyframes = arrayOf(
//        Keyframe.ofFloat(0f, 1f),
//        Keyframe.ofFloat(1f, 0f),
//      )
//
//      val propertyValuesHolder = PropertyValuesHolder.ofKeyframe(
//        DrawProperties.TIME_SCALE, *keyframes, Keyframe.ofFloat(1f, 0f)
//      )

//      play(
//        ObjectAnimator.ofFloat(drawProperties, DrawProperties.TIME_SCALE, drawProperties.timeScale).apply {
//          setAutoCancel(true)
//        },
        ObjectAnimator.ofFloat(drawProperties, DrawProperties.TIME_SCALE, 1f).apply {
          interpolator = AnimationUtils.loadInterpolator(
            context,
            android.R.interpolator.accelerate_decelerate
          )
          duration = ambientTransitionMs
          setAutoCancel(true)
        }
//      )
//    }

//  val animator = ObjectAnimator.ofFloat(drawProperties, DrawProperties.TIME_SCALE, 1.0f).apply {
//    interpolator = AnimationUtils.loadInterpolator(
//      context,
//      android.R.interpolator.accelerate_decelerate
//    )
//    setAutoCancel(true)
//  }


//  override fun onRenderParametersChanged(renderParameters: RenderParameters) {
//    super.onRenderParametersChanged(renderParameters)
//
//    preloadBitmaps()
//  }

//  private var animating = false
  private var animatingEnterOld = false
  private var animatingExitOld = false
  private var animating = false

  private lateinit var state: UserStyle

  var bitmapsInitialized: Boolean = false
  var bitmapsScale: Float = 0f

  override suspend fun init() {
    super.init()
//    preloadBitmaps()
  }

  init {
    scope.launch {
      currentUserStyleRepository.userStyle.collect { userStyle ->
        updateWatchFaceData(userStyle)
        bitmapsInitialized = false
//        preloadBitmaps(bounds.width())
        state = userStyle
      }
    }

    coroutineScope.launch {
      watchState.isAmbient.collect { ambient ->
        isHeadless = watchState.isHeadless
        isAmbient = ambient!!

        if (!watchState.isHeadless) {
//          animating = true

          if (isAmbient) {
//            Log.d("@@@", "ambient on")
//            drawProperties.timeScale = 0f
//            ambientEnterAnimator.setupStartValues()
            interactiveDrawModeUpdateDelayMillis = 33


//            Log.d("@@@", "cancel before enter")
//
//            interactiveDrawModeUpdateDelayMillis = 16

//            ambientEnterAnimator.apply {
//              doOnStart {
////                Log.d("@@@", "enter start")
////                interactiveDrawModeUpdateDelayMillis = 16
//              }
//
//              doOnEnd {
////                Log.d("@@@", "enter end")
//
////                animating = false
////                interactiveDrawModeUpdateDelayMillis = interactiveFrameDelay
////                interactiveDrawModeUpdateDelayMillis = 16
//              }
//            }
//            ambientExitAnimator.cancel()

//            enteringInteractive = false
//            enteringAmbient = true
//            enteringAmbientFrame = 1
//            interactiveDrawModeUpdateDelayMillis = 16

//            if (!animating) {
//              animating = true
//            }

//            Log.d("@@@", ambientTransitionMs.toString())

            ambientAnimator.cancel()
            animating = true
            ambientAnimator.setFloatValues(0f)
            ambientAnimator.duration = ((drawProperties.timeScale) * (ambientTransitionMs*2).toFloat()).toLong()
            ambientAnimator.start()

//            ambientEnterAnimator.doOnEnd {
//              animatingEnter = false
//            }

//            ambientAnimator.setupStartValues()
//            ambientEnterAnimator.duration = (drawProperties.timeScale * ambientTransitionMs.toFloat()).toLong()
//            ambientAnimator.childAnimations[0].duration = (drawProperties.timeScale * (ambientTransitionMs/2).toFloat()).toLong()
//            ambientAnimator.childAnimations[1].duration = (drawProperties.timeScale * (ambientTransitionMs/2*3).toFloat()).toLong()
//            interactiveDrawModeUpdateDelayMillis = 16

//            ambientAnimator.setDuration(ambientTransitionMs*2)
//            ambientAnimator.start()

          } else {
            interactiveDrawModeUpdateDelayMillis = 16
//            Log.d("@@@", "ambient off")
//            interactiveDrawModeUpdateDelayMillis = 16
//            Log.d("@@@", "cancel before exit")

//            ambientEnterAnimator.removeAllListeners()
//            ambientExitAnimator.removeAllListeners()
////
//            ambientEnterAnimator.cancel()
//            ambientExitAnimator.cancel()

            ambientAnimator.cancel()
            animating = true
            ambientAnimator.setFloatValues(1f)
            ambientAnimator.duration = ((1f-drawProperties.timeScale) * (ambientTransitionMs).toFloat()).toLong()
            ambientAnimator.start()

//            ambientExitAnimator.doOnEnd {
//              animatingExit = false
//            }
//
//            interactiveDrawModeUpdateDelayMillis = 16
//            animating = true

//            ambientExitAnimator.apply {
//              doOnStart {
////                Log.d("@@@", "exit start")
////                animating = true
//              }
//              doOnEnd {
////                Log.d("@@@", "exit end")
////                animating = false
////                interactiveDrawModeUpdateDelayMillis = interactiveFrameDelay
//              }
//            }

//            ambientEnterAnimator.cancel()

//            enteringInteractive = true
//            enteringAmbient = false
//            interactiveDrawModeUpdateDelayMillis = 16
//            enteringInteractiveFrame = 1

//            if (!animating) {
//              animating = true
//              interactiveDrawModeUpdateDelayMillis = 16
//            }

//            Log.d("@@@", "starting from ${(1f-drawProperties.timeScale) * AMBIENT_TRANSITION_MS.toFloat()} / $AMBIENT_TRANSITION_MS")
//            ambientAnimator.setupStartValues()
//            ambientExitAnimator.currentPlayTime = ((1f-drawProperties.timeScale) * AMBIENT_TRANSITION_MS.toFloat()).toLong()

//            ambientAnimator.duration = ((drawProperties.timeScale) * ambientTransitionMs.toFloat()).toLong()
//            ambientAnimator.childAnimations[1].duration = ((1f-drawProperties.timeScale) * ambientTransitionMs.toFloat()).toLong()
//            interactiveDrawModeUpdateDelayMillis = 16
//            ambientAnimator.setDuration(ambientTransitionMs)
//            ambientAnimator.start()
          }
        } else {
          drawProperties.timeScale = 1f
        }
      }
    }
  }

  private val memoryCache: LruCache<String, Bitmap> = LruCache<String, Bitmap>(484)

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
      secondPaint.textSize = when (watchFaceData.layoutStyle.id) {
        LayoutStyle.SPORT.id -> 56f
        else -> 48f
      }

      ampmPaint.color = watchFaceColors.tertiaryColor

      batteryPaint.color = watchFaceColors.tertiaryColor
      batteryIconPaint.color = watchFaceColors.tertiaryColor

      if (watchFaceData.ambientStyle.id == AmbientStyle.OUTLINE.id) {
        ambientHourPaint.typeface = context.resources.getFont(R.font.m8stealth57thin)
        ambientHourPaint.textSize = 8F

//        if (watchFaceData.bigAmbient) {
//          ambientHourPaint.typeface = context.resources.getFont(R.font.m8stealth57thinbig)
//          ambientHourPaint.textSize = 8F
//        }
        if (false) {
          ambientHourPaint.typeface = context.resources.getFont(R.font.m8stealth57thinbig)
          ambientHourPaint.textSize = 8F
        }

        ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57thin)
        ambientMinutePaint.textSize = 8F

//        if (watchFaceData.bigAmbient) {
//          ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57thinbig)
//          ambientMinutePaint.textSize = 8F
//        }
        if (false) {
          ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57thinbig)
          ambientMinutePaint.textSize = 8F
        }
      } else if (watchFaceData.ambientStyle.id == AmbientStyle.BOLD_OUTLINE.id) {
        ambientHourPaint.typeface = context.resources.getFont(R.font.m8stealth57thick)
        ambientHourPaint.textSize = 8F
//        if (watchFaceData.bigAmbient) {
//          ambientHourPaint.typeface = context.resources.getFont(R.font.m8stealth57thickbig)
//          ambientHourPaint.textSize = 8F
//        }
        if (false) {
          ambientHourPaint.typeface = context.resources.getFont(R.font.m8stealth57thickbig)
          ambientHourPaint.textSize = 8F
        }

        ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57thick)
        ambientMinutePaint.textSize = 8F
//        if (watchFaceData.bigAmbient) {
//          ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57thickbig)
//          ambientMinutePaint.textSize = 8F
//        }
        if (false) {
          ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57thickbig)
          ambientMinutePaint.textSize = 8F
        }
      } else if (watchFaceData.ambientStyle.id == AmbientStyle.FILLED.id) {
        ambientHourPaint.typeface = context.resources.getFont(R.font.m8stealth57)
        ambientHourPaint.textSize = 112F / 14f * 16f
//        if (watchFaceData.bigAmbient) {
//          ambientHourPaint.textSize = 112F / 14f * 18f
//        }
        if (false) {
          ambientHourPaint.textSize = 112F / 14f * 18f
        }

        ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57)
        ambientMinutePaint.textSize = 112F / 14f * 16f
//        if (watchFaceData.bigAmbient) {
//          ambientMinutePaint.textSize = 112F / 14f * 18f
//        }
        if (false) {
          ambientMinutePaint.textSize = 112F / 14f * 18f
        }

      }

      is24Format = watchFaceData.militaryTime

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

  private fun preloadBitmaps(bounds: Rect, scale: Float) {
    Log.d("WatchCanvasRenderer", "preloadBitmaps($scale)")
    bitmapsScale = scale

    val compBmp = Bitmap.createBitmap(
      bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888
    )
    memoryCache.put("comp", compBmp)

    val canvas = Canvas()
    preloadHourBitmaps(canvas, scale)
    preloadHourBitmapsOutline(canvas, scale)
    preloadHourBitmapsOutlineBig(canvas, scale)
    preloadHourBitmapsBoldOutline(canvas, scale)
    preloadHourBitmapsBoldOutlineBig(canvas, scale)
    preloadMinuteBitmaps(canvas, scale)
    preloadMinuteBitmapsOutline(canvas, scale)
    preloadMinuteBitmapsOutlineBig(canvas, scale)
    preloadMinuteBitmapsBoldOutline(canvas, scale)
    preloadMinuteBitmapsBoldOutlineBig(canvas, scale)
    preloadSecondBitmaps(canvas, scale)
    preloadAmPmBitmaps(canvas, scale)
  }

  private val hourPaintNormal2: Paint
    get() = Paint(hourPaint).apply {
      isAntiAlias = false

      color = watchFaceColors.primaryColor

      typeface = context.resources.getFont(R.font.m8stealth57)
      textSize = 144f
    }

  private val hourPaint2: Paint
    get() = Paint(hourPaint).apply {
      isAntiAlias = false

      color = watchFaceColors.primaryColor

      typeface = context.resources.getFont(R.font.m8stealth57thin)
      textSize = 8f
    }

  private val hourPaintBig2: Paint
    get() = Paint(hourPaint).apply {
      isAntiAlias = false

      color = watchFaceColors.primaryColor

      typeface = context.resources.getFont(R.font.m8stealth57thinbig)
      textSize = 8f
    }

  private val hourPaintBold2: Paint
    get() = Paint(hourPaint).apply {
      isAntiAlias = false

      color = watchFaceColors.primaryColor

      typeface = context.resources.getFont(R.font.m8stealth57thick)
      textSize = 8f
    }

  private val hourPaintBoldBig2: Paint
    get() = Paint(hourPaint).apply {
      isAntiAlias = false

      color = watchFaceColors.primaryColor

      typeface = context.resources.getFont(R.font.m8stealth57thickbig)
      textSize = 8f
    }

  private val minutePaintNormal2: Paint
    get() = Paint(hourPaint).apply {
      isAntiAlias = false

      color = watchFaceColors.secondaryColor

      typeface = context.resources.getFont(R.font.m8stealth57)
      textSize = 144f
    }

  private val minutePaint2: Paint
    get() = Paint(hourPaint).apply {
      isAntiAlias = false

      color = watchFaceColors.secondaryColor

      typeface = context.resources.getFont(R.font.m8stealth57thin)
      textSize = 8f
    }

  private val minutePaintBig2: Paint
    get() = Paint(hourPaint).apply {
      isAntiAlias = false

      color = watchFaceColors.secondaryColor

      typeface = context.resources.getFont(R.font.m8stealth57thinbig)
      textSize = 8f
    }

  private val minutePaintBold2: Paint
    get() = Paint(hourPaint).apply {
      isAntiAlias = false

      color = watchFaceColors.secondaryColor

      typeface = context.resources.getFont(R.font.m8stealth57thick)
      textSize = 8f
    }

  private val minutePaintBoldBig2: Paint
    get() = Paint(hourPaint).apply {
      isAntiAlias = false

      color = watchFaceColors.secondaryColor

      typeface = context.resources.getFont(R.font.m8stealth57thickbig)
      textSize = 8f
    }

  private val secondPaintNormal2: Paint
    get() = Paint(hourPaint).apply {
      isAntiAlias = false

      color = watchFaceColors.tertiaryColor

      typeface = context.resources.getFont(R.font.m8stealth57)
      textSize = 56f
    }

  private val ampmPaint2: Paint
    get() = Paint(hourPaint).apply {
      isAntiAlias = false

      color = watchFaceColors.tertiaryColor

      typeface = context.resources.getFont(R.font.m8stealth57)
      textSize = 40f
    }

  private fun preloadBitmap(tmpCanvas: Canvas, time: String, paint: Paint): Bitmap {
    var textBounds = Rect()
    paint.getTextBounds(time, 0, time.length, textBounds)
    textBounds = Rect(0, 0, textBounds.width(), textBounds.height())

    val bmp = Bitmap.createBitmap(
      textBounds.width(), textBounds.height(), Bitmap.Config.ARGB_8888
    )

    tmpCanvas.setBitmap(bmp)

    tmpCanvas.drawText(
      time,
      0f,
      textBounds.height().toFloat(),
      paint,
    )

    tmpCanvas.setBitmap(null)

    return bmp//.asShared()
  }

  private fun preloadHourBitmaps(canvas: Canvas, scale: Float) {
    for (i in 0..23) {
      val hour = i.toString().padStart(2, '0')

      val bmp = preloadBitmap(canvas, hour, hourPaintNormal2.apply {
        textSize *= scale
      })

      val cacheKey = "hour_normal_$hour"

      memoryCache.put(cacheKey, bmp)
    }
  }

  private fun preloadHourBitmapsOutline(canvas: Canvas, scale: Float) {
    for (i in 0..23) {
      val hour = i.toString().padStart(2, '0')

      val bmp = preloadBitmap(canvas, hour, Paint(hourPaint2).apply {
        textSize *= scale
      })

      val cacheKey = "hour_outline_$hour"
      memoryCache.put(cacheKey, bmp)
    }
  }

  private fun preloadHourBitmapsOutlineBig(canvas: Canvas, scale: Float) {
    for (i in 0..23) {
      val hour = i.toString().padStart(2, '0')

      val bmp = preloadBitmap(canvas, hour, Paint(hourPaintBig2).apply {
        textSize *= scale
      })

      val cacheKey = "hour_big_outline_$hour"
      memoryCache.put(cacheKey, bmp)
    }
  }

  private fun preloadHourBitmapsBoldOutline(canvas: Canvas, scale: Float) {
    for (i in 0..23) {
      val hour = i.toString().padStart(2, '0')

      val bmp = preloadBitmap(canvas, hour, Paint(hourPaintBold2).apply {
        textSize *= scale
      })

      val cacheKey = "hour_bold_outline_$hour"
      memoryCache.put(cacheKey, bmp)
    }
  }

  private fun preloadHourBitmapsBoldOutlineBig(canvas: Canvas, scale: Float) {
    for (i in 0..23) {
      val hour = i.toString().padStart(2, '0')

      val bmp = preloadBitmap(canvas, hour, Paint(hourPaintBoldBig2).apply {
        textSize *= scale
      })

      val cacheKey = "hour_big_bold_outline_$hour"
      memoryCache.put(cacheKey, bmp)
    }
  }

  private fun preloadMinuteBitmaps(canvas: Canvas, scale: Float) {
    for (i in 0..59) {
      val minute = i.toString().padStart(2, '0')

      val bmp = preloadBitmap(canvas, minute, minutePaintNormal2.apply {
        textSize *= scale
      })

      val cacheKey = "minute_normal_$minute"

      memoryCache.put(cacheKey, bmp)
    }
  }

  private fun preloadMinuteBitmapsOutline(canvas: Canvas, scale: Float) {
    for (i in 0..59) {
      val minute = i.toString().padStart(2, '0')

      val bmp = preloadBitmap(canvas, minute, minutePaint2.apply {
        textSize *= scale
      })

      val cacheKey = "minute_outline_$minute"
      memoryCache.put(cacheKey, bmp)
    }
  }

  private fun preloadMinuteBitmapsOutlineBig(canvas: Canvas, scale: Float) {
    for (i in 0..59) {
      val minute = i.toString().padStart(2, '0')

      val bmp = preloadBitmap(canvas, minute, minutePaintBig2.apply {
        textSize *= scale
      })

      val cacheKey = "minute_big_outline_$minute"
      memoryCache.put(cacheKey, bmp)
    }
  }

  private fun preloadMinuteBitmapsBoldOutline(canvas: Canvas, scale: Float) {
    for (i in 0..59) {
      val minute = i.toString().padStart(2, '0')

      val bmp = preloadBitmap(canvas, minute, minutePaintBold2.apply {
        textSize *= scale
      })

      val cacheKey = "minute_bold_outline_$minute"
      memoryCache.put(cacheKey, bmp)
    }
  }

  private fun preloadMinuteBitmapsBoldOutlineBig(canvas: Canvas, scale: Float) {
    for (i in 0..59) {
      val minute = i.toString().padStart(2, '0')

      val bmp = preloadBitmap(canvas, minute, minutePaintBoldBig2.apply {
        textSize *= scale
      })

      val cacheKey = "minute_big_bold_outline_$minute"
      memoryCache.put(cacheKey, bmp)
    }
  }

  private fun preloadSecondBitmaps(canvas: Canvas, scale: Float) {
    for (i in 0..60) {
      val second = if (i == 60) "M8" else i.toString().padStart(2, '0')

      val bmp = preloadBitmap(canvas, second, secondPaintNormal2.apply {
        textSize *= scale
      })
      val cacheKey = "second_normal_$second"
      memoryCache.put(cacheKey, bmp)
    }
  }

  private fun preloadAmPmBitmaps(canvas: Canvas, scale: Float) {
    for (text in arrayOf("AM", "PM")) {
      val bmp = preloadBitmap(canvas, text, ampmPaint2.apply {
        textSize *= scale
      })
      val cacheKey = "ampm_$text"
      memoryCache.put(cacheKey, bmp)
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

  private val scale
    get() = if (!isHeadless) drawProperties.timeScale else 1f

  fun interpolate(start: Float, end: Float): Float {
    return start + easeInOutCubic(scale) * (end - start)
  }

  val timeScale: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.INFO1.id, LayoutStyle.INFO2.id, LayoutStyle.INFO3.id, LayoutStyle.INFO4.id, LayoutStyle.SPORT.id -> {
        when(watchFaceData.ambientStyle) {
          AmbientStyle.OUTLINE, AmbientStyle.BOLD_OUTLINE -> {
            if (drawProperties.timeScale == 0f) {
              18f / 18f
            } else {
              interpolate(16f / 18f, 14f / 18f)
            }
          }
          AmbientStyle.BIG_OUTLINE, AmbientStyle.BIG_BOLD_OUTLINE, AmbientStyle.BIG_FILLED -> {
            if (drawProperties.timeScale == 0f) {
              18f / 18f
            } else {
              interpolate(18f / 18f, 14f / 18f)
            }
          }
          AmbientStyle.FILLED -> {
            if (drawProperties.timeScale == 0f) {
              16f / 18f
            } else {
              interpolate(16f / 18f, 14f / 18f)
            }
          }
          AmbientStyle.DETAILED -> 14f / 18f
        }
      }

      LayoutStyle.FOCUS.id -> {
        when(watchFaceData.ambientStyle) {
          AmbientStyle.OUTLINE, AmbientStyle.BOLD_OUTLINE -> {
            if (drawProperties.timeScale == 0f) {
              18f / 18f
            } else {
              16f / 18f
            }
          }
          AmbientStyle.BIG_OUTLINE, AmbientStyle.BIG_BOLD_OUTLINE, AmbientStyle.BIG_FILLED -> {
            if (drawProperties.timeScale == 0f) {
              18f / 18f
            } else {
              interpolate(18f / 18f, 16f / 18f)
            }
          }
          AmbientStyle.FILLED-> 16f / 18f
          AmbientStyle.DETAILED-> 16f / 18f
        }
      }

      else -> 18f / 18f
    }

  val complicationsScale: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.FOCUS.id -> {
        when(watchFaceData.ambientStyle) {
          AmbientStyle.BIG_OUTLINE, AmbientStyle.BIG_BOLD_OUTLINE, AmbientStyle.BIG_FILLED -> {
            interpolate(18f / 16f, 16f / 16f)
//            interpolate(17f / 16f, 16f / 16f)
          }

          else -> 16f / 16f
        }
      }

      else -> {
        when(watchFaceData.ambientStyle) {
          AmbientStyle.BIG_OUTLINE, AmbientStyle.BIG_BOLD_OUTLINE, AmbientStyle.BIG_FILLED -> {
            interpolate(18f / 14f, 14f / 14f)
//            interpolate(16f / 14f, 14f / 14f)
          }

          AmbientStyle.DETAILED -> 14f / 14f

          else -> {
            interpolate(16f / 14f, 14f / 14f)
//            interpolate(15f / 14f, 14f / 14f)
          }
        }
      }
    }

  val hourOffsetY: Float
    get() = when (watchFaceData.ambientStyle) {
      AmbientStyle.OUTLINE, AmbientStyle.BOLD_OUTLINE -> {
        if (drawProperties.timeScale == 0f) {
          -64f
        } else {
          -72f
        }
      }

      else -> -72f
    }

  val minuteOffsetY: Float
    get() = when (watchFaceData.ambientStyle) {
      AmbientStyle.OUTLINE, AmbientStyle.BOLD_OUTLINE -> {
        if (drawProperties.timeScale == 0f) {
          64f
        } else {
          72f
        }
      }

      else -> 72f
    }

  val shouldDrawSeconds: Boolean
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.INFO1.id, LayoutStyle.INFO3.id, LayoutStyle.SPORT.id -> true
      else -> false
    }

  val secondsOffsetX: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.SPORT.id -> 94f
      else -> 129f
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

  val timeOffsetX: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.SPORT.id ->  {
        when(watchFaceData.ambientStyle) {
          AmbientStyle.DETAILED -> -45f
          else -> interpolate(0f, -45f)
        }
      }
      else -> 0f
    }

  val complicationsOffsetX: Float
    get() = when (watchFaceData.layoutStyle.id) {
      LayoutStyle.SPORT.id ->  {
        when(watchFaceData.ambientStyle) {
          AmbientStyle.DETAILED -> 0f
          else -> interpolate(35f, 0f)
        }
      }
      else -> 0f
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

  private var frame: Int = 0

  private var last: Instant = Instant.now()

  override fun render(
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime,
    sharedAssets: AnalogSharedAssets,
  ) {
    // all calculations are done with 384x384 resolution in mind
    val renderScale = min(bounds.width(), bounds.height()).toFloat() / 384.0f

    canvas.drawColor(Color.parseColor("#ff000000"))

    if (!this::state.isInitialized) {
      // placeholder
      return
    }

    if (!bitmapsInitialized || bitmapsScale != renderScale) {
      preloadBitmaps(bounds, renderScale)
      bitmapsInitialized = true
    }

    val took = measureNanoTime {
      frame++

      if (debug) {
        val p2 = Paint()
        p2.color = Color.parseColor("#aaff1111")
        p2.typeface = context.resources.getFont(R.font.m8stealth57)
        p2.textSize = 8f

        val text = "f $frame r $interactiveDrawModeUpdateDelayMillis s ${"%.2f".format(drawProperties.timeScale)}"

        val textBounds = Rect()
        p2.getTextBounds(text, 0, text.length, textBounds)

        canvas.drawText(
          text,
          192f - textBounds.width() / 2,
          192f + textBounds.height() / 2,
          p2,
        )
      }

      canvas.withScale(
        timeScale,
        timeScale,
        bounds.exactCenterX(),
        bounds.exactCenterY()
      ) {

        canvas.withTranslation(timeOffsetX * renderScale, 0f) {
          if (renderParameters.watchFaceLayers.contains(WatchFaceLayer.BASE)) {

            val opacity = interpolate(0.75f, 1f)

            var textStyle = when {
              watchFaceData.ambientStyle.id == AmbientStyle.OUTLINE.id && drawProperties.timeScale == 0f -> "outline"
              watchFaceData.ambientStyle.id == AmbientStyle.BIG_OUTLINE.id && drawProperties.timeScale == 0f -> "big_outline"
              watchFaceData.ambientStyle.id == AmbientStyle.BOLD_OUTLINE.id && drawProperties.timeScale == 0f -> "bold_outline"
              watchFaceData.ambientStyle.id == AmbientStyle.BIG_BOLD_OUTLINE.id && drawProperties.timeScale == 0f -> "big_bold_outline"
              else -> "normal"
            }

            if (true) {
              val hourText = getHour(zonedDateTime).toString().padStart(2, '0')

              val cacheKey = "hour_${textStyle}_$hourText"

              val hourBmp = memoryCache.get(cacheKey)

              val hourOffsetX = 0f
              val hourOffsetY = hourOffsetY * renderScale

              canvas.drawBitmap(
                hourBmp,
                bounds.exactCenterX() - hourBmp.width / 2f + hourOffsetX,
                bounds.exactCenterY() - hourBmp.height / 2f + hourOffsetY,
                Paint().apply { alpha = (opacity * 255).toInt() },
              )
            }

            if (true) {
              val minuteText = zonedDateTime.minute.toString().padStart(2, '0')
              val cacheKey = "minute_${textStyle}_$minuteText"

              val minuteBmp = memoryCache.get(cacheKey)

              val minuteOffsetX = 0f
              val minuteOffsetY = minuteOffsetY * renderScale

              canvas.drawBitmap(
                minuteBmp,
                bounds.exactCenterX() - minuteBmp.width / 2f + minuteOffsetX,
                bounds.exactCenterY() - minuteBmp.height / 2f + minuteOffsetY,
                Paint().apply { alpha = (opacity * 255).toInt() },
              )
            }
          }
        }
      }

      canvas.withScale(complicationsScale, complicationsScale, bounds.exactCenterX(), bounds.exactCenterY()) {
        canvas.withTranslation(complicationsOffsetX, 0f) {

          val compBmp = memoryCache.get("comp")

          val compCanvas = Canvas()
          compBmp.eraseColor(Color.TRANSPARENT)
          compCanvas.setBitmap(compBmp)

          if (renderParameters.watchFaceLayers.contains(WatchFaceLayer.BASE)) {
            if (shouldDrawSeconds) {
                val secondText = if (false && isAmbient) "M8" else zonedDateTime.second.toString().padStart(2, '0')
                val cacheKey = "second_normal_$secondText"

                val secondBmp = memoryCache.get(cacheKey)

                val offsetX = secondsOffsetX * renderScale
                val offsetY = secondsOffsetY * renderScale

                compCanvas.drawBitmap(
                  secondBmp,
                  bounds.exactCenterX() - secondBmp.width / 2 + offsetX,
                  bounds.exactCenterY() - secondBmp.height / 2 + offsetY,
                  Paint(),
                )

            }

            if (shouldDrawAmPm) {
              val ampmText = getAmPm(zonedDateTime).uppercase()
              val cacheKey = "ampm_$ampmText"

              val ampmBmp = memoryCache.get(cacheKey)

              val offsetX = 83f * renderScale
              val offsetY = 24f * renderScale

              compCanvas.drawBitmap(
                ampmBmp,
                bounds.exactCenterX() - ampmBmp.width / 2 + offsetX,
                bounds.exactCenterY() - ampmBmp.height / 2 + offsetY,
                Paint(),
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
            (watchFaceData.ambientStyle == AmbientStyle.DETAILED || drawProperties.timeScale != 0f)
          ) {
            drawComplications(compCanvas, zonedDateTime)
          }

          val opacity = if (watchFaceData.ambientStyle == AmbientStyle.DETAILED) interpolate(0.75f, 1f) else interpolate(0f, 1f)

          canvas.drawBitmap(
            compBmp,
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            Paint().apply { alpha = (opacity * 255).toInt() },
          )

        }
      }

    }

    if (debugTiming) {
      Log.d("WatchCanvasRenderer", "render took ${took.toFloat() / 1000000.0}ms, cache ${memoryCache.size()} / ${memoryCache.maxSize()}")
    }

    if (animating && !ambientAnimator.isRunning) {
      animating = false
      interactiveDrawModeUpdateDelayMillis = interactiveFrameDelay
    }

//    Log.d("@@@", "Render time diff ${last.until(Instant.now(), ChronoUnit.MICROS)/1000.0f}ms")
    last = Instant.now()

  }

  override fun shouldAnimate(): Boolean {
    return super.shouldAnimate() || animating
  }

  // ----- All drawing functions -----
  private fun drawComplications(canvas: Canvas, zonedDateTime: ZonedDateTime) {
    for ((_, complication) in complicationSlotsManager.complicationSlots) {
      if (complication.enabled) {
        complication.render(canvas, zonedDateTime, renderParameters)
      }
    }
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
    var timeScale: Float = 1f
  ) {
    companion object {
      val TIME_SCALE = object : FloatProperty<DrawProperties>("timeScale") {
        override fun setValue(obj: DrawProperties, value: Float) {
          if (value.isNaN()) {
            throw IllegalArgumentException("timeScale cannot be NaN")
          }
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

