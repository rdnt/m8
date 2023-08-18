/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.rdnt.m8face.data.watchface

import android.content.Context
import android.graphics.drawable.Icon
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.ListUserStyleSetting
import dev.rdnt.m8face.R

/**
 * Represents watch face color style options the user can select (includes the unique id, the
 * complication style resource id, and general watch face color style resource ids).
 *
 * The companion object offers helper functions to translate a unique string id to the correct enum
 * and convert all the resource ids to their correct resources (with the Context passed in). The
 * renderer will use these resources to render the actual colors and ComplicationDrawables of the
 * watch face.
 */

//val lab = ColorSpace.get(ColorSpace.Named.CIE_LAB)
//val dciP3 = ColorSpace.get(ColorSpace.Named.DCI_P3)

@Keep
enum class ColorStyle(
  val id: String,
  @StringRes val nameResourceId: Int,
  @DrawableRes val iconResourceId: Int,
  @ColorRes val primaryColorId: Int,
  @ColorRes val secondaryColorId: Int,
  @ColorRes val tertiaryColorId: Int,
) {
  MAUVE(
    id = "mauve",
    nameResourceId = R.string.mauve_style_name,
    iconResourceId = R.drawable.mauve_style_icon,
    primaryColorId = R.color.mauve_primary_color,
    secondaryColorId = R.color.mauve_secondary_color,
    tertiaryColorId = R.color.mauve_tertiary_color,
  ),

  PLUM_WEB(
    id = "plum_web",
    nameResourceId = R.string.plum_web_style_name,
    iconResourceId = R.drawable.plum_web_style_icon,
    primaryColorId = R.color.plum_web_primary_color,
    secondaryColorId = R.color.plum_web_secondary_color,
    tertiaryColorId = R.color.plum_web_tertiary_color,
  ),

  ORCHID(
    id = "orchid",
    nameResourceId = R.string.orchid_style_name,
    iconResourceId = R.drawable.orchid_style_icon,
    primaryColorId = R.color.orchid_primary_color,
    secondaryColorId = R.color.orchid_secondary_color,
    tertiaryColorId = R.color.orchid_tertiary_color,
  ),

  SALMON(
    id = "salmon",
    nameResourceId = R.string.salmon_style_name,
    iconResourceId = R.drawable.salmon_style_icon,
    primaryColorId = R.color.salmon_primary_color,
    secondaryColorId = R.color.salmon_secondary_color,
    tertiaryColorId = R.color.salmon_tertiary_color,
  ),

  TANGERINE(
    id = "tangerine",
    nameResourceId = R.string.tangerine_style_name,
    iconResourceId = R.drawable.tangerine_style_icon,
    primaryColorId = R.color.tangerine_primary_color,
    secondaryColorId = R.color.tangerine_secondary_color,
    tertiaryColorId = R.color.tangerine_tertiary_color,
  ),

  APRICOT(
    id = "apricot",
    nameResourceId = R.string.apricot_style_name,
    iconResourceId = R.drawable.apricot_style_icon,
    primaryColorId = R.color.apricot_primary_color,
    secondaryColorId = R.color.apricot_secondary_color,
    tertiaryColorId = R.color.apricot_tertiary_color,
  ),

  JASMINE(
    id = "jasmine",
    nameResourceId = R.string.jasmine_style_name,
    iconResourceId = R.drawable.jasmine_style_icon,
    primaryColorId = R.color.jasmine_primary_color,
    secondaryColorId = R.color.jasmine_secondary_color,
    tertiaryColorId = R.color.jasmine_tertiary_color,
  ),

  KEY_LIME(
    id = "key_lime",
    nameResourceId = R.string.key_lime_style_name,
    iconResourceId = R.drawable.key_lime_style_icon,
    primaryColorId = R.color.key_lime_primary_color,
    secondaryColorId = R.color.key_lime_secondary_color,
    tertiaryColorId = R.color.key_lime_tertiary_color,
  ),

  MINT(
    id = "mint",
    nameResourceId = R.string.mint_style_name,
    iconResourceId = R.drawable.mint_style_icon,
    primaryColorId = R.color.mint_primary_color,
    secondaryColorId = R.color.mint_secondary_color,
    tertiaryColorId = R.color.mint_tertiary_color,
  ),

  AQUAMARINE(
    id = "aquamarine",
    nameResourceId = R.string.aquamarine_style_name,
    iconResourceId = R.drawable.aquamarine_style_icon,
    primaryColorId = R.color.aquamarine_primary_color,
    secondaryColorId = R.color.aquamarine_secondary_color,
    tertiaryColorId = R.color.aquamarine_tertiary_color,
  ),

  TURQUOISE(
    id = "turquoise",
    nameResourceId = R.string.turquoise_style_name,
    iconResourceId = R.drawable.turquoise_style_icon,
    primaryColorId = R.color.turquoise_primary_color,
    secondaryColorId = R.color.turquoise_secondary_color,
    tertiaryColorId = R.color.turquoise_tertiary_color,
  ),

  AQUA(
    id = "aqua",
    nameResourceId = R.string.aqua_style_name,
    iconResourceId = R.drawable.aqua_style_icon,
    primaryColorId = R.color.aqua_primary_color,
    secondaryColorId = R.color.aqua_secondary_color,
    tertiaryColorId = R.color.aqua_tertiary_color,
  ),

  SKY_BLUE(
    id = "sky_blue",
    nameResourceId = R.string.sky_blue_style_name,
    iconResourceId = R.drawable.sky_blue_style_icon,
    primaryColorId = R.color.sky_blue_primary_color,
    secondaryColorId = R.color.sky_blue_secondary_color,
    tertiaryColorId = R.color.sky_blue_tertiary_color,
  ),

  CAPRI(
    id = "capri",
    nameResourceId = R.string.capri_style_name,
    iconResourceId = R.drawable.capri_style_icon,
    primaryColorId = R.color.capri_primary_color,
    secondaryColorId = R.color.capri_secondary_color,
    tertiaryColorId = R.color.capri_tertiary_color,
  ),

  CORNFLOWER(
    id = "cornflower",
    nameResourceId = R.string.cornflower_style_name,
    iconResourceId = R.drawable.cornflower_style_icon,
    primaryColorId = R.color.cornflower_primary_color,
    secondaryColorId = R.color.cornflower_secondary_color,
    tertiaryColorId = R.color.cornflower_tertiary_color,
  ),

  LAST_DANCE(
    id = "last_dance",
    nameResourceId = R.string.last_dance_style_name,
    iconResourceId = R.drawable.last_dance_style_icon,
    primaryColorId = R.color.last_dance_primary_color,
    secondaryColorId = R.color.last_dance_secondary_color,
    tertiaryColorId = R.color.last_dance_tertiary_color,
  ),

  BUBBLEGUM(
    id = "bubblegum",
    nameResourceId = R.string.bubblegum_style_name,
    iconResourceId = R.drawable.bubblegum_style_icon,
    primaryColorId = R.color.bubblegum_primary_color,
    secondaryColorId = R.color.bubblegum_secondary_color,
    tertiaryColorId = R.color.bubblegum_tertiary_color,
  ),

  DREAM(
    id = "dream",
    nameResourceId = R.string.dream_style_name,
    iconResourceId = R.drawable.dream_style_icon,
    primaryColorId = R.color.dream_primary_color,
    secondaryColorId = R.color.dream_secondary_color,
    tertiaryColorId = R.color.dream_tertiary_color,
  ),

  BUBBLETEA(
    id = "bubbletea",
    nameResourceId = R.string.bubbletea_style_name,
    iconResourceId = R.drawable.bubbletea_style_icon,
    primaryColorId = R.color.bubbletea_primary_color,
    secondaryColorId = R.color.bubbletea_secondary_color,
    tertiaryColorId = R.color.bubbletea_tertiary_color,
  ),

  FOREST(
    id = "forest",
    nameResourceId = R.string.forest_style_name,
    iconResourceId = R.drawable.forest_style_icon,
    primaryColorId = R.color.forest_primary_color,
    secondaryColorId = R.color.forest_secondary_color,
    tertiaryColorId = R.color.forest_tertiary_color,
  ),

  CHAMPAGNE(
    id = "champagne",
    nameResourceId = R.string.champagne_style_name,
    iconResourceId = R.drawable.champagne_style_icon,
    primaryColorId = R.color.champagne_primary_color,
    secondaryColorId = R.color.champagne_secondary_color,
    tertiaryColorId = R.color.champagne_tertiary_color,
  ),

  RETRO(
    id = "retro",
    nameResourceId = R.string.retro_style_name,
    iconResourceId = R.drawable.retro_style_icon,
    primaryColorId = R.color.retro_primary_color,
    secondaryColorId = R.color.retro_secondary_color,
    tertiaryColorId = R.color.retro_tertiary_color,
  ),

  FLAMINGO(
    id = "flamingo",
    nameResourceId = R.string.flamingo_style_name,
    iconResourceId = R.drawable.flamingo_style_icon,
    primaryColorId = R.color.flamingo_primary_color,
    secondaryColorId = R.color.flamingo_secondary_color,
    tertiaryColorId = R.color.flamingo_tertiary_color,
  ),

  COTTON_CANDY(
    id = "cotton_candy",
    nameResourceId = R.string.cotton_candy_style_name,
    iconResourceId = R.drawable.cotton_candy_style_icon,
    primaryColorId = R.color.cotton_candy_primary_color,
    secondaryColorId = R.color.cotton_candy_secondary_color,
    tertiaryColorId = R.color.cotton_candy_tertiary_color,
  ),

  NORD(
    id = "nord",
    nameResourceId = R.string.nord_style_name,
    iconResourceId = R.drawable.nord_style_icon,
    primaryColorId = R.color.nord_primary_color,
    secondaryColorId = R.color.nord_secondary_color,
    tertiaryColorId = R.color.nord_tertiary_color,
  ),

  FROST(
    id = "frost",
    nameResourceId = R.string.frost_style_name,
    iconResourceId = R.drawable.frost_style_icon,
    primaryColorId = R.color.frost_primary_color,
    secondaryColorId = R.color.frost_secondary_color,
    tertiaryColorId = R.color.frost_tertiary_color,
  ),

  STEEL(
    id = "steel",
    nameResourceId = R.string.steel_style_name,
    iconResourceId = R.drawable.steel_style_icon,
    primaryColorId = R.color.steel_primary_color,
    secondaryColorId = R.color.steel_secondary_color,
    tertiaryColorId = R.color.steel_tertiary_color,
  ),

  SNOW(
    id = "snow",
    nameResourceId = R.string.snow_style_name,
    iconResourceId = R.drawable.snow_style_icon,
    primaryColorId = R.color.snow_primary_color,
    secondaryColorId = R.color.snow_secondary_color,
    tertiaryColorId = R.color.snow_tertiary_color,
  ),

  ONYX(
    id = "onyx",
    nameResourceId = R.string.onyx_style_name,
    iconResourceId = R.drawable.onyx_style_icon,
    primaryColorId = R.color.onyx_primary_color,
    secondaryColorId = R.color.onyx_secondary_color,
    tertiaryColorId = R.color.onyx_tertiary_color,
  );

  companion object {
    fun getColorStyleConfig(id: String): ColorStyle {
      return when (id) {
        LAST_DANCE.id -> LAST_DANCE
        MAUVE.id -> MAUVE
        PLUM_WEB.id -> PLUM_WEB
        ORCHID.id -> ORCHID
        SALMON.id -> SALMON
        TANGERINE.id -> TANGERINE
        APRICOT.id -> APRICOT
        JASMINE.id -> JASMINE
        KEY_LIME.id -> KEY_LIME
        MINT.id -> MINT
        AQUAMARINE.id -> AQUAMARINE
        TURQUOISE.id -> TURQUOISE
        AQUA.id -> AQUA
        SKY_BLUE.id -> SKY_BLUE
        CAPRI.id -> CAPRI
        CORNFLOWER.id -> CORNFLOWER
        BUBBLEGUM.id -> BUBBLEGUM
        DREAM.id -> DREAM
        BUBBLETEA.id -> BUBBLETEA
        FOREST.id -> FOREST
        CHAMPAGNE.id -> CHAMPAGNE
        RETRO.id -> RETRO
        FLAMINGO.id -> FLAMINGO
        COTTON_CANDY.id -> COTTON_CANDY
        NORD.id -> NORD
        FROST.id -> FROST
        STEEL.id -> STEEL
        SNOW.id -> SNOW
        ONYX.id -> ONYX
        else -> LAST_DANCE
      }
    }

    fun toOptionList(context: Context): List<ListUserStyleSetting.ListOption> {
      val colorStyleList = enumValues<ColorStyle>()

      return colorStyleList.map { style ->
        colorStyleToListOption(context, style)
      }
    }

    fun colorStyleToListOption(context: Context, style: ColorStyle): ListUserStyleSetting.ListOption {
      return ListUserStyleSetting.ListOption(
        UserStyleSetting.Option.Id(style.id),
        context.resources,
        style.nameResourceId,
        Icon.createWithResource(
          context,
          style.iconResourceId
        )
      )
    }
  }
}
