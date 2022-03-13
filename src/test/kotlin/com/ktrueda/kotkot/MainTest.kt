package com.ktrueda.kotkot

import org.junit.jupiter.api.Test
import java.io.File

val skipCompile = true

class MainTest {
    @Test
    fun can_run() {
        // run kotlinc
        if (!skipCompile) {
            ProcessBuilder().command(listOf("sh", "build.sh"))
                .directory(File("./src/test/resources"))
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
                .waitFor();
        }

        File("./target/src/").walk()
            .map { it.toPath() }
            .filter { it.toAbsolutePath().toString().endsWith(".kt") }
            .map { it.fileName.toString().split('.')[0] }
            .forEach {
                val cf: ClassFile = ClassFile.load(File("./target/src/${it}Kt.class"))
                val executor = Executor(cf)
                executor.runMain()
            }
    }
}