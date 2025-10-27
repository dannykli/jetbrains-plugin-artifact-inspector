package org.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.max
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

typealias Crc = Long
typealias FileSize = Long

class ArtifactComparer(val file1: String, val file2: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val artifact1: ArtifactInfo
    private val artifact2: ArtifactInfo
    private val comparisonInfo: ArtifactComparison

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
        // Artifact comparison variables
        val artifact1Summary = summariseArtifact(artifact1)
        val artifact2Summary = summariseArtifact(artifact2)
        val commonFiles: MutableList<ArtifactEntry> = mutableListOf()
        val commonDirs: MutableList<ArtifactEntry> = mutableListOf()
        val addedFiles: MutableList<ArtifactEntry> = mutableListOf()
        val addedDirs: MutableList<ArtifactEntry> = mutableListOf()
        val removedFiles: MutableList<ArtifactEntry> = mutableListOf()
        val removedDirs: MutableList<ArtifactEntry> = mutableListOf()
        val changedFiles: MutableList<ArtifactEntry> = mutableListOf()
        val renamedFiles: MutableList<ArtifactEntry> = mutableListOf()

        val artifact1Filenames = artifact1.entries.map(ArtifactEntry::name).toSet()
        val artifact1Files: MutableMap<FileKey, ArtifactEntry> = mutableMapOf()
        val artifact1Dirs: MutableMap<String, ArtifactEntry> = mutableMapOf()

        for (entry in artifact1.entries) {
            if (entry.isDirectory) {
                artifact1Dirs[entry.name] = entry
            } else {
                artifact1Files[FileKey(entry.crc, entry.size)] = entry
            }
        }

        for (entry in artifact2.entries) {
            if (entry.isDirectory) {
                if (artifact1Dirs.containsKey(entry.name)) {
                    commonDirs.add(entry)
                    artifact1Dirs.remove(entry.name)
                } else {
                    addedDirs.add(entry)
                }
            } else {
                val commonArtifact1File = artifact1Files[FileKey(entry.crc, entry.size)]
                if (commonArtifact1File != null) {
                    if (commonArtifact1File.name == entry.name) {
                        commonFiles.add(entry)
                    } else {
                        renamedFiles.add(entry)
                    }
                    artifact1Files.remove(FileKey(entry.crc, entry.size))
                } else {
                    if (artifact1Filenames.contains(entry.name)) {
                        changedFiles.add(entry)
                    } else {
                        addedFiles.add(entry)
                    }
                }
            }
        }

        for ((_, entry) in artifact1Dirs) {
            removedDirs.add(entry)
        }

        for ((_, entry) in artifact1Files) {
            removedFiles.add(entry)
        }

        return ArtifactComparison (
            artifact1Summary,
            artifact2Summary,
            commonFiles,
            commonDirs,
            addedFiles,
            addedDirs,
            removedFiles,
            removedDirs,
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
        println("  Directories: ${comparisonInfo.artifact1.noOfDirs}")
        println("  Size: ${comparisonInfo.artifact1.totalSize}B")
        println("  Last modified: ${formatEntryTime(comparisonInfo.artifact1.lastModified)}")
        println()
        println("Artifact B: ${comparisonInfo.artifact2.name}")
        println("  Files: ${comparisonInfo.artifact2.noOfFiles}")
        println("  Directories: ${comparisonInfo.artifact2.noOfDirs}")
        println("  Size: ${comparisonInfo.artifact2.totalSize}B")
        println("  Last modified: ${formatEntryTime(comparisonInfo.artifact2.lastModified)}")
        println()
        println("Common entries: ${comparisonInfo.commonFiles.size + comparisonInfo.commonDirs.size}")
        println("  - Files in common: ${comparisonInfo.commonFiles.size}")
        println("  - Directories in common: ${comparisonInfo.commonDirs.size}")
        println()
        println("Differences:")
        println("  - Added files: ${comparisonInfo.addedFiles.size}")
        println("  - Added directories: ${comparisonInfo.addedDirs.size}")
        println("  - Removed files: ${comparisonInfo.removedFiles.size}")
        println("  - Removed directories: ${comparisonInfo.removedDirs.size}")
        println("  - Changed files: ${comparisonInfo.changedFiles.size}")
        println("  - Renamed files: ${comparisonInfo.renamedFiles.size}")
        println()
        println("Similarity score: ${"%.1f".format(calculateSimilarityScore())}%")
        println()
    }

    fun writeToJson() {
        println("Writing full comparison information to JSON...")
        val jsonString = json.encodeToString(comparisonInfo)
        File("$file1-$file2-comparison.json").writeText(jsonString)
    }

    private fun formatEntryTime(epochMillis: Long): String {
        val instant = Instant.ofEpochMilli(epochMillis)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }

    private fun calculateSimilarityScore(): Double {
        val totalEntries = artifact1.entries.size + artifact2.entries.size
        val commonEntries = comparisonInfo.commonFiles.size + comparisonInfo.commonDirs.size
        return (commonEntries.toDouble() / totalEntries.toDouble()) * 100
    }

    private fun summariseArtifact(artifact: ArtifactInfo): ArtifactSummary {
        var noOfFiles = 0
        var noOfDirs = 0
        var totalSize: Long = 0
        var lastModified: Long = 0
        for (entry in artifact.entries) {
            if (entry.isDirectory) {
                noOfDirs++
            } else {
                noOfFiles++
                totalSize += entry.size
            }
            lastModified = max(lastModified, entry.time)
        }
        return ArtifactSummary(artifact.name, noOfFiles, noOfDirs, totalSize, lastModified)
    }
}

@Serializable
data class ArtifactComparison(
    val artifact1: ArtifactSummary,  // simple summary of artifact 1
    val artifact2: ArtifactSummary,  // simple summary of artifact 2
    val commonFiles: List<ArtifactEntry>,  // files that are in both
    val commonDirs: List<ArtifactEntry>,   // dirs that are in both
    val addedFiles: List<ArtifactEntry>,   // files in artifact 2 but not artifact 1
    val addedDirs: List<ArtifactEntry>,    // files in artifact 2 but not artifact 1
    val removedFiles: List<ArtifactEntry>, // files in artifact 1 but not artifact 2
    val removedDirs: List<ArtifactEntry>,  // dirs in artifact 1 but not artifact 2
    val changedFiles: List<ArtifactEntry>, // files that have same names in both but different contents
    val renamedFiles: List<ArtifactEntry>, // files that have same contents in both but different names
)

// Print to terminal length of the above arrays, and in a summary .json file actually have the list of files

@Serializable
data class ArtifactSummary(
    val name: String,
    val noOfFiles: Int,
    val noOfDirs: Int,
    val totalSize: Long,
    val lastModified: Long,
)

data class FileKey(val crc: Long, val size: Long)