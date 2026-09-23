package com.kejing.towerwood.data.repository

import android.content.Context
interface GameRepository {
    fun theme(): String = "NATURAL"
    fun saveTheme(value:String) {}
    fun sound(): Boolean = true
    fun saveSound(enabled: Boolean) {}
    fun best(): Int
    fun saveBest(score: Int)
    fun saveRound(value: String)
    fun round(): String?
}
class LocalGameRepository(context: Context) : GameRepository {
    private val prefs = context.getSharedPreferences("tower_wood", Context.MODE_PRIVATE)
    override fun theme() = prefs.getString("wood_theme","NATURAL") ?: "NATURAL"
    override fun saveTheme(value:String) { prefs.edit().putString("wood_theme",value).apply() }
    override fun sound() = prefs.getBoolean("sound",true)
    override fun saveSound(enabled:Boolean) { prefs.edit().putBoolean("sound",enabled).apply() }
    override fun best() = prefs.getInt("best", 0)
    override fun saveBest(score: Int) { if (score > best()) prefs.edit().putInt("best", score).apply() }
    override fun saveRound(value: String) { prefs.edit().putString("round", value).apply() }
    override fun round() = prefs.getString("round", null)
}
