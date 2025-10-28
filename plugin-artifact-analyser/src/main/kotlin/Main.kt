package dannykli.pluginanalyser

import java.io.File

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage:")
        println("  inspect <artifact.zip|jar>")
        println("  compare <output1> <output2>")
        return
    }

    when (val command = args[0]) {
        "inspect" -> {
            if (args.size < 2) {
                println("Error: Missing artifact path")
                return
            }
            val artifactPath = args[1]
            validateFilePath(artifactPath, arrayOf("jar", "zip"))
            println("Inspecting $artifactPath ...")
            val artifactParser = ArtifactParser(artifactPath)
            val directory = File(artifactPath).parent ?: "."
            val baseName = File(artifactPath).nameWithoutExtension
            artifactParser.writeTo("$directory/$baseName-report.json")
        }

        "compare" -> {
            if (args.size < 3) {
                println("Error: Missing file paths for comparison")
                return
            }
            val file1 = args[1]
            val file2 = args[2]
            validateFilePath(file1, arrayOf("json"))
            println("Comparing $file1 and $file2 ...")
            val artifactComparer = ArtifactComparer(file1, file2)
            artifactComparer.writeToTerminal()
            artifactComparer.writeToJson()
        }

        else -> {
            println("Unknown command: $command")
            println("Available commands: inspect, compare")
        }
    }
}