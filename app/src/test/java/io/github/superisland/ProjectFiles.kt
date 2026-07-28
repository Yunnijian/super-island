package io.github.superisland

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private val projectRoot: Path by lazy {
    generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()) { it.parent }
        .firstOrNull { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
        ?: error("Cannot locate the project root from ${System.getProperty("user.dir")}")
}

internal fun sourceFile(relativePath: String): Path =
    projectPath(relativePath).also { path ->
        check(Files.exists(path)) { "Cannot locate $relativePath below $projectRoot" }
    }

internal fun projectPath(relativePath: String): Path = projectRoot.resolve(relativePath)

internal fun upstreamFile(relativePath: String): Path {
    val configuredRoot = System.getenv("SUPER_ISLAND_UPSTREAMS_DIR")
    val upstreamRoot =
        configuredRoot?.let(Paths::get)
            ?: Paths.get(System.getProperty("user.home"), ".cache", "super-island", "upstreams")
    return upstreamRoot.resolve(relativePath).also { path ->
        check(Files.exists(path)) { "Cannot locate pinned upstream source $path" }
    }
}
