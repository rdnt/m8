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
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
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

class HorizontalTextComplication(private val context: Context) : CanvasComplication {
  private val bitmapCache: BitmapCacheEntry = BitmapCacheEntry()

  var debug: Boolean = false;

  var tertiaryColor: Int = Color.parseColor("#8888bb")
    set(tertiaryColor) {
      field = tertiaryColor
      textPaint.color = tertiaryColor
    }

  var opacity: Float = 1f
    set(opacity) {
      field = opacity

      val color = ColorUtils.blendARGB(Color.TRANSPARENT, tertiaryColor, opacity)

      textPaint.color = color
    }

  var offsetX: Float = 0f

  var scale: Float = 0f

  private val textPaint = Paint().apply {
    isAntiAlias = true
    typeface = context.resources.getFont(R.font.m8stealth57)
    textAlign = Paint.Align.LEFT
    color = tertiaryColor
    textSize = 112f / 14f / 7f
  }

  override fun render(
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime,
    renderParameters: RenderParameters,
    slotId: Int
  ) {
    if (bounds.isEmpty) return


    val bitmap: Bitmap

    when (data.type) {
      ComplicationType.SHORT_TEXT -> {
        bitmap = drawShortTextComplication(bounds, data as ShortTextComplicationData)
      }

      else -> return
    }

    canvas.withScale(scale, scale, canvas.width/2f, canvas.height/2f) {
      canvas.drawBitmap(
        bitmap,
        bounds.left + offsetX,
        bounds.top.toFloat(),
        Paint(),
      )
    }
  }

  private fun drawShortTextComplication(
    bounds: Rect,
    data: ShortTextComplicationData
  ): Bitmap {
    val hash = "${bounds},${data.text},${data.title},${data.monochromaticImage?.image?.resId},${tertiaryColor},${opacity},${debug}"

    val cached = bitmapCache.get(hash)
    if (cached != null) {
      return cached
    }

    val bitmap = Bitmap.createBitmap(
      bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888
    )
    val bitmapCanvas = Canvas(bitmap)

    val rect = Rect(0, 0, bitmap.width, bitmap.height)

    renderShortTextComplication(bitmapCanvas, rect, data)

    renderDebug(bitmapCanvas, rect)

    bitmapCache.set(hash, bitmap)

    return bitmap
  }

  private fun renderShortTextComplication(
    canvas: Canvas,
    bounds: Rect,
    data: ShortTextComplicationData,
  ) {
    val now = Instant.now()

    var text = data.text.getTextAt(context.resources, now).toString().uppercase()
    if (text == "--") {
      return
    }

    val p = Paint(textPaint)
    p.textSize *= 14f

//    val textBounds = Rect()
//    p.getTextBounds(text, 0, text.length, textBounds)




    val textBounds = Rect()
    p.getTextBounds(text, 0, text.length, textBounds)

    canvas.drawText(
      text,
//      192f-textBounds.width().toFloat()/2f,
//      192f+textBounds.height()/2+offsetY,
//      0f,
//      bounds.height().toFloat()/2f,
      bounds.left.toFloat() + 14f,
      bounds.exactCenterY() + textBounds.height() / 2,
      p,
    )
  }

  private fun renderDebug(canvas: Canvas, bounds: Rect) {
    if (debug) {
      canvas.drawRect(bounds, Paint().apply {
        this.color = ColorUtils.blendARGB(Color.TRANSPARENT, Color.parseColor("#aa02d7f2"), opacity)
        style = Paint.Style.STROKE
        strokeWidth = 2f
      })
      val p2 = Paint()
      p2.color = ColorUtils.blendARGB(Color.TRANSPARENT, Color.parseColor("#aa02d7f2"), opacity)
      p2.typeface = context.resources.getFont(R.font.m8stealth57)
      p2.textSize = 8f
      canvas.drawText(
        "r ${bitmapCache.loads} w ${bitmapCache.renders}",
        bounds.left + 3f,
        bounds.bottom - 3f,
        p2,
      )
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
  }
}

fun createHorizontalTextComplicationFactory(context: Context) = CanvasComplicationFactory { _, _ ->
  HorizontalTextComplication(context)
}
