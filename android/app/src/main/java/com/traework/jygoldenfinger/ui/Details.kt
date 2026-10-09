package com.traework.jygoldenfinger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.traework.jygoldenfinger.game.GameBook
import com.traework.jygoldenfinger.game.GameItem
import com.traework.jygoldenfinger.game.SkillAttrs

/** 物品效果属性：键名 → 中文名（取自游戏 GameConst.ProItemDic） */
object GameText {

    private val ITEM_ATTR_LABELS = linkedMapOf(
        "Attack" to "攻击力", "Defence" to "防御", "Qinggong" to "轻功",
        "AddHp" to "生命", "AddMaxHp" to "最大生命",
        "AddMp" to "内力", "AddMaxMp" to "最大内力", "AddTili" to "体力",
        "Heal" to "医疗", "UsePoison" to "使毒", "DePoison" to "解毒", "AntiPoison" to "抗毒",
        "Quanzhang" to "拳掌", "Yujian" to "剑术", "Shuadao" to "刀术",
        "Qimen" to "奇门", "Anqi" to "暗器", "Wuxuechangshi" to "武学常识",
        "AddPinde" to "品德", "Zuoyouhubo" to "左右互搏",
        "AttackPoison" to "功夫带毒", "ChangePoisonLevel" to "中毒解毒"
    )

    fun itemTypeName(t: Int): String = when (t) {
        0 -> "道具"
        1 -> "装备"
        2 -> "武功秘籍"
        3 -> "消耗品"
        4 -> "暗器"
        else -> "物品"
    }

    fun damageTypeName(v: Int): String = when (v) {
        0 -> "普通"
        1 -> "吸内"
        2 -> "用毒"
        3 -> "解毒"
        4 -> "医疗"
        else -> "其他"
    }

    fun coverTypeName(v: Int): String = when (v) {
        0 -> "点攻击"
        1 -> "线攻击"
        2 -> "十字攻击"
        3 -> "面攻击"
        4 -> "菱形"
        else -> "—"
    }

    /** 物品效果行，如「攻击力 +10」 */
    fun itemAttrLines(attrs: Map<String, Int>): List<String> {
        if (attrs.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        ITEM_ATTR_LABELS.forEach { (key, label) ->
            val v = attrs[key] ?: return@forEach
            if (v != 0) out += "$label ${if (v > 0) "+" else ""}$v"
        }
        return out
    }

    /** 武功属性行 */
    fun skillAttrLines(a: SkillAttrs): List<String> {
        val out = mutableListOf<String>()
        out += "伤害类型：${damageTypeName(a.damageType)}"
        out += "攻击范围：${coverTypeName(a.coverType)}"
        if (a.mpCost > 0) out += "消耗内力：${a.mpCost}"
        if (a.poison > 0) out += "带毒：${a.poison}"
        return out
    }

    /** 武功威力行：优先显示当前等级，否则列全 */
    fun skillPowerLines(a: SkillAttrs, level: Int): List<String> {
        if (a.atkByLevel.isEmpty()) return emptyList()
        val cur = a.attackAt(level)
        val out = mutableListOf<String>()
        if (cur > 0) out += "当前威力（Lv.$level）：$cur"
        val maxLv = a.atkByLevel.size
        out += "威力成长：Lv.1 ${a.atkByLevel.first()} → Lv.$maxLv ${a.atkByLevel.last()}"
        return out
    }

    fun itemDetail(item: GameItem): InfoContent {
        val sections = mutableListOf<InfoSection>()
        val attrs = itemAttrLines(item.attrs)
        if (attrs.isNotEmpty()) sections += InfoSection("效果", attrs)
        return InfoContent(
            title = item.name,
            subtitle = "${itemTypeName(item.itemType)} · 数量 ${item.count}",
            desc = item.desc.ifBlank { null },
            sections = sections
        )
    }

    fun bookDetail(book: GameBook, learned: Boolean): InfoContent {
        val sections = mutableListOf<InfoSection>()
        val bookAttrs = itemAttrLines(book.attrs)
        if (bookAttrs.isNotEmpty()) sections += InfoSection("秘籍效果", bookAttrs)
        val sk = skillAttrLines(book.skillAttrs)
        if (sk.isNotEmpty()) sections += InfoSection("所学武功「${book.skillName}」", sk)
        val power = skillPowerLines(book.skillAttrs, 1)
        if (power.isNotEmpty()) sections += InfoSection("威力", power)
        if (book.needExp > 0) sections += InfoSection("学习门槛", listOf("所需历练：${book.needExp}"))
        return InfoContent(
            title = book.name,
            subtitle = if (learned) "已学会「${book.skillName}」" else "研读可学「${book.skillName}」",
            desc = book.desc.ifBlank { null },
            sections = sections
        )
    }

    fun skillDetail(name: String, level: Int, attrs: SkillAttrs): InfoContent {
        val sections = mutableListOf<InfoSection>()
        val a = skillAttrLines(attrs)
        if (a.isNotEmpty()) sections += InfoSection("属性", a)
        val power = skillPowerLines(attrs, level)
        if (power.isNotEmpty()) sections += InfoSection("威力", power)
        return InfoContent(
            title = name,
            subtitle = "游戏内 Lv.$level",
            desc = null,
            sections = sections
        )
    }
}

data class InfoSection(val title: String, val lines: List<String>)

data class InfoContent(
    val title: String,
    val subtitle: String? = null,
    val desc: String? = null,
    val sections: List<InfoSection> = emptyList()
)

/** 通用详情弹窗：标题 + 副标题 + 介绍 + 分组属性 */
@Composable
fun InfoDialog(content: InfoContent, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(content.title, fontWeight = FontWeight.Bold)
                content.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content.desc?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                if (content.desc == null && content.sections.isEmpty()) {
                    Text(
                        "暂无更多介绍与属性。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                content.sections.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        section.lines.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}