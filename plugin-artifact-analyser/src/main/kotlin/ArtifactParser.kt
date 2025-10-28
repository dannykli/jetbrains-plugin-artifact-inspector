package dannykli.pluginanalyser

import java.io.File
import java.util.zip.ZipFile
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
            if (!entry.isDirectory) {

                artifactEntries.add(ArtifactEntry(entry.name, entry.crc, entry.size, entry.time))
            }

        }
        return ArtifactInfo(filename, artifactEntries)
    }

    fun writeTo(filename: String) {
        println("Written artifact summary to $filename")
        val jsonString = json.encodeToString(artifact)
        File(filename).writeText(jsonString)
    }
}