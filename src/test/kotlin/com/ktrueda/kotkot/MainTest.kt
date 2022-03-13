package com.ktrueda.kotkot

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File
import kotlin.streams.asStream


class MainTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun compile() {
            ProcessBuilder().command(listOf("sh", "build.sh"))
                .directory(File("./src/test/resources"))
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
                .waitFor();
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

    @Test
    fun classLoader() {
        val mcl = MyClassLoader.load(File("./target/src/"))
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun dev() {
        val cf: ClassFile = ClassFile.load(File("./target/src/OtherClassKt.class"))
        val executor = Executor(cf)
        executor.runMain()
    }
}