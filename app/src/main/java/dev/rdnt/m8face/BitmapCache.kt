package dev.rdnt.m8face

import android.graphics.Bitmap

const val HOURS_BITMAP_KEY = "hours"
const val MINUTES_BITMAP_KEY = "minutes"
const val SECONDS_BITMAP_KEY = "seconds"

class BitmapCache {
  private val entries: MutableMap<String, BitmapCacheEntry> = mutableMapOf()

  fun get(k: String, h: String): Bitmap? {
    return entries[k]?.get(h)
  }

  fun set(k: String, h: String, b: Bitmap?) {
    val entry = entries.getOrPut(k) { BitmapCacheEntry() }
    entry.set(h, b)
  }

  fun renders(k: String): Int {
    return entries[k]?.renders ?: 0
  }

  fun loads(k: String): Int {
    return entries[k]?.loads ?: 0
  }
}

class BitmapCacheEntry {
  var renders: Int = 0
  var loads: Int = 0
  private var hash: String = ""
  private var bitmap: Bitmap? = null

  fun get(h: String): Bitmap? {
    loads++

    if (bitmap != null && h == hash) {
      return bitmap
    }

    return null
  }

  fun set(h: String, b: Bitmap?) {
    renders++

    hash = h
    bitmap = b
  }
}
