package dannykli.pluginanalyser

import kotlinx.serialization.Serializable

@Serializable
data class ArtifactInfo(val name: String, val entries: List<ArtifactEntry>)

@Serializable
data class ArtifactEntry(val name: String, val size: Long, val time: Long, val sha256: String)