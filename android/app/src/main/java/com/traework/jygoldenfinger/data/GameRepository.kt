package com.traework.jygoldenfinger.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

class GameRepository(context: Context) {

    private val file = File(context.filesDir, "player_state.json")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): PlayerState = runCatching {
        val st = if (file.exists()) {
            json.decodeFromString(PlayerState.serializer(), file.readText())
        } else {
            Defaults.defaultState()
        }
        val migrated = migrate(st)
        if (migrated !== st) save(migrated)
        migrated
    }.getOrElse { Defaults.defaultState() }

    /**
     * 修复历史持久化数据：
     *  1) 内置兑换项刷新为最新定义（修正 gameId / kind / amount）；
     *  2) 未绑定有效物品 ID（gameId<=0）的物品兑换项移除，避免下发无效指令。
     */
    private fun migrate(s: PlayerState): PlayerState {
        val builtin = Defaults.defaultState().redeemItems.associateBy { it.id }
        var changed = false
        val fixed = s.redeemItems.mapNotNull { item ->
            val d = builtin[item.id]
            when {
                d != null -> {
                    val n = item.copy(kind = d.kind, gameId = d.gameId, amount = d.amount)
                    if (n != item) changed = true
                    n
                }
                item.kind == "ITEM" && item.gameId <= 0 -> {
                    changed = true
                    null
                }
                else -> item
            }
        }
        return if (changed) s.copy(redeemItems = fixed) else s
    }

    fun save(state: PlayerState) {
        runCatching {
            file.writeText(json.encodeToString(PlayerState.serializer(), state))
        }
    }
}