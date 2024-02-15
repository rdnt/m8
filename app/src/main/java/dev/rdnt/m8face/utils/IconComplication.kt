package dev.rdnt.m8face.utils

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toRect
import androidx.core.graphics.withRotation
import androidx.wear.watchface.CanvasComplication
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.RenderParameters
import androidx.wear.watchface.complications.data.*
import dev.rdnt.m8face.BitmapCacheEntry
import dev.rdnt.m8face.R
import java.time.Instant
import java.time.ZonedDateTime

class IconComplication(private val context: Context) : CanvasComplication {
  private val bitmapCache: BitmapCacheEntry = BitmapCacheEntry()

  var debug: Boolean = false;

  var tertiaryColor: Int = Color.parseColor("#8888bb")
    set(tertiaryColor) {
      field = tertiaryColor
      iconPaint.colorFilter = PorterDuffColorFilter(tertiaryColor, PorterDuff.Mode.SRC_IN)
    }

  var opacity: Float = 1f
    set(opacity) {
      field = opacity

      val color = ColorUtils.blendARGB(Color.TRANSPARENT, tertiaryColor, opacity)

      iconPaint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
      imagePaint.alpha = (opacity * 255).toInt()
    }

  private val iconPaint = Paint().apply {
    colorFilter = PorterDuffColorFilter(tertiaryColor, PorterDuff.Mode.SRC_IN)
  }

  private val imagePaint = Paint()

  override fun render(
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime,
    renderParameters: RenderParameters,
    slotId: Int
  ) {
    if (bounds.isEmpty) return

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

    when (data.type) {
      ComplicationType.MONOCHROMATIC_IMAGE -> {
        renderMonochromaticImageComplication(
          canvas,
          bounds,
          data as MonochromaticImageComplicationData
        )
      }

      ComplicationType.SMALL_IMAGE -> {
        renderSmallImageComplication(canvas, bounds, data as SmallImageComplicationData)
      }

      else -> return
    }
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

fun createIconComplicationFactory(context: Context) = CanvasComplicationFactory { _, _ ->
  IconComplication(context)
}
