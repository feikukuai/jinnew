package com.traework.jygoldenfinger.data

import java.util.UUID

/** 初始武功库：经典《金庸群侠传》武学 + 本 Mod《诗酒芳华录》特有武功 */
object Defaults {

    fun defaultState(): PlayerState {
        val arts = mutableListOf<MartialArt>()
        var idx = 0
        fun add(name: String, category: String) {
            arts += MartialArt(id = "wf_%03d".format(idx++), name = name, category = category)
        }

        listOf(
            "野球拳", "罗汉拳", "太极拳", "七伤拳", "空明拳", "黯然销魂掌", "降龙十八掌",
            "玄冥神掌", "蛤蟆功", "一阳指", "拈花指", "六脉神剑", "乾坤指",
            "独孤九剑", "太极剑法", "辟邪剑法", "金蛇剑法", "苗家剑法", "玉女剑法",
            "全真剑法", "神山剑法", "西瓜刀法", "血刀刀法", "胡家刀法", "燃木刀法",
            "打狗棒法", "伏魔杖法"
        ).forEach { add(it, "外功") }

        listOf(
            "九阳神功", "九阴真经", "易筋经", "洗髓经", "北冥神功", "小无相功",
            "紫霞神功", "神照经", "葵花宝典", "太玄经", "龙象般若功", "罗汉伏魔功"
        ).forEach { add(it, "内功") }

        listOf("凌波微步", "神行百变", "梯云纵", "飞天神行").forEach { add(it, "轻功") }

        val tasks = listOf(
            TaskItem(
                id = UUID.randomUUID().toString(), title = "写作 1000 字", type = TaskType.HABIT,
                pointReward = 30, note = "任意创作，正文满 1000 字", createdAt = now()
            ),
            TaskItem(
                id = UUID.randomUUID().toString(), title = "锻炼 30 分钟", type = TaskType.HABIT,
                pointReward = 25, note = "跑步 / 健身 / 球类均可", createdAt = now()
            ),
            TaskItem(
                id = UUID.randomUUID().toString(), title = "早起（7 点前）", type = TaskType.DAILY,
                pointReward = 15, createdAt = now()
            ),
            TaskItem(
                id = UUID.randomUUID().toString(), title = "阅读 20 页", type = TaskType.DAILY,
                pointReward = 15, createdAt = now()
            ),
            TaskItem(
                id = UUID.randomUUID().toString(), title = "背 30 个单词", type = TaskType.TODO,
                pointReward = 40, createdAt = now()
            ),
            TaskItem(
                id = UUID.randomUUID().toString(), title = "看一集电视剧", type = TaskType.REWARD,
                pointCost = 60, note = "用兑换点换取的娱乐时间", createdAt = now()
            ),
            TaskItem(
                id = UUID.randomUUID().toString(), title = "喝一杯奶茶", type = TaskType.REWARD,
                pointCost = 120, createdAt = now()
            )
        )

        // 武功秘籍不在此硬编码：由游戏内 Mod 导出真实「秘籍目录」，App 动态生成兑换项。
        val redeem = listOf(
            RedeemItem("rd_01", "游戏内银两 x1000", 50, "SILVER", amount = 1000),
            RedeemItem("rd_03", "主角历练 +200", 150, "EXP", amount = 200),
            RedeemItem("rd_04", "神兵·玄铁重剑", 800, "ITEM", gameId = 106, amount = 1)
        )

        return PlayerState(
            points = 100,
            tasks = tasks,
            martialArts = arts,
            redeemItems = redeem
        )
    }

    fun now(): Long = System.currentTimeMillis()
}