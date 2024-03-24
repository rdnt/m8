package dev.rdnt.m8face

import android.graphics.Bitmap
import android.util.Log

//const val HOURS_BITMAP_KEY = "hours"
//const val MINUTES_BITMAP_KEY = "minutes"
//const val SECONDS_BITMAP_KEY = "seconds"
//const val AMPM_BITMAP_KEY = "ampm"
//
//const val TIME_BITMAP_KEY = "time"
//const val AUX_BITMAP_KEY = "aux"

class BitmapCache {
  private val entries: MutableMap<String, BitmapCacheEntry> = mutableMapOf()

  override fun toString(): String {
    //loop entries
    var result = ""
    entries.forEach { (k, v) -> result += "{$k: $v} " }
    return "(entries= $result)"
  }

  fun get(k: String, h: String): Bitmap? {
    return entries[k]?.get(h)
  }

  fun set(k: String, h: String, b: Bitmap?) {
    val entry = entries.getOrPut(k) { BitmapCacheEntry() }
    entry.set(h, b)
  }

  fun renders(k: String): Int {
    return entries[k]?.renders ?: 1
  }

  fun loads(k: String): Int {
    return entries[k]?.loads ?: 1
  }
}

class BitmapCacheEntry {
  var renders: Int = 1
  var loads: Int = 1
  private var hash: String = ""
  private var bitmap: Bitmap? = null

  override fun toString(): String {
    return "(renders=$renders, loads=$loads)"
  }

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
