package com.traework.jygoldenfinger.game

import com.traework.jygoldenfinger.data.PlayerState
import java.io.File

/**
 * 把 App 状态导出成「游戏内 Lua Mod 可直接 load 的账本」。
 *
 * 为什么是 Lua 表而不是 JSON：游戏内 xLua 没有 JSON 解析器，但可以直接
 * `load(ledger.lua)()` 拿到一张 table，所以这里手写 Lua 表文本。
 */
object LedgerExporter {

    private fun luaQuote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> {}
                else -> sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    fun buildLua(state: PlayerState): String {
        val sb = StringBuilder()
        sb.append("-- 江湖金手指账本 · 由伴侣 App 自动生成，请勿手改\n")
        sb.append("return {\n")
        sb.append("  version = 1,\n")
        sb.append("  exportedAt = ").append(System.currentTimeMillis()).append(",\n")
        sb.append("  points = ").append(state.points).append(",\n")
        sb.append("  pointsEarnedTotal = ").append(state.pointsEarnedTotal).append(",\n")
        sb.append("  pointsSpentTotal = ").append(state.pointsSpentTotal).append(",\n")

        sb.append("  skills = {\n")
        state.martialArts.filter { it.expAllocated > 0 }.forEach { art ->
            sb.append("    { name = ").append(luaQuote(art.name))
                .append(", category = ").append(luaQuote(art.category))
                .append(", level = ").append(art.level)
                .append(", exp = ").append(art.expAllocated)
                .append(" },\n")
        }
        sb.append("  },\n")

        sb.append("  pending = {\n")
        state.commands.forEach { cmd ->
            // 每条指令带 id：游戏结算后账本只剩未成功的项，App 据此精确清掉已生效的指令
            val head = "    { id = " + luaQuote(cmd.id) + ", "
            when (cmd.type) {
                "SKILL" -> sb.append(head).append("kind = \"SKILL\", skill = ").append(luaQuote(cmd.skill))
                    .append(", level = ").append(cmd.amount).append(" },\n")
                "ITEM" -> sb.append(head).append("kind = \"ITEM\", item = ").append(cmd.itemId)
                    .append(", amount = ").append(cmd.amount).append(" },\n")
                "SILVER" -> sb.append(head).append("kind = \"SILVER\", amount = ").append(cmd.amount).append(" },\n")
                "EXP" -> sb.append(head).append("kind = \"EXP\", amount = ").append(cmd.amount).append(" },\n")
            }
        }
        sb.append("  },\n")
        sb.append("}\n")
        return sb.toString()
    }

    /** 写入所有候选交换目录，返回成功写入的文件路径 */
    fun export(state: PlayerState): List<String> {
        val text = buildLua(state)
        val written = mutableListOf<String>()
        GamePaths.exchangeDirCandidates().forEach { dir ->
            if (GamePaths.ensureDir(dir)) {
                runCatching {
                    val f = File(dir, GamePaths.LEDGER_FILE)
                    f.writeText(text)
                    written += f.absolutePath
                }
            }
        }
        return written
    }

    fun pathHint(): String =
        GamePaths.exchangeDirCandidates().joinToString(" 或 ") { "${it.path}/${GamePaths.LEDGER_FILE}" }
}