package dannykli.pluginanalyser

import kotlin.io.path.createTempDirectory
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArtifactParserTest {

    private val json = Json { prettyPrint = true }

    private fun createTestZip(fileName: String, entries: List<String>, tempDir: File): File {
        val zipFile = File(tempDir, fileName)
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            for (entryName in entries) {
                val entry = ZipEntry(entryName)
                zos.putNextEntry(entry)
                zos.write("Test content for $entryName".toByteArray())
                zos.closeEntry()
            }
        }
        return zipFile
    }

    @Test
    fun parseZipFileCorrectly() {
        val tempDir = createTempDirectory().toFile()
        val entries = listOf("file1.txt", "file2.txt")
        val zipFile = createTestZip("test.zip", entries, tempDir)

        val parser = ArtifactParser(zipFile.absolutePath)

        val artifact = parser.parse()

        assertEquals(zipFile.absolutePath, artifact.name)
        assertEquals(entries.size, artifact.entries.size)
        assertTrue(artifact.entries.any { it.name == "file1.txt" })
        assertTrue(artifact.entries.any { it.name == "file2.txt" })
    }

    @Test
    fun writeToCreatesCorrectJsonFile() {
        val tempDir = createTempDirectory().toFile()
        val entries = listOf("file1.txt")
        val zipFile = createTestZip("test.zip", entries, tempDir)

        val parser = ArtifactParser(zipFile.absolutePath)
        val outputJsonFile = File(tempDir, "artifact.json")
        parser.writeTo(outputJsonFile.absolutePath)

        assertTrue(outputJsonFile.exists())

        val artifactFromJson = json.decodeFromString<ArtifactInfo>(outputJsonFile.readText())
        assertEquals(zipFile.absolutePath, artifactFromJson.name)
        assertEquals(1, artifactFromJson.entries.size)
        assertEquals("file1.txt", artifactFromJson.entries[0].name)
    }
}
