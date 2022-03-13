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

private class MyByteArrayInputStream : ByteArrayInputStream {
    constructor(ba: ByteArray) : super(ba) {
    }

    fun getPos(): Int {
        return pos
    }

    fun setPos(pos: Int) {
        this.pos = pos
    }
}

private fun ByteArray.myInputStream(): MyByteArrayInputStream {
    return MyByteArrayInputStream(this)
}

class Frame(
    private val classLoader: MyClassLoader,
    private val classFile: ClassFile,
    private val method: Method,
    private var localVariables: Array<Any>
) {
    private val logger = KotlinLogging.logger {}
    val operandStack = Stack<Any>()
    val code: Code = classFile.getBinaryCode(method) ?: throw RuntimeException()
    private val inputStream: MyByteArrayInputStream = code.binaryCode.myInputStream()

    fun run(): Any? {
        inputStream.reset()
        logger.info(
            """
            ############## frame created/resumed ########
            frameHash: ${code.binaryCode.toHex()}
            class: ${classFile.getThisClassName()}
            method: ${(classFile.constantPools[method.nameIndex - 1] as ConstantPoolUtf8).info.decodeToString()}
            currentPos: ${inputStream.getPos()}
            ############################################
        """.trimIndent()
        )
        while (true) {
            val opCode = inputStream.read()
            logger.info(
                """
                ####### new  op code ###########
                frame: ${this.hashCode()}
                opCode: ${Integer.toHexString(opCode)}
                operandStack: $operandStack
                localVariables: $localVariables
                currentPos: ${inputStream.getPos() - 1}
                ################################
                """.trimIndent()
            )
            val nextFrame = when (opCode) {
                0x1 -> aconstnull(inputStream)
                0x4 -> iconst(1, inputStream)
                0x5 -> iconst(2, inputStream)
                0x6 -> iconst(3, inputStream)
                0x7 -> iconst(4, inputStream)
                0x10 -> bipush(inputStream)
                0x12 -> ldc(inputStream)
                0x1a -> iload(0, inputStream)
                0x1c -> iload(2, inputStream)
                0x3b -> istore(0, inputStream)
                0x60 -> iadd(inputStream)
                0x64 -> isub(inputStream)
                0xa7 -> goto(inputStream)
                0xaa -> tableswitch(inputStream)
                0xac -> {
                    return@run operandStack.pop()
                }
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

    //0x1
    private fun aconstnull(inputStream: ByteArrayInputStream): Frame? {
        operandStack.push(null)
        return null
    }

    //0x4, 0x5, 0x6, 0x7
    private fun iconst(n: Int, inputStream: ByteArrayInputStream): Frame? {
        operandStack.push(n)
        return null
    }

    //0x10
    private fun bipush(inputStream: ByteArrayInputStream): Frame? {
        operandStack.push(inputStream.read())
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

    //0x60
    private fun iadd(inputStream: MyByteArrayInputStream): Frame? {
        val value1 = operandStack.pop() as Int
        val value2 = operandStack.pop() as Int
        operandStack.push(value2 + value1)
        return null
    }

    //0x64
    private fun isub(inputStream: MyByteArrayInputStream): Frame? {
        logger.info("OPCODE: isub")
        val value1 = operandStack.pop() as Int
        val value2 = operandStack.pop() as Int
        operandStack.push(value2 - value1)
        return null
    }

    //0xa7
    private fun goto(inputStream: MyByteArrayInputStream): Frame? {
        val instructionPos = inputStream.getPos() - 1
        inputStream.setPos(instructionPos + inputStream.read(2).toInt())
        return null
    }

    //0xaa
    private fun tableswitch(inputStream: MyByteArrayInputStream): Frame? {
        logger.info("OPCODE: tableswitch")
        val instructionPos = inputStream.getPos() - 1
        inputStream.read((3 - instructionPos) % 4)
        val defaultValue = inputStream.read(4).toInt()
        val lowValue = inputStream.read(4).toInt()
        val highValue = inputStream.read(4).toInt()
        val index = operandStack.pop() as Int

        val nextPos = if (index < lowValue || index > highValue) {
            instructionPos + defaultValue
        } else {
            val diff = index - lowValue
            repeat(diff - 1) {
                inputStream.read(4)
            }
            instructionPos + inputStream.read(4).toInt()
        }
        logger.debug("next pos ${nextPos}")
        inputStream.setPos(nextPos)
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
        val methodDescriptor = methodArgsExpUtf8.info.decodeToString()

        logger.debug("$className.${methodName} ${methodArgsExpUtf8.info.decodeToString()}")

        val args = mutableListOf<Any>()
        repeat(1) {//TODO
            args.add(operandStack.pop())
        }

        if (className == "java/io/PrintStream") {
            println(args[args.size - 1])//TODO
            return null
        } else {
            val classFile = classLoader.get(className) ?: throw RuntimeException("class not found")
            val method = classFile.findMethod(methodName, methodDescriptor)!![0]
            val frame = Frame(classLoader, classFile, method, args.toTypedArray())
            return frame
        }
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


        val targetClassFile = classLoader.get(className) ?: throw RuntimeException("Class not found")
        val foundMethods = targetClassFile.findMethod(methodName, methodDescriptor)
        if (foundMethods.isEmpty()) {
            throw RuntimeException("$methodName $methodDescriptor not found")
        }

        logger.debug("desciptor $methodDescriptor")
        val args = if (methodDescriptor == "()V") {//TODO
            listOf()
        } else {
            listOf(operandStack.pop()) //TODO
        }

        val frame = Frame(classLoader, targetClassFile, foundMethods[0], args.toTypedArray())
        return frame
    }
}

class Executor(
    private val classLoader: MyClassLoader,
    private val mainClass: String
) {
    private val classFile: ClassFile = classLoader.get(mainClass) ?: throw RuntimeException("class not found")
    private val logger = KotlinLogging.logger {}
    private val frameStack = Stack<Frame>()

    fun runMain() {
        val mainMethod = classFile.findMethod("main", "([Ljava/lang/String;)V")
        if (mainMethod.isEmpty()) {
            throw RuntimeException("main method not found")
        }
        val firstFrame = Frame(classLoader, classFile, mainMethod[0], Array<Any>(0) {})
        frameStack.push(firstFrame)

        while (!frameStack.isEmpty()) {
            val additionalFrame = frameStack.peek().run()
            if (additionalFrame != null) {
                if (additionalFrame is Frame) {
                    frameStack.push(additionalFrame)
                } else if (additionalFrame !is Unit) {
                    frameStack.pop()
                    frameStack.peek().operandStack.push(additionalFrame)
                }
            } else {
                frameStack.pop()
            }
        }
    }
}