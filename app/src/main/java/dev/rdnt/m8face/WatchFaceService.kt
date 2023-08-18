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

import android.os.Build
import android.view.SurfaceHolder
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.view.DisplayCompat.getSupportedModes
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
import dev.rdnt.m8face.utils.createComplicationSlotManager
import dev.rdnt.m8face.utils.createUserStyleSchema

/**
 * Handles much of the boilerplate needed to implement a watch face (minus rendering code; see
 * [WatchCanvasRenderer]) including the complications and settings (styles user can change on
 * the watch face).
 */
@Keep
class WatchFaceService : WatchFaceService() {

  // Used by Watch Face APIs to construct user setting options and repository.
  override fun createUserStyleSchema(): UserStyleSchema =
    createUserStyleSchema(context = applicationContext)

  // Creates all complication user settings and adds them to the existing user settings
  // repository.
  override fun createComplicationSlotsManager(
    currentUserStyleRepository: CurrentUserStyleRepository
  ): ComplicationSlotsManager = createComplicationSlotManager(
    context = applicationContext,
    currentUserStyleRepository = currentUserStyleRepository
  )

  @RequiresApi(Build.VERSION_CODES.Q)
  override suspend fun createWatchFace(
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    complicationSlotsManager: ComplicationSlotsManager,
    currentUserStyleRepository: CurrentUserStyleRepository
  ): WatchFace {
//        Log.d(TAG, "createWatchFace()")

    // Creates class that renders the watch face.
    val renderer = WatchCanvasRenderer(
      context = applicationContext,
      surfaceHolder = surfaceHolder,
      watchState = watchState,
      complicationSlotsManager = complicationSlotsManager,
      currentUserStyleRepository = currentUserStyleRepository,
      canvasType = CanvasType.HARDWARE,
    )

    // Creates the watch face.
    return WatchFace(
      watchFaceType = WatchFaceType.DIGITAL,
      renderer = renderer
    )
  }

  companion object {
    const val TAG = "WatchFaceService"
  }
}
