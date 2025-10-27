package org.example

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
            // TODO: call validate file path function
            println("Inspecting $artifactPath ...")
            val artifactParser = ArtifactParser(artifactPath)
            artifactParser.writeTo("$artifactPath-out")
        }

        "compare" -> {
            if (args.size < 3) {
                println("Error: Missing file paths for comparison")
                return
            }
            val file1 = args[1]
            val file2 = args[2]
            // TODO: call validate file path function
            println("Comparing $file1 and $file2 ...")
            // TODO: Call your comparison function here
            // use filesize and crc to reduce chance of false positive
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