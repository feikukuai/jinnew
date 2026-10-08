package com.traework.jygoldenfinger.game

import android.os.Environment
import java.io.File

object GamePaths {

    /** 《群侠传，启动！》官方安卓包名 */
    const val GAME_PACKAGE = "com.jynew.wuxia_launch"

    /** 金手指配套 Mod 的目录名（Mod 清单 Id） */
    const val GF_MOD_ID = "goldenfinger"

    /** App 写给游戏内 Lua Mod 的账本文件名（Lua 表格式，可直接 load） */
    const val LEDGER_FILE = "ledger.lua"

    /** 游戏内 Lua Mod 回写给 App 的角色面板文件名（JSON） */
    const val STATE_FILE = "state.json"

    val externalRoot: File get() = Environment.getExternalStorageDirectory()

    /**
     * 游戏可识别的 Mod 目录候选，按优先级排列。
     * jynew 安卓版历史上使用过这两个位置，两个都写入以保证兼容。
     */
    fun modDirCandidates(): List<File> = listOf(
        File(externalRoot, "jynew/mods"),
        File(externalRoot, "Android/data/$GAME_PACKAGE/files/mods")
    )

    /**
     * 金手指交换目录候选，必须与 goldenfinger.lua 内的 DIRS 完全一致：
     * Lua Mod 从这里读 ledger.lua，也往这里写 state.json。
     */
    fun exchangeDirCandidates(): List<File> = listOf(
        File(externalRoot, "jynew/$GF_MOD_ID"),
        File(externalRoot, "Android/data/$GAME_PACKAGE/files/$GF_MOD_ID")
    )

    fun primaryModDir(): File = modDirCandidates().firstOrNull { it.isDirectory }
        ?: modDirCandidates().first()

    fun ensureDir(dir: File): Boolean = dir.isDirectory || dir.mkdirs()
}