package dannykli.pluginanalyser

import java.io.File

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage:")
        println("  analyse <artifact.zip|jar>")
        println("  compare <output1> <output2>")
        println()
        println("Options:")
        println("  -v   Print detailed comparison results (e.g. duplicate files, etc.)")
        return
    }

    val verbose = args.contains("-v")

    when (val command = args[0]) {
        "analyse" -> {
            if (args.size < 2) {
                println("Error: Missing artifact path")
                return
            }
            val artifactPath = args[1]

            if (!validateFilePath(artifactPath, arrayOf("jar", "zip")))
                return

            println("Inspecting $artifactPath ...")

            val artifactParser = ArtifactParser(artifactPath)
            val directory = File(artifactPath).parent ?: "."
            val baseName = File(artifactPath).nameWithoutExtension
            artifactParser.writeTo("$directory/$baseName-report.json")
        }

        "compare" -> {
            if (args.size < 3 || (args.size == 3 && verbose)) {
                println("Error: Missing file paths for comparison")
                return
            }
            val file1 = if (verbose) args[2] else args[1]
            val file2 = if (verbose) args[3] else args[2]

            if (!validateFilePath(file1, arrayOf("json")))
                return
            if (!validateFilePath(file2, arrayOf("json")))
                return

            println("Comparing $file1 and $file2 ...")

            val artifactComparer = ArtifactComparer(file1, file2)
            artifactComparer.writeToTerminal(verbose)
            artifactComparer.writeToJson()
        }

        else -> {
            println("Unknown command: $command")
            println("Available commands: inspect, compare")
        }
    }
}