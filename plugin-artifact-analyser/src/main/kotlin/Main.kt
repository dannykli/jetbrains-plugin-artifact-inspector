package dannykli.pluginanalyser

import java.io.File

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage:")
        println("  analyse <artifact.zip|jar>")
        println("  compare <file1> <file2>")
        println()
        println("Options:")
        println("  -v   Print detailed comparison results (e.g. duplicate files, etc.)")
        println("  -a   Compare two plugin artifacts directly (analyse + compare)")
        return
    }

    val verbose = args.contains("-v")
    val both = args.contains("-a")

    when (val command = args[0]) {
        "analyse" -> {
            if (args.size < 2) {
                println("Error: Missing artifact path")
                return
            }
            val artifactPath = args[1]

            if (!validateFilePath(artifactPath, arrayOf("jar", "zip")))
                return

            analyseFile(artifactPath)
        }

        "compare" -> {
            val numOptionsSpecified = (if (verbose) 1 else 0) + (if (both) 1 else 0)
            if (args.size < 3 + numOptionsSpecified) {
                println("Error: Missing file paths for comparison")
                return
            }
            var file1 = args[1 + numOptionsSpecified]
            var file2 = args[2 + numOptionsSpecified]

            if (both) {
                if (!validateFilePath(file1, arrayOf("jar", "zip")))
                    return
                if (!validateFilePath(file2, arrayOf("jar", "zip")))
                    return
                file1 = analyseFile(file1)
                file2 = analyseFile(file2)
            } else {
                if (!validateFilePath(file1, arrayOf("json")))
                    return
                if (!validateFilePath(file2, arrayOf("json")))
                    return
            }

            val filename1 = File(file1).nameWithoutExtension.removeSuffix("-report")
            val filename2 = File(file2).nameWithoutExtension.removeSuffix("-report")

            println("Comparing $filename1 and $filename2 ...")

            val artifactComparer = ArtifactComparer(file1, file2)
            artifactComparer.writeToTerminal(verbose)
            artifactComparer.writeToJson()
        }

        else -> {
            println("Unknown command: $command")
            println("Available commands: analyse, compare")
        }
    }
}