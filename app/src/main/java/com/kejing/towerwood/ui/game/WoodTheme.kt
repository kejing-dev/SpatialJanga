package com.kejing.towerwood.ui.game

import com.kejing.towerwood.R

enum class WoodTheme(val label:String, val preview:Int, val assets:List<String>) {
    NATURAL("原木棕",R.drawable.theme_natural,listOf("wood_block")),
    WALNUT("胡桃木",R.drawable.theme_walnut,listOf("walnut")),
    MONO("黑白",R.drawable.theme_mono,listOf("charcoal","ivory")),
    PASTEL("淡彩虹",R.drawable.theme_pastel,listOf("rose","peach","butter","mint","milkblue","lavender"));
    fun assetForBlock(id:Int)=assets[(id/3)%assets.size]
    companion object { fun fromSaved(value:String)=entries.firstOrNull { it.name==value } ?: NATURAL }
}
