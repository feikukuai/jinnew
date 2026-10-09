package com.traework.jygoldenfinger.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.traework.jygoldenfinger.data.RedeemItem
import com.traework.jygoldenfinger.data.SkillCurve
import com.traework.jygoldenfinger.game.GameBook
import com.traework.jygoldenfinger.game.GameItem
import com.traework.jygoldenfinger.game.GameSkill
import com.traework.jygoldenfinger.game.GameState
import com.traework.jygoldenfinger.game.SkillAttrs

private val CATEGORIES = listOf("外功", "内功", "轻功", "绝学")
private const val TAB_REDEEM = 0
private const val TAB_ALLOCATE = 1

@Composable
fun GoldenFingerScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val gameState by vm.gameState.collectAsState()
    var tab by remember { mutableStateOf(TAB_REDEEM) }
    var category by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var bookQuery by remember { mutableStateOf("") }
    var bookExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDeleteArt by remember { mutableStateOf<MartialArt?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<InfoContent?>(null) }

    // 主角在游戏内已学会的武功：名 → 游戏内武功（含等级与属性）
    val learned = remember(gameState?.player?.wugongs) {
        gameState?.player?.wugongs?.associate { it.name to it } ?: emptyMap()
    }
    val learnedNames = remember(learned) { learned.keys }

    val keyword = query.trim()
    val visible = remember(state.martialArts, category, learned, keyword) {
        state.martialArts
            .filter { learned.containsKey(it.name) }
            .filter { category == null || it.category == category }
            .filter { keyword.isEmpty() || it.name.contains(keyword, ignoreCase = true) }
            .sortedWith(compareByDescending<MartialArt> { it.level }.thenBy { it.name })
    }

    val allBooks = gameState?.books.orEmpty()
    val bookKeyword = bookQuery.trim()
    val books = remember(allBooks, bookKeyword) {
        if (bookKeyword.isEmpty()) allBooks
        else allBooks.filter {
            it.name.contains(bookKeyword, ignoreCase = true) ||
                it.skillName.contains(bookKeyword, ignoreCase = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { PoolHeader(state) }

            // ── 主角状态（游戏内只读面板，可折叠；两个页签共享）──
            item { SectionHeader("主角状态", hint = "游戏内实时同步") }
            item {
                GameProgressCard(
                    gameState, vm,
                    expanded = statusExpanded,
                    onToggle = { statusExpanded = !statusExpanded },
                    onSkill = { info = GameText.skillDetail(it.name, it.humanLevel, it.attrs) },
                    onItem = { info = GameText.itemDetail(it) }
                )
            }

            // ── 页签：兑换（花兑换点） / 分配武功（投兑换点）──
            item {
                TabRow(
                    selectedTabIndex = tab,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Tab(
                        selected = tab == TAB_REDEEM,
                        onClick = { tab = TAB_REDEEM },
                        text = { Text("兑换") }
                    )
                    Tab(
                        selected = tab == TAB_ALLOCATE,
                        onClick = { tab = TAB_ALLOCATE },
                        text = { Text("分配武功") }
                    )
                }
            }

            if (tab == TAB_REDEEM) {
                // ── 兑换：武功秘籍 ──
                item { SectionHeader("兑换武功秘籍", hint = "兑换后进游戏研读即学会") }
                if (allBooks.isEmpty()) {
                    item {
                        HintText("进入游戏读档后，这里会列出可兑换的武功秘籍（读取游戏内真实秘籍目录）。兑换后到游戏包裹中研读即可学会。")
                    }
                } else {
                    item { SearchField(bookQuery, "搜索秘籍 / 武功名称") { bookQuery = it } }
                    if (books.isEmpty()) {
                        item { HintText("没有匹配「$bookKeyword」的秘籍，换个关键词试试。") }
                    } else {
                        val shown = if (bookExpanded) books else books.take(6)
                        items(shown, key = { "book_${it.itemId}" }) { book ->
                            BookCard(book, learnedNames, vm) {
                                info = GameText.bookDetail(book, learnedNames.contains(book.skillName))
                            }
                        }
                        if (books.size > 6) {
                            item {
                                OutlinedButton(
                                    onClick = { bookExpanded = !bookExpanded },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (bookExpanded) "收起" else "展开全部 ${books.size} 本秘籍") }
                            }
                        }
                    }
                }

                // ── 兑换：游戏资源 ──
                item { SectionHeader("兑换游戏资源", hint = "银两 / 历练 / 神兵") }
                item { RedeemSection(state, vm) }
            } else {
                // ── 分配：兑换点投入武功 ──
                item { SectionHeader("分配武功", hint = "只能分配给主角已学会的武功") }
                item { CategoryRow(category) { category = it } }
                item { SearchField(query, "搜索武功名称") { query = it } }
                item {
                    Text(
                        "兑换点可随时、任意次分配，不必一次用完。分配后写入账本，进游戏读档即生效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when {
                    gameState?.player == null -> item {
                        HintText("尚未读取到游戏数据。进入游戏并读档后，这里会列出主角已学会的武功，并显示游戏内等级。")
                    }
                    visible.isEmpty() -> item {
                        HintText(
                            if (keyword.isNotEmpty()) "没有匹配「$keyword」的已学武功，换个关键词试试。"
                            else "主角当前还没有学会任何武功。可先到「兑换」页换取秘籍，进游戏研读学会后再回来分配。"
                        )
                    }
                    else -> items(visible, key = { it.id }) { art ->
                        val gs = learned[art.name]
                        ArtCard(
                            art, gs?.humanLevel ?: 0, state.points, vm,
                            onInfo = {
                                info = GameText.skillDetail(
                                    art.name, gs?.humanLevel ?: art.level, gs?.attrs ?: SkillAttrs()
                                )
                            },
                            onDelete = { pendingDeleteArt = art }
                        )
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { showRestoreConfirm = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("恢复默认武功库")
                    }
                }
            }
        }

        if (tab == TAB_ALLOCATE) {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("添加武功") }
            )
        }
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

    pendingDeleteArt?.let { art ->
        ConfirmDialog(
            title = "删除武功",
            text = "确定从列表移除「${art.name}」吗？仅移除本地条目，不影响游戏内已学武功。",
            confirmText = "删除",
            destructive = true,
            onDismiss = { pendingDeleteArt = null },
            onConfirm = { vm.deleteMartialArt(art.id) }
        )
    }

    if (showRestoreConfirm) {
        ConfirmDialog(
            title = "恢复默认库",
            text = "将把本地武功列表重置为默认库，已添加或已投入的自定义武功条目会被覆盖（游戏内数据不受影响）。",
            confirmText = "恢复",
            destructive = true,
            onDismiss = { showRestoreConfirm = false },
            onConfirm = { vm.restoreDefaultArts() }
        )
    }

    info?.let { InfoDialog(it) { info = null } }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 10.dp)
    )
}

@Composable
private fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "清空搜索")
                }
            }
        }
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
                "累计获得 ${state.pointsEarnedTotal} · 累计消耗 ${state.pointsSpentTotal} · 已投入武功 ${state.pointsInvested}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun GameProgressCard(
    gameState: GameState?,
    vm: AppViewModel,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSkill: (GameSkill) -> Unit,
    onItem: (GameItem) -> Unit
) {
    val player = gameState?.player
    var itemsExpanded by remember { mutableStateOf(false) }
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
                IconButton(onClick = onToggle) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开"
                    )
                }
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

            if (!expanded) {
                Text(
                    "已学武功 ${player.wugongs.size} 门 · 包裹 ${player.items.size} 种 · 点右侧箭头展开查看详情",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Text(
                "已学武功 ${player.wugongs.size} 门 · 点击查看属性",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (player.wugongs.isEmpty()) {
                Text("（暂无）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                player.wugongs.forEach { w ->
                    Text(
                        "· ${w.name}　Lv.${w.humanLevel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSkill(w) }
                            .padding(vertical = 4.dp)
                    )
                }
            }

            Text(
                "包裹 ${player.items.size} 种 · 点击查看介绍",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (player.items.isEmpty()) {
                Text("（空）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val shownItems = if (itemsExpanded) player.items else player.items.take(6)
                shownItems.forEach { it ->
                    Text(
                        "· ${it.name}${if (it.isBook) "【秘籍】" else ""} x${it.count}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItem(it) }
                            .padding(vertical = 4.dp)
                    )
                }
                if (player.items.size > 6) {
                    TextButton(onClick = { itemsExpanded = !itemsExpanded }) {
                        Text(if (itemsExpanded) "收起" else "展开全部 ${player.items.size} 种")
                    }
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
private fun ArtCard(
    art: MartialArt,
    gameLevel: Int,
    pool: Long,
    vm: AppViewModel,
    onInfo: () -> Unit,
    onDelete: () -> Unit
) {
    val maxed = art.level >= SkillCurve.MAX_LEVEL
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f).clickable { onInfo() },
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
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
                        "游戏内 Lv.$gameLevel · 点击查看属性",
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
                IconButton(onClick = onDelete) {
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
private fun BookCard(book: GameBook, learnedNames: Set<String>, vm: AppViewModel, onInfo: () -> Unit) {
    val learned = learnedNames.contains(book.skillName)
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onInfo() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(book.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (learned) "已学会「${book.skillName}」" else "研读可学「${book.skillName}」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "点击查看介绍与属性",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Button(
                onClick = { vm.redeemBook(book) },
                enabled = !learned,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) { Text(if (learned) "已学会" else "${vm.bookCost(book)} 兑换点") }
        }
    }
}

@Composable
private fun RedeemSection(state: PlayerState, vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.redeemItems.forEach { item ->
            RedeemCard(item, vm)
        }
    }
}

@Composable
private fun RedeemCard(item: RedeemItem, vm: AppViewModel) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    when (item.kind) {
                        "SILVER" -> "游戏内银两 x${item.amount}"
                        "EXP" -> "主角历练 +${item.amount}"
                        else -> "游戏物品 x${item.amount}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { vm.redeem(item) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) { Text("${item.pointCost} 兑换点") }
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