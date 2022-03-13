package com.ktrueda.kotkot

import mu.KotlinLogging
import java.io.ByteArrayInputStream
import java.util.*

private fun ByteArrayInputStream.read(n: Int): ByteArray {
    val ba = ByteArray(n)
    repeat(n) {
        ba[it] = read().toByte()
    }
    return ba
}

class Executor(private val classFile: ClassFile) {
    private val logger = KotlinLogging.logger {}
    private val stack = Stack<Any>()
    fun runMain() {
        val mainMethod = classFile.findMethod("main") ?: throw RuntimeException("main method not found")
        val mainMethodCode = classFile.getBinaryCode(mainMethod) ?: throw RuntimeException("main method Code not found")
        logger.debug("mainMethodCode $mainMethodCode")
        run(mainMethodCode)
    }

    private fun run(code: Code) {
        val inputStream = code.binaryCode.inputStream()
        while (true) {
            val opCode = inputStream.read()
            logger.info(
                """
                ####### new  op code ###########
                opCode: $opCode
                stack: $stack
                ################################
                """.trimIndent()
            )
            when (opCode) {
                0x12 -> ldc(inputStream)
                0xb2 -> getstatic(inputStream)
                0xb6 -> invokevirtual(inputStream)
                else -> throw RuntimeException("Not-implemented opcode $opCode")
            }
        }
    }

    //0x12
    private fun ldc(inputStream: ByteArrayInputStream) {
        val cpIndex = inputStream.read(1).toInt()
        val cp = classFile.constantPools[cpIndex - 1]
        if (cp is ConstantPoolString) {
            val cpUtf8 = classFile.constantPools[cp.stringIndex - 1] as ConstantPoolUtf8
            stack.push(cpUtf8.info.decodeToString())
        } else if (cp is ConstantPoolInteger) {
            stack.push(cp.value)
        } else {
            throw RuntimeException("Unknown")
        }
    }

    //0xb2
    private fun getstatic(inputStream: ByteArrayInputStream) {
        logger.info("OPCODE: getstatic")
        val poolIndex = inputStream.read(2).toInt()
        val cpFieldRef = classFile.constantPools[poolIndex - 1] as ConstantPoolFieldRef

        val cpClass = classFile.constantPools[cpFieldRef.classIndex - 1] as ConstantPoolClass
        val cpClassName = classFile.constantPools[cpClass.nameIndex - 1] as ConstantPoolUtf8
        val className = cpClassName.info.decodeToString()

        val cpNameAndType = classFile.constantPools[cpFieldRef.nameAndTypeIndex - 1] as ConstantPoolNameAndType
        val cpMethodName = classFile.constantPools[cpNameAndType.nameIndex - 1] as ConstantPoolUtf8
        val methodName = cpMethodName.info.decodeToString()

        logger.debug("$className.$methodName")

        stack.push("$className.$methodName")
    }

    //0xb6
    private fun invokevirtual(inputStream: ByteArrayInputStream) {
        logger.info("OPCODE: invokevirtual")
        val cpIndex = inputStream.read(2).toInt()

        val cpMethodRef = classFile.constantPools[cpIndex - 1] as ConstantPoolMethodRef
        val cpClass = classFile.constantPools[cpMethodRef.classIndex - 1] as ConstantPoolClass
        val cpClassNameUtf8 = classFile.constantPools[cpClass.nameIndex - 1] as ConstantPoolUtf8
        val className = cpClassNameUtf8.info.decodeToString()

        val cpNameAndType = classFile.constantPools[cpMethodRef.nameAndTypeIndex - 1] as ConstantPoolNameAndType
        val methodNameUtf8 = classFile.constantPools[cpNameAndType.nameIndex - 1] as ConstantPoolUtf8
        val methodArgsExpUtf8 = classFile.constantPools[cpNameAndType.descriptorIndex - 1] as ConstantPoolUtf8

        logger.debug("$className.${methodNameUtf8.info.decodeToString()} ${methodArgsExpUtf8.info.decodeToString()}")

    }
}