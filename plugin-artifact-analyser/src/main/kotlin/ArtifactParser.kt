package org.example

import java.io.File
import java.util.zip.ZipFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class ArtifactParser(val filename: String) {
    private val artifact: ArtifactInfo

    private val json = Json { prettyPrint = true }

    init {
        artifact = parse()
    }

    fun parse(): ArtifactInfo {
        val file = File(this.filename)
        val zipEntries = ZipFile(file).entries().asSequence().toList()
        val artifactEntries: MutableList<ArtifactEntry> = mutableListOf();
        for (entry in zipEntries) {
            artifactEntries.add(ArtifactEntry(entry.name, entry.crc, entry.size, entry.time, entry.isDirectory))
        }
        return ArtifactInfo(filename, artifactEntries)
    }

    fun writeTo(filename: String) {
        val jsonString = json.encodeToString(artifact.entries)
        File(filename).writeText(jsonString)
    }
}

data class ArtifactInfo(val name: String, val entries: List<ArtifactEntry>)

@Serializable
data class ArtifactEntry(val name: String, val crc: Long, val size: Long, val time: Long, val isDirectory: Boolean)