package com.traework.jygoldenfinger.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class GameSkill(
    val key: Int = 0,
    val level: Int = 0,
    val name: String = ""
)

@Serializable
data class GameItem(
    val itemId: Int = 0,
    val count: Int = 0,
    val name: String = "",
    val isBook: Boolean = false
)

/** 游戏内「武功秘籍」目录（ItemType==2），用于生成兑换列表 */
@Serializable
data class GameBook(
    val itemId: Int = 0,
    val name: String = "",
    val skillKey: Int = 0,
    val skillName: String = "",
    /** 学习该秘籍所需的历练/悟性门槛，用于推算兑换点价格 */
    val needExp: Int = 0
)

@Serializable
data class GameRole(
    val key: Int = 0,
    val name: String = "",
    val level: Int = 0,
    val exp: Int = 0,
    val hp: Int = 0,
    val maxHp: Int = 0,
    val mp: Int = 0,
    val maxMp: Int = 0,
    val attack: Int = 0,
    val qinggong: Int = 0,
    val defence: Int = 0,
    val quanzhang: Int = 0,
    val yujian: Int = 0,
    val shuadao: Int = 0,
    val qimen: Int = 0,
    val anqi: Int = 0,
    @SerialName("wuxuechangshi") val wuxueChangshi: Int = 0,
    val pinde: Int = 0,
    val shengwang: Int = 0,
    @SerialName("iq") val iq: Int = 0,
    val wugongs: List<GameSkill> = emptyList(),
    val items: List<GameItem> = emptyList()
)

@Serializable
data class GameState(
    val version: Int = 1,
    val modId: String = "",
    /** 当前读档槽位；-1 表示未知 */
    val archiveIndex: Int = -1,
    val money: Long = 0L,
    val team: List<Int> = emptyList(),
    val books: List<GameBook> = emptyList(),
    val player: GameRole? = null
)

/** 读取游戏内 Lua Mod 回写的角色面板 */
object GameStateReader {

    private val json = Json { ignoreUnknownKeys = true }

    fun read(): GameState? {
        GamePaths.exchangeDirCandidates().forEach { dir ->
            val f = File(dir, GamePaths.STATE_FILE)
            if (f.isFile) {
                runCatching {
                    return json.decodeFromString(GameState.serializer(), f.readText())
                }
            }
        }
        return null
    }

    private val idRe = Regex("""id\s*=\s*"([^"]+)"""")

    /**
     * 账本里仍待结算的指令 id 集合。
     * 游戏 Mod 结算后会重写 ledger.lua，只保留未成功的项，因此账本中已消失的 id
     * 即表示「已生效」。返回 null 表示账本不可读（尚未导出过），此时不做任何清理。
     */
    fun pendingCommandIds(): Set<String>? {
        GamePaths.exchangeDirCandidates().forEach { dir ->
            val f = File(dir, GamePaths.LEDGER_FILE)
            if (f.isFile) {
                val text = runCatching { f.readText() }.getOrNull() ?: return@forEach
                val idx = text.indexOf("pending")
                if (idx < 0) return@forEach
                return idRe.findAll(text.substring(idx)).map { it.groupValues[1] }.toSet()
            }
        }
        return null
    }
}