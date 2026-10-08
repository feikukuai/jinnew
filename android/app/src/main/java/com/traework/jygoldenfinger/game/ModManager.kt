package com.traework.jygoldenfinger.game

import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

data class ModEntry(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val desc: String,
    val dir: File
) {
    val files: List<String>
        get() = dir.listFiles()?.filter { it.isFile }?.map { it.name }?.sorted() ?: emptyList()

    val totalBytes: Long
        get() = dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
}

object ModManager {

    fun scanInstalled(): List<ModEntry> {
        val result = LinkedHashMap<String, ModEntry>()
        GamePaths.modDirCandidates().forEach { base ->
            if (!base.isDirectory) return@forEach
            base.listFiles()?.filter { it.isDirectory }?.forEach inner@{ dir ->
                val xml = dir.listFiles()
                    ?.firstOrNull { it.isFile && it.extension.equals("xml", ignoreCase = true) }
                    ?: return@inner
                parseMod(xml)?.let { result.putIfAbsent(it.id, it) }
            }
        }
        return result.values.sortedBy { it.name }
    }

    fun parseMod(xmlFile: File): ModEntry? = runCatching {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(xmlFile.inputStream(), "utf-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "GameModInfo") {
                fun attr(n: String) = parser.getAttributeValue(null, n) ?: ""
                return@runCatching ModEntry(
                    id = attr("Id").ifBlank { xmlFile.nameWithoutExtension },
                    name = attr("Name").ifBlank { xmlFile.nameWithoutExtension },
                    author = attr("Author"),
                    version = attr("Version"),
                    desc = attr("Desc"),
                    dir = xmlFile.parentFile ?: xmlFile
                )
            }
            event = parser.next()
        }
        null
    }.getOrNull()

    /** 从压缩包安装 Mod：解出 jynew Mod 三件套(xml + 资源包)到 Mod 目录 */
    fun installFromZip(context: Context, uri: Uri): Result<ModEntry> = runCatching {
        val tmp = File(context.cacheDir, "mod_import_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                ZipInputStream(BufferedInputStream(ins)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!name.contains("..")) {
                            val out = File(tmp, name)
                            if (entry.isDirectory) {
                                out.mkdirs()
                            } else {
                                out.parentFile?.mkdirs()
                                out.outputStream().use { zis.copyTo(it) }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: throw IOException("无法读取所选文件")

            val xmlFile = tmp.walkTopDown()
                .firstOrNull { it.isFile && it.extension.equals("xml", true) && looksLikeModInfo(it) }
                ?: throw IOException("压缩包内未找到 Mod 清单 (*.xml 含 GameModInfo)")

            val modRoot = xmlFile.parentFile ?: tmp
            val id = xmlFile.nameWithoutExtension
            val targetDir = File(GamePaths.primaryModDir(), id)

            if (!GamePaths.ensureDir(targetDir.parentFile!!)) {
                throw IOException("无法创建 Mod 目录：${targetDir.parent}（请授予「所有文件访问权限」）")
            }
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            modRoot.listFiles()?.forEach { f ->
                if (f.isDirectory) {
                    f.copyRecursively(File(targetDir, f.name), overwrite = true)
                } else {
                    f.copyTo(File(targetDir, f.name), overwrite = true)
                }
            }

            parseMod(xmlFile)?.copy(dir = targetDir)
                ?: throw IOException("Mod 清单解析失败")
        } finally {
            tmp.deleteRecursively()
        }
    }

    fun deleteMod(entry: ModEntry): Boolean = runCatching {
        entry.dir.deleteRecursively()
    }.getOrDefault(false)

    private fun looksLikeModInfo(file: File): Boolean = runCatching {
        file.inputStream().use { stream ->
            val buf = ByteArray(4096)
            val read = stream.read(buf)
            read > 0 && String(buf, 0, read, Charsets.UTF_8).contains("GameModInfo")
        }
    }.getOrDefault(false)
}