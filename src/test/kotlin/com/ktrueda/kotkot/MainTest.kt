package com.ktrueda.kotkot

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File
import java.util.stream.Stream
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
    fun runAllTestFiles(): Stream<DynamicTest> {
        val mcl = MyClassLoader.load(File("./target/src/"))
        return File("./target/src/").walk()
            .map { it.toPath() }
            .filter { it.toAbsolutePath().toString().endsWith(".kt") }
            .map { it.fileName.toString().split('.')[0] }
            .map {
                DynamicTest.dynamicTest(it) {
                    val executor = Executor(mcl, "${it}Kt")
                    executor.runMain()
                }
            }.asStream()
    }

    @Test
    fun classLoader() {
        MyClassLoader.load(File("./target/src/"))
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun dev() {
//        val mcl = MyClassLoader.load(File("./target/src/"))
//        val executor = Executor(mcl, "OtherClassKt")
//        executor.runMain()
        ClassFile.load(File("./target/src/kotlin/ranges/URangesKt"))
    }
}