package dannykli.pluginanalyser

import java.io.File


fun validateFilePath(path: String, extensions: Array<String>): Boolean {
    val file = File(path)

    if (!file.exists()) {
        println("Error: File not found: $path")
        return false
    }
    if (!file.isFile) {
        println("Error: Path is not a file: $path")
        return false
    }
    if (!file.canRead()) {
        println("Error: File cannot be read: $path")
        return false
    }

    return extensions.contains(file.extension)
}

fun analyseFile(filePath: String): String {
    println("Inspecting $filePath ...")

    val artifactParser = ArtifactParser(filePath)
    val directory = File(filePath).parent ?: "."
    val baseName = File(filePath).nameWithoutExtension
    artifactParser.writeTo("$directory/$baseName-report.json")
    return "$directory/$baseName-report.json"
}