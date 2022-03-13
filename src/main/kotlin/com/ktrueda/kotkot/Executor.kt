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
        val mainMethod = classFile.findMethod("main", "([Ljava/lang/String;)V")
        if (mainMethod.isEmpty()) {
            throw RuntimeException("main method not found")
        }
        val mainMethodCode =
            classFile.getBinaryCode(mainMethod[0]) ?: throw RuntimeException("main method Code not found")
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
                0xb1 -> {
                    _return(inputStream)
                    return
                }
                0xb6 -> invokevirtual(inputStream)
                0xb8 -> invokestatic(inputStream)
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

    //0xb1
    private fun _return(inputStream: ByteArrayInputStream) {

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
        val methodName = methodNameUtf8.info.decodeToString()
        val methodArgsExpUtf8 = classFile.constantPools[cpNameAndType.descriptorIndex - 1] as ConstantPoolUtf8

        logger.debug("$className.${methodName} ${methodArgsExpUtf8.info.decodeToString()}")

        val args = mutableListOf<Any>()
        repeat(1) {//TODO
            args.add(stack.pop())
        }

        if (className == "java/io/PrintStream") {
            println(args[args.size - 1])//TODO
        } else {
            TODO()
        }
    }

    //0xb8
    private fun invokestatic(inputStream: ByteArrayInputStream) {
        val indexByte1 = inputStream.read()
        val indexByte2 = inputStream.read()
        val index = indexByte1 * 255 + indexByte2

        val cpMethodRef = classFile.constantPools[index - 1] as ConstantPoolMethodRef

        val cpClass = classFile.constantPools[cpMethodRef.classIndex - 1] as ConstantPoolClass
        val cpNameUtf8 = classFile.constantPools[cpClass.nameIndex - 1] as ConstantPoolUtf8
        val className = cpNameUtf8.info.decodeToString()

        val cpMethod = classFile.constantPools[cpMethodRef.nameAndTypeIndex - 1] as ConstantPoolNameAndType
        val cpMethodUtf8 = classFile.constantPools[cpMethod.nameIndex - 1] as ConstantPoolUtf8
        val methodName = cpMethodUtf8.info.decodeToString()
        val methodDescriptorUtf8 = classFile.constantPools[cpMethod.descriptorIndex - 1] as ConstantPoolUtf8
        val methodDescriptor = methodDescriptorUtf8.info.decodeToString()

        assert(className == classFile.getThisClassName())//TODO

        val foundMethods = classFile.findMethod(methodName, methodDescriptor)
        if (foundMethods.isEmpty()) {
            throw RuntimeException("$methodName $methodDescriptor not found")
        }

        val targetCode = classFile.getBinaryCode(foundMethods[0])//TODO
    }
}