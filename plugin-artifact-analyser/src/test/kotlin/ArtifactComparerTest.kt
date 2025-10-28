package dannykli.pluginanalyser

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class ArtifactComparerTest {

    private val json = Json { prettyPrint = true }

    private fun writeTempArtifact(filename: String, artifact: ArtifactInfo): File {
        val file = File.createTempFile(filename, ".json")
        file.writeText(json.encodeToString(artifact))
        return file
    }

    @Test
    fun testCommonAddedRemovedChangedRenamedFiles() {
        // Artifact 1
        val artifact1 = ArtifactInfo(
            name = "artifact1.zip",
            entries = listOf(
                ArtifactEntry("file1.txt", 111, 10, 1000),
                ArtifactEntry("file2.txt", 222, 20, 2000),
                ArtifactEntry("file3.txt", 333, 30, 3000)
            )
        )
        // Artifact 2
        val artifact2 = ArtifactInfo(
            name = "artifact2.zip",
            entries = listOf(
                ArtifactEntry("file1.txt", 111, 10, 1000), // common
                ArtifactEntry("file2-renamed.txt", 222, 20, 2000), // renamed
                ArtifactEntry("file3.txt", 999, 30, 4000), // changed
                ArtifactEntry("file4.txt", 444, 40, 5000) // added
            )
        )

        val file1 = writeTempArtifact("artifact1", artifact1)
        val file2 = writeTempArtifact("artifact2", artifact2)

        val comparer = ArtifactComparer(file1.absolutePath, file2.absolutePath)
        val comparison = comparer.compare()
        println("COMPARISON $comparison")

        // Check common files
        assertEquals(1, comparison.commonFiles.size)
        assertEquals("file1.txt", comparison.commonFiles[0].name)

        // Check renamed files
        assertEquals(1, comparison.renamedFiles.size)
        assertEquals("file2-renamed.txt", comparison.renamedFiles[0].name)

        // Check changed files
        assertEquals(1, comparison.changedFiles.size)
        assertEquals("file3.txt", comparison.changedFiles[0].name)

        // Check added files
        assertEquals(1, comparison.addedFiles.size)
        assertEquals("file4.txt", comparison.addedFiles[0].name)

        // Check removed files
        assertEquals(0, comparison.removedFiles.size) // all artifact1 files matched or changed/renamed
    }

    @Test
    fun testSimilarityScoreCalculation() {
        val artifact1 = ArtifactInfo(
            name = "artifact1.zip",
            entries = listOf(
                ArtifactEntry("file1.txt", 111, 10, 1000)
            )
        )
        val artifact2 = ArtifactInfo(
            name = "artifact2.zip",
            entries = listOf(
                ArtifactEntry("file1.txt", 111, 10, 1000)
            )
        )

        val file1 = writeTempArtifact("artifact1", artifact1)
        val file2 = writeTempArtifact("artifact2", artifact2)

        val comparer = ArtifactComparer(file1.absolutePath, file2.absolutePath)

        val similarity = comparer.javaClass
            .getDeclaredMethod("calculateSimilarityScore")
            .apply { isAccessible = true }
            .invoke(comparer) as Double

        println("SIMILARITY $similarity")
        assertTrue(similarity > 99.0) // should be ~100%
    }
}
