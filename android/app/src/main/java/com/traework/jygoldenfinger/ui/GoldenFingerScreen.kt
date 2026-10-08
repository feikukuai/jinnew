package com.traework.jygoldenfinger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.traework.jygoldenfinger.data.MartialArt
import com.traework.jygoldenfinger.data.PlayerState
import com.traework.jygoldenfinger.data.SkillCurve
import com.traework.jygoldenfinger.game.GameState

private val CATEGORIES = listOf("外功", "内功", "轻功", "绝学")

@Composable
fun GoldenFingerScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val gameState by vm.gameState.collectAsState()
    var category by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    // 主角在游戏内已学会的武功：名 → 游戏内等级
    val learned = remember(gameState?.player?.wugongs) {
        gameState?.player?.wugongs?.associate { it.name to it.level } ?: emptyMap()
    }

    val visible = remember(state.martialArts, category, learned) {
        state.martialArts
            .filter { learned.containsKey(it.name) }
            .filter { category == null || it.category == category }
            .sortedWith(compareByDescending<MartialArt> { it.level }.thenBy { it.name })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { PoolHeader(state) }
            item { GameProgressCard(gameState, vm) }

            item { SectionHeader("可分配武功（主角已学会）") }
            item { CategoryRow(category) { category = it } }
            item {
                Text(
                    "兑换点可随时、任意次分配，不必一次用完；只能分配给主角已在游戏内学会的武功。分配后写入账本，进游戏读档即生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when {
                gameState?.player == null -> item {
                    HintText("尚未读取到游戏数据。进入游戏并读档后，这里会列出主角已学会的武功，并显示游戏内等级。")
                }
                visible.isEmpty() -> item {
                    HintText("主角当前还没有学会任何武功。可先到「任务」页兑换武功秘籍，进游戏研读学会后再回来分配。")
                }
                else -> items(visible, key = { it.id }) { art ->
                    ArtCard(art, learned[art.name] ?: 0, state.points, vm)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.weight(1f)) {
                        Text("添加武功")
                    }
                    OutlinedButton(onClick = { vm.restoreDefaultArts() }, modifier = Modifier.weight(1f)) {
                        Text("恢复默认库")
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("添加武功") }
        )
    }

    if (showAdd) {
        AddArtDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, cat ->
                vm.addMartialArt(name, cat)
                showAdd = false
            }
        )
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
private fun PoolHeader(state: PlayerState) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("可分配兑换点", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                state.points.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "累计获得 ${state.pointsEarnedTotal} · 已投入武功 ${state.pointsInvested}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun GameProgressCard(gameState: GameState?, vm: AppViewModel) {
    val player = gameState?.player
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "游戏内进度",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { vm.refreshGameState() }) { Text("刷新") }
            }

            if (player == null) {
                Text(
                    "进入游戏并读档后，游戏内 Mod 会把当前存档的角色面板、包裹与已学武功导出到这里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val slot = if ((gameState.archiveIndex) >= 0) "存档 ${gameState.archiveIndex + 1}" else "当前存档"
            Text(
                "$slot · ${player.name} · Lv.${player.level}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "气血 ${player.hp}/${player.maxHp} · 内力 ${player.mp}/${player.maxMp} · 银两 ${gameState.money}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                "已学武功 ${player.wugongs.size} 门",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (player.wugongs.isEmpty()) {
                Text("（暂无）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                player.wugongs.forEach { w ->
                    Text(
                        "· ${w.name}　Lv.${w.level / 100}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                "包裹 ${player.items.size} 种",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (player.items.isEmpty()) {
                Text("（空）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                player.items.forEach { it ->
                    Text(
                        "· ${it.name}${if (it.isBook) "【秘籍】" else ""} x${it.count}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("全部") })
        CATEGORIES.forEach { c ->
            FilterChip(selected = selected == c, onClick = { onSelect(c) }, label = { Text(c) })
        }
    }
}

@Composable
private fun ArtCard(art: MartialArt, gameLevel: Int, pool: Long, vm: AppViewModel) {
    val maxed = art.level >= SkillCurve.MAX_LEVEL
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(art.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        AssistChip(onClick = {}, label = { Text(art.category) })
                    }
                    Text(
                        if (maxed) "已满级 · 累计投入 ${art.expAllocated} 兑换点"
                        else "已投入 ${art.expAllocated} 兑换点 · 距下一级还需 ${SkillCurve.expToNextLevel(art.expAllocated)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "游戏内 Lv.$gameLevel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    "Lv.${art.level}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = { vm.deleteMartialArt(art.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.outline)
                }
            }

            LinearProgressIndicator(
                progress = { SkillCurve.progressInLevel(art.expAllocated) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.allocate(art.id, 50) }, enabled = pool > 0 && !maxed) { Text("+50") }
                OutlinedButton(onClick = { vm.allocate(art.id, 100) }, enabled = pool > 0 && !maxed) { Text("+100") }
                OutlinedButton(
                    onClick = { vm.allocate(art.id, Int.MAX_VALUE) },
                    enabled = pool > 0 && !maxed
                ) { Text("全部投入") }
            }
        }
    }
}

@Composable
private fun AddArtDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("外功") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加武功") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("武功名称，如「降龙十八掌」") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("分类", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIES.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, category) }) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}