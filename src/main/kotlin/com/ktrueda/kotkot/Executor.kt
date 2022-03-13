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

class Frame(private val classFile: ClassFile, private val code: Code) {
    private val logger = KotlinLogging.logger {}
    private val operandStack = Stack<Any>()
    private val localVariables = Array<Any>(code.maxLocals) { 0 }
    private val inputStream: ByteArrayInputStream = code.binaryCode.inputStream()

    fun run(): Frame? {
        inputStream.reset()
        while (true) {
            val opCode = inputStream.read()
            logger.info(
                """
                ####### new  op code ###########
                frame: ${this.hashCode()}
                opCode: ${Integer.toHexString(opCode)}
                operandStack: $operandStack
                localVariables: $localVariables
                ################################
                """.trimIndent()
            )
            val nextFrame = when (opCode) {
                0x6 -> iconst(3, inputStream)
                0x12 -> ldc(inputStream)
                0x1a -> iload(0, inputStream)
                0x3b -> istore(0, inputStream)
                0xb2 -> getstatic(inputStream)
                0xb1 -> {
                    _return(inputStream)
                    return@run null
                }
                0xb6 -> invokevirtual(inputStream)
                0xb8 -> invokestatic(inputStream)
                else -> throw RuntimeException("Not-implemented opcode ${Integer.toHexString(opCode)}")
            }
            if (nextFrame != null) {
                inputStream.mark(1)
                return nextFrame;
            }
        }
        return null
    }

    //0x6
    private fun iconst(n: Int, inputStream: ByteArrayInputStream): Frame? {
        operandStack.push(n)
        return null
    }

    //0x12
    private fun ldc(inputStream: ByteArrayInputStream): Frame? {
        logger.info("OPCODE: ldc")
        val cpIndex = inputStream.read(1).toInt()
        val cp = classFile.constantPools[cpIndex - 1]
        if (cp is ConstantPoolString) {
            val cpUtf8 = classFile.constantPools[cp.stringIndex - 1] as ConstantPoolUtf8
            operandStack.push(cpUtf8.info.decodeToString())
        } else if (cp is ConstantPoolInteger) {
            operandStack.push(cp.value)
        } else {
            throw RuntimeException("Unknown")
        }
        return null
    }

    //0x1a
    private fun iload(n: Int, inputStream: ByteArrayInputStream): Frame? {
        operandStack.push(localVariables[n])
        return null
    }

    //0x3b
    private fun istore(n: Int, inputStream: ByteArrayInputStream): Frame? {
        localVariables[n] = operandStack.pop()
        return null
    }

    //0xb2
    private fun getstatic(inputStream: ByteArrayInputStream): Frame? {
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

        operandStack.push("$className.$methodName")
        return null
    }

    //0xb1
    private fun _return(inputStream: ByteArrayInputStream): Frame? {

        return null
    }

    //0xb6
    private fun invokevirtual(inputStream: ByteArrayInputStream): Frame? {
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
            args.add(operandStack.pop())
        }

        if (className == "java/io/PrintStream") {
            println(args[args.size - 1])//TODO
        } else {
            TODO()
        }
        return null
    }

    //0xb8
    private fun invokestatic(inputStream: ByteArrayInputStream): Frame? {
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

        val targetCode = classFile.getBinaryCode(foundMethods[0])
            ?: throw RuntimeException("$methodName $methodDescriptor not found")//TODO
        return Frame(classFile, targetCode)
    }
}

class Executor(private val classFile: ClassFile) {
    private val logger = KotlinLogging.logger {}
    private val frameStack = Stack<Frame>()
    fun runMain() {
        val mainMethod = classFile.findMethod("main", "([Ljava/lang/String;)V")
        if (mainMethod.isEmpty()) {
            throw RuntimeException("main method not found")
        }
        val mainMethodCode =
            classFile.getBinaryCode(mainMethod[0]) ?: throw RuntimeException("main method Code not found")
        val firstFrame = Frame(classFile, mainMethodCode)
        frameStack.push(firstFrame)

        while (!frameStack.isEmpty()) {
            val additionalFrame = frameStack.peek().run()
            if (additionalFrame != null) {
                frameStack.push(additionalFrame)
            } else {
                frameStack.pop()
            }
        }
    }
}