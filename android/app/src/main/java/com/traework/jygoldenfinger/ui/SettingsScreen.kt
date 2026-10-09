package com.traework.jygoldenfinger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.traework.jygoldenfinger.util.StoragePermission
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val gameState by vm.gameState.collectAsState()
    var hasStorage by remember { mutableStateOf(StoragePermission.hasAllFilesAccess()) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)

    // 用户去系统设置授权后返回，重新读取权限状态
    OnResumeEffect { hasStorage = StoragePermission.hasAllFilesAccess() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { QuickStartCard() }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("金手指账本", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        vm.ledgerHint(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "待下发指令：${state.commands.size} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { vm.exportLedgerNow() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("立即导出账本", color = MaterialTheme.colorScheme.onPrimary) }
                        OutlinedButton(onClick = { confirmClear = true }) { Text("清空指令") }
                    }
                    if (!hasStorage) {
                        OutlinedButton(
                            onClick = { StoragePermission.requestAllFilesAccess(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("授予「所有文件访问权限」") }
                    }
                }
            }
        }

        item { SectionHeader("游戏内进度") }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val gs = gameState
                    val player = gs?.player
                    if (player == null) {
                        Text(
                            "尚未读取到游戏数据。进入游戏并读档后，游戏内 Mod 会导出主角面板，这里即可看到。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val slot = if (gs.archiveIndex >= 0) "存档 ${gs.archiveIndex + 1} · " else ""
                        Text(
                            "$slot${player.name} · Lv.${player.level}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "气血 ${player.hp}/${player.maxHp} · 内力 ${player.mp}/${player.maxMp}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "攻击 ${player.attack} · 防御 ${player.defence} · 轻功 ${player.qinggong}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "银两 ${gameState?.money ?: 0} · 武功 ${player.wugongs.size} 门 · 物品 ${player.items.size} 种",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { vm.refreshGameState() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("刷新游戏数据") }
                }
            }
        }

        item { SectionHeader("最近记录") }

        if (state.log.isEmpty()) {
            item {
                Text("暂无记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        items(state.log.take(40)) { entry ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                    Text(
                        fmt.format(Date(entry.time)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "  ${entry.text}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (entry.deltaPoint != 0) {
                        Text(
                            " ${if (entry.deltaPoint > 0) "+" else ""}${entry.deltaPoint}兑换点",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (entry.deltaPoint > 0) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("重置全部数据") }
        }

        item {
            Text(
                "江湖金手指 v1.3 · 伴侣 App\n现实任务 → 兑换点 → 武功。把现实中的坚持，练成江湖里的绝学。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清空待下发指令",
            text = "将清空当前「待下发指令」队列（共 ${state.commands.size} 条）。若游戏尚未读取这些指令，它们将不会生效。",
            confirmText = "清空",
            destructive = true,
            onDismiss = { confirmClear = false },
            onConfirm = { vm.clearAppliedCommands() }
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "重置全部数据",
            text = "将清空本地全部任务、兑换点、武功分配与待下发指令，恢复到初始状态。此操作不可恢复。",
            confirmText = "重置",
            destructive = true,
            onDismiss = { confirmReset = false },
            onConfirm = { vm.resetAll() }
        )
    }
}

@Composable
private fun QuickStartCard() {
    val steps = listOf(
        "① 安装并启动游戏：先在「Mod」页授予「所有文件访问权限」，再导入金手指 Mod 压缩包。",
        "② 进游戏读档：进入游戏并载入任意存档，游戏内 Mod 会把角色面板、包裹、已学武功导出到 App。",
        "③ 回到 App 设定任务：在「任务」页新增现实目标（习惯/日常/待办/奖励），完成即可赚取兑换点。",
        "④ 兑换与分配：在「金手指」页兑换武功秘籍或游戏资源，并把兑换点分配给已学会的武功。",
        "⑤ 生效：再次进游戏读档，账本中的指令即被结算，武功升级、银两与物品到账。"
    )
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "快速上手",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            steps.forEach { step ->
                Text(
                    step,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}