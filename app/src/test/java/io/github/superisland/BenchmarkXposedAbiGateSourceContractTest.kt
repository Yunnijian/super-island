package io.github.superisland

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkXposedAbiGateSourceContractTest {
    @Test
    fun benchmarkVerificationRunsAfterR8AndIsPartOfCheck() {
        val appBuild = sourceFile("app/build.gradle.kts").readText()
        val rootBuild = sourceFile("build.gradle.kts").readText()

        assertTrue(
            "The ABI gate must be a named verification task",
            "tasks.register<VerifyBenchmarkXposedAbiTask>(\"verifyBenchmarkXposedAbi\")" in appBuild,
        )
        assertTrue(
            "The ABI gate must inspect the final minified benchmark output",
            "dependsOn(\"assembleBenchmark\")" in appBuild &&
                "outputs/mapping/benchmark/mapping.txt" in appBuild &&
                "outputs/apk/benchmark" in appBuild,
        )
        assertTrue(
            "The app check lifecycle must include the post-R8 ABI gate",
            "tasks.named(\"check\")" in appBuild &&
                "dependsOn(verifyBenchmarkXposedAbi)" in appBuild,
        )
        assertTrue(
            "The root check lifecycle must include the post-R8 ABI gate",
            "dependsOn(verifyMiuixPolicy, \":app:verifyBenchmarkXposedAbi\")" in rootBuild,
        )
    }

    @Test
    fun benchmarkVerificationChecksTheExternalXposedContractWithoutSyntheticNames() {
        val appBuild = sourceFile("app/build.gradle.kts").readText()

        listOf(
            "META-INF/xposed/java_init.list",
            "io.github.superisland.hook.systemui.SuperIslandXposedModule",
            "onPackageLoaded",
            "PackageLoadedParam",
            "intercept",
            "XposedInterface\\\$Chain",
            "XposedInterface\\\$Hooker",
        ).forEach { contract ->
            assertTrue("The ABI gate must check $contract", contract in appBuild)
        }
        assertTrue(
            "The optional DEX audit must dynamically enumerate mapped Hooker candidates",
            "dexHookerCandidates.any" in appBuild &&
                "it in mappedHookerClasses" in appBuild &&
                "HOOKER_INTERFACE_DESCRIPTOR in code" in appBuild,
        )
        assertFalse(
            "The ABI gate must not depend on an R8-generated class name",
            "ExternalSyntheticLambda" in appBuild,
        )
    }

}
