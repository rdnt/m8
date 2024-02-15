package dev.rdnt.m8face.utils

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toRect
import androidx.core.graphics.withScale
import androidx.wear.watchface.CanvasComplication
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.RenderParameters
import androidx.wear.watchface.complications.data.*
import dev.rdnt.m8face.BitmapCache
import dev.rdnt.m8face.BitmapCacheEntry
import dev.rdnt.m8face.R
import java.time.Instant
import java.time.ZonedDateTime

class VerticalComplication(private val context: Context) : CanvasComplication {
  private val bitmapCache: BitmapCacheEntry = BitmapCacheEntry()

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

  var opacity: Float = 1f
    set(opacity) {
      field = opacity

      val color = ColorUtils.blendARGB(Color.TRANSPARENT, tertiaryColor, opacity)
      textPaint.color = color
      titlePaint.color = color

      iconPaint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
      imagePaint.alpha = (opacity * 255).toInt()

      prefixPaint.color = color
      prefixPaint.alpha = 100
    }

  var scale: Float = 1f

  private val textPaint = Paint().apply {
    isAntiAlias = true
    isDither = true
    isFilterBitmap = true
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

  private val imagePaint = Paint()

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

      ComplicationType.MONOCHROMATIC_IMAGE -> {
        bitmap = drawMonochromaticImageComplication(
          bounds,
          data as MonochromaticImageComplicationData
        )
      }

      ComplicationType.SMALL_IMAGE -> {
        bitmap = drawSmallImageComplication(bounds, data as SmallImageComplicationData)
      }

      else -> return
    }

    canvas.withScale(scale, scale, canvas.width/2f, canvas.height/2f) {
      canvas.drawBitmap(
        bitmap,
        bounds.left.toFloat(),
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

  private fun drawMonochromaticImageComplication(
    bounds: Rect,
    data: MonochromaticImageComplicationData
  ): Bitmap {
    val hash = "${bounds},${data.monochromaticImage.image.resId},${tertiaryColor},${opacity},${debug}"

    val cached = bitmapCache.get(hash)
    if (cached != null) {
      return cached
    }

    val bitmap = Bitmap.createBitmap(
      bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888
    )
    val bitmapCanvas = Canvas(bitmap)

    val rect = Rect(0, 0, bitmap.width, bitmap.height)

    renderMonochromaticImageComplication(bitmapCanvas, rect, data)

    renderDebug(bitmapCanvas, rect)

    bitmapCache.set(hash, bitmap)

    return bitmap
  }

  private fun drawSmallImageComplication(
    bounds: Rect,
    data: SmallImageComplicationData
  ): Bitmap {
    val hash = "${bounds},${data.smallImage.image.resId},${tertiaryColor},${opacity},${debug}"

    val cached = bitmapCache.get(hash)
    if (cached != null) {
      return cached
    }

    val bitmap = Bitmap.createBitmap(
      bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888
    )
    val bitmapCanvas = Canvas(bitmap)

    val rect = Rect(0, 0, bitmap.width, bitmap.height)

    renderSmallImageComplication(bitmapCanvas, rect, data)

    renderDebug(bitmapCanvas, rect)

    bitmapCache.set(hash, bitmap)

    return bitmap
  }

  private fun renderDebug(canvas: Canvas, bounds: Rect) {
    if (debug) {
      canvas.drawRect(bounds, Paint().apply {
        this.color = Color.parseColor("#aa02d7f2")
        style = Paint.Style.STROKE
        strokeWidth = 2f
      })
      val p2 = Paint()
      p2.color = Color.parseColor("#aa02d7f2")
      p2.typeface = context.resources.getFont(R.font.m8stealth57)
      p2.textSize = 8f
      canvas.drawText(
        "r ${bitmapCache.loads}",
        bounds.left + 3f,
        bounds.bottom - 12f,
        p2,
      )
      canvas.drawText(
        "w ${bitmapCache.renders}",
        bounds.left + 3f,
        bounds.bottom - 3f,
        p2,
      )
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

    val isBattery =
      data.dataSource?.className == "com.google.android.clockwork.sysui.experiences.complications.providers.BatteryProviderService"

    val threeDigit = isBattery

    var title: String? = null
    var icon: Bitmap? = null
    var iconBounds = Rect()

    if (isBattery) {
      val drawable = ContextCompat.getDrawable(context, R.drawable.battery_icon_32)!!
      icon = drawable.toBitmap(
        (32f).toInt(),
        (32f).toInt()
      )
      iconBounds =
        Rect(0, 0, (32f).toInt(), (32f).toInt())
    } else if (data.monochromaticImage != null) {
      val drawable = data.monochromaticImage!!.image.loadDrawable(context)
      if (drawable != null) {
        val size = (bounds.width().coerceAtMost(bounds.height()).toFloat() / 2f).toInt()

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

    if (text.length <= 3) {
      textPaint.textSize = 24F
    } else if (text.length <= 6) {
      textPaint.textSize = 16F
    } else {
      textPaint.textSize = 12F
    }

    val textBounds = Rect()

    if (threeDigit) {
      textPaint.getTextBounds("000", 0, 3, textBounds)
    } else {
      textPaint.getTextBounds(text, 0, text.length, textBounds)
    }

    val titleBounds = Rect()

    if (title != null) {
      if (title.length <= 3) {
        titlePaint.textSize = 24F
      } else if (title.length <= 6) {
        titlePaint.textSize = 16F
      } else {
        titlePaint.textSize = 12F
      }

      titlePaint.getTextBounds(title, 0, title.length, titleBounds)
    }

    var iconOffsetY = 0f
    var titleOffsetY = 0f
    var textOffsetY = 0f

    if (icon != null) {
      val height = iconBounds.height() + textBounds.height()

      iconOffsetY = (height - iconBounds.height()).toFloat() / 2f
      textOffsetY = (height - textBounds.height()).toFloat() / 2f

      iconOffsetY += 6f
      if (isBattery) {
        iconOffsetY = iconOffsetY.toInt().toFloat()
      }

      textOffsetY += 6f
    } else if (title != null) {
      val height = titleBounds.height() + textBounds.height()

      titleOffsetY = (height - titleBounds.height()).toFloat() / 2f
      textOffsetY = (height - textBounds.height()).toFloat() / 2f

      titleOffsetY += 6f
      textOffsetY += 6f
    }







    if (icon != null) {
      val dstRect = RectF(
        bounds.exactCenterX() - iconBounds.width() / 2,
        bounds.exactCenterY() - iconBounds.height() / 2 - iconOffsetY,
        bounds.exactCenterX() + iconBounds.width() / 2,
        bounds.exactCenterY() + iconBounds.height() / 2 - iconOffsetY,
      )

      canvas.drawBitmap(icon, iconBounds, dstRect, iconPaint)
    } else if (title != null) {
      canvas.drawText(
        title,
        bounds.exactCenterX() - titleBounds.width() / 2,
        bounds.exactCenterY() + titleBounds.height() / 2 - titleOffsetY,
        titlePaint
      )
    }

    if (prefixLen > 0) {
      val prefix = "".padStart(prefixLen, '0')
      prefixPaint.textSize = textPaint.textSize

      canvas.drawText(
        prefix,
        bounds.exactCenterX() - textBounds.width() / 2,
        bounds.exactCenterY() + textBounds.height() / 2 + textOffsetY,
        prefixPaint
      )
    }

    canvas.drawText(
      text,
      bounds.exactCenterX() - textBounds.width() / 2,
      bounds.exactCenterY() + textBounds.height() / 2 + textOffsetY,
      textPaint
    )

  }

  private fun renderMonochromaticImageComplication(
    canvas: Canvas,
    bounds: Rect,
    data: MonochromaticImageComplicationData,
  ) {
    val icon: Bitmap
    val iconBounds: Rect

    val drawable = data.monochromaticImage.image.loadDrawable(context) ?: return

    val size = (bounds.width().coerceAtMost(bounds.height()).toFloat() * 0.75f).toInt()

    icon = drawable.toBitmap(size, size)
    iconBounds = Rect(0, 0, size, size)

    val dstRect = RectF(
      bounds.exactCenterX() - iconBounds.width() / 2,
      bounds.exactCenterY() - iconBounds.height() / 2,
      bounds.exactCenterX() + iconBounds.width() / 2,
      bounds.exactCenterY() + iconBounds.height() / 2,
    )

    canvas.drawBitmap(icon, iconBounds, dstRect, iconPaint)
  }

  private fun renderSmallImageComplication(
    canvas: Canvas,
    bounds: Rect,
    data: SmallImageComplicationData,
  ) {
    val icon: Bitmap
    val iconBounds: Rect

    val drawable = data.smallImage.image.loadDrawable(context) ?: return

    val size = (bounds.width().coerceAtMost(bounds.height()).toFloat() * 0.75f).toInt()

    icon = drawable.toBitmap(size, size)
    iconBounds = Rect(0, 0, size, size)

    val dstRect = RectF(
      bounds.exactCenterX() - iconBounds.width() / 2,
      bounds.exactCenterY() - iconBounds.height() / 2,
      bounds.exactCenterX() + iconBounds.width() / 2,
      bounds.exactCenterY() + iconBounds.height() / 2,
    )

    canvas.drawBitmap(icon, iconBounds, dstRect, imagePaint)
  }

  override fun drawHighlight(
    canvas: Canvas,
    bounds: Rect,
    boundsType: Int,
    zonedDateTime: ZonedDateTime,
    color: Int
  ) {
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

fun createVerticalComplicationFactory(context: Context) = CanvasComplicationFactory { _, _ ->
  VerticalComplication(context)
}
