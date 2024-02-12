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
import dev.rdnt.m8face.R
import java.time.Instant
import java.time.ZonedDateTime

class HorizontalTextComplication(private val context: Context) : CanvasComplication {
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

//    // DEBUG
//    canvas.drawRect(bounds, Paint().apply {
//      color = Color.parseColor("#22ffffff")
//    })

    when (data.type) {
      ComplicationType.SHORT_TEXT -> {
        renderShortTextComplication(canvas, bounds, data as ShortTextComplicationData)
      }

      else -> return
    }
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
    p.textSize *= 14f / 42f * bounds.height()

//    val textBounds = Rect()
//    p.getTextBounds(text, 0, text.length, textBounds)




    val cacheBitmap = Bitmap.createBitmap(
      bounds.width(),
      bounds.height(),
      Bitmap.Config.ARGB_8888
    )
    val bitmapCanvas = Canvas(cacheBitmap)

    val textBounds = Rect()
    p.getTextBounds(text, 0, text.length, textBounds)

    bitmapCanvas.drawText(
      text,
//      192f-textBounds.width().toFloat()/2f,
//      192f+textBounds.height()/2+offsetY,
      0f,
      bounds.height().toFloat()/2f,
//      bounds.left.toFloat() + 14f / 42f * bounds.height(),
//      bounds.exactCenterY() + textBounds.height() / 2,
      p,
    )





    canvas.withScale(scale, scale, canvas.width/2f, canvas.height/2f) {
      canvas.withTranslation(offsetX, 0f) {
        canvas.drawBitmap(
          cacheBitmap,
          bounds.left.toFloat() + 14f / 42f * bounds.height(),
//        bounds.top.toFloat(),
          bounds.top.toFloat() + textBounds.height() / 2,
//        bounds.left.toFloat() + 14f / 42f * bounds.height(),
//        bounds.exactCenterY() + textBounds.height() / 2,

          Paint(),
        )

//      canvas.drawText(
//        text,
//        bounds.left.toFloat() + 14f / 42f * bounds.height(),
//        bounds.exactCenterY() + textBounds.height() / 2,
//        p
//      )
      }
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
