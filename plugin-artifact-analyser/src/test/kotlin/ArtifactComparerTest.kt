package dannykli.pluginanalyser

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*
import java.io.File
import java.nio.file.Files

class ArtifactComparerTest {

    private val json = Json { prettyPrint = true }

    private fun writeTempArtifact(filename: String, artifact: ArtifactInfo): File {
        val tempDir = Files.createTempDirectory("artifact_test_").toFile()
        val file = File(tempDir, "$filename.json")
        file.writeText(json.encodeToString(artifact))
        return file
    }

    @Test
    fun testComprehensiveComparison() {
        val artifact1 = ArtifactInfo(
            name = "artifact1.zip",
            entries = listOf(
                ArtifactEntry("common.txt", 10, 1000, "sha1"),
                ArtifactEntry("renamed_old.txt", 20, 2000, "sha2"),
                ArtifactEntry("changed.txt", 30, 3000, "sha3"),
                ArtifactEntry("removed.txt", 40, 4000, "sha4"),
                ArtifactEntry("dup1.txt", 50, 5000, "dupHash"),
                ArtifactEntry("dup2.txt", 50, 5000, "dupHash")
            )
        )

        val artifact2 = ArtifactInfo(
            name = "artifact2.zip",
            entries = listOf(
                ArtifactEntry("common.txt", 10, 1000, "sha1"), // common
                ArtifactEntry("renamed_new.txt", 20, 2000, "sha2"), // renamed
                ArtifactEntry("changed.txt", 30, 3000, "shaX"), // changed
                ArtifactEntry("added.txt", 40, 5000, "sha5"), // added
                ArtifactEntry("dupA.txt", 50, 5000, "dupHash2"),
                ArtifactEntry("dupB.txt", 50, 5000, "dupHash2")
            )
        )

        val file1 = writeTempArtifact("artifact1", artifact1)
        val file2 = writeTempArtifact("artifact2", artifact2)

        val comparer = ArtifactComparer(file1.absolutePath, file2.absolutePath)
        val result = comparer.compare()

        // --- Assertions ---
        assertEquals(listOf("common.txt"), result.commonFiles.map { it.name })
        assertEquals(listOf("added.txt", "dupA.txt", "dupB.txt"), result.addedFiles.map { it.name })
        assertEquals(listOf("removed.txt", "dup1.txt", "dup2.txt"), result.removedFiles.map { it.name })
        assertEquals(listOf("changed.txt"), result.changedFiles.map { it.name })
        assertEquals(listOf("renamed_new.txt"), result.renamedFiles.map { it.name })

        // Duplicate detection
        assertEquals(1, result.duplicateFiles.artifact1Duplicates.size)
        assertEquals(1, result.duplicateFiles.artifact2Duplicates.size)
        assertTrue(result.duplicateFiles.artifact1Duplicates[0].any { it.name == "dup1.txt" })
        assertTrue(result.duplicateFiles.artifact2Duplicates[0].any { it.name == "dupA.txt" })
    }

    @Test
    fun testSimilarityScoringDifferentCases() {
        fun similarity(artifact1: ArtifactInfo, artifact2: ArtifactInfo): Double {
            val file1 = writeTempArtifact("a1", artifact1)
            val file2 = writeTempArtifact("a2", artifact2)
            val comparer = ArtifactComparer(file1.absolutePath, file2.absolutePath)
            val method = comparer.javaClass.getDeclaredMethod("calculateSimilarityScore")
            method.isAccessible = true
            return method.invoke(comparer) as Double
        }

        val identical = similarity(
            ArtifactInfo("a1", listOf(ArtifactEntry("f.txt", 1, 0, "sha"))),
            ArtifactInfo("a2", listOf(ArtifactEntry("f.txt", 1, 0, "sha")))
        )
        assertEquals(100.0, identical, 0.001)

        val halfCommon = similarity(
            ArtifactInfo("a1", listOf(
                ArtifactEntry("f1", 1, 0, "sha1"),
                ArtifactEntry("f2", 1, 0, "sha2")
            )),
            ArtifactInfo("a2", listOf(ArtifactEntry("f1", 1, 0, "sha1")))
        )
        assertTrue(halfCommon < 100.0)

        val renamedOnly = similarity(
            ArtifactInfo("a1", listOf(ArtifactEntry("f1", 1, 0, "sha"))),
            ArtifactInfo("a2", listOf(ArtifactEntry("f2", 1, 0, "sha")))
        )
        // renamed weight = 0.9 → 90%
        assertEquals(90.0, renamedOnly, 0.1)
    }

    @Test
    fun testWriteToJsonProducesValidFile() {
        val artifact1 = ArtifactInfo("a1", listOf(ArtifactEntry("f1", 1, 0, "sha")))
        val artifact2 = ArtifactInfo("a2", listOf(ArtifactEntry("f1", 1, 0, "sha")))

        val file1 = writeTempArtifact("a1", artifact1)
        val file2 = writeTempArtifact("a2", artifact2)
        val comparer = ArtifactComparer(file1.absolutePath, file2.absolutePath)

        comparer.writeToJson()

        val outputFile = File("${file1.nameWithoutExtension}-${file2.nameWithoutExtension}-comparison.json")
        assertTrue(outputFile.exists(), "Expected JSON output file to exist")
        val text = outputFile.readText()
        assertTrue(text.contains("commonFiles"))
        outputFile.delete()
    }

    @Test
    fun testArtifactSummaryCalculation() {
        val artifact = ArtifactInfo(
            "a.zip",
            listOf(
                ArtifactEntry("a", 10, 1000, "s1"),
                ArtifactEntry("b", 20, 2000, "s2")
            )
        )
        val f1 = writeTempArtifact("a1", artifact)
        val f2 = writeTempArtifact("a2", artifact)

        val comparer = ArtifactComparer(f1.absolutePath, f2.absolutePath)
        val method = comparer.javaClass.getDeclaredMethod("summariseArtifact", ArtifactInfo::class.java)
        method.isAccessible = true
        val summary = method.invoke(comparer, artifact) as ArtifactSummary

        assertEquals(2, summary.noOfFiles)
        assertEquals(30, summary.totalSize)
        assertEquals(2000, summary.lastModified)
    }
}
