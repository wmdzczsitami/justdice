package com.itami.justdice

enum class DiceType(val imageRes: Int) {
    RED(R.drawable.dice_red), BLUE(R.drawable.dice_blue),
    YELLOW(R.drawable.dice_yellow), GREEN(R.drawable.dice_green), PURPLE(R.drawable.dice_purple);
    companion object { fun random() = entries.random() }
}

data class Dice(val type: DiceType, val star: Int = 1) {
    val currentStar = star.coerceIn(1, 7)
}