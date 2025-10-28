package dannykli.pluginanalyser

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.max
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


class ArtifactComparer(val file1: String, val file2: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val artifact1: ArtifactInfo
    private val artifact2: ArtifactInfo
    private val comparisonInfo: ArtifactComparison
    private var ignoredFiles: List<ArtifactEntry> = mutableListOf()

    init {
        artifact1 = readFile(file1)
        artifact2 = readFile(file2)
        comparisonInfo = compare()
    }

    private fun readFile(filename: String): ArtifactInfo {
        val jsonString = File(filename).readText()
        return json.decodeFromString(jsonString)
    }

    fun compare(): ArtifactComparison {
        // warning: The correctness of this function relies on each file's file key being unique, specifically that
        //   the following holds:
        //      file A and file B have same file key (crc, size) ==> file A and B have the same contents
        // Whilst this is not guaranteed to be true, it is extremely likely that the above holds in practical scenarios

        // Artifact comparison variables
        val artifact1Summary = summariseArtifact(artifact1)
        val artifact2Summary = summariseArtifact(artifact2)
        val commonFiles: MutableList<ArtifactEntry> = mutableListOf()
        val addedFiles: MutableList<ArtifactEntry> = mutableListOf()
        val changedFiles: MutableList<ArtifactEntry> = mutableListOf()
        val renamedFiles: MutableList<ArtifactEntry> = mutableListOf()
        val removedFiles: List<ArtifactEntry>

        val artifact1Filenames = artifact1.entries.map(ArtifactEntry::name).toSet()

        val artifact1Files: MutableMap<FileKey, ArtifactEntry> = mutableMapOf()

        // In the rare case that two different files have the same file key, we must keep track of these
        //   to avoid overwriting the first file. It is so rare that we are okay to ignore them in this case.
        val duplicateFiles1: MutableList<ArtifactEntry> = mutableListOf()

        for (entry in artifact1.entries) {
                val key = FileKey(entry.crc, entry.size)
                if (artifact1Files.containsKey(key)) {
                    duplicateFiles1.add(entry)
                    continue
                }
                artifact1Files[key] = entry
        }

        // Again, we do the same to check for multiple files with the same file key
        val duplicateFiles2: MutableList<ArtifactEntry> = mutableListOf()
        val artifact2Keys: MutableSet<FileKey> = mutableSetOf()

        for (entry in artifact2.entries) {
            val key = FileKey(entry.crc, entry.size)
            if (artifact2Keys.contains(key)) {
                duplicateFiles2.add(entry)
            }
            artifact2Keys.add(key)
        }

        this.ignoredFiles = duplicateFiles1 + duplicateFiles2

        val originalNamesOfRenamedFiles = mutableSetOf<String>()

        for (entry in artifact2.entries) {
            if (duplicateFiles2.contains(entry)) {
                continue
            }
            val commonArtifact1File = artifact1Files[FileKey(entry.crc, entry.size)]
            if (commonArtifact1File != null) {
                if (commonArtifact1File.name == entry.name) {
                    // File is same in artifact 2 as in artifact 1 with same name
                    commonFiles.add(entry)
                } else {
                    // File is same in artifact 2 as in artifact 1 with different names
                    renamedFiles.add(entry)
                    originalNamesOfRenamedFiles.add(commonArtifact1File.name)
                }
            } else {
                if (artifact1Filenames.contains(entry.name)) {
                    // Filename exists in artifact 1 but not same contents
                    changedFiles.add(entry)
                } else {
                    // New file
                    addedFiles.add(entry)
                }
            }
        }

        // Left-over files in artifact1 that were not found in artifact2 and not renamed
        val artifact2Filenames = artifact2.entries.map(ArtifactEntry::name).toSet()
        val notRemovedFiles = artifact2Filenames + originalNamesOfRenamedFiles
        removedFiles = artifact1Files.values.filter { !notRemovedFiles.contains(it.name) }

        return ArtifactComparison (
            artifact1Summary,
            artifact2Summary,
            commonFiles,
            addedFiles,
            removedFiles,
            changedFiles,
            renamedFiles
        )
    }

    fun writeToTerminal() {
        println("Artifact comparison summary")
        println("===========================")
        println()
        println("Artifact A: ${comparisonInfo.artifact1.name}")
        println("  Files: ${comparisonInfo.artifact1.noOfFiles}")
        println("  Size: ${comparisonInfo.artifact1.totalSize}B")
        println("  Last modified: ${formatEntryTime(comparisonInfo.artifact1.lastModified)}")
        println()
        println("Artifact B: ${comparisonInfo.artifact2.name}")
        println("  Files: ${comparisonInfo.artifact2.noOfFiles}")
        println("  Size: ${comparisonInfo.artifact2.totalSize}B")
        println("  Last modified: ${formatEntryTime(comparisonInfo.artifact2.lastModified)}")
        println()
        println("Files in common: ${comparisonInfo.commonFiles.size}")
        println()
        println("Differences:")
        println("  - Added files: ${comparisonInfo.addedFiles.size}")
        println("  - Removed files: ${comparisonInfo.removedFiles.size}")
        println("  - Changed files: ${comparisonInfo.changedFiles.size}")
        println("  - Renamed files: ${comparisonInfo.renamedFiles.size}")

        if (ignoredFiles.isNotEmpty()) {
            println()
            println("Comparison ignored these files: ${ignoredFiles.joinToString { it.name }}")
        }

        println()
        println("Similarity score: ${"%.1f".format(calculateSimilarityScore())}%")
        println()
    }

    fun writeToJson() {
        println("Writing full comparison information to JSON...")
        val jsonString = json.encodeToString(comparisonInfo)
        val outputFilename = "${File(file1).nameWithoutExtension}-${File(file2).nameWithoutExtension}-comparison.json"
        File(outputFilename).writeText(jsonString)
        print("View full comparison JSON at $outputFilename")
    }

    private fun formatEntryTime(epochMillis: Long): String {
        val instant = Instant.ofEpochMilli(epochMillis)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }

    private fun calculateSimilarityScore(): Double {
        val totalFiles = comparisonInfo.commonFiles.size +
                comparisonInfo.addedFiles.size +
                comparisonInfo.removedFiles.size +
                comparisonInfo.changedFiles.size +
                comparisonInfo.renamedFiles.size
        val weightedRenamed = 0.9 * comparisonInfo.renamedFiles.size
        val common = comparisonInfo.commonFiles.size
        return ((common + weightedRenamed) / totalFiles.toDouble()) * 100
    }

    private fun summariseArtifact(artifact: ArtifactInfo): ArtifactSummary {
        var totalSize: Long = 0
        var lastModified: Long = 0
        for (entry in artifact.entries) {
            totalSize += entry.size
            lastModified = max(lastModified, entry.time)
        }
        return ArtifactSummary(artifact.name, artifact.entries.size, totalSize, lastModified)
    }
}

@Serializable
data class ArtifactComparison(
    val artifact1: ArtifactSummary,  // simple summary of artifact 1
    val artifact2: ArtifactSummary,  // simple summary of artifact 2
    val commonFiles: List<ArtifactEntry>,  // files that are in both
    val addedFiles: List<ArtifactEntry>,   // files in artifact 2 but not artifact 1
    val removedFiles: List<ArtifactEntry>, // files in artifact 1 but not artifact 2
    val changedFiles: List<ArtifactEntry>, // files that have same names in both but different contents
    val renamedFiles: List<ArtifactEntry>, // files that have same contents in both but different names
)

@Serializable
data class ArtifactSummary(
    val name: String,
    val noOfFiles: Int,
    val totalSize: Long,
    val lastModified: Long,
)

data class FileKey(val crc: Long, val size: Long)