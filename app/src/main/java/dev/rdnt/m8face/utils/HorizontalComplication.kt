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
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toRectF
import androidx.core.graphics.withScale
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
import kotlin.math.max

private const val debug = false

class HorizontalComplication(private val context: Context) : CanvasComplication {
  private val memoryCache = LruCache<String, Bitmap>(1)

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
    }

//  var opacity: Float = 1f
//    set(opacity) {
//      field = opacity
//
//      val color = ColorUtils.blendARGB(Color.TRANSPARENT, tertiaryColor, 1f)
//
//      textPaint.color = color
//      titlePaint.color = color
//
//      iconPaint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
//
//      prefixPaint.color = color
//      prefixPaint.alpha = 100
//    }

//  var offsetX: Float = 0f

//  var scale: Float = 1f

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

    renderDebug(canvas, bounds.toRectF())

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
    if (cached != null) {
      return cached
    }

    val bitmap = Bitmap.createBitmap(
      bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888
    )
    val bitmapCanvas = Canvas(bitmap)

    val rect = Rect(0, 0, bitmap.width, bitmap.height)

    renderShortTextComplication(bitmapCanvas, rect, data)

    memoryCache.put("", bitmap)

    return bitmap
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

  private fun renderShortTextComplication(
    canvas: Canvas,
    bounds: Rect,
    data: ShortTextComplicationData
  ) {
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
    memoryCache.remove("")
  }
}

fun createHorizontalComplicationFactory(context: Context) = CanvasComplicationFactory { _, _ ->
  HorizontalComplication(context)
}
