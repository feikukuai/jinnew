package com.traework.jygoldenfinger.data

import kotlinx.serialization.Serializable

/** 任务类型：习惯(可反复)、日常(每日重置)、待办(一次性)、奖励(消耗兑换点兑换) */
enum class TaskType(val label: String) {
    HABIT("习惯"),
    DAILY("日常"),
    TODO("待办"),
    REWARD("奖励")
}

@Serializable
data class TaskItem(
    val id: String,
    val title: String,
    val type: TaskType,
    /** 完成后获得的兑换点 */
    val pointReward: Int = 10,
    /** 仅奖励类使用：兑换所需兑换点 */
    val pointCost: Int = 0,
    val note: String = "",
    val createdAt: Long = 0L,
    val lastDoneAt: Long? = null,
    val doneCount: Int = 0,
    val streak: Int = 0,
    /** 日常类上次打卡日期 yyyy-MM-dd */
    val lastDailyDate: String? = null,
    /** 待办类是否已完成 */
    val todoDone: Boolean = false
)

@Serializable
data class MartialArt(
    val id: String,
    val name: String,
    val category: String = "外功",
    /** 游戏内武功 ID（用于精确同步），0 表示未指定 */
    val gameId: Int = 0,
    /** 已投入的兑换点（决定等级） */
    val expAllocated: Int = 0
) {
    val level: Int get() = SkillCurve.levelForExp(expAllocated)
}

/** 游戏资源兑换条目（现实兑换点 -> 游戏内资源） */
@Serializable
data class RedeemItem(
    val id: String,
    val name: String,
    val pointCost: Int,
    /** ITEM: 游戏物品; SILVER: 游戏内银两; EXP: 主角历练（游戏内经验） */
    val kind: String = "ITEM",
    /** 游戏内物品 ID（kind=ITEM 时使用），0 表示未指定 */
    val gameId: Int = 0,
    val amount: Int = 1,
    val note: String = ""
)

@Serializable
data class LogEntry(
    val time: Long,
    val text: String,
    /** 本次变动的兑换点（正为获得，负为消耗） */
    val deltaPoint: Int = 0
)

/** 待下发给游戏的金手指指令 */
@Serializable
data class LedgerCommand(
    val id: String,
    /** SKILL: 设置武功等级; ITEM: 发放物品; SILVER: 发放银两; EXP: 主角历练 */
    val type: String,
    val skill: String = "",
    val itemId: Int = 0,
    val amount: Int = 0,
    val time: Long = 0L
)

@Serializable
data class PlayerState(
    /** 兑换点余额：可分配给武功，也可兑换游戏资源 */
    val points: Long = 0L,
    /** 累计获得的兑换点 */
    val pointsEarnedTotal: Long = 0L,
    /** 累计消耗的兑换点（投入武功 + 兑换资源 + 兑换奖励） */
    val pointsSpentTotal: Long = 0L,
    val tasks: List<TaskItem> = emptyList(),
    val martialArts: List<MartialArt> = emptyList(),
    val redeemItems: List<RedeemItem> = emptyList(),
    val commands: List<LedgerCommand> = emptyList(),
    val log: List<LogEntry> = emptyList(),
    val installedModNames: List<String> = emptyList()
) {
    /** 已投入武功的兑换点合计 */
    val pointsInvested: Long get() = martialArts.sumOf { it.expAllocated.toLong() }
}

/** 武功等级曲线：升到下一级所需兑换点 = BASE + (当前等级-1) * STEP */
object SkillCurve {
    const val MAX_LEVEL = 10
    private const val BASE = 100
    private const val STEP = 50

    fun neededForNextLevel(level: Int): Int = BASE + (level - 1) * STEP

    fun totalExpForLevel(level: Int): Int {
        var sum = 0
        for (l in 1 until level) sum += neededForNextLevel(l)
        return sum
    }

    fun levelForExp(exp: Int): Int {
        var level = 1
        var acc = 0
        while (level < MAX_LEVEL && acc + neededForNextLevel(level) <= exp) {
            acc += neededForNextLevel(level)
            level++
        }
        return level
    }

    /** 距离下一级还差多少兑换点；已满级返回 0 */
    fun expToNextLevel(exp: Int): Int {
        val level = levelForExp(exp)
        if (level >= MAX_LEVEL) return 0
        val target = totalExpForLevel(level + 1)
        return (target - exp).coerceAtLeast(0)
    }

    fun progressInLevel(exp: Int): Float {
        val level = levelForExp(exp)
        if (level >= MAX_LEVEL) return 1f
        val floorExp = totalExpForLevel(level)
        val need = neededForNextLevel(level)
        if (need <= 0) return 1f
        return ((exp - floorExp).toFloat() / need).coerceIn(0f, 1f)
    }
}