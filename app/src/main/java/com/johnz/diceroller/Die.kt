package com.johnz.diceroller

import kotlin.random.Random

/**
 * 1️⃣ 定义「骰子规则」本身（最关键）
 */
sealed class Die {
    abstract fun roll(): Int
    abstract fun maxValue(): Int
    abstract fun possibleValues(): List<Int>
}

/**
 * 2️⃣ 标准骰子（d6 / d20）
 */
class StandardDie(private val faces: Int) : Die() {
    override fun roll(): Int = Random.nextInt(1, faces + 1)
    override fun maxValue(): Int = faces
    override fun possibleValues(): List<Int> = (1..faces).toList()
}

/**
 * 3️⃣ 自定义点数骰子（离散值）
 */
class CustomValueDie(private val values: List<Int>) : Die() {
    override fun roll(): Int = values.random()
    override fun maxValue(): Int = values.maxOrNull() ?: 1
    override fun possibleValues(): List<Int> = values
}

/**
 * 4️⃣ 权重骰子（概率不均）
 */
class WeightedDie(private val weightedValues: List<Pair<Int, Int>>) : Die() {
    private val pool = weightedValues.flatMap { (v, w) -> List(w) { v } }
    override fun roll(): Int = pool.random()
    override fun maxValue(): Int = weightedValues.maxOf { it.first }
    override fun possibleValues(): List<Int> = weightedValues.map { it.first }
}

/**
 * 辅助：常量骰子（用于处理公式中的 +5, -2 等常量）
 */
class ConstantDie(private val value: Int) : Die() {
    override fun roll(): Int = value
    override fun maxValue(): Int = value
    override fun possibleValues(): List<Int> = listOf(value)
}

/**
 * 四、RollResult 的正确形态（动画友好）
 * 增加了 coefficient 以支持减法运算 (例如 -1d4)
 */
data class SingleDieRoll(
    val die: Die,
    val value: Int,
    val coefficient: Int = 1 // 1 for addition, -1 for subtraction
)

data class RollResult(
    val total: Int,
    val rolls: List<SingleDieRoll>,
    val maxTotal: Int,
    val breakdown: String
)