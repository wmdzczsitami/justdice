package com.itami.justdice


enum class MobType(val imageRes: Int, val baseHp: Int, val speed: Float) {
    NORMAL(R.drawable.mob_normal, 100, 0.001f),
    FAST(R.drawable.mob_fast, 50, 0.002f), // 더 빠름
    BIG(R.drawable.mob_big, 300, 0.0005f),
    BOSS(R.drawable.mob_boss, 1000, 0.0005f); // 느림
}
data class Mob(
    val id: Long = System.nanoTime(),
    val type: MobType,
    var progress: Float = 0f,
    var hp: Long = type.baseHp.toLong() // Long으로 변경
)