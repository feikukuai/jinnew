package com.traework.jygoldenfinger.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.traework.jygoldenfinger.game.GameLauncher
import com.traework.jygoldenfinger.game.GamePaths
import com.traework.jygoldenfinger.game.ModEntry
import com.traework.jygoldenfinger.util.StoragePermission

@Composable
fun ModScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val mods by vm.mods.collectAsState()
    val hasStorage = remember { StoragePermission.hasAllFilesAccess() }
    val gameInstalled = remember { GameLauncher.isInstalled(context) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { vm.installMod(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Mod 目录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        GamePaths.modDirCandidates().forEach { dir ->
                            Text(dir.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(
                            if (hasStorage) "已获得「所有文件访问权限」" else "尚未获得「所有文件访问权限」，无法写入 Mod 目录",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasStorage) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Text("  导入 Mod 压缩包", color = MaterialTheme.colorScheme.onPrimary)
                    }
                    OutlinedButton(onClick = { vm.refreshMods() }) { Text("刷新") }
                }
            }

            if (!hasStorage) {
                item {
                    OutlinedButton(
                        onClick = { StoragePermission.requestAllFilesAccess(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("前往授予「所有文件访问权限」") }
                }
            }

            item {
                Button(
                    onClick = {
                        if (!GameLauncher.launch(context)) {
                            vm.notify("未检测到游戏《群侠传，启动！》（${GamePaths.GAME_PACKAGE}），请先安装")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(if (gameInstalled) "  启动游戏" else "  未检测到游戏，点击查看", color = MaterialTheme.colorScheme.onSecondary)
                }
            }

            item { SectionHeader("已安装 Mod（${mods.size}）") }

            if (mods.isEmpty()) {
                item {
                    Text(
                        "还没有 Mod。把你下载的 jynew Mod 压缩包（内含 xml + 资源包）导入即可。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            items(mods, key = { it.id }) { mod ->
                ModCard(mod, vm)
            }

            item {
                Text(
                    "提示：安装后在游戏内点击「手动导入」即可加载；下次进入游戏会自动识别。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ModCard(mod: ModEntry, vm: AppViewModel) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(mod.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Id: ${mod.id} · 版本 ${mod.version.ifBlank { "未知" }} · 作者 ${mod.author.ifBlank { "未知" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { vm.removeMod(mod) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.outline)
                }
            }
            if (mod.desc.isNotBlank()) {
                Text(
                    mod.desc.replace("\r\n", " ").replace("\n", " ").take(160),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = {}, label = { Text("${mod.files.size} 个文件") })
                AssistChip(onClick = {}, label = { Text("%.1f MB".format(mod.totalBytes / 1024.0 / 1024.0)) })
            }
        }
    }
}