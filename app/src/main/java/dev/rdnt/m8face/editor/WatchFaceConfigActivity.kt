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

@file:OptIn(
  ExperimentalSnapperApi::class,
  ExperimentalHorologistComposeLayoutApi::class,
  ExperimentalHorologistApi::class,
  ExperimentalFoundationApi::class,
)

package dev.rdnt.m8face.editor

import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants.KEYBOARD_TAP
import android.view.HapticFeedbackConstants.LONG_PRESS
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Color.Companion.Blue
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily.Companion.Default
import androidx.compose.ui.text.font.FontWeight.Companion.Medium
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.*
import androidx.wear.compose.material.ButtonDefaults.outlinedButtonBorder
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.navscaffold.ExperimentalHorologistComposeLayoutApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithSnap
import com.google.android.horologist.compose.rotaryinput.toRotaryScrollAdapter
import dev.chrisbanes.snapper.ExperimentalSnapperApi
import dev.rdnt.m8face.R
import dev.rdnt.m8face.data.watchface.AmbientStyle
import dev.rdnt.m8face.data.watchface.ColorStyle
import dev.rdnt.m8face.data.watchface.SecondsStyle
import dev.rdnt.m8face.theme.WearAppTheme
import dev.rdnt.m8face.utils.BOTTOM_COMPLICATION_ID
import dev.rdnt.m8face.utils.HORIZONTAL_COMPLICATION_HEIGHT
import dev.rdnt.m8face.utils.HORIZONTAL_COMPLICATION_LEFT_BOUND
import dev.rdnt.m8face.utils.HORIZONTAL_COMPLICATION_OFFSET
import dev.rdnt.m8face.utils.HORIZONTAL_COMPLICATION_RIGHT_BOUND
import dev.rdnt.m8face.utils.LEFT_COMPLICATION_ID
import dev.rdnt.m8face.utils.RIGHT_COMPLICATION_ID
import dev.rdnt.m8face.utils.TOP_COMPLICATION_ID
import dev.rdnt.m8face.utils.VERTICAL_COMPLICATION_OFFSET
import dev.rdnt.m8face.utils.VERTICAL_COMPLICATION_TOP_BOUND
import dev.rdnt.m8face.utils.VERTICAL_COMPLICATION_WIDTH
import kotlinx.coroutines.launch

@Preview()
@Composable
fun ScrollableColumnTest() {
  val item = remember { mutableStateOf(2) }
  val focusRequester = remember { FocusRequester() }

  ScrollableColumn(
    focusRequester,
    items = 16,
    threshold = 100f,
    initialItem = item.value,
    onValueChanged = {},
  )

  Column(Modifier.padding(40.dp, 20.dp, 0.dp, 20.dp)) {
    DebugText(text = "sel ${item.value}")
  }
}

@Composable
fun ScrollableColumn(
  focusRequester: FocusRequester,
  items: Int,
  threshold: Float,
  initialItem: Int,
  onValueChanged: (Int) -> Unit,
) {
  Log.d("Editor", "ScrollableColumn($items, $threshold, $initialItem)")

  val selectedItem = remember { mutableStateOf(initialItem) }

  val maxValue = remember { (items * threshold) }

  val position = remember { mutableStateOf(initialItem * threshold + threshold / 2f) }
  val size = remember { mutableStateOf((1.0f / items).coerceAtLeast(0.1f)) }

  val state = remember {
    CustomPositionIndicatorState(
      derivedStateOf {
        (selectedItem.value.toFloat() / (items.toFloat() - 1f))
      },
      size,
    )
  }

  Scaffold(
    Modifier
      .onPreRotaryScrollEvent { false }
      .fillMaxSize(),
    positionIndicator = {
      PositionIndicator(
        state = state,
        indicatorHeight = 78.dp,
        indicatorWidth = 6.dp,
        paddingHorizontal = 5.dp,
        color = remember { Color(0xFFe6ff7b) },
        reverseDirection = false,
        position = PositionIndicatorAlignment.End,
      )
    }
  ) {
    val view = LocalView.current

    Box(
      modifier = Modifier
        .fillMaxSize()
        .scrollable(
          orientation = Orientation.Vertical,
          state = rememberScrollableState { delta ->
            val value = position.value - delta


            if (value < 0f) {
              if (position.value != 0f) {
                view.performHapticFeedback(LONG_PRESS)
              }

              position.value = 0f
            } else if (value > maxValue) {
              if (position.value != maxValue - 1) {
                view.performHapticFeedback(LONG_PRESS)
              }

              position.value = maxValue - 1
            } else {
              position.value = value
              val selected = (value / threshold)
                .toInt()
                .coerceAtMost(items - 1)
              if (selected != selectedItem.value) {
                selectedItem.value = selected
                view.performHapticFeedback(KEYBOARD_TAP)
              }
            }

            delta
          }
        )
        .onRotaryScrollEvent {
          val value = position.value + it.verticalScrollPixels

          if (value < 0f) {
            if (position.value != 0f) {
              view.performHapticFeedback(LONG_PRESS)
            }

            position.value = 0f
          } else if (value > maxValue) {
            if (position.value != maxValue - 1) {
              view.performHapticFeedback(LONG_PRESS)
            }

            position.value = maxValue - 1
          } else {
            position.value = value
            val selected = (value / threshold)
              .toInt()
              .coerceAtMost(items - 1)
            if (selected != selectedItem.value) {
              selectedItem.value = selected
              view.performHapticFeedback(KEYBOARD_TAP)
            }
          }

          true
        }
        .focusRequester(focusRequester)
        .focusable(),
    )
  }

  LaunchedEffect(selectedItem.value) {
    onValueChanged(selectedItem.value)
  }
}

class WatchFaceConfigActivity : ComponentActivity() {
  private val stateHolder: WatchFaceConfigStateHolder
    by lazy {
      WatchFaceConfigStateHolder(
        lifecycleScope,
        this@WatchFaceConfigActivity
      )
    }

  private val colorStyles by lazy {
    enumValues<ColorStyle>()
  }

  private val ambientStyles by lazy {
    enumValues<AmbientStyle>()
  }

  private val secondsStyles by lazy {
    enumValues<SecondsStyle>()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    lifecycleScope.launch {
      stateHolder
      colorStyles
      ambientStyles
      secondsStyles
    }

    setContent {
      WatchfaceConfigApp(
        stateHolder,
        colorStyles,
        ambientStyles,
        secondsStyles,
        resources.configuration.isScreenRound,
      )
    }
  }
}

@Composable
fun WatchfaceConfigApp(
  stateHolder: WatchFaceConfigStateHolder,
  colorStyles: Array<ColorStyle>,
  ambientStyles: Array<AmbientStyle>,
  secondsStyles: Array<SecondsStyle>,
  screenIsRound: Boolean,
) {
  Log.d("Editor", "WatchfaceConfigApp()")

  WearAppTheme {

    CompositionLocalProvider(
      LocalRippleTheme provides CustomRippleTheme,
    ) {

      val uiState: WatchFaceConfigStateHolder.EditWatchFaceUiState by stateHolder.uiState.collectAsStateWithLifecycle()

      when (val state = uiState) {
        is WatchFaceConfigStateHolder.EditWatchFaceUiState.Success -> {
          val bitmap = state.userStylesAndPreview.previewImage.asImageBitmap()
          val colorIndex =
            colorStyles.indexOfFirst { it.id == state.userStylesAndPreview.colorStyleId }
          val ambientStyleIndex =
            ambientStyles.indexOfFirst { it.id == state.userStylesAndPreview.ambientStyleId }
          val secondsStyleIndex =
            secondsStyles.indexOfFirst { it.id == state.userStylesAndPreview.secondsStyleId }
          val militaryTimeEnabled = state.userStylesAndPreview.militaryTime
          val bigAmbientEnabled = state.userStylesAndPreview.bigAmbient

          Box(
            Modifier
              .fillMaxSize()
              .zIndex(1f)
              .background(Black)
          ) {
            ConfigScaffold(
              stateHolder,
              colorStyles,
              ambientStyles,
              secondsStyles,
              bitmap,
              colorIndex,
              ambientStyleIndex,
              secondsStyleIndex,
              militaryTimeEnabled,
              bigAmbientEnabled,
            )
          }
        }

        else -> {
          Box(
            Modifier
              .fillMaxSize()
              .zIndex(2f)
              .background(Black)
          ) {
            SplashScreen(screenIsRound)
          }
        }
      }

    }
  }
}

@Composable
fun ConfigScaffold(
  stateHolder: WatchFaceConfigStateHolder,
  colorStyles: Array<ColorStyle>,
  ambientStyles: Array<AmbientStyle>,
  secondsStyles: Array<SecondsStyle>,
  bitmap: ImageBitmap,
  colorIndex: Int,
  ambientStyleIndex: Int,
  secondsStyleIndex: Int,
  militaryTimeEnabled: Boolean,
  bigAmbientEnabled: Boolean,
) {
  Log.d(
    "Editor",
    "ConfigScaffold($colorIndex, $ambientStyleIndex, $militaryTimeEnabled, $bigAmbientEnabled)"
  )

  val pagerState = rememberPagerState { 5 }

  Scaffold(
    positionIndicator = {
      val pageIndicatorState: PageIndicatorState = remember {
        object : PageIndicatorState {
          override val pageOffset: Float
            get() = pagerState.currentPageOffsetFraction
          override val selectedPage: Int
            get() = pagerState.currentPage
          override val pageCount: Int
            get() = pagerState.pageCount
        }
      }

      HorizontalPageIndicator(
        pageIndicatorState = pageIndicatorState,
        Modifier
          .padding(4.dp)
          .zIndex(4f)
      )
    },
    modifier = Modifier
      .onPreRotaryScrollEvent { false }
      .fillMaxSize()
      .zIndex(1f)
  ) {

    val focusRequester0 = remember { FocusRequester() }
    val focusRequester1 = remember { FocusRequester() }
    val focusRequester2 = remember { FocusRequester() }


    HorizontalPager(
      flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
      state = pagerState,
      modifier = Modifier
        .onPreRotaryScrollEvent { pagerState.currentPage != 0 && pagerState.currentPage != 1 && pagerState.currentPage != 2 }
        .zIndex(3f), // don't ask
      key = {
        it
      }
    ) { page ->
      when (page) {
        0 -> ColorStyleSelect(focusRequester0, stateHolder, colorStyles, colorIndex)
        1 -> SecondsStyleSelect(focusRequester1, stateHolder, secondsStyles, secondsStyleIndex)
        2 -> AmbientStyleSelect(focusRequester2, stateHolder, ambientStyles, ambientStyleIndex)
        3 -> ComplicationPicker(stateHolder)
        4 -> Options(stateHolder, militaryTimeEnabled, bigAmbientEnabled)
      }
    }


    if (pagerState.currentPage == 2) { // special background for third page (ambient style)
      var id = 0
      val current =
        ambientStyles.indexOfFirst { it.id == (stateHolder.uiState.value as WatchFaceConfigStateHolder.EditWatchFaceUiState.Success).userStylesAndPreview.ambientStyleId }


      if (current == 0) {
        if (!militaryTimeEnabled && !bigAmbientEnabled) {
          id = R.drawable.preview_ambient_outline
        } else if (militaryTimeEnabled && !bigAmbientEnabled) {
          id = R.drawable.preview_ambient_outline_military
        } else if (!militaryTimeEnabled && bigAmbientEnabled) {
          id = R.drawable.preview_ambient_outline_big
        } else if (militaryTimeEnabled && bigAmbientEnabled) {
          id = R.drawable.preview_ambient_outline_military_big
        }
      } else if (current == 1) {
        if (!militaryTimeEnabled && !bigAmbientEnabled) {
          id = R.drawable.preview_ambient_bold
        } else if (militaryTimeEnabled && !bigAmbientEnabled) {
          id = R.drawable.preview_ambient_bold_military
        } else if (!militaryTimeEnabled && bigAmbientEnabled) {
          id = R.drawable.preview_ambient_bold_big
        } else if (militaryTimeEnabled && bigAmbientEnabled) {
          id = R.drawable.preview_ambient_bold_military_big
        }
      } else if (current == 2) {
        if (!militaryTimeEnabled && !bigAmbientEnabled) {
          id = R.drawable.preview_ambient_filled
        } else if (militaryTimeEnabled && !bigAmbientEnabled) {
          id = R.drawable.preview_ambient_filled_military
        } else if (!militaryTimeEnabled && bigAmbientEnabled) {
          id = R.drawable.preview_ambient_filled_big
        } else if (militaryTimeEnabled && bigAmbientEnabled) {
          id = R.drawable.preview_ambient_filled_military_big
        }
      }

      Image(
        painterResource(id = id),
        contentDescription = "Preview",
        colorFilter = ColorFilter.tint(
          colorResource(id = colorStyles[colorIndex].primaryColorId),
          BlendMode.Darken
        ),
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxSize()
          .zIndex(1f)
          .clip(TopHalfRectShape)
          .scale(1f)
      )
      Image(
        painterResource(id = id),
        contentDescription = "Preview",
        colorFilter = ColorFilter.tint(
          colorResource(id = colorStyles[colorIndex].secondaryColorId),
          BlendMode.Darken
        ),
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxSize()
          .zIndex(1f)
          .clip(BottomHalfRectShape)
          .scale(1f)
      )
    } else {
      Preview(bitmap)
    }

    Overlay(pagerState)

    LaunchedEffect(pagerState.currentPage) {
      Log.d("Editor", "LaunchedEffect(${pagerState.currentPage})")

      if (pagerState.currentPage == 0) {
        focusRequester0.requestFocus()
      } else if (pagerState.currentPage == 1) {
        focusRequester1.requestFocus()
      } else if (pagerState.currentPage == 2) {
        focusRequester2.requestFocus()
      }
    }
  }
}

@Composable
fun Overlay(
  pagerState: PagerState,
) {
  val offset = pagerState.currentPage + pagerState.currentPageOffsetFraction

  if (offset < 3.0f) {
    var opacity = 1.0f
    if (offset > 2.0f) {
      opacity = 3.0f - offset
    }
    Box(
      modifier = Modifier
        .fillMaxSize()
        .alpha(opacity * 0.75f)
        .background(
          brush = Brush.verticalGradient(
            startY = 192f,
            colors = listOf(
              Color(0x00000000),
              Color(0xFF000000),
            )
          )
        )
        .zIndex(2f),
    )
  }

  if (offset > 2.0f && offset <= 4.0f) {
    val opacity = if (offset > 3.0f) {
      1f
    } else {
      offset - 2f
    }

    val opacity2 = if (offset <= 3.0f) {
      0f
    } else {
      (offset - 3.0f)
    }

    var extend = 0f
    if (opacity <= 0.5f) {
      extend = 4f
    }

    val ringColor = ColorUtils.blendARGB(
      android.graphics.Color.TRANSPARENT,
      android.graphics.Color.BLACK,
      opacity * 0.75f
    )

    Box(
      Modifier
        .zIndex(2f)
        .fillMaxSize()
        .border(10.dp, Color(ringColor), CircleShape)
    )

    Column(
      Modifier
        .fillMaxSize()
        .padding(10.dp)
        .clip(CircleShape)
        .zIndex(2f)
    ) {
      Box(
        Modifier
          .weight(HORIZONTAL_COMPLICATION_OFFSET + HORIZONTAL_COMPLICATION_HEIGHT)
          .fillMaxWidth()
          .alpha(opacity2 * 0.75f)
          .background(Black)
      )

      Row(
        Modifier
          .weight(1f - HORIZONTAL_COMPLICATION_OFFSET * 2 - HORIZONTAL_COMPLICATION_HEIGHT * 2 + extend)
          .fillMaxWidth()
      ) {
        Box(
          Modifier
            .weight(VERTICAL_COMPLICATION_OFFSET + VERTICAL_COMPLICATION_WIDTH)
            .fillMaxHeight()
            .alpha(opacity2 * 0.75f)
            .background(Black)
        )

        Box(
          Modifier
            .weight(1f - VERTICAL_COMPLICATION_OFFSET * 2 - VERTICAL_COMPLICATION_WIDTH * 2 + extend)
            .fillMaxHeight()
            .alpha(opacity * 0.75f)
            .background(Black)
        )

        Box(
          Modifier
            .weight(VERTICAL_COMPLICATION_OFFSET + VERTICAL_COMPLICATION_WIDTH)
            .fillMaxHeight()
            .alpha(opacity2 * 0.75f)
            .background(Black)
        )
      }

      Box(
        Modifier
          .weight(HORIZONTAL_COMPLICATION_OFFSET + HORIZONTAL_COMPLICATION_HEIGHT)
          .fillMaxWidth()
          .alpha(opacity2 * 0.75f)
          .background(Black)
      )
    }
  }
}

@Composable
fun Options(
  stateHolder: WatchFaceConfigStateHolder,
  militaryTime: Boolean,
  bigAmbient: Boolean,
) {
  Box(
    Modifier
      .fillMaxSize()
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      ToggleChip(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp, 0.dp)
          .height((40.dp)),
        checked = militaryTime,
        colors = ToggleChipDefaults.toggleChipColors(
          checkedStartBackgroundColor = Transparent,
          checkedEndBackgroundColor = Transparent,
          uncheckedStartBackgroundColor = Transparent,
          uncheckedEndBackgroundColor = Transparent,
        ),
        onCheckedChange = {
          stateHolder.setMilitaryTime(it)
        },
        toggleControl = {
          Switch(
            modifier = Modifier.padding(0.dp),
            checked = militaryTime,
          )
        },
        label = {
          Text(
            text = "Military Time",
            fontSize = 14.sp,
            fontWeight = Medium,
            fontFamily = Default,
            color = White
          )
        },
      )

      ToggleChip(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp, 0.dp)
          .height((40.dp)),
        checked = bigAmbient,
        colors = ToggleChipDefaults.toggleChipColors(
          checkedStartBackgroundColor = Transparent,
          checkedEndBackgroundColor = Transparent,
          uncheckedStartBackgroundColor = Transparent,
          uncheckedEndBackgroundColor = Transparent,
        ),
        onCheckedChange = {
          stateHolder.setBigAmbient(it)
        },
        toggleControl = {
          Switch(
            modifier = Modifier.padding(0.dp),
            checked = bigAmbient,
          )
        },
        label = {
          Text(
            text = "Big Ambient",
            fontSize = 14.sp,
            fontWeight = Medium,
            fontFamily = Default,
            color = White
          )
        },
      )
    }
  }
}

private object CustomRippleTheme : RippleTheme {
  @Composable
  override fun defaultColor(): Color = Color(0xFFfaf9ff)

  @Composable
  override fun rippleAlpha(): RippleAlpha = RippleTheme.defaultRippleAlpha(
    Color(0xFFFFFFFF),
    lightTheme = !isSystemInDarkTheme()
  )
}

@Composable
fun SplashScreen(
  screenIsRound: Boolean,
) {
  Log.d("Editor", "SplashScreen()")

  if (screenIsRound) {
    Image(
      painterResource(id = R.drawable.watch_preview_subtle),
      contentDescription = "Preview",
      modifier = Modifier.fillMaxSize()
    )
  } else {
    Image(
      painterResource(id = R.drawable.watch_preview_subtle),
      contentDescription = "Preview",
      modifier = Modifier.fillMaxSize()
    )
  }

  ColorName("Loading...")
}

@Composable
fun Preview(
  bitmap: ImageBitmap,
) {
  Log.d("Editor", "Preview()")

  Image(
    bitmap = bitmap,
    contentDescription = "Preview",
    modifier = Modifier
      .fillMaxSize()
      .zIndex(1f)
  )
}

@Composable
fun ColorStyleSelect(
  focusRequester: FocusRequester,
  stateHolder: WatchFaceConfigStateHolder,
  colorStyles: Array<ColorStyle>,
  colorIndex: Int,
) {
  Log.d("Editor", "PrimarySelect($colorIndex)")

  val colorIdsSize = remember { colorStyles.size }

  Box(
    Modifier
      .fillMaxSize()
  ) {
    ScrollableColumn(
      focusRequester,
      colorIdsSize,
      100f,
      colorIndex,
    ) { itemIndex ->
      val current =
        colorStyles.indexOfFirst { it.id == (stateHolder.uiState.value as WatchFaceConfigStateHolder.EditWatchFaceUiState.Success).userStylesAndPreview.colorStyleId }
      if (current != itemIndex) {
        Log.d("Editor", "setColorStyle(${colorStyles[itemIndex].id})")
        stateHolder.setColorStyle(colorStyles[itemIndex].id)
      }
    }

    ColorName(stringResource(colorStyles[colorIndex].nameResourceId))
  }


}

@Composable
fun AmbientStyleSelect(
  focusRequester: FocusRequester,
  stateHolder: WatchFaceConfigStateHolder,
  ambientStyles: Array<AmbientStyle>,
  styleIndex: Int,
) {
  Log.d("Editor", "AmbientStyleSelect($stateHolder, $ambientStyles, $styleIndex)")

  Box(
    Modifier
      .fillMaxSize()
  ) {
    ScrollableColumn(
      focusRequester,
      ambientStyles.size,
      100f,
      styleIndex,
    ) { itemIndex ->
      val current =
        ambientStyles.indexOfFirst { it.id == (stateHolder.uiState.value as WatchFaceConfigStateHolder.EditWatchFaceUiState.Success).userStylesAndPreview.ambientStyleId }
      if (current != itemIndex) {
        Log.d("Editor", "setColorStyle(${ambientStyles[itemIndex].id})")
        stateHolder.setAmbientStyle(ambientStyles[itemIndex].id)
      }
    }

    ColorName(stringResource(ambientStyles[styleIndex].nameResourceId))
  }
}


@Composable
fun SecondsStyleSelect(
  focusRequester: FocusRequester,
  stateHolder: WatchFaceConfigStateHolder,
  secondsStyles: Array<SecondsStyle>,
  styleIndex: Int,
) {
  Log.d("Editor", "SecondsStyleSelect($stateHolder, $secondsStyles, $styleIndex)")

  Box(
    Modifier
      .fillMaxSize()
  ) {
    ScrollableColumn(
      focusRequester,
      secondsStyles.size,
      100f,
      styleIndex,
    ) { itemIndex ->
      val current =
        secondsStyles.indexOfFirst { it.id == (stateHolder.uiState.value as WatchFaceConfigStateHolder.EditWatchFaceUiState.Success).userStylesAndPreview.secondsStyleId }
      if (current != itemIndex) {
        Log.d("Editor", "setSecondsStyle(${secondsStyles[itemIndex].id})")
        stateHolder.setSecondsStyle(secondsStyles[itemIndex].id)
      }
    }

    ColorName(stringResource(secondsStyles[styleIndex].nameResourceId))
  }
}

@Composable
fun CustomPositionIndicator(
  position: State<Float>,
  size: State<Float>,
  ready: Boolean,
) {
  Log.d("Editor", "CustomPositionIndicator()")

  if (ready) {
    PositionIndicator(
      state = CustomPositionIndicatorState(position, size),
      indicatorHeight = 50.dp,
      indicatorWidth = 4.dp,
      paddingHorizontal = 5.dp,
      color = Color(0xFFe6ff7b),
      reverseDirection = false,
      position = PositionIndicatorAlignment.End
    )
  }
}

class CustomPositionIndicatorState(
  private val position: State<Float>,
  private val size: State<Float>
) : PositionIndicatorState {
  override val positionFraction get() = position.value
  override fun sizeFraction(scrollableContainerSizePx: Float) = size.value
  override fun visibility(scrollableContainerSizePx: Float) = PositionIndicatorVisibility.Show

  override fun equals(other: Any?) =
    other is CustomPositionIndicatorState &&
      position == other.position &&
      size == other.size

  override fun hashCode(): Int = position.hashCode() + 31 * size.hashCode()
}

@Composable
fun ColorPicker(
  state: ScalingLazyListState,
  focusRequester: FocusRequester,
  count: Int,
) {
  Log.d("Editor", "ColorPicker($state $focusRequester $count)")

  val adapter = remember { state.toRotaryScrollAdapter() }

  ScalingLazyColumn(
    flingBehavior = ScalingLazyColumnDefaults.snapFlingBehavior(state = state),
    userScrollEnabled = true,
    contentPadding = PaddingValues(0.dp),
    anchorType = ScalingLazyListAnchorType.ItemCenter,
    verticalArrangement = Arrangement.spacedBy(
      space = 0.dp,
      alignment = Alignment.CenterVertically
    ),
    modifier = Modifier
      .fillMaxSize()
      .focusable()
      .focusRequester(focusRequester)
      .rotaryWithSnap(adapter, focusRequester),
    state = state,
    autoCentering = AutoCenteringParams(itemIndex = 0, itemOffset = 0),
    scalingParams = ScalingLazyColumnDefaults.scalingParams(
      edgeScale = 1.0F,
      edgeAlpha = 1.0F,
      minTransitionArea = 0.0F,
      maxTransitionArea = 0.0F,
      scaleInterpolator = LinearEasing
    ),
  ) {
    items(
      count = count,
      key = {
        it
      }
    ) {
//      DebugColorItem(colorIds[it].primaryColorId)
      ColorItem()
    }
  }
}

@Composable
fun ComplicationPicker(
  stateHolder: WatchFaceConfigStateHolder,
) {
  Log.d("Editor", "ComplicationPicker()")

  Box (
    Modifier
      .fillMaxSize()
  ) {
    Column(
      Modifier
        .fillMaxSize()
    ) {
      Row(
        modifier = Modifier
          .weight(HORIZONTAL_COMPLICATION_OFFSET, true)
      ) {}
      Row(
        modifier = Modifier
          .weight(HORIZONTAL_COMPLICATION_HEIGHT, true)
      ) {
        Box(
          Modifier
            .weight(HORIZONTAL_COMPLICATION_LEFT_BOUND, true)
            .fillMaxHeight()
        )
        OutlinedButton(
          onClick = { stateHolder.setComplication(TOP_COMPLICATION_ID) },
          border = outlinedButtonBorder(
            Color(0xFF5c6063),
            borderWidth = 2.dp
          ),
          modifier = Modifier
            .weight(HORIZONTAL_COMPLICATION_RIGHT_BOUND - HORIZONTAL_COMPLICATION_LEFT_BOUND, true)
            .fillMaxHeight()
        ) {}
        Box(
          Modifier
            .weight(HORIZONTAL_COMPLICATION_LEFT_BOUND, true)
            .fillMaxHeight()
        )
      }
      Box(
        modifier = Modifier
          .weight(1f - HORIZONTAL_COMPLICATION_OFFSET * 2 - HORIZONTAL_COMPLICATION_HEIGHT * 2, true)
      )
      Row(
        modifier = Modifier
          .weight(HORIZONTAL_COMPLICATION_HEIGHT, true)
      ) {
        Box(
          Modifier
            .weight(HORIZONTAL_COMPLICATION_LEFT_BOUND, true)
            .fillMaxHeight()
        )
        OutlinedButton(
          onClick = { stateHolder.setComplication(BOTTOM_COMPLICATION_ID) },
          border = outlinedButtonBorder(
            Color(0xFF5c6063),
            borderWidth = 2.dp
          ),
          modifier = Modifier
            .weight(HORIZONTAL_COMPLICATION_RIGHT_BOUND - HORIZONTAL_COMPLICATION_LEFT_BOUND, true)
            .fillMaxHeight()
        ) {}
        Box(
          Modifier
            .weight(HORIZONTAL_COMPLICATION_LEFT_BOUND, true)
            .fillMaxHeight()
        )
      }
      Row(
        modifier = Modifier
          .weight(HORIZONTAL_COMPLICATION_OFFSET, true)
      ) {}
    }

    Column(
      Modifier
        .fillMaxSize()
    ) {
      Box(
        modifier = Modifier
          .weight(VERTICAL_COMPLICATION_TOP_BOUND, true)
      )

      Row(
        modifier = Modifier
          .weight(1f - VERTICAL_COMPLICATION_TOP_BOUND * 2, true)
      ) {
        Box(
          Modifier
            .weight(VERTICAL_COMPLICATION_OFFSET, true)
            .fillMaxHeight()
        )
        OutlinedButton(
          onClick = { stateHolder.setComplication(LEFT_COMPLICATION_ID) },
          border = outlinedButtonBorder(
            Color(0xFF5c6063),
            borderWidth = 2.dp
          ),
          modifier = Modifier
            .weight(VERTICAL_COMPLICATION_WIDTH, true)
            .fillMaxHeight(),
        ) {}
        Box(
          Modifier
            .weight(1f - VERTICAL_COMPLICATION_WIDTH * 2 - VERTICAL_COMPLICATION_OFFSET * 2, true)
            .fillMaxHeight()
        )
        OutlinedButton(
          onClick = { stateHolder.setComplication(RIGHT_COMPLICATION_ID) },
          border = outlinedButtonBorder(
            Color(0xFF5c6063),
            borderWidth = 2.dp
          ),
          modifier = Modifier
            .weight(VERTICAL_COMPLICATION_WIDTH, true)
            .fillMaxHeight(),
        ) {}
        Box(
          Modifier
            .weight(VERTICAL_COMPLICATION_OFFSET, true)
            .fillMaxHeight()
        )
      }

      Box(
        modifier = Modifier
          .weight(VERTICAL_COMPLICATION_TOP_BOUND, true)
      )
    }
  }
}

@Composable
fun ColorName(name: String) {
  Log.d("Editor", "ColorName($name)")

  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(Modifier.weight(29f))

    Text(
      text = name,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .weight(4f)
        .height(20.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(Color(0xFF202124))
        .padding(12.dp, 1.dp),
      fontSize = 12.sp,
      fontWeight = Medium,
      fontFamily = Default,
      color = White
    )

    Box(Modifier.weight(6f))
  }
}

@Composable
fun Label(label: String) {
  Log.d("Editor", "Label($label)")

  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
//    Box(Modifier
//      .padding(0.dp, 140.dp, 0.dp, 0.dp)
//      .height(28.dp)
//      .width(64.dp)
//      .clip(RoundedCornerShape(15.dp))
//      .background(Color(0xFF0000FF))
//      .padding(12.dp, 1.dp),
//    )

    Box(Modifier.weight(3.3f))

    Text(
      text = label,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .weight(4f)
        .clip(RoundedCornerShape(10.dp))
        .background(Color(0xFF000000))
        .padding(12.dp, 1.dp),
      fontSize = 12.sp,
      fontWeight = Medium,
      fontFamily = Default,
      color = Color(0xFFfaf9ff)
    )

    Box(Modifier.weight(32f))
  }
}


@Composable
fun ColorItem() {
//  Log.d("Editor", "ColorItem()")

  Box(
    modifier = Modifier
      .padding(0.dp, 0.dp)
      .height(48.dp)
      .width(0.dp)
      .alpha(0.0F)
  )
}

@Composable
fun DebugColorItem(colorId: Int?) {
  Log.d("Editor", "DebugColorItem()")

  Box(
    modifier = Modifier
      .padding(0.dp, 0.dp)
      .height(48.dp)
      .fillMaxWidth()
      .alpha(0.0F)
//        .alpha(0.2F)
      .background(colorId?.let { colorResource(colorId) } ?: Black)
  )
}

@Composable
fun DebugText(text: String) {
  Box(
    Modifier
  ) {
    Text(text)
  }
}
