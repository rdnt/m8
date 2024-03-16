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
import androidx.core.graphics.toRectF
import androidx.core.graphics.withScale
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

class HorizontalComplication(private val context: Context) : CanvasComplication {
  private val bitmapCache: BitmapCacheEntry = BitmapCacheEntry()

  init {
    Log.d("HorizontalComplication", "Constructor ran")
  }

  var debug: Boolean = false;

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

    val bitmap: Bitmap

    when (data.type) {
      ComplicationType.SHORT_TEXT -> {
        bitmap = drawShortTextComplication(bounds, data as ShortTextComplicationData)
      }

      else -> return
    }

//    canvas.withScale(scale, scale, canvas.width/2f, canvas.height/2f) {
      renderDebug(canvas, bounds.toRectF())

      canvas.drawBitmap(
        bitmap,
        bounds.left.toFloat(),
        bounds.top.toFloat(),
        Paint(),
//        Paint().apply { alpha = (opacity * 255).toInt() },
      )
//    }
  }


  private fun drawShortTextComplication(
    bounds: Rect,
    data: ShortTextComplicationData
  ): Bitmap {
    val hash = "${bounds},${data.text},${data.title},${data.monochromaticImage?.image?.resId},${tertiaryColor},${debug}"

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

    bitmapCache.set(hash, bitmap)

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
      canvas.drawText(
        "r ${bitmapCache.loads} w ${bitmapCache.renders}",
        bounds.left + 3f,
        bounds.bottom - 3f,
        p2,
      )
    }
  }

  private fun renderShortTextComplication(
    canvas: Canvas,
    bounds: Rect,
    data: ShortTextComplicationData
  ) {
    val now = Instant.now()

    var text = data.text.getTextAt(context.resources, now).toString().uppercase()
    if (text == "--") {
      return
    }

    val isBattery =
      data.dataSource?.className == "com.google.android.clockwork.sysui.experiences.complications.providers.BatteryProviderService"

    val threeDigit = isBattery

    var title: String? = null
    var icon: Bitmap? = null
    var iconBounds = Rect()

    if (isBattery) {
      val drawable = ContextCompat.getDrawable(context, R.drawable.battery_icon_32)!!
      icon = drawable.toBitmap(
        (32f / 186f * canvas.width).toInt(),
        (32f / 186f * canvas.width).toInt()
      )
      iconBounds =
        Rect(0, 0, (32f / 186f * canvas.width).toInt(), (32f / 186f * canvas.width).toInt())
    } else if (data.monochromaticImage != null) {
      val drawable = data.monochromaticImage!!.image.loadDrawable(context)
      if (drawable != null) {
        val size = (bounds.width().coerceAtMost(bounds.height()).toFloat() * 0.55f).toInt()

        icon = drawable.toBitmap(size, size)
        iconBounds = Rect(0, 0, size, size)
      }
    }

    var prefixLen = 0

    if (threeDigit) {
      prefixLen = 3 - text.length
      text = text.padStart(3, ' ')
    }

    if (data.title != null && !data.title!!.isPlaceholder()) {
      title = data.title!!.getTextAt(context.resources, now).toString().uppercase()
    }

    textPaint.textSize = 24F / 186f * canvas.width

    val textBounds = Rect()

    if (threeDigit) {
      textPaint.getTextBounds("000", 0, 3, textBounds)
    } else {
      textPaint.getTextBounds(text, 0, text.length, textBounds)
    }

    val titleBounds = Rect()

    if (title != null) {
      titlePaint.textSize = textPaint.textSize
      titlePaint.getTextBounds(title, 0, title.length, titleBounds)
    }

    var iconOffsetX = 0f
    var titleOffsetX = 0f
    var textOffsetX = 0f

    if (title != null) {
      val width = titleBounds.width() + textBounds.width()

      titleOffsetX = (width - titleBounds.width()).toFloat() / 2f
      textOffsetX = (width - textBounds.width()).toFloat() / 2f

      titleOffsetX += 6f / 186f * canvas.width
      textOffsetX += 6f / 186f * canvas.width
    } else if (icon != null) {
      val width = iconBounds.width() + textBounds.width()

      iconOffsetX = (width - iconBounds.width()).toFloat() / 2f
      textOffsetX = (width - textBounds.width()).toFloat() / 2f

      iconOffsetX += 9f / 186f * canvas.width
      textOffsetX += 9f / 186f * canvas.width

      if (isBattery) {
        iconOffsetX = iconOffsetX.toInt().toFloat()
      }
    }

    if (title != null) {
      canvas.drawText(
        title,
        bounds.exactCenterX() - titleBounds.width() / 2 - titleOffsetX,
        bounds.exactCenterY() + titleBounds.height() / 2,
        titlePaint
      )
    } else if (icon != null) {
      val dstRect = RectF(
        bounds.exactCenterX() - iconBounds.width() / 2f - iconOffsetX,
        bounds.exactCenterY() - iconBounds.height() / 2f,
        bounds.exactCenterX() + iconBounds.width() / 2f - iconOffsetX,
        bounds.exactCenterY() + iconBounds.height() / 2f,
      )

      canvas.drawBitmap(icon, iconBounds, dstRect, iconPaint)
    }


    if (prefixLen > 0) {
      val prefix = "".padStart(prefixLen, '0')
      prefixPaint.textSize = textPaint.textSize

      canvas.drawText(
        prefix,
        bounds.exactCenterX() - textBounds.width() / 2 + textOffsetX,
        bounds.exactCenterY() + textBounds.height() / 2,
        prefixPaint
      )
    }

    canvas.drawText(
      text,
      bounds.exactCenterX() - textBounds.width() / 2 + textOffsetX,
      bounds.exactCenterY() + textBounds.height() / 2,
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
