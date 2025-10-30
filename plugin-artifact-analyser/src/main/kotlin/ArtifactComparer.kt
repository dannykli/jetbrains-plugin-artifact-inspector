package dannykli.pluginanalyser

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

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
        // The correctness of this function relies on each file's sha256 being unique, specifically that
        //   the following holds:
        //      file A and file B have same file key ==> file A and B have the same contents
        // We are able to assume that this holds in all practical scenarios

        // Artifact comparison variables
        val artifact1Summary = summariseArtifact(artifact1)
        val artifact2Summary = summariseArtifact(artifact2)
        val commonFiles: MutableList<ArtifactEntry> = mutableListOf()
        val addedFiles: MutableList<ArtifactEntry> = mutableListOf()
        val changedFiles: MutableList<ArtifactEntry> = mutableListOf()
        val renamedFiles: MutableList<ArtifactEntry> = mutableListOf()
        val removedFiles: List<ArtifactEntry>
        val duplicateFiles: DuplicateFileSet

        val artifact1Filenames = artifact1.entries.map(ArtifactEntry::name).toSet()

        // stores files in artifact 1 as (sha256, entry) pairs
        val artifact1FileMap: MutableMap<FileKey, ArtifactEntry> = mutableMapOf()

        // stores sets of files that are duplicated
        val duplicateContentInArtifact1: MutableMap<FileKey, MutableSet<ArtifactEntry>> = mutableMapOf()

        for (entry in artifact1.entries) {
            val key = FileKey(entry.sha256)
            if (artifact1FileMap.containsKey(key)) {
                val originalEntry = artifact1FileMap[key]!!
                duplicateContentInArtifact1.getOrPut(key) { mutableSetOf() }.addAll(listOf(entry, originalEntry))
                continue
            }
            artifact1FileMap[key] = entry
        }

        val artifact2FileMap: MutableMap<FileKey, ArtifactEntry> = mutableMapOf()

        val duplicateContentInArtifact2: MutableMap<FileKey, MutableSet<ArtifactEntry>> = mutableMapOf()

        for (entry in artifact2.entries) {
            val key = FileKey(entry.sha256)
            if (artifact2FileMap.containsKey(key)) {
                val originalEntry = artifact2FileMap[key]!!
                duplicateContentInArtifact2.getOrPut(key) { mutableSetOf() }.addAll(listOf(entry, originalEntry))
                continue
            }
            artifact2FileMap[key] = entry
        }

        val originalNamesOfRenamedFiles = mutableSetOf<String>()

        // Iterate through artifact2 files and determine whether they are common, renamed, changed or added
        for (entry in artifact2.entries) {
            val key = FileKey(entry.sha256)
            val commonFile = artifact1FileMap[key]
            if (commonFile != null) {
                // If artifact2 entry has same content and name as an entry in artifact1, then add to common files
                if (commonFile.name == entry.name || duplicateContentInArtifact1[key]?.any({it.name == entry.name}) ?: false) {
                    // File is same in artifact 2 as in artifact 1 with same name
                    commonFiles.add(entry)
                } else {
                    // File is same in artifact 2 as in artifact 1 with different names
                    renamedFiles.add(entry)
                    originalNamesOfRenamedFiles.add(commonFile.name)
                }
            } else {
                if (artifact1Filenames.contains(entry.name)) {
                    // Filename exists in artifact 1 but do not have same contents
                    changedFiles.add(entry)
                } else {
                    // New file
                    addedFiles.add(entry)
                }
            }
        }

        // Left-over files in artifact1 that were not found in artifact2 and not renamed are marked as removed
        val artifact2Filenames = artifact2.entries.map(ArtifactEntry::name).toSet()
        val notRemovedFiles = artifact2Filenames + originalNamesOfRenamedFiles
        removedFiles = artifact1.entries.filter { !notRemovedFiles.contains(it.name) }

        duplicateFiles = DuplicateFileSet(
            duplicateContentInArtifact1.values.map { it.toSet() },
            duplicateContentInArtifact2.values.map { it.toSet() }
        )

        return ArtifactComparison (
            artifact1Summary,
            artifact2Summary,
            commonFiles,
            addedFiles,
            removedFiles,
            changedFiles,
            renamedFiles,
            duplicateFiles
        )
    }

    fun writeToTerminal(verbose: Boolean) {
        println("Artifact comparison summary")
        println("===========================")
        println()
        println("Artifact A: ${comparisonInfo.artifact1.name}")
        println("  Files: ${comparisonInfo.artifact1.noOfFiles}")
        println("  Size: ${formatBytes(comparisonInfo.artifact1.totalSize)}")
        println()
        println("Artifact B: ${comparisonInfo.artifact2.name}")
        println("  Files: ${comparisonInfo.artifact2.noOfFiles}")
        println("  Size: ${formatBytes(comparisonInfo.artifact2.totalSize)}")
        println()
        println("Files in common: ${comparisonInfo.commonFiles.size}")
        println()
        println("Differences:")
        println("  - Added files: ${comparisonInfo.addedFiles.size}")
        println("  - Removed files: ${comparisonInfo.removedFiles.size}")
        println("  - Changed files: ${comparisonInfo.changedFiles.size}")
        println("  - Renamed files: ${comparisonInfo.renamedFiles.size}")
        println()

        if (comparisonInfo.duplicateFiles.artifact1Duplicates.isNotEmpty()) {
            println("Note: Found ${comparisonInfo.duplicateFiles.artifact1Duplicates.size} sets of duplicate files in artifact A"
                + if (verbose) ":" else "")
            if (verbose) {
                printDuplicateFiles(comparisonInfo.duplicateFiles.artifact1Duplicates)
                println()
            }
        }

        if (comparisonInfo.duplicateFiles.artifact2Duplicates.isNotEmpty()) {
            println("Note: Found ${comparisonInfo.duplicateFiles.artifact2Duplicates.size} sets of duplicate files in artifact B"
                + if (verbose) ":" else "")
            if (verbose) {
                printDuplicateFiles(comparisonInfo.duplicateFiles.artifact2Duplicates)
                println()
            }
        }

        println("Similarity score: ${"%.1f".format(calculateSimilarityScore())}%")
        println()
    }

    private fun printDuplicateFiles(duplicates: List<Set<ArtifactEntry>>) {
        val n = duplicates.size
        for (i in 0..<n) {
            val setOfDuplicates = duplicates[i]
            setOfDuplicates.forEach { println("  - ${it.name}") }
            if (i < n - 1) {
                println()
            }
        }
    }

    fun writeToJson() {
        println("Writing full comparison information to JSON...")
        val jsonString = json.encodeToString(comparisonInfo)
        val filename1 = File(file1).nameWithoutExtension.removeSuffix("-report")
        val filename2 = File(file2).nameWithoutExtension.removeSuffix("-report")
        val outputFilename = "$filename1-$filename2-comparison.json"
        File(outputFilename).writeText(jsonString)
        print("View full comparison JSON at $outputFilename")
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        var value = bytes.toDouble()
        var i = 0
        while (value >= 1024 && i < units.size - 1) {
            value /= 1024
            i++
        }
        return String.format("%.2f %s", value, units[i])
    }

    private fun calculateSimilarityScore(): Double {
        val totalFiles = comparisonInfo.commonFiles.size +
                comparisonInfo.addedFiles.size +
                comparisonInfo.removedFiles.size +
                comparisonInfo.changedFiles.size +
                comparisonInfo.renamedFiles.size
        if (totalFiles == 0) return 100.0
        val weightedRenamed = 0.9 * comparisonInfo.renamedFiles.size
        val common = comparisonInfo.commonFiles.size
        return ((common + weightedRenamed) / totalFiles.toDouble()) * 100
    }

    private fun summariseArtifact(artifact: ArtifactInfo): ArtifactSummary {
        var totalSize: Long = 0
        for (entry in artifact.entries) {
            totalSize += entry.size
        }
        return ArtifactSummary(artifact.name, artifact.entries.size, totalSize)
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
    val duplicateFiles: DuplicateFileSet,  // files where the entire contents is duplicated within a respective artifact
)

@Serializable
data class ArtifactSummary(
    val name: String,
    val noOfFiles: Int,
    val totalSize: Long,
)

data class FileKey(val sha256: String)

@Serializable
data class DuplicateFileSet(val artifact1Duplicates: List<Set<ArtifactEntry>>, val artifact2Duplicates: List<Set<ArtifactEntry>>)