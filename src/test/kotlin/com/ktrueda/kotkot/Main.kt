package com.ktrueda.kotkot

import java.io.File

fun main(){
    // run kotlinc
    ProcessBuilder().command(listOf("sh", "build.sh"))
        .directory(File("./src/test/resources"))
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start();
}