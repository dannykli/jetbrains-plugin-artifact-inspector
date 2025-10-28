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