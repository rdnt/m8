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
import androidx.core.graphics.drawable.toBitmap
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

class HorizontalComplication(private val context: Context) : CanvasComplication {
  private val renderer = ComplicationRenderer()

  init {
    Log.d("HorizontalComplication", "Constructor ran")
  }

  var tertiaryColor: Int = Color.parseColor("#8888bb")
    set(tertiaryColor) {
      field = tertiaryColor
      textPaint.color = tertiaryColor
      titlePaint.color = tertiaryColor
      iconPaint.colorFilter = PorterDuffColorFilter(tertiaryColor, PorterDuff.Mode.SRC_IN)
      prefixPaint.color = tertiaryColor
      prefixPaint.alpha = 100
      renderer.reset()
    }

  private val textPaint = Paint().apply {
    isAntiAlias = true
    typeface = context.resources.getFont(R.font.m8stealth57)
    textAlign = Paint.Align.LEFT
    color = tertiaryColor
  }

  private val titlePaint = Paint().apply {
    isAntiAlias = true
    typeface = context.resources.getFont(R.font.m8stealth57)
    textAlign = Paint.Align.LEFT
    color = tertiaryColor
  }

  private val iconPaint = Paint().apply {
    colorFilter = PorterDuffColorFilter(tertiaryColor, PorterDuff.Mode.SRC_IN)
  }

  private val prefixPaint = Paint().apply {
    isAntiAlias = true
    typeface = context.resources.getFont(R.font.m8stealth57)
    textAlign = Paint.Align.LEFT
    color = Color.parseColor("#343434")
    alpha = 127
  }

  override fun render(
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime,
    renderParameters: RenderParameters,
    slotId: Int
  ) {
    if (bounds.isEmpty) return

    val data = if (data.type == ComplicationType.NO_DATA) {
      val placeholder = (data as NoDataComplicationData).placeholder
      placeholder ?: data
    } else {
      data
    }

    val bitmap = when (data.type) {
      ComplicationType.SHORT_TEXT -> {
        renderer.render<ShortTextComplicationData>(bounds, data, ::renderShortTextComplication)
      }

      else -> return
    }

    canvas.drawBitmap(
      bitmap,
      bounds.left.toFloat(),
      bounds.top.toFloat(),
      Paint(),
    )
  }

  private fun renderShortTextComplication(
    canvas: Canvas,
    bounds: Rect,
    complData: ComplicationData
  ) {
    val data = complData as ShortTextComplicationData

    val now = Instant.now()

    var text = data.text.getTextAt(context.resources, now).toString().uppercase()

    val title = data.title?.getTextAt(context.resources, now)?.toString()?.uppercase()
    if (title != null) {
      text = "$text $title"
    }

    var icon: Bitmap? = null
    var iconBounds = Rect()
    if (title == null) {
      val bmpSize = (bounds.width().coerceAtMost(bounds.height()).toFloat() * 0.55f).toInt()

      icon = data.monochromaticImage?.image?.loadDrawable(context)?.toBitmap(bmpSize, bmpSize)
      iconBounds = Rect(0, 0, bmpSize, bmpSize)
    }

    textPaint.textSize = 24F / 186f * canvas.width

    val textBounds = Rect()
    textPaint.getTextBounds(text, 0, text.length, textBounds)

    var iconOffsetX = 0f
    var textOffsetX = 0f

    if (icon != null) {
      val width = iconBounds.width() + textBounds.width()

      iconOffsetX = (width - iconBounds.width()).toFloat() / 2f
      textOffsetX = (width - textBounds.width()).toFloat() / 2f

      iconOffsetX += 9f / 186f * canvas.width
      textOffsetX += 9f / 186f * canvas.width
    }

    if (title == null && icon != null) {
      val dstRect = RectF(
        bounds.exactCenterX() - iconBounds.width() / 2f - iconOffsetX,
        bounds.exactCenterY() - iconBounds.height() / 2f,
        bounds.exactCenterX() + iconBounds.width() / 2f - iconOffsetX,
        bounds.exactCenterY() + iconBounds.height() / 2f,
      )

      canvas.drawBitmap(icon, iconBounds, dstRect, iconPaint)
    }

    canvas.drawText(
      text,
      bounds.exactCenterX() - textBounds.width() / 2 + textOffsetX,
      bounds.exactCenterY() + textPaint.fontSpacing / 2,
      textPaint
    )
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

fun createHorizontalComplicationFactory(context: Context) = CanvasComplicationFactory { _, _ ->
  HorizontalComplication(context)
}
