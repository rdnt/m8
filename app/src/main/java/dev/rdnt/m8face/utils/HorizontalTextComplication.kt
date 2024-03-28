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
import android.util.LruCache
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toRectF
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import androidx.wear.watchface.CanvasComplication
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.RenderParameters
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import dev.rdnt.m8face.BitmapCacheEntry
import dev.rdnt.m8face.R
import java.time.Instant
import java.time.ZonedDateTime

private const val debug = false

class HorizontalTextComplication(private val context: Context) : CanvasComplication {
  private val memoryCache = LruCache<String, Bitmap>(1)

  var tertiaryColor: Int = Color.parseColor("#8888bb")
    set(tertiaryColor) {
      field = tertiaryColor
      textPaint.color = tertiaryColor
    }

  private val textPaint = Paint().apply {
    isAntiAlias = true
    typeface = context.resources.getFont(R.font.m8stealth57)
    textAlign = Paint.Align.LEFT
    color = tertiaryColor
    textSize = 112f / 14f / 7f * 14f
  }

  override fun render(
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime,
    renderParameters: RenderParameters,
    slotId: Int
  ) {
    if (bounds.isEmpty) return

    if (memoryCache.get("") == null) {
      Log.d("@@@", "create bitmap")
      val bitmap = Bitmap.createBitmap(
        bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888
      )
      memoryCache.put("", bitmap)
    }

    return

    val bitmap = when (data.type) {
      ComplicationType.SHORT_TEXT -> {
        drawShortTextComplication(bounds, data as ShortTextComplicationData)
      }

      ComplicationType.NO_DATA -> {
        val placeholder = (data as NoDataComplicationData).placeholder
        if (placeholder != null && placeholder.type == ComplicationType.SHORT_TEXT) {
          drawShortTextComplication(bounds, placeholder as ShortTextComplicationData)
        } else {
          return
        }
      }

      else -> return
    }

//      renderDebug(canvas, bounds.toRectF())

      canvas.drawBitmap(
        bitmap,
        bounds.left.toFloat(),
        bounds.top.toFloat(),
        Paint(),
      )
  }

  private fun drawShortTextComplication(
    bounds: Rect,
    data: ShortTextComplicationData
  ): Bitmap {
    val cached = memoryCache.get("")
    return cached
    cached.eraseColor(Color.TRANSPARENT)
    val bitmap = cached

//    val bitmap = Bitmap.createBitmap(
//      bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888
//    )
    val bitmapCanvas = Canvas(bitmap)

    val rect = Rect(0, 0, bitmap.width, bitmap.height)

    renderShortTextComplication(bitmapCanvas, rect, data)

    memoryCache.put("", bitmap)

    return bitmap
  }

  private fun renderShortTextComplication(
    canvas: Canvas,
    bounds: Rect,
    data: ShortTextComplicationData,
  ) {
    val now = Instant.now()

    val text = data.text.getTextAt(context.resources, now).toString().uppercase()

    val textBounds = Rect()
    textPaint.getTextBounds(text, 0, text.length, textBounds)

    canvas.drawText(
      text,
      bounds.left.toFloat() + 14f,
      bounds.exactCenterY() + textBounds.height() / 2,
      textPaint,
    )
  }

  private fun renderDebug(canvas: Canvas, bounds: RectF) {
    if (debug) {
      canvas.drawRect(bounds, Paint().apply {
        this.color = ColorUtils.blendARGB(Color.TRANSPARENT, Color.parseColor("#aa02d7f2"), 1f)
        style = Paint.Style.STROKE
        strokeWidth = 2f
      })
      val p2 = Paint()
      p2.color = ColorUtils.blendARGB(Color.TRANSPARENT, Color.parseColor("#aa02d7f2"), 1f)
      p2.typeface = context.resources.getFont(R.font.m8stealth57)
      p2.textSize = 8f
//      canvas.drawText(
//        "r ${bitmapCache.loads} w ${bitmapCache.renders}",
//        bounds.left + 3f,
//        bounds.bottom - 3f,
//        p2,
//      )
    }
  }

  override fun drawHighlight(
    canvas: Canvas,
    bounds: Rect,
    boundsType: Int,
    zonedDateTime: ZonedDateTime,
    color: Int
  ) {
    // Rendering of highlights
  }

  private var data: ComplicationData = NoDataComplicationData()

  override fun getData(): ComplicationData = data

  override fun loadData(
    complicationData: ComplicationData,
    loadDrawablesAsynchronous: Boolean
  ) {
    data = complicationData
//    memoryCache.remove("")
  }
}

fun createHorizontalTextComplicationFactory(context: Context) = CanvasComplicationFactory { _, _ ->
  HorizontalTextComplication(context)
}
