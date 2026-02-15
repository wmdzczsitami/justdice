package com.itami.justdice

data class WaveStep(val type: MobType, val count: Int, val interval: Long = 800L)

fun getWaveSteps(round: Int): List<WaveStep> {
    val r = if (round % 10 == 0) 10 else round % 10 // 1~10 단위로 루프
    return when (r) {
        in 1..4 -> listOf(WaveStep(MobType.NORMAL, 15))
        5 -> listOf(WaveStep(MobType.BIG, 1), WaveStep(MobType.NORMAL, 10), WaveStep(MobType.FAST, 3))
        in 6..9 -> listOf(WaveStep(MobType.NORMAL, 15))
        10 -> listOf(WaveStep(MobType.BOSS, 1))
        else -> listOf(WaveStep(MobType.NORMAL, 696969))
    }
}