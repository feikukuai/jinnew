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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.traework.jygoldenfinger.data.PlayerState
import com.traework.jygoldenfinger.data.TaskItem
import com.traework.jygoldenfinger.data.TaskType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaskScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    var filter by remember { mutableStateOf<TaskType?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TaskItem?>(null) }

    val dayFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val today = dayFmt.format(Date())

    val visible = remember(state.tasks, filter) {
        state.tasks.filter { filter == null || it.type == filter }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { HeaderStats(state) }
            item { FilterRow(filter) { filter = it } }

            if (visible.isEmpty()) {
                item {
                    Text(
                        "还没有任务，点右下角「新增任务」开始设定你的现实目标。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }

            items(visible, key = { it.id }) { task ->
                TaskCard(task, today, vm) { pendingDelete = task }
            }

            if (visible.isNotEmpty()) {
                item {
                    Text(
                        "完成任务赚取兑换点，再到「金手指」页兑换武功秘籍与游戏资源。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("新增任务") }
        )
    }

    if (showAdd) {
        AddTaskDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title, type, reward, cost, note ->
                vm.addTask(title, type, reward, cost, note)
                showAdd = false
            }
        )
    }

    pendingDelete?.let { task ->
        ConfirmDialog(
            title = "删除任务",
            text = "确定删除「${task.title}」吗？此操作不可恢复。",
            confirmText = "删除",
            destructive = true,
            onDismiss = { pendingDelete = null },
            onConfirm = { vm.deleteTask(task.id) }
        )
    }
}

@Composable
private fun HeaderStats(state: PlayerState) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "兑换点余额",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    state.points.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "累计获得 ${state.pointsEarnedTotal} · 累计消耗 ${state.pointsSpentTotal}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "已投入武功 ${state.pointsInvested}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun FilterRow(selected: TaskType?, onSelect: (TaskType?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("全部") })
        TaskType.values().forEach { t ->
            FilterChip(
                selected = selected == t,
                onClick = { onSelect(t) },
                label = { Text(t.label) }
            )
        }
    }
}

@Composable
private fun TaskCard(task: TaskItem, today: String, vm: AppViewModel, onDelete: () -> Unit) {
    val doneToday = task.type == TaskType.DAILY && task.lastDailyDate == today
    val done = task.type == TaskType.TODO && task.todoDone

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypeBadge(task.type)
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                    )
                }
                if (task.note.isNotBlank()) {
                    Text(task.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (task.type) {
                        TaskType.REWARD -> AssistChip(
                            onClick = {},
                            label = { Text("${task.pointCost} 兑换点") },
                            colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.tertiary)
                        )
                        else -> {
                            AssistChip(onClick = {}, label = { Text("+${task.pointReward} 兑换点") })
                        }
                    }
                    if (task.doneCount > 0) {
                        AssistChip(onClick = {}, label = { Text("已 ${task.doneCount} 次") })
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (task.type) {
                    TaskType.HABIT -> Row {
                        IconButton(onClick = { vm.undoHabit(task) }) {
                            Icon(Icons.Filled.Remove, contentDescription = "撤销", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.completeTask(task) }) {
                            Icon(Icons.Filled.Check, contentDescription = "完成", tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    TaskType.DAILY -> Button(
                        onClick = { vm.completeTask(task) },
                        enabled = !doneToday,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) { Text(if (doneToday) "已打卡" else "打卡") }
                    TaskType.TODO -> Button(
                        onClick = { vm.completeTask(task) },
                        enabled = !done,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) { Text(if (done) "已完成" else "完成") }
                    TaskType.REWARD -> Button(
                        onClick = { vm.completeTask(task) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) { Text("兑换") }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(type: TaskType) {
    AssistChip(
        onClick = {},
        label = { Text(type.label) },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = when (type) {
                TaskType.HABIT -> MaterialTheme.colorScheme.secondary
                TaskType.DAILY -> MaterialTheme.colorScheme.primary
                TaskType.TODO -> MaterialTheme.colorScheme.onSurfaceVariant
                TaskType.REWARD -> MaterialTheme.colorScheme.tertiary
            }
        )
    )
}

@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, TaskType, Int, Int, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TaskType.HABIT) }
    var reward by remember { mutableStateOf("20") }
    var cost by remember { mutableStateOf("60") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增任务") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("任务名称，如「写作 1000 字」") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("类型", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskType.values().forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                    }
                }
                if (type == TaskType.REWARD) {
                    OutlinedTextField(
                        value = cost,
                        onValueChange = { cost = it.filter { c -> c.isDigit() } },
                        label = { Text("兑换所需兑换点") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = reward,
                        onValueChange = { reward = it.filter { c -> c.isDigit() } },
                        label = { Text("完成奖励（兑换点）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    title,
                    type,
                    reward.toIntOrNull() ?: 0,
                    cost.toIntOrNull() ?: 0,
                    note
                )
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}