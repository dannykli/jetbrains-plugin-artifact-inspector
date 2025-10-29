package dannykli.pluginanalyser

import java.io.File
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.InputStream
import java.util.zip.ZipInputStream
import java.security.MessageDigest

class ArtifactParser(val filename: String) {
    private val artifact: ArtifactInfo

    private val json = Json { prettyPrint = true }

    init {
        artifact = parse()
    }

    fun parse(): ArtifactInfo {
        val file = File(this.filename)
        val zipFile = ZipFile(file)
        val zipEntries = zipFile.entries().asSequence().toList()
        val artifactEntries: MutableList<ArtifactEntry> = mutableListOf();
        val messageDigest = MessageDigest.getInstance("SHA-256")

        for (entry in zipEntries) {
            if (!entry.isDirectory) {
                val inputStream = zipFile.getInputStream(entry)

                val sha256 = getSha256Hash(inputStream, messageDigest)

                artifactEntries.add(ArtifactEntry(entry.name, entry.crc, entry.size, sha256))
            }

        }
        return ArtifactInfo(filename, artifactEntries)
    }

    private fun getSha256Hash(inputStream: InputStream, messageDigest: MessageDigest): String {
        // Read file's bytes and feed them to hasher
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            messageDigest.update(buffer, 0, bytesRead)
        }

        // Get final hash
        val hashBytes = messageDigest.digest()

        // Reset hasher for next file
        messageDigest.reset()

        return hashBytes.toHexString()
    }

    fun writeTo(filename: String) {
        println("Written artifact summary to $filename")
        val jsonString = json.encodeToString(artifact)
        File(filename).writeText(jsonString)
    }
}