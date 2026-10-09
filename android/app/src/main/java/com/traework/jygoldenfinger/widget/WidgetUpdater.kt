package com.traework.jygoldenfinger.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.traework.jygoldenfinger.MainActivity
import com.traework.jygoldenfinger.R
import com.traework.jygoldenfinger.data.Defaults
import com.traework.jygoldenfinger.data.PlayerState
import com.traework.jygoldenfinger.data.TaskItem
import com.traework.jygoldenfinger.data.TaskType
import com.traework.jygoldenfinger.game.GameState
import com.traework.jygoldenfinger.game.GameStateReader
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 桌面小组件读取的数据快照：App 本地状态 + 游戏内角色面板 */
data class WidgetData(val state: PlayerState, val game: GameState?)

object WidgetLoader {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(context: Context): WidgetData {
        val state = runCatching {
            val f = File(context.filesDir, "player_state.json")
            if (f.isFile) json.decodeFromString(PlayerState.serializer(), f.readText())
            else Defaults.defaultState()
        }.getOrElse { Defaults.defaultState() }
        val game = runCatching { GameStateReader.read() }.getOrNull()
        return WidgetData(state, game)
    }
}

/**
 * 统一刷新所有金手指小组件。App 内数据变化或游戏数据同步后调用，
 * 小组件进程与 App 同进程，直接读取持久化文件即可。
 */
object WidgetUpdater {

    private const val MAX_ROWS = 6

    private val ROW_IDS = intArrayOf(
        R.id.row_1, R.id.row_2, R.id.row_3, R.id.row_4, R.id.row_5, R.id.row_6
    )

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context) ?: return
        val data = WidgetLoader.load(context)
        push(context, mgr, PointsWidgetProvider::class.java, R.layout.widget_points) { v -> bindPoints(v, data) }
        push(context, mgr, TasksWidgetProvider::class.java, R.layout.widget_tasks) { v -> bindTasks(v, data) }
        push(context, mgr, StatsWidgetProvider::class.java, R.layout.widget_stats) { v -> bindStats(v, data) }
        push(context, mgr, ArtsWidgetProvider::class.java, R.layout.widget_arts) { v -> bindArts(v, data) }
        push(context, mgr, ItemsWidgetProvider::class.java, R.layout.widget_items) { v -> bindItems(v, data) }
    }

    private fun push(
        context: Context,
        mgr: AppWidgetManager,
        cls: Class<*>,
        layout: Int,
        bind: (RemoteViews) -> Unit
    ) {
        val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
        if (ids.isEmpty()) return
        val pi = openApp(context)
        ids.forEach { id ->
            val v = RemoteViews(context.packageName, layout)
            bind(v)
            v.setOnClickPendingIntent(R.id.widget_root, pi)
            mgr.updateAppWidget(id, v)
        }
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun bindRows(v: RemoteViews, lines: List<String>) {
        ROW_IDS.forEachIndexed { i, id ->
            if (i < lines.size) {
                v.setTextViewText(id, lines[i])
                v.setViewVisibility(id, View.VISIBLE)
            } else {
                v.setViewVisibility(id, View.GONE)
            }
        }
    }

    // ---------------- 兑换点 ----------------

    private fun bindPoints(v: RemoteViews, d: WidgetData) {
        val s = d.state
        v.setTextViewText(R.id.tv_points, s.points.toString())
        v.setTextViewText(R.id.tv_sub1, "累计获得 ${s.pointsEarnedTotal} · 累计消耗 ${s.pointsSpentTotal}")
        v.setTextViewText(R.id.tv_sub2, "已投入武功 ${s.pointsInvested} · 待下发 ${s.commands.size}")
    }

    // ---------------- 任务 ----------------

    private fun bindTasks(v: RemoteViews, d: WidgetData) {
        val tasks = d.state.tasks
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        v.setTextViewText(R.id.tv_title, "任务")
        val undone = tasks.count { !isDone(it, today) }
        v.setTextViewText(R.id.tv_sub, "共 ${tasks.size} 项 · 待完成 $undone 项")
        val lines = tasks.sortedBy { rank(it, today) }.take(MAX_ROWS).map { t ->
            val badge = when (t.type) {
                TaskType.HABIT -> "习"
                TaskType.DAILY -> "常"
                TaskType.TODO -> "办"
                TaskType.REWARD -> "奖"
            }
            val delta = if (t.type == TaskType.REWARD) "-${t.pointCost}" else "+${t.pointReward}"
            val mark = if (isDone(t, today)) "✓ " else ""
            "$mark[$badge] ${t.title}　$delta"
        }
        bindRows(v, lines)
    }

    private fun isDone(t: TaskItem, today: String): Boolean = when (t.type) {
        TaskType.DAILY -> t.lastDailyDate == today
        TaskType.TODO -> t.todoDone
        else -> false
    }

    private fun rank(t: TaskItem, today: String): Int {
        val done = if (isDone(t, today)) 1 else 0
        return done * 10 + when (t.type) {
            TaskType.DAILY -> 0
            TaskType.HABIT -> 1
            TaskType.TODO -> 2
            TaskType.REWARD -> 3
        }
    }

    // ---------------- 角色属性 ----------------

    private fun bindStats(v: RemoteViews, d: WidgetData) {
        val g = d.game
        val p = g?.player
        if (p == null) {
            v.setTextViewText(R.id.tv_title, "角色属性")
            v.setTextViewText(R.id.tv_sub, "进入游戏读档后自动同步")
            bindRows(v, emptyList())
            return
        }
        v.setTextViewText(R.id.tv_title, "${p.name} · Lv.${p.level}")
        val slot = if (g.archiveIndex >= 0) "存档 ${g.archiveIndex + 1}" else "当前存档"
        v.setTextViewText(R.id.tv_sub, "$slot · 银两 ${g.money} · 历练 ${p.exp}")
        bindRows(
            v,
            listOf(
                "气血 ${p.hp}/${p.maxHp}　内力 ${p.mp}/${p.maxMp}",
                "攻击 ${p.attack}　防御 ${p.defence}　轻功 ${p.qinggong}",
                "拳掌 ${p.quanzhang}　御剑 ${p.yujian}　耍刀 ${p.shuadao}",
                "奇门 ${p.qimen}　暗器 ${p.anqi}　武学 ${p.wuxueChangshi}",
                "品德 ${p.pinde}　声望 ${p.shengwang}　资质 ${p.iq}",
                "已学武功 ${p.wugongs.size} 门 · 包裹 ${p.items.size} 种"
            )
        )
    }

    // ---------------- 已学武功 ----------------

    private fun bindArts(v: RemoteViews, d: WidgetData) {
        val w = d.game?.player?.wugongs.orEmpty()
        v.setTextViewText(R.id.tv_title, "已学武功")
        if (w.isEmpty()) {
            v.setTextViewText(R.id.tv_sub, "进入游戏读档后自动同步")
            bindRows(v, emptyList())
            return
        }
        v.setTextViewText(R.id.tv_sub, "共 ${w.size} 门")
        bindRows(v, w.take(MAX_ROWS).map { "· ${it.name}　Lv.${it.humanLevel}" })
    }

    // ---------------- 包裹 ----------------

    private fun bindItems(v: RemoteViews, d: WidgetData) {
        val items = d.game?.player?.items.orEmpty()
        v.setTextViewText(R.id.tv_title, "包裹")
        if (items.isEmpty()) {
            v.setTextViewText(R.id.tv_sub, "进入游戏读档后自动同步")
            bindRows(v, emptyList())
            return
        }
        v.setTextViewText(R.id.tv_sub, "共 ${items.size} 种")
        bindRows(
            v,
            items.take(MAX_ROWS).map { "· ${it.name}${if (it.isBook) "【秘籍】" else ""} x${it.count}" }
        )
    }
}