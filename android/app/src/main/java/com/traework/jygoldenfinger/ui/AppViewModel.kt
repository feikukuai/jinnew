package com.traework.jygoldenfinger.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.traework.jygoldenfinger.data.LogEntry
import com.traework.jygoldenfinger.data.MartialArt
import com.traework.jygoldenfinger.data.PlayerState
import com.traework.jygoldenfinger.data.RedeemItem
import com.traework.jygoldenfinger.data.SkillCurve
import com.traework.jygoldenfinger.data.TaskItem
import com.traework.jygoldenfinger.data.TaskType
import com.traework.jygoldenfinger.data.GameRepository
import com.traework.jygoldenfinger.data.Defaults
import com.traework.jygoldenfinger.data.LedgerCommand
import com.traework.jygoldenfinger.game.GameBook
import com.traework.jygoldenfinger.game.GameState
import com.traework.jygoldenfinger.game.GameStateReader
import com.traework.jygoldenfinger.game.LedgerExporter
import com.traework.jygoldenfinger.game.ModEntry
import com.traework.jygoldenfinger.game.ModManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = GameRepository(app)

    private val _state = MutableStateFlow(repo.load())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _mods = MutableStateFlow<List<ModEntry>>(emptyList())
    val mods: StateFlow<List<ModEntry>> = _mods.asStateFlow()

    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        refreshMods()
        refreshGameState()
    }

    fun clearMessage() {
        _message.value = null
    }

    fun notify(msg: String) {
        _message.value = msg
    }

    private fun say(msg: String) {
        _message.value = msg
    }

    private fun update(block: (PlayerState) -> PlayerState) {
        val next = block(_state.value)
        _state.value = next
        repo.save(next)
    }

    private fun log(text: String, deltaPoint: Int = 0) =
        LogEntry(System.currentTimeMillis(), text, deltaPoint)

    private fun autoExport() {
        runCatching { LedgerExporter.export(_state.value) }
    }

    // ==================== 任务 ====================

    fun completeTask(task: TaskItem) {
        val today = dayFmt.format(Date())
        when (task.type) {
            TaskType.REWARD -> redeemRewardTask(task)

            TaskType.DAILY -> {
                if (task.lastDailyDate == today) {
                    say("「${task.title}」今日已打卡")
                    return
                }
                update { st ->
                    st.copy(
                        tasks = st.tasks.map {
                            if (it.id == task.id)
                                it.copy(
                                    lastDailyDate = today,
                                    doneCount = it.doneCount + 1,
                                    streak = it.streak + 1,
                                    lastDoneAt = System.currentTimeMillis()
                                )
                            else it
                        },
                        points = st.points + task.pointReward,
                        pointsEarnedTotal = st.pointsEarnedTotal + task.pointReward,
                        log = (listOf(log("完成日常「${task.title}」", task.pointReward)) + st.log).take(300)
                    )
                }
                say("日常「${task.title}」 +${task.pointReward} 兑换点")
                autoExport()
            }

            TaskType.TODO -> {
                if (task.todoDone) {
                    say("「${task.title}」已完成")
                    return
                }
                update { st ->
                    st.copy(
                        tasks = st.tasks.map {
                            if (it.id == task.id) it.copy(todoDone = true, doneCount = it.doneCount + 1, lastDoneAt = System.currentTimeMillis())
                            else it
                        },
                        points = st.points + task.pointReward,
                        pointsEarnedTotal = st.pointsEarnedTotal + task.pointReward,
                        log = (listOf(log("完成待办「${task.title}」", task.pointReward)) + st.log).take(300)
                    )
                }
                say("待办「${task.title}」 +${task.pointReward} 兑换点")
                autoExport()
            }

            TaskType.HABIT -> {
                update { st ->
                    st.copy(
                        tasks = st.tasks.map {
                            if (it.id == task.id)
                                it.copy(
                                    doneCount = it.doneCount + 1,
                                    streak = it.streak + 1,
                                    lastDoneAt = System.currentTimeMillis()
                                )
                            else it
                        },
                        points = st.points + task.pointReward,
                        pointsEarnedTotal = st.pointsEarnedTotal + task.pointReward,
                        log = (listOf(log("完成习惯「${task.title}」", task.pointReward)) + st.log).take(300)
                    )
                }
                say("习惯「${task.title}」 +${task.pointReward} 兑换点")
                autoExport()
            }
        }
    }

    /** 习惯类撤销一次打卡，回收已发放的兑换点 */
    fun undoHabit(task: TaskItem) {
        if (task.doneCount <= 0) {
            say("「${task.title}」还没有打卡记录")
            return
        }
        update { st ->
            st.copy(
                tasks = st.tasks.map {
                    if (it.id == task.id)
                        it.copy(
                            doneCount = (it.doneCount - 1).coerceAtLeast(0),
                            streak = (it.streak - 1).coerceAtLeast(0)
                        )
                    else it
                },
                points = (st.points - task.pointReward).coerceAtLeast(0),
                pointsEarnedTotal = (st.pointsEarnedTotal - task.pointReward).coerceAtLeast(0),
                log = (listOf(log("撤销习惯「${task.title}」", -task.pointReward)) + st.log).take(300)
            )
        }
        say("已撤销「${task.title}」")
        autoExport()
    }

    private fun redeemRewardTask(task: TaskItem) {
        val cost = task.pointCost
        if (_state.value.points < cost) {
            say("兑换点不足，还差 ${cost - _state.value.points}")
            return
        }
        update { st ->
            st.copy(
                points = st.points - cost,
                pointsSpentTotal = st.pointsSpentTotal + cost,
                tasks = st.tasks.map { if (it.id == task.id) it.copy(doneCount = it.doneCount + 1, lastDoneAt = System.currentTimeMillis()) else it },
                log = (listOf(log("兑换奖励「${task.title}」", -cost)) + st.log).take(300)
            )
        }
        say("已兑换「${task.title}」，花费 $cost 兑换点")
    }

    fun addTask(title: String, type: TaskType, reward: Int, cost: Int, note: String) {
        if (title.isBlank()) {
            say("请输入任务名称")
            return
        }
        val task = TaskItem(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            type = type,
            pointReward = reward.coerceAtLeast(0),
            pointCost = cost.coerceAtLeast(0),
            note = note.trim(),
            createdAt = System.currentTimeMillis()
        )
        update { it.copy(tasks = it.tasks + task) }
        say("已新增${type.label}「${task.title}」")
    }

    fun updateTask(task: TaskItem) {
        update { st -> st.copy(tasks = st.tasks.map { if (it.id == task.id) task else it }) }
    }

    fun deleteTask(id: String) {
        update { st -> st.copy(tasks = st.tasks.filterNot { it.id == id }) }
    }

    fun resetTodo(task: TaskItem) {
        update { st ->
            st.copy(tasks = st.tasks.map { if (it.id == task.id) it.copy(todoDone = false) else it })
        }
    }

    // ==================== 金手指：兑换点分配 ====================

    fun allocate(artId: String, amount: Int) {
        val st = _state.value
        val art = st.martialArts.firstOrNull { it.id == artId } ?: return
        val cap = SkillCurve.totalExpForLevel(SkillCurve.MAX_LEVEL)
        val room = (cap - art.expAllocated).coerceAtLeast(0)
        if (room <= 0) {
            say("「${art.name}」已满级")
            return
        }
        val give = minOf(amount.toLong(), st.points, room.toLong()).toInt()
        if (give <= 0) {
            say("兑换点不足（当前 ${st.points}）")
            return
        }
        val beforeLevel = art.level
        val newExp = art.expAllocated + give
        val afterLevel = SkillCurve.levelForExp(newExp)
        val cmd = LedgerCommand(
            id = UUID.randomUUID().toString(),
            type = "SKILL",
            skill = art.name,
            amount = afterLevel,
            time = System.currentTimeMillis()
        )
        update { s ->
            s.copy(
                martialArts = s.martialArts.map { if (it.id == artId) it.copy(expAllocated = newExp) else it },
                points = s.points - give,
                pointsSpentTotal = s.pointsSpentTotal + give,
                commands = s.commands + cmd,
                log = (listOf(log("分配 $give 兑换点 → 「${art.name}」")) + s.log).take(300)
            )
        }
        if (afterLevel > beforeLevel) {
            say("「${art.name}」突破！Lv.$beforeLevel → Lv.$afterLevel")
        } else {
            say("「${art.name}」+$give 兑换点 · Lv.$afterLevel")
        }
        autoExport()
    }

    fun addMartialArt(name: String, category: String) {
        if (name.isBlank()) {
            say("请输入武功名称")
            return
        }
        if (_state.value.martialArts.any { it.name == name.trim() }) {
            say("「${name.trim()}」已存在")
            return
        }
        update { st ->
            st.copy(martialArts = st.martialArts + MartialArt(UUID.randomUUID().toString(), name.trim(), category))
        }
        say("已添加武功「${name.trim()}」")
    }

    fun deleteMartialArt(id: String) {
        update { st -> st.copy(martialArts = st.martialArts.filterNot { it.id == id }) }
    }

    fun restoreDefaultArts() {
        update { st -> st.copy(martialArts = Defaults.defaultState().martialArts) }
        say("已恢复默认武功库")
    }

    // ==================== 兑换游戏资源 ====================

    fun redeem(item: RedeemItem) {
        if (item.kind == "ITEM" && item.gameId <= 0) {
            say("「${item.name}」未绑定有效的游戏物品 ID，无法兑换")
            return
        }
        val cost = item.pointCost
        if (_state.value.points < cost) {
            say("兑换点不足，还差 ${cost - _state.value.points}")
            return
        }
        val cmd = LedgerCommand(
            id = UUID.randomUUID().toString(),
            type = when (item.kind) {
                "SILVER" -> "SILVER"
                "EXP" -> "EXP"
                else -> "ITEM"
            },
            skill = item.name,
            itemId = item.gameId,
            amount = item.amount,
            time = System.currentTimeMillis()
        )
        update { st ->
            st.copy(
                points = st.points - cost,
                pointsSpentTotal = st.pointsSpentTotal + cost,
                commands = st.commands + cmd,
                log = (listOf(log("兑换「${item.name}」", -cost)) + st.log).take(300)
            )
        }
        say("已兑换「${item.name}」，进入游戏后生效")
        autoExport()
    }

    fun addRedeemItem(name: String, cost: Int, kind: String, amount: Int, note: String) {
        if (name.isBlank()) {
            say("请输入资源名称")
            return
        }
        update { st ->
            st.copy(
                redeemItems = st.redeemItems + RedeemItem(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    pointCost = cost.coerceAtLeast(0),
                    kind = kind,
                    amount = amount.coerceAtLeast(1),
                    note = note.trim()
                )
            )
        }
        say("已添加兑换项「${name.trim()}」")
    }

    fun deleteRedeemItem(id: String) {
        update { st -> st.copy(redeemItems = st.redeemItems.filterNot { it.id == id }) }
    }

    /** 秘籍兑换价格：按学习门槛推算，最低 80 兑换点 */
    fun bookCost(book: GameBook): Int = (book.needExp / 2).coerceAtLeast(80)

    /** 兑换武功秘籍：下发物品指令，进游戏后在包裹中研读即学会对应武功 */
    fun redeemBook(book: GameBook) {
        val cost = bookCost(book)
        if (_state.value.points < cost) {
            say("兑换点不足，还差 ${cost - _state.value.points}")
            return
        }
        val cmd = LedgerCommand(
            id = UUID.randomUUID().toString(),
            type = "ITEM",
            skill = book.skillName,
            itemId = book.itemId,
            amount = 1,
            time = System.currentTimeMillis()
        )
        update { st ->
            st.copy(
                points = st.points - cost,
                pointsSpentTotal = st.pointsSpentTotal + cost,
                commands = st.commands + cmd,
                log = (listOf(log("兑换秘籍《${book.name}》", -cost)) + st.log).take(300)
            )
        }
        say("已兑换秘籍《${book.name}》，进游戏研读即可学会「${book.skillName}」")
        autoExport()
    }

    fun clearAppliedCommands() {
        update { st -> st.copy(commands = emptyList()) }
        say("已清空待下发指令")
    }

    // ==================== Mod 管理 ====================

    fun refreshMods() {
        _mods.value = runCatching { ModManager.scanInstalled() }.getOrDefault(emptyList())
    }

    fun installMod(uri: Uri) {
        ModManager.installFromZip(getApplication(), uri)
            .onSuccess {
                refreshMods()
                say("已安装 Mod《${it.name}》v${it.version} → ${it.dir.parentFile?.name}/${it.dir.name}")
            }
            .onFailure { say("安装失败：${it.message}") }
    }

    fun removeMod(entry: ModEntry) {
        if (ModManager.deleteMod(entry)) {
            refreshMods()
            say("已删除 Mod《${entry.name}》")
        } else {
            say("删除失败：${entry.dir.path}")
        }
    }

    // ==================== 账本与数据 ====================

    fun exportLedgerNow() {
        val files = LedgerExporter.export(_state.value)
        if (files.isEmpty()) {
            say("导出失败：请先授予「所有文件访问权限」")
        } else {
            say("账本已导出：${files.first()}")
        }
    }

    fun refreshGameState() {
        val gs = runCatching { GameStateReader.read() }.getOrNull()
        _gameState.value = gs
        syncConsumedCommands()
        // 把游戏内已学会的武功并入本地库，使其可分配兑换点
        val learned = gs?.player?.wugongs?.map { it.name }?.filter { it.isNotBlank() } ?: return
        update { st ->
            val missing = learned.filter { name -> st.martialArts.none { it.name == name } }
            if (missing.isEmpty()) st
            else st.copy(
                martialArts = st.martialArts + missing.map { name ->
                    MartialArt(id = "gf_$name", name = name, category = "外功")
                }
            )
        }
    }

    /**
     * 账本里已不存在的指令 id 表示游戏已结算生效，自动从「待下发」队列移除，
     * 避免游戏已改数据、App 却一直显示「待下发指令」。
     */
    private fun syncConsumedCommands() {
        val remaining = runCatching { GameStateReader.pendingCommandIds() }.getOrNull() ?: return
        update { st ->
            val kept = st.commands.filter { it.id in remaining }
            if (kept.size == st.commands.size) st else st.copy(commands = kept)
        }
    }

    fun ledgerHint(): String = LedgerExporter.pathHint()

    fun resetAll() {
        val fresh = Defaults.defaultState()
        _state.value = fresh
        repo.save(fresh)
        autoExport()
        say("已重置全部数据")
    }
}