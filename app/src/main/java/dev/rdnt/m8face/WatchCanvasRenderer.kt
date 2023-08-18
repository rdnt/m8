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
import android.util.Log
import android.view.SurfaceHolder
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import androidx.annotation.Keep
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
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
import dev.rdnt.m8face.data.watchface.SecondsStyle
import dev.rdnt.m8face.data.watchface.WatchFaceColorPalette.Companion.convertToWatchFaceColorPalette
import dev.rdnt.m8face.data.watchface.WatchFaceData
import dev.rdnt.m8face.utils.AMBIENT_STYLE_SETTING
import dev.rdnt.m8face.utils.BIG_AMBIENT_SETTING
import dev.rdnt.m8face.utils.COLOR_STYLE_SETTING
import dev.rdnt.m8face.utils.HorizontalComplication
import dev.rdnt.m8face.utils.MILITARY_TIME_SETTING
import dev.rdnt.m8face.utils.SECONDS_STYLE_SETTING
import dev.rdnt.m8face.utils.VerticalComplication
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest


// Default for how long each frame is displayed at expected frame rate.
//private const val updateDelay: Long = 60000 / 16
//private const val DEFAULT_INTERACTIVE_DRAW_MODE_UPDATE_DELAY_MILLIS: Long = 60000
private const val DEFAULT_INTERACTIVE_DRAW_MODE_UPDATE_DELAY_MILLIS: Long = 16
//private const val updateDelay: Long = 16

//private const val FONT = dev.rdnt.m8face.R.font.m8stealth57
//
//private val m8font57 = ResourcesCompat.getFont(context, FONT)

/**
 * Renders watch face via data in Room database. Also, updates watch face state based on setting
 * changes by user via [userStyleRepository.addUserStyleListener()].
 */
//@RequiresApi(Build.VERSION_CODES.Q)
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
    CoroutineScope( Dispatchers.Main.immediate)

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

  // Initializes paint object for painting the clock hands with default values.
//    private val clockHandPaint = Paint().apply {
//        isAntiAlias = true
//        strokeWidth =
//            context.resources.getDimensionPixelSize(R.dimen.clock_hand_stroke_width).toFloat()
//    }

  private val outerElementPaint = Paint().apply {
    isAntiAlias = true
//        color = Color.parseColor("#8b8bb8")
    color = watchFaceColors.tertiaryColor
  }

  private val secondHandPaint = Paint().apply {
    isAntiAlias = true
//    color = watchFaceColors.tertiaryColor
    color = Color.parseColor("#45455C")
  }

  private val datePaint = Paint().apply {
    isAntiAlias = true
    typeface = context.resources.getFont(R.font.m8stealth57)
//    letterSpacing = -0.00F // set spacing to 14px exactly
    textSize = 24F
    textAlign = Paint.Align.CENTER
//        color = Color.parseColor("#8b8bb8")
    color = watchFaceColors.tertiaryColor
  }

  private val hourPaint = Paint().apply {
    isAntiAlias = true // make sure text is not anti-aliased even with this on
    typeface = context.resources.getFont(R.font.m8stealth57)
//    letterSpacing = -0.02F // set spacing to 14px exactly
//        textSize = context.resources.getDimensionPixelSize(R.dimen.hour_mark_size).toFloat()
    textSize = 112f/14f*14f // TODO: 98f/112f
    color = watchFaceColors.primaryColor
  }

  private val ambientHourPaint = Paint().apply {
    isAntiAlias = true
    val big = watchFaceData.bigAmbient

    if (watchFaceData.ambientStyle.id == AmbientStyle.OUTLINE.id) {
      typeface = context.resources.getFont(R.font.m8stealth57thin)
      textSize = 8F
//      letterSpacing = -0.02F
      if (big) {
        typeface = context.resources.getFont(R.font.m8stealth57thinbig)
        textSize = 8F
      }

    } else if (watchFaceData.ambientStyle.id == AmbientStyle.BOLD_OUTLINE.id) {
      typeface = context.resources.getFont(R.font.m8stealth57thick)
      textSize = 8F // 90px high
//      letterSpacing = -0.02F
      if (big) {
        typeface = context.resources.getFont(R.font.m8stealth57thickbig)
        textSize = 8F
      }
    } else if (watchFaceData.ambientStyle.id == AmbientStyle.FILLED.id) {
      typeface = context.resources.getFont(R.font.m8stealth57)
      textSize = 112F/14f*16f
//      letterSpacing = -0.02f
      if (big) {
        textSize = 112F/14f*18f
      }
    }

//    letterSpacing = -0.02F // set spacing to 14px exactly
//        textSize = context.resources.getDimensionPixelSize(R.dimen.hour_mark_size).toFloat()
//    textSize = 24F
//        color = Color.parseColor("#d6cafd")
    color = watchFaceColors.primaryColor


//        bpp.color = watchFaceColors.tertiaryColor
  }

  private val minutePaint = Paint().apply {
    isAntiAlias = true // make sure text is not anti-aliased even with this on
    typeface = context.resources.getFont(R.font.m8stealth57)
//    letterSpacing = -0.02F // set spacing to 14px exactly
//        textSize = context.resources.getDimensionPixelSize(R.dimen.hour_mark_size).toFloat()
    textSize = 112f/14f*14f // TODO: 98f/112F
//        color = Color.parseColor("#32ecff")
//        color = watchFaceColors.secondaryColor
//        setColor(watchFaceColors.secondaryColor.pack())
    color = watchFaceColors.secondaryColor
  }

  private val ambientMinutePaint = Paint().apply {
    isAntiAlias = true
    val big = watchFaceData.bigAmbient

    if (watchFaceData.ambientStyle.id == AmbientStyle.OUTLINE.id) {
      typeface = context.resources.getFont(R.font.m8stealth57thin)
      textSize = 8F
//      letterSpacing = -0.02F
      if (big) {
        typeface = context.resources.getFont(R.font.m8stealth57thinbig)
        textSize = 8F
      }
    } else if (watchFaceData.ambientStyle.id == AmbientStyle.BOLD_OUTLINE.id) {
      typeface = context.resources.getFont(R.font.m8stealth57thick)
      textSize = 8F
//      letterSpacing = -0.02F
      if (big) {
        typeface = context.resources.getFont(R.font.m8stealth57thickbig)
        textSize = 8F
      }
    } else if (watchFaceData.ambientStyle.id == AmbientStyle.FILLED.id) {
      typeface = context.resources.getFont(R.font.m8stealth57)
      textSize = 112F/14f*16f
//      letterSpacing = -0.02f
      if (big) {
        textSize = 112F/14f*18f
      }
    }

//    typeface = context.resources.getFont(R.font.m8stealth57outline)
//    letterSpacing = -0.02F // set spacing to 14px exactly
//        textSize = context.resources.getDimensionPixelSize(R.dimen.hour_mark_size).toFloat()
//    textSize = 24F
//        color = Color.parseColor("#99f6ff")
//        setColor(watchFaceColors.secondaryColor.pack())
    color = watchFaceColors.secondaryColor
  }

  private val batteryPaint = Paint().apply {
    isAntiAlias = true
    typeface = context.resources.getFont(R.font.m8stealth57)
//        letterSpacing = -0.02F // set spacing to 14px exactly
    textSize = 24F
//        color = Color.parseColor("#8b8bb8")
    color = watchFaceColors.tertiaryColor
  }

  private val batteryPrefixPaint = Paint().apply {
    isAntiAlias = true
    typeface = context.resources.getFont(R.font.m8stealth57)
//        letterSpacing = -0.02F // set spacing to 14px exactly
    textSize = 24F
    color = Color.parseColor("#343434") // TODO: either store on selected theme or calculate
//      color = watchFaceColors.tertiaryColor
  }

  private val batteryIconPaint = Paint().apply {
    isAntiAlias = true
//        color = Color.parseColor("#8b8bb8")
    color = watchFaceColors.tertiaryColor
  }

  private lateinit var secondHand: Path

  // Changed when setting changes cause a change in the minute hand arm (triggered by user in
  // updateUserStyle() via userStyleRepository.addUserStyleListener()).
  private var armLengthChangedRecalculateClockHands: Boolean = false

  // Default size of watch face drawing area, that is, a no size rectangle. Will be replaced with
  // valid dimensions from the system.
  private var currentWatchFaceSize = Rect(0, 0, 0, 0)

  private var is24Format: Boolean = watchFaceData.militaryTime
//
//  private val timeSetReceiver = object : BroadcastReceiver() {
//    override fun onReceive(contxt: Context?, intent: Intent?) {
//      is24Format = DateFormat.is24HourFormat(context)
//    }
//  }

  private val AMBIENT_TRANSITION_MS = 1000L
  private var drawProperties = DrawProperties()

  private var prevDrawMode = DrawMode.INTERACTIVE

  private val ambientExitAnimator =
    AnimatorSet().apply {
//      doOnStart {
//        Log.d("@@@", "Exit start")
//      }
//      doOnEnd {
//        Log.d("@@@", "Exit end")
//      }
      val linearOutSlow =
        AnimationUtils.loadInterpolator(
          context,
          android.R.interpolator.linear_out_slow_in
        )
      play(
        ObjectAnimator.ofFloat(drawProperties, DrawProperties.TIME_SCALE,  drawProperties.timeScale, 1.0f).apply {
          duration = AMBIENT_TRANSITION_MS
          interpolator = linearOutSlow
          setAutoCancel(false)
        },
      )
    }

  private val ambientEnterAnimator =
    AnimatorSet().apply {
//      doOnStart {
//        Log.d("@@@", "Enter start")
//      }
//      doOnEnd {
//        Log.d("@@@", "Enter end")
//      }
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
          duration = AMBIENT_TRANSITION_MS*5/9
          interpolator = linearOutSlow
          setAutoCancel(false)
        },
//        ObjectAnimator.ofFloat(drawProperties, DrawProperties.TIME_SCALE, 0f, 0f).apply {
//          duration = AMBIENT_TRANSITION_MS
//          interpolator = linearOutSlow
//          startDelay = AMBIENT_TRANSITION_MS
//          setAutoCancel(false)
//        },
//        ObjectAnimator.ofFloat(drawProperties, DrawProperties.TIME_SCALE,  0f).apply {
//          duration = AMBIENT_TRANSITION_MS
//          interpolator = LinearInterpolator()
//          setAutoCancel(false)
//        },
//        ObjectAnimator.ofFloat(drawProperties, DrawProperties.TIME_SCALE,  0.0f, 0f).apply {
//          duration = 500L
//          interpolator = linearOutSlow
//          setAutoCancel(false)
//        },
//        ObjectAnimator.ofFloat(drawProperties, DrawProperties.TIME_SCALE,  0.0f, 0.0f).apply {
//          duration = 500L
//          interpolator = linearOutSlow
//          setAutoCancel(true)
//        },
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
//        Log.d("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@", isAmbient.toString())
        if (isAmbient!!) { // you call this readable? come on
//          ambientEnterAnimator.cancel()
          ambientExitAnimator.cancel()
//          ambientEnterAnimator.setupStartValues()
//          ambientEnterAnimator.duration = AMBIENT_TRANSITION_MS*(drawProperties.timeScale*1000).toInt()/1000
//          ambientEnterAnimator.start()
          drawProperties.timeScale = 0f

//          ambientEnterAnimator.doOnEnd {
//            drawProperties.timeScale = 0f
//            postInvalidate()
//          }
//            ambientEnterAnimator.play(
//              ObjectAnimator.ofFloat(drawProperties, DrawProperties.TIME_SCALE,  0f, 0f).apply {
//              setupStartValues()
//              removeAllListeners()
//              duration = AMBIENT_TRANSITION_MS
//              interpolator = LinearInterpolator()
//              setAutoCancel(false)
//            })
//          }
//          val linearOutSlow =
//            AnimationUtils.loadInterpolator(
//              context,
//              android.R.interpolator.linear_out_slow_in
//            )

//          ambientEnterAnimator.play(
//            ObjectAnimator.ofFloat(drawProperties, DrawProperties.TIME_SCALE,  drawProperties.timeScale, 1.0f)
//          )
        } else {
//          ambientEnterAnimator.cancel()
          ambientExitAnimator.setupStartValues()
//          ambientExitAnimator.duration = AMBIENT_TRANSITION_MS*((1f-drawProperties.timeScale)*1000).toInt()/1000
          ambientExitAnimator.start()
//          val linearOutSlow =
//            AnimationUtils.loadInterpolator(
//              context,
//              android.R.interpolator.linear_out_slow_in
//            )
//          ambientExitAnimator.play(
//            ObjectAnimator.ofFloat(drawProperties, DrawProperties.TIME_SCALE,  drawProperties.timeScale, 0f))


//          ambientExitAnimator.cancel()

//          ambientEnterAnimator.doOnEnd {
//
        //            ()
//          }
//          ambientEnterAnimator.cancel()

//          drawProperties.timeScale = 0f
        }
      }
    }

//    IntentFilter("android.intent.action.TIME_SET").let { filter ->
//      context.registerReceiver(timeSetReceiver, filter)
//    }
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
//  @RequiresApi(Build.VERSION_CODES.Q)
  private fun updateWatchFaceData(userStyle: UserStyle) {
//    Log.d(TAG, "updateWatchFace(): $userStyle")

    var newWatchFaceData: WatchFaceData = watchFaceData

    // Loops through user style and applies new values to watchFaceData.
    for (options in userStyle) {
      when (options.key.id.toString()) {
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

//                DRAW_HOUR_PIPS_STYLE_SETTING -> {
//                    val booleanValue = options.value as
//                        UserStyleSetting.BooleanUserStyleSetting.BooleanOption
//
//                    newWatchFaceData = newWatchFaceData.copy(
//                        drawHourPips = booleanValue.value
//                    )
//                }
//                WATCH_HAND_LENGTH_STYLE_SETTING -> {
//                    val doubleValue = options.value as
//                        UserStyleSetting.DoubleRangeUserStyleSetting.DoubleRangeOption
//
//                    // The arm lengths are usually only calculated the first time the watch face is
//                    // loaded to reduce the ops in the onDraw(). Because we updated the minute hand
//                    // watch length, we need to trigger a recalculation.
//                    armLengthChangedRecalculateClockHands = true
//
//                    // Updates length of minute hand based on edits from user.
//                    val newMinuteHandDimensions = newWatchFaceData.minuteHandDimensions.copy(
//                        lengthFraction = doubleValue.value.toFloat()
//                    )
//
//                    newWatchFaceData = newWatchFaceData.copy(
//                        minuteHandDimensions = newMinuteHandDimensions
//                    )
//                }
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
//      secondHandPaint.color = watchFaceColors.tertiaryColor
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
//        ambientHourPaint.letterSpacing = -0.02F

        if (watchFaceData.bigAmbient) {
          ambientHourPaint.typeface = context.resources.getFont(R.font.m8stealth57thinbig)
          ambientHourPaint.textSize = 8F
        }

        ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57thin)
        ambientMinutePaint.textSize = 8F
//        ambientMinutePaint.letterSpacing = -0.02F

        if (watchFaceData.bigAmbient) {
          ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57thinbig)
          ambientMinutePaint.textSize = 8F
        }
      } else if (watchFaceData.ambientStyle.id == AmbientStyle.BOLD_OUTLINE.id) {
        ambientHourPaint.typeface = context.resources.getFont(R.font.m8stealth57thick)
        ambientHourPaint.textSize = 8F
//        ambientHourPaint.letterSpacing = -0.02F
        if (watchFaceData.bigAmbient) {
          ambientHourPaint.typeface = context.resources.getFont(R.font.m8stealth57thickbig)
          ambientHourPaint.textSize = 8F
        }

        ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57thick)
        ambientMinutePaint.textSize = 8F
//        ambientMinutePaint.letterSpacing = -0.02F
        if (watchFaceData.bigAmbient) {
          ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57thickbig)
          ambientMinutePaint.textSize = 8F
        }
      } else if (watchFaceData.ambientStyle.id == AmbientStyle.FILLED.id) {
        ambientHourPaint.typeface = context.resources.getFont(R.font.m8stealth57)
        ambientHourPaint.textSize = 112F/14f*16f
//        ambientHourPaint.textSize = 8f
//        ambientHourPaint.letterSpacing = -0.02f
        if (watchFaceData.bigAmbient) {
          ambientHourPaint.textSize = 112F/14f*18f
//          ambientHourPaint.textSize = 8f
        }

        ambientMinutePaint.typeface = context.resources.getFont(R.font.m8stealth57)
        ambientMinutePaint.textSize = 112F/14f*16f
//        ambientHourPaint.textSize = 8f
//        ambientMinutePaint.letterSpacing = -0.02f
        if (watchFaceData.bigAmbient) {
          ambientMinutePaint.textSize = 112F/14f*18f
//          ambientHourPaint.textSize = 8f
        }

      }

      is24Format = watchFaceData.militaryTime

      // TODO: update colors for all elements here

      // Applies the user chosen complication color scheme changes. ComplicationDrawables for
      // each of the styles are defined in XML so we need to replace the complication's
      // drawables.
      for ((_, complication) in complicationSlotsManager.complicationSlots) {

        when (complication.renderer) {
          is VerticalComplication -> (complication.renderer as VerticalComplication).tertiaryColor = watchFaceColors.tertiaryColor
          is HorizontalComplication -> (complication.renderer as HorizontalComplication).tertiaryColor = watchFaceColors.tertiaryColor
          else -> {}
        }

//                val complicationDrawable = ComplicationDrawable(context)
//                complicationDrawable.activeStyle.backgroundColor = Color.parseColor("#ff0000")
//                complicationDrawable.activeStyle.textColor = Color.parseColor("#ff0000")

//                (complication.renderer as CanvasComplicationDrawable).drawable = complicationDrawable
//                complication.renderer = complicationDrawable
//                complicationDrawable.activeStyle = ComplicationStyle(iconColor = Color.parseColor("#ff0000"))
//                complicationDrawable.activeStyle.iconColor = Color.parseColor("#ff0000")
//                complicationDrawable.activeStyle.titleColor = Color.parseColor("#00ff00")


//                ComplicationDrawable().apply {
//                    activeStyle.iconColor =
////                    setBorderStyleActive(ComplicationDrawable.BORDER_STYLE_NONE)
////                    setBorderStyleAmbient(ComplicationDrawable.BORDER_STYLE_NONE)
////                    setTitleSizeActive(resources.getDimensionPixelSize(R.dimen.title_size))
////                    setTextSizeActive(resources.getDimensionPixelSize(R.dimen.text_size))
////                    setTitleSizeAmbient(resources.getDimensionPixelSize(R.dimen.title_size))
////                    setTextSizeAmbient(resources.getDimensionPixelSize(R.dimen.text_size))
//                }.let {
//                    (complication.renderer as CanvasComplicationDrawable).drawable = it
//                }

//                (complication.renderer as CanvasComplicationDrawable).drawable = complicationDrawable
//        ComplicationDrawable.getDrawable(
//          context,
//          watchFaceColors.complicationStyleDrawableId
//        )?.let {
////          it.activeStyle.titleColor = watchFaceColors.tertiaryColor
////          it.activeStyle.textColor = watchFaceColors.tertiaryColor
////          it.activeStyle.iconColor = watchFaceColors.tertiaryColor
////          (complication.renderer as CanvasComplicationDrawable).drawable = it
//        }
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

//  @RequiresApi(Build.VERSION_CODES.Q)
  override fun render(
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime,
    sharedAssets: AnalogSharedAssets,
  ) {
//  Log.d("===", drawProperties.timeScale.toString())
//    if (prevDrawMode != renderParameters.drawMode) {
//
//      animate = (
//        prevDrawMode == DrawMode.AMBIENT && renderParameters.drawMode == DrawMode.INTERACTIVE
//          ||
//          prevDrawMode == DrawMode.INTERACTIVE && renderParameters.drawMode == DrawMode.AMBIENT
//        )
////      animate = renderParameters.drawMode != DrawMode.AMBIENT && renderParameters.drawMode == DrawMode.INTERACTIVE
//      prevDrawMode = renderParameters.drawMode
//    }

//    Log.d("@@@", "animate")

    updateRefreshRate()
//    Log.d(TAG, "render()")

//        Log.d(TAG, "render()")
//        val backgroundColor = if (renderParameters.drawMode == DrawMode.AMBIENT) {
//            watchFaceColors.ambientBackgroundColor
//        } else {
//            watchFaceColors.activeBackgroundColor
//        }

    canvas.drawColor(Color.parseColor("#ff000000"))

//        color( 0.67 0.59 1)

//        val lab = ColorSpace.get(ColorSpace.Named.CIE_LAB)
//        val testcolor = Color.valueOf(68.24F, 24.35F, -47.83F, 1.0F, lab).convert(ColorSpace.get(ColorSpace.Named.DCI_P3))
//        canvas.drawColor(testcolor.first.pack())

//        val dciP3 = ColorSpace.get(ColorSpace.Named.DCI_P3)
//        val testcolor = Color.valueOf(0.67F, 0.59F, 1.0F, 1.0F, dciP3)
//        canvas.drawColor(testcolor.pack())

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

//      Log.d("@@@", "timescale ${drawProperties.timeScale} rendermode ${renderParameters.drawMode}")

      if (drawProperties.timeScale == 0f) {
//        Log.d("===", "AMBIENT " + drawProperties.timeScale.toString())

        var hourOffsetX: Float = 0f
        var hourOffsetY: Float = 0f
        var minuteOffsetX: Float = 0f
        var minuteOffsetY: Float = 0f

        var scaleOffset = 1f
//        if (this.watchFaceData.bigAmbient) {
//          scaleOffset = 18f/14f
//        } else {
//          scaleOffset = 16f/14f
//        }

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

//        Log.d("@@@", ambientMinutePaint.letterSpacing.toString())
        drawTime(canvas, bounds, hour, ambientHourPaint, hourOffsetX, hourOffsetY, 0f)
        drawTime(canvas, bounds, zonedDateTime.minute, ambientMinutePaint, minuteOffsetX, minuteOffsetY, 0f)
      } else {
//        Log.d("@@@", "interactive")
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

//        if (!(!ambientEnterAnimator.isRunning && !ambientExitAnimator.isRunning)) {
//          var hourOffsetX: Float = 0f
//          var hourOffsetY: Float = 0f
//          var minuteOffsetX: Float = 0f
//          var minuteOffsetY: Float = 0f
//
          var scaleOffset = 0f
          if (this.watchFaceData.bigAmbient) {
            scaleOffset = 18f/14f-1f
          } else {
            scaleOffset = 16f/14f-1f
          }
//
//          when (watchFaceData.ambientStyle.id) {
//            AmbientStyle.OUTLINE.id -> {
//              if (watchFaceData.bigAmbient) {
//                hourOffsetX = -99f
//                hourOffsetY = -9f
//                minuteOffsetX = -99f
//                minuteOffsetY = 135f
//              } else {
//                hourOffsetX = -88f
//                hourOffsetY = -8f
//                minuteOffsetX = -88f
//                minuteOffsetY = 120f
//              }
//            }
//            AmbientStyle.BOLD_OUTLINE.id -> {
//              if (watchFaceData.bigAmbient) {
//                hourOffsetX = -99f
//                hourOffsetY = -9f
//                minuteOffsetX = -99f
//                minuteOffsetY = 135f
//              } else {
//                hourOffsetX = -88f
//                hourOffsetY = -8f
//                minuteOffsetX = -88f
//                minuteOffsetY = 120f
//              }
//            }
//            AmbientStyle.FILLED.id -> {
//              if (watchFaceData.bigAmbient) {
//                hourOffsetX = -99f
//                hourOffsetY = -9f
//                minuteOffsetX = -99f
//                minuteOffsetY = 135f
//              } else {
//                hourOffsetX = -88f
//                hourOffsetY = -8f
//                minuteOffsetX = -88f
//                minuteOffsetY = 120f
//              }
//            }
//          }
//
////        Log.d("@@@", ambientMinutePaint.letterSpacing.toString())
//          drawTime(canvas, bounds, hour, ambientHourPaint, hourOffsetX, hourOffsetY, 0f)
//          drawTime(canvas, bounds, zonedDateTime.minute, ambientMinutePaint, minuteOffsetX, minuteOffsetY, 0f)
//        } else {
          drawTime(canvas, bounds, hour, hourPaint,-77f, -7f, scaleOffset) // Rect(0, 0, 152, 14))
          drawTime(canvas, bounds, zonedDateTime.minute, minutePaint, -77f, 105f, scaleOffset)//Rect(0, 0, 152, -210))
//        }
//        drawDate(canvas, bounds, zonedDateTime)
//        drawBattery(canvas, bounds, battery)
//        drawSecond(canvas, bounds, zonedDateTime)
      }

    }

    if (renderParameters.watchFaceLayers.contains(WatchFaceLayer.COMPLICATIONS) &&
//      renderParameters.drawMode != DrawMode.AMBIENT
      drawProperties.timeScale != 0f
    ) {
      drawComplications(canvas, zonedDateTime)
    }


    // @@@ draws numbers around
//        if (renderParameters.drawMode == DrawMode.INTERACTIVE &&
//            renderParameters.watchFaceLayers.contains(WatchFaceLayer.BASE) &&
//            watchFaceData.drawHourPips
//        ) {
//            drawNumberStyleOuterElement(
//                canvas,
//                bounds,
//                watchFaceData.numberRadiusFraction,
//                watchFaceData.numberStyleOuterCircleRadiusFraction,
//                watchFaceColors.activeOuterElementColor,
//                watchFaceData.numberStyleOuterCircleRadiusFraction,
//                watchFaceData.gapBetweenOuterCircleAndBorderFraction
//            )
//        }
  }

  override fun shouldAnimate(): Boolean {
//    Log.d("@@@", "SHOULD " + (ambientEnterAnimator.isRunning || super.shouldAnimate()).toString())
    // Make sure we keep animating while ambientEnterAnimator is running.
    return ambientEnterAnimator.isRunning || super.shouldAnimate()
//    return ambientEnterAnimator.isRunning || ambientExitAnimator.isRunning || super.shouldAnimate()
  }

  private fun animating(): Boolean {
//    return ambientEnterAnimator.isRunning|| ambientExitAnimator.isRunning
    return ambientEnterAnimator.isRunning || ambientExitAnimator.isRunning
//    return ambientEnterAnimator.isRunning || ambientExitAnimator.isRunning
//    return animate
//    return (renderParameters.drawMode != DrawMode.AMBIENT && renderParameters.drawMode == DrawMode.INTERACTIVE)
  }

  // ----- All drawing functions -----
  private fun drawComplications(canvas: Canvas, zonedDateTime: ZonedDateTime) {
//    Log.d("=====================", drawProperties.timeScale.toString())

    var opacity = 1f

    opacity = this.easeInOutCirc(drawProperties.timeScale)

    for ((_, complication) in complicationSlotsManager.complicationSlots) {
      if (complication.enabled) {
        when (complication.renderer) {
          is VerticalComplication -> (complication.renderer as VerticalComplication).opacity = opacity
          is HorizontalComplication -> (complication.renderer as HorizontalComplication).opacity = opacity
          else -> {}
        }

        complication.render(canvas, zonedDateTime, renderParameters)
      }
    }
  }

  private fun drawDate(
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime
  ) {
    val date = zonedDateTime.month.toString().take(3) + " " + zonedDateTime.dayOfMonth.toString()
    val dp = Paint(datePaint)
    dp.textSize = dp.textSize / 384F * bounds.width()
    dp.color = watchFaceColors.tertiaryColor

    canvas.drawText(
      date,
      bounds.exactCenterX() + (1F / 384F * bounds.width()), // ideally +1.5 but it will cause aliasing
      bounds.exactCenterY() - (138F / 384F * bounds.width()),
      dp
    )
  }

  private fun drawBattery(
    canvas: Canvas,
    bounds: Rect,
    battery: Int
  ) {
    val drawable = getDrawable(context, R.drawable.battery_icon)!!
    val icon = drawable.toBitmap(30, 15)

    val srcRect = Rect(0, 0, 30, 15)
    val dstRect = RectF(
      bounds.exactCenterX() + (-45F / 384F * bounds.width()),
      bounds.exactCenterY() + (141F / 384F * bounds.width()),
      bounds.exactCenterX() + ((-45F + 30F) / 384F * bounds.width()),
      bounds.exactCenterY() + ((141F + 15F) / 384F * bounds.width()),
    )


    batteryIconPaint.colorFilter =
      PorterDuffColorFilter(watchFaceColors.tertiaryColor, PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(icon, srcRect, dstRect, batteryIconPaint)

    val value = battery.toString()
    val prefixLen = 3 - value.length
    val prefix = "".padStart(prefixLen, '0')

    val bp = Paint(batteryPaint)
    bp.textSize = bp.textSize / 384F * bounds.width()
    bp.color = watchFaceColors.tertiaryColor

    canvas.drawText(
      value.padStart(3, ' '),
      bounds.exactCenterX() - (6F / 384F * bounds.width()),
      bounds.exactCenterY() + (159F / 384F * bounds.width()),
      bp
    )

    val bpp = Paint(batteryPrefixPaint)
    bpp.textSize = bpp.textSize / 384F * bounds.width()
//        bpp.color = watchFaceColors.tertiaryColor

    canvas.drawText(
      prefix,
      bounds.exactCenterX() - (6F / 384F * bounds.width()),
      bounds.exactCenterY() + (159F / 384F * bounds.width()),
      bpp
    )
  }

//  override fun shouldAnimate(): Boolean {
//    // Make sure we keep animating while ambientEnterAnimator is running.
//    return startAnimation && super.shouldAnimate()
//  }

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

//    scale *= this.easeInOutCirc((1f-drawProperties.timeScale) / 1)

//    Log.d("@@@", scale.toString())
//    if (!ambientExitAnimator.isRunning) {
      p.isAntiAlias = true
//      val scaleOffset: Float

//      scale += this.easeInOutCirc((this.transitionDuration-this.transitionTicker) / this.transitionDuration) * (scaleOffset - 1f)

//      scale += this.easeInOutCirc((1f-drawProperties.timeScale) / 1) * (scaleOffset - 1f)

//      scale += this.easeInOutCirc(drawProperties.timeScale * (1f - scaleOffset))

    scale += (1f-this.easeInOutCirc(drawProperties.timeScale)) * scaleOffset

//    }

    canvas.withScale(scale, scale, bounds.exactCenterX(), bounds.exactCenterY()) {
      canvas.drawText(
        time.toString().padStart(2, '0'),
        bounds.exactCenterX() + offsetX / 384F * bounds.width().toFloat(),
        bounds.exactCenterY() + offsetY / 384F * bounds.height().toFloat(),
        p
      )
    }
  }

  private fun drawSecond(
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime
  ) {
        val rect = RectF(2F, 2F, bounds.width().toFloat()-2F,bounds.height().toFloat()-2F)

    outerElementPaint.style = Paint.Style.FILL_AND_STROKE
    outerElementPaint.strokeWidth = 4F
//    outerElementPaint.color = watchFaceColors.tertiaryColor
    outerElementPaint.strokeCap = Paint.Cap.SQUARE
//        canvas.drawArc(
//            rect,
//            0F,
//            360F,
//            true,
//            outerElementPaint,
//        )

    // Retrieve current time to calculate location/rotation of watch arms.
    val nanoOfDate = zonedDateTime.toLocalTime().toNanoOfDay()

//        // X and Y coordinates of the center of the circle.
//        val centerX = 0.5f * bounds.width().toFloat()
//        val centerY = bounds.width() * (gapBetweenOuterCircleAndBorderFraction + radiusFraction)
//
    val secondsPerSecondHandRotation = Duration.ofMinutes(1).seconds
    val secondsRotation =
      (nanoOfDate.toFloat() / 1000000000F).rem(secondsPerSecondHandRotation) * 360.0f /
        secondsPerSecondHandRotation
//        datePaint.color = watchFaceColors.activeSecondaryColor
//
//


//        outerElementPaint.color = Color.parseColor("#333333")
//        canvas.drawRect(
//            rect,
//            outerElementPaint,
//        )

//    outerElementPaint.color = Color.parseColor("#8b8bb8")

    canvas.withRotation(secondsRotation + 90 - 2, bounds.exactCenterX(), bounds.exactCenterY()) {
//            canvas.drawArc(
//                rect,
//                0F,
//                4F,
//                false,
//                outerElementPaint,
//            )

      canvas.drawRect(RectF(4F, 4F, 8F, 8F), outerElementPaint)
            canvas.drawCircle(
                16F,
                centerY,
                2F,
                outerElementPaint
            )
    }

//        }
  }

  private fun drawDashes(
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime
  ) {
//    outerElementPaint.style = Paint.Style.FILL_AND_STROKE
//    outerElementPaint.strokeWidth = 0f
//    outerElementPaint.strokeCap = Paint.Cap.SQUARE

    // Retrieve current time to calculate location/rotation of watch arms.
    val nanoOfDate = zonedDateTime.toLocalTime().toNanoOfDay()

//        // X and Y coordinates of the center of the circle.
//        val centerX = 0.5f * bounds.width().toFloat()
//        val centerY = bounds.width() * (gapBetweenOuterCircleAndBorderFraction + radiusFraction)
//
    val secondsPerSecondHandRotation = Duration.ofMinutes(1).seconds
    val secondsRotation =
      (nanoOfDate.toFloat() / 1000000000F).rem(secondsPerSecondHandRotation) * 360.0f /
        secondsPerSecondHandRotation

//    secondsRotation = (secondsRotation.mod(24f).div(2f) + 351f).mod(360f)

    var secondHandSize = 12f
    val secondHandPaint = Paint(this.secondHandPaint)

    var transitionOffset = 0f

//    if (animating()) {
//      secondHandSize *= this.easeInOutCirc(this.transitionTicker / this.transitionDuration)
//      secondHandPaint.alpha = (this.easeInOutCirc(this.transitionTicker / this.transitionDuration)*secondHandPaint.alpha).toInt()
//      transitionOffset = this.easeInOutCirc(1- this.transitionTicker / this.transitionDuration) * 16f

      secondHandSize *= this.easeInOutCirc(this.drawProperties.timeScale)
      secondHandPaint.alpha = (this.easeInOutCirc(this.drawProperties.timeScale)*secondHandPaint.alpha).toInt()
      transitionOffset = this.easeInOutCirc(1- this.drawProperties.timeScale) * 16f

//      if (isEntering) {
//        transitionOffset *= -1
//      }
//    }

    canvas.withRotation(secondsRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
      canvas.drawRect(
        RectF(
          (bounds.width()/2f-1.25f/384F * bounds.width()),
          transitionOffset/ 384F * bounds.width(),
          (bounds.width()/2f+1.25f/384F * bounds.width()),
          (secondHandSize+transitionOffset*2)/ 384F * bounds.width(),
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

          size = minSize + (maxSize-minSize) * ratio
          alpha = minAlpha + (maxAlpha-minAlpha) * ratio
        } else {
          val ratio = (354f - secondsRotation) / 354f

          if (secondsRotation < 6f) {
            val ratio2 = 1f - secondsRotation / 6f
            color = ColorUtils.blendARGB(color, Color.WHITE, ratio2)
          }

          size = minSize + (maxSize-minSize) * ratio
          alpha = minAlpha + (maxAlpha-minAlpha) * ratio
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
          }
          else if (diff >= -6f && diff < 0) {
            color = ColorUtils.blendARGB(color, Color.WHITE, (6f+diff)/6f)
            alpha = maxAlpha
            size = maxSize
          }
          else if (diff >= 0 && diff < 6f) { // fade in next second indicator
            alpha = minAlpha + (maxAlpha-minAlpha) * (6f - diff) / 6f
            size = maxSize - 2f + (6f - diff) / 3f
          } else {
            size = minSize
            alpha = minAlpha
          }
        }
      }

//      if (animating()) {
//        size *= this.easeInOutCirc(this.transitionTicker / this.transitionDuration)
//        alpha *= this.easeInOutCirc(this.transitionTicker / this.transitionDuration)

        size *= this.easeInOutCirc(this.drawProperties.timeScale)
        alpha *= this.easeInOutCirc(this.drawProperties.timeScale)
//      }

      outerElementPaint.color = color
      outerElementPaint.alpha = alpha.toInt()

//      val dist = calculateLengthToPaint(i, bounds.height().toFloat()) - bounds.width()/2 // top: -dist, bottom: -dist/2

      canvas.withRotation(i, bounds.exactCenterX(), bounds.exactCenterY()) {
        canvas.drawRect(
          RectF(
            bounds.exactCenterX()-weight/384F * bounds.width(),
            transitionOffset/384F * bounds.width(),
            bounds.exactCenterX()+weight/384F * bounds.width(),
            (size+transitionOffset*2)/384F * bounds.width(),
          ),
          outerElementPaint
        )
      }

      i+= 6f
    }
  }

  fun calculateLengthToPaint(angle: Float, heightOfSquare: Float): Float {
    var flippy = (angle % 90).toDouble()
    if (flippy > 45.0) {
      flippy -= 90.0
      flippy = Math.abs(flippy)
    }
    return heightOfSquare / 2.0f / Math.cos(Math.toRadians(flippy)).toFloat()
  }

  private fun drawDots(
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime
  ) {
//    outerElementPaint.style = Paint.Style.FILL_AND_STROKE
//    outerElementPaint.strokeWidth = 0f
//    outerElementPaint.strokeCap = Paint.Cap.SQUARE

    // Retrieve current time to calculate location/rotation of watch arms.
    val nanoOfDate = zonedDateTime.toLocalTime().toNanoOfDay()

//        // X and Y coordinates of the center of the circle.
//        val centerX = 0.5f * bounds.width().toFloat()
//        val centerY = bounds.width() * (gapBetweenOuterCircleAndBorderFraction + radiusFraction)
//
    val secondsPerSecondHandRotation = Duration.ofMinutes(1).seconds
    val secondsRotation =
      (nanoOfDate.toFloat() / 1000000000F).rem(secondsPerSecondHandRotation) * 360.0f /
        secondsPerSecondHandRotation

//    secondsRotation = (secondsRotation.mod(24f).div(2f) + 351f).mod(360f)

    val div = secondsRotation.div(6f).toInt()
    val mod = secondsRotation.mod(6f)

    val rotation = div*6f + this.easeInOutCirc(mod / 6f) * 6f

//    outerElementPaint.color = Color.parseColor("#45455C")

    var transitionOffset = 0f
    val secondHandPaint = Paint(this.secondHandPaint)

    var centerY = 8f
    var secondHandSize = 3.5f
//    if (animating()) {
//      centerY *= this.easeInOutCirc(this.transitionTicker / this.transitionDuration)
//      secondHandSize *= this.easeInOutCirc(this.transitionTicker / this.transitionDuration)
//      transitionOffset = this.easeInOutCirc(1- this.transitionTicker / this.transitionDuration) * 24f
//      secondHandPaint.alpha = (this.easeInOutCirc(this.transitionTicker / this.transitionDuration)*secondHandPaint.alpha).toInt()

      centerY *= this.easeInOutCirc(this.drawProperties.timeScale)
      secondHandSize *= this.easeInOutCirc(this.drawProperties.timeScale)
      transitionOffset = this.easeInOutCirc(1- this.drawProperties.timeScale) * 24f
      secondHandPaint.alpha = (this.easeInOutCirc(this.drawProperties.timeScale)*secondHandPaint.alpha).toInt()

//      if (isEntering) {
//        transitionOffset *= -1
//      }
//    }


    canvas.withRotation(rotation, bounds.exactCenterX(), bounds.exactCenterY()) {
//      canvas.drawRect(RectF(bounds.width()/2f-1f, 0F, bounds.width()/2f+1f, 14F), outerElementPaint)
      canvas.drawCircle(
        bounds.centerX().toFloat(),
        (centerY+transitionOffset),
        secondHandSize/ 384F * bounds.width(),
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

          size = minSize + (maxSize-minSize) * ratio
          alpha = minAlpha + (maxAlpha-minAlpha) * ratio
        } else {
          val ratio = (354f - secondsRotation) / 354f

          if (secondsRotation < 6f) {
            val ratio2 = 1f - secondsRotation / 6f
            color = ColorUtils.blendARGB(color, Color.WHITE, ratio2)
          }

          size = minSize + (maxSize-minSize) * ratio
          alpha = minAlpha + (maxAlpha-minAlpha) * ratio
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
          }
          else if (diff >= -6f && diff < 0) {
            color = ColorUtils.blendARGB(color, Color.WHITE, (6f+diff)/6f)
            alpha = maxAlpha
            size = maxSize
          }
          else if (diff >= 0 && diff < 6f) { // fade in next second indicator
            alpha = minAlpha + (maxAlpha-minAlpha) * (6f - diff) / 6f
            size = minSize + (maxSize-minSize) * (6f - diff) / 6f
          } else {
            size = minSize
            alpha = minAlpha
          }
        }
      }

      centerY = 8f
//      if (animating()) {
//        size = 0.5f * this.easeInOutCirc(this.transitionTicker / this.transitionDuration) * size + 0.5f * size
//        centerY *= this.easeInOutCirc(this.transitionTicker / this.transitionDuration)
//        alpha *= this.easeInOutCirc(this.transitionTicker / this.transitionDuration)

        size = 0.5f * this.easeInOutCirc(this.drawProperties.timeScale) * size + 0.5f * size
        centerY *= this.easeInOutCirc(this.drawProperties.timeScale)
        alpha *= this.easeInOutCirc(this.drawProperties.timeScale)
//      }

      outerElementPaint.color = color
      outerElementPaint.alpha = alpha.toInt()

      canvas.withRotation(i, bounds.exactCenterX(), bounds.exactCenterY()) {
        canvas.drawCircle(
          bounds.centerX().toFloat(),
          centerY+transitionOffset,
          size/ 384F * bounds.width(),
          outerElementPaint
        )
      }

      i+= 6f
    }
  }

  /*
   * Rarely called (only when watch face surface changes; usually only once) from the
   * drawClockHands() method.
   */
//    private fun recalculateClockHands(bounds: Rect) {
//        Log.d(TAG, "recalculateClockHands()")
//        secondHand =
//            createClockHand(
//                bounds,
//                watchFaceData.secondHandDimensions.lengthFraction,
//                watchFaceData.secondHandDimensions.widthFraction,
//                watchFaceData.gapBetweenHandAndCenterFraction,
//                watchFaceData.secondHandDimensions.xRadiusRoundedCorners,
//                watchFaceData.secondHandDimensions.yRadiusRoundedCorners
//            )
//    }

  /**
   * Returns a round rect clock hand if {@code rx} and {@code ry} equals to 0, otherwise return a
   * rect clock hand.
   *
   * @param bounds The bounds use to determine the coordinate of the clock hand.
   * @param length Clock hand's length, in fraction of {@code bounds.width()}.
   * @param thickness Clock hand's thickness, in fraction of {@code bounds.width()}.
   * @param gapBetweenHandAndCenter Gap between inner side of arm and center.
   * @param roundedCornerXRadius The x-radius of the rounded corners on the round-rectangle.
   * @param roundedCornerYRadius The y-radius of the rounded corners on the round-rectangle.
   */
  private fun createClockHand(
    bounds: Rect,
    length: Float,
    thickness: Float,
    gapBetweenHandAndCenter: Float,
    roundedCornerXRadius: Float,
    roundedCornerYRadius: Float
  ): Path {
    val width = bounds.width()
    val centerX = bounds.exactCenterX()
    val centerY = bounds.exactCenterY()
    val left = centerX - thickness / 2 * width
    val top = centerY - (gapBetweenHandAndCenter + length) * width
    val right = centerX + thickness / 2 * width
    val bottom = centerY - gapBetweenHandAndCenter * width
    val path = Path()

    if (roundedCornerXRadius != 0.0f || roundedCornerYRadius != 0.0f) {
      path.addRoundRect(
        left,
        top,
        right,
        bottom,
        roundedCornerXRadius,
        roundedCornerYRadius,
        Path.Direction.CW
      )
    } else {
      path.addRect(
        left,
        top,
        right,
        bottom,
        Path.Direction.CW
      )
    }
    return path
  }

  /** Draws the outer circle on the top middle of the given bounds. */
  private fun drawTopMiddleCircle(
    canvas: Canvas,
    bounds: Rect,
    radiusFraction: Float,
    gapBetweenOuterCircleAndBorderFraction: Float
  ) {
    outerElementPaint.style = Paint.Style.FILL_AND_STROKE

    // X and Y coordinates of the center of the circle.
    val centerX = 0.5f * bounds.width().toFloat()
    val centerY = bounds.width() * (gapBetweenOuterCircleAndBorderFraction + radiusFraction)

    canvas.drawCircle(
      centerX,
      centerY,
      radiusFraction * bounds.width(),
      outerElementPaint
    )
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
//            Log.d("@@@@@@", value.toString()  + " " + (value == 0f).toString())
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

    // Painted between pips on watch face for hour marks.
    private val HOUR_MARKS = arrayOf("3", "6", "9", "12")

    // Used to canvas.scale() to scale watch hands in proper bounds. This will always be 1.0.
    private const val WATCH_HAND_SCALE = 1.0f
  }
}
