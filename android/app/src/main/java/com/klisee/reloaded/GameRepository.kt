package com.klisee.reloaded

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Owns the game in the app's private storage.
 *
 * The launcher inflates the game itself (so the emulator no longer depends on any archive name)
 * into `files/game/`, and records where it came from in `files/game.json`.
 */
class GameRepository(private val context: Context) {

    sealed interface Status {
        data object Missing : Status
        data class Ready(
            val exe: String,
            val version: String?,
            val source: String?,
            val installedAt: Long,
            val sizeBytes: Long,
        ) : Status
    }

    private val files: File get() = context.filesDir
    private val gameDir: File get() = File(files, "game")
    private val metaFile: File get() = File(files, "game.json")
    val logFile: File get() = File(files, "emulator.log")

    /** The emulator's executable name is not pinned, so it is discovered by extension. */
    fun status(): Status {
        val exe = gameDir.listFiles()?.firstOrNull { it.isFile && it.extension.equals("exe", ignoreCase = true) }
            ?: return Status.Missing
        // A gutted `data` folder is not an install either: the game would start and fail.
        val data = File(gameDir, "data")
        if (!data.isDirectory || (data.listFiles()?.size ?: 0) < 5) return Status.Missing
        val meta = readMeta()
        return Status.Ready(
            exe = exe.name,
            version = meta?.optString("version")?.ifEmpty { null },
            source = meta?.optString("source")?.ifEmpty { null },
            installedAt = meta?.optLong("installedAt", 0L) ?: 0L,
            sizeBytes = meta?.optLong("sizeBytes", 0L) ?: 0L,
        )
    }

    /** Frees the extracted game, leaving the launcher ready for another import or download. */
    suspend fun deleteGame() = withContext(Dispatchers.IO) {
        gameDir.deleteRecursively()
        metaFile.delete()
        Unit
    }

    /**
     * What a "save" actually is: the saved games plus the teams and custom characters the game's own
     * migration instructions tell you to carry over. Keeping the same folder names means a zip made
     * here also works by hand on the PC copy, and the other way round.
     */
    private val saveFolders = listOf("partidas", "equipos", "char_custom")

    /** Zips the saves into the cache, ready to be handed to a file picker. */
    suspend fun exportSaves(): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val data = File(gameDir, "data")
            val out = File(context.cacheDir, "partidas.zip")
            var written = 0
            ZipOutputStream(out.outputStream().buffered()).use { zip ->
                saveFolders.forEach { folder ->
                    val root = File(data, folder)
                    if (!root.isDirectory) return@forEach
                    root.walkTopDown().filter { it.isFile }.forEach { file ->
                        zip.putNextEntry(ZipEntry("$folder/" + file.relativeTo(root).invariantSeparatorsPath))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        written++
                    }
                }
            }
            if (written == 0) {
                out.delete()
                error("No hay partidas que exportar todavia.")
            }
            out
        }
    }

    /** Restores an exported zip into the game's data folder, replacing what is there. */
    suspend fun importSaves(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val data = File(gameDir, "data")
            if (!data.isDirectory) error("Primero instala el juego.")
            var files = 0
            // A save is a .savc file; the .data and .nfo beside it only describe it. Counting every
            // file in the zip said "51 partidas" when there was really one.
            var saves = 0
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val target = File(data, name)
                        // Only the save folders, and never a path that escapes them.
                        val wanted = saveFolders.any { name.startsWith("$it/") }
                        val safe = target.canonicalPath.startsWith(data.canonicalPath + File.separator)
                        if (!entry.isDirectory && wanted && safe) {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                            files++
                            // Backups (`reloaded1_bk.savc`) sit beside the real slot, so they are not
                            // counted as separate saves.
                            val isSlot = name.startsWith("partidas/") &&
                                name.endsWith(".savc", ignoreCase = true) &&
                                !name.contains("_bk", ignoreCase = true)
                            if (isSlot) saves++
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("No pude abrir el archivo.")
            if (files == 0) error("Ese zip no trae partidas del juego.")
            saves
        }
    }

    private fun readMeta(): JSONObject? = runCatching { JSONObject(metaFile.readText()) }.getOrNull()

    /**
     * Copies the bundled MIDI soundfont next to the game, which is where the emulator looks for it.
     * It ships inside the APK so music works on any device without depending on external paths.
     */
    suspend fun prepare(): Unit = withContext(Dispatchers.IO) {
        val exe = gameDir.listFiles()?.firstOrNull { it.isFile && it.extension.equals("exe", ignoreCase = true) }
        if (exe != null && File(gameDir, "data").isDirectory) {
            // Installs made before the launcher recorded these still need their size and date.
            val meta = readMeta() ?: JSONObject()
            if (meta.optLong("sizeBytes", 0L) == 0L || meta.optLong("installedAt", 0L) == 0L) {
                meta.put("sizeBytes", gameDir.walkTopDown().filter { it.isFile }.sumOf { it.length() })
                if (meta.optLong("installedAt", 0L) == 0L) meta.put("installedAt", System.currentTimeMillis())
                metaFile.writeText(meta.toString())
            }
        }
        if (!gameDir.isDirectory) return@withContext
        val target = File(gameDir, "soundfont.sf2")
        if (target.isFile && target.length() > 0) return@withContext
        runCatching {
            context.assets.open("soundfont.sf2").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        Unit
    }

    /** Imports a zip picked with the system file chooser. */
    suspend fun import(uri: Uri, onProgress: (Int) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File(files, "import.zip")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: error("No pude abrir el fichero elegido.")
                installZip(temp, version = null, source = "manual", onProgress = onProgress).getOrThrow()
                prepare()
            } finally {
                temp.delete()
            }
        }
    }

    /** Downloads a zip from a direct URL (following redirects) and installs it. */
    suspend fun download(url: String, cookies: String?, onProgress: (Int) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File(files, "download.zip")
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android) ReloadedLauncher/1.0")
                    if (!cookies.isNullOrEmpty()) setRequestProperty("Cookie", cookies)
                }
                connection.connect()
                if (connection.responseCode !in 200..299) error("El servidor respondio ${connection.responseCode}.")
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            written += n
                            if (total > 0) onProgress((written * 100 / total).toInt())
                        }
                    }
                }
                installZip(temp, version = null, source = "descarga", onProgress = onProgress).getOrThrow()
                prepare()
            } finally {
                temp.delete()
            }
        }
    }

    /** Validates, inflates and swaps in a game zip. Mirrors the emulator's own requirements. */
    private fun installZip(zip: File, version: String?, source: String, onProgress: (Int) -> Unit): Result<Unit> = runCatching {
        requireGameZip(zip)
        val staging = File(files, "game.staging")
        staging.deleteRecursively()
        staging.mkdirs()

        val total = zip.length().coerceAtLeast(1)
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            val buffer = ByteArray(64 * 1024)
            var written = 0L
            var entry = input.nextEntry
            while (entry != null) {
                val target = File(staging, entry.name)
                // Reject zip-slip: entries must stay inside the staging directory.
                if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                    error("El zip tiene una ruta no valida: ${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { output ->
                        var n = input.read(buffer)
                        while (n > 0) {
                            output.write(buffer, 0, n)
                            written += n
                            onProgress(((written * 100) / total).toInt().coerceIn(0, 99))
                            n = input.read(buffer)
                        }
                    }
                }
                input.closeEntry()
                entry = input.nextEntry
            }
        }

        if (staging.listFiles()?.none { it.isFile && it.extension.equals("exe", ignoreCase = true) } != false ||
            !File(staging, "data").isDirectory
        ) {
            staging.deleteRecursively()
            error("El zip no trae el .exe y la carpeta data/ en la raiz.")
        }

        gameDir.deleteRecursively()
        if (!staging.renameTo(gameDir)) {
            staging.deleteRecursively()
            error("No pude mover el juego a su carpeta definitiva.")
        }
        metaFile.writeText(
            JSONObject()
                .put("version", version ?: "")
                .put("source", source)
                .put("installedAt", System.currentTimeMillis())
                .put("sizeBytes", gameDir.walkTopDown().filter { it.isFile }.sumOf { it.length() })
                .toString(),
        )
        onProgress(100)
    }

    /** Cheap structural check before doing the expensive extraction. */
    private fun requireGameZip(file: File) {
        ZipFile(file).use { zip ->
            var exe = false
            var data = false
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (!name.contains('/') && name.endsWith(".exe", ignoreCase = true)) exe = true
                if (name.startsWith("data/")) data = true
            }
            if (!exe || !data) error("Ese zip no parece Pokemon Reloaded: falta el .exe o la carpeta data/ en la raiz.")
        }
    }
}
