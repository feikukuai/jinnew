package com.traework.jygoldenfinger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (accent) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = if (accent) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (accent) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, hint: String? = null) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (hint != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 破坏性操作前的二次确认，避免误触丢失数据 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "确定",
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(
                    confirmText,
                    color = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 页面回到前台时执行一次。用于刷新「所有文件访问权限」「游戏是否安装」这类
 * 系统状态 —— 用户去系统设置授权后返回，普通重组不会触发，必须监听 ON_RESUME。
 */
@Composable
fun OnResumeEffect(block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(block)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) current()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}