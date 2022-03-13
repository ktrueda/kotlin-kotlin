package com.ktrueda.kotkot

import org.junit.jupiter.api.Test
import java.io.File

val skipCompile = false

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

        val cf: ClassFile = ClassFile.load(File("./target/src/HelloWorldKt.class"))
        val executor = Executor(cf)
        executor.runMain()
    }
}