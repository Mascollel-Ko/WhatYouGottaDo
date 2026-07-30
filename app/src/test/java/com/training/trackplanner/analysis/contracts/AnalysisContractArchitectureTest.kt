package com.training.trackplanner.analysis.contracts

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnalysisContractArchitectureTest {
    @Test
    fun newContractPathDoesNotInferMeaningFromNamesKeysOrDelimitedMetadata() {
        val sourceRoot = repoFile("app/src/main/java/com/training/trackplanner/analysis/contracts")
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    val compact = line.replace(" ", "")
                    val forbidden = listOf(
                        ".contains(",
                        ".split(",
                        ".startsWith(",
                        ".endsWith("
                    ).firstOrNull(compact::contains)
                    forbidden?.let { "${file.name}:${index + 1}: $it" }
                }
            }
            .toList()

        assertTrue("Forbidden inference in typed contract path:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    private fun repoFile(path: String): File {
        val current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val root = generateSequence(current) { directory -> directory.parentFile?.takeUnless { it == directory } }
            .firstOrNull { directory -> File(directory, "settings.gradle.kts").isFile }
            ?: error("Repository root not found from ${current.absolutePath}.")
        return File(root, path)
    }
}
