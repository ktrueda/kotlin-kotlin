package com.ktrueda.kotkot

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.streams.asStream


val skipCompile = true

class MainTest {

    companion object {
        @BeforeAll
        fun compile() {
            if (!skipCompile) {
                ProcessBuilder().command(listOf("sh", "build.sh"))
                    .directory(File("./src/test/resources"))
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
                    .waitFor();
            }
        }
    }

    @TestFactory
    fun runAllTestFiles() = File("./target/src/").walk()
        .map { it.toPath() }
        .filter { it.toAbsolutePath().toString().endsWith(".kt") }
        .map { it.fileName.toString().split('.')[0] }
        .map {
            DynamicTest.dynamicTest(it) {
                val cf: ClassFile = ClassFile.load(File("./target/src/${it}Kt.class"))
                val executor = Executor(cf)
                executor.runMain()
            }
        }.asStream()
}