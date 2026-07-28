package io.github.superisland.hook.systemui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private val projectRoot: Path by lazy {
    generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()) { it.parent }
        .firstOrNull { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
        ?: error("Cannot locate the project root from ${System.getProperty("user.dir")}")
}

internal fun sourceFile(relativePath: String): Path =
    projectRoot.resolve(relativePath).also { path ->
        check(Files.exists(path)) { "Cannot locate $relativePath below $projectRoot" }
    }

internal fun sourceFilesUnder(relativePath: String): List<Path> =
    Files.walk(sourceFile(relativePath)).use { paths ->
        paths.filter(Files::isRegularFile).toList()
    }
