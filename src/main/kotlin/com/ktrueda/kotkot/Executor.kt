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
    private var localVariables: Array<Any?>,
    private val heap: Stack<Map<String, Any?>>
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
            maxLocals: ${code.maxLocals}
            localVariables: ${localVariables.map { it?.toString() ?: "null" }}
            ############################################
        """.trimIndent()
        )
        while (true) {
            val opCode = inputStream.read()
            logger.info(
                """
                ####### new  op code ###########
                frameHash: ${this.hashCode()}
                class.method: ${classFile.getThisClassName()}.${(classFile.constantPools[method.nameIndex - 1] as ConstantPoolUtf8).info.decodeToString()}
                opCode: ${Integer.toHexString(opCode)}
                operandStack: $operandStack
                localVariables: ${localVariables.map { it?.toString() ?: "null" }}
                currentPos: ${inputStream.getPos() - 1}
                heap: ${heap.map { it?.toString() ?: "null" }}
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
                0x1b -> iload(1, inputStream)
                0x1c -> iload(2, inputStream)
                0x1d -> iload(3, inputStream)
                0x2a -> aload(0, inputStream)
                0x2b -> aload(1, inputStream)
                0x2c -> aload(2, inputStream)
                0x2d -> aload(3, inputStream)
                0x3b -> istore(0, inputStream)
                0x4b -> astore(0, inputStream)
                0x4c -> astore(1, inputStream)
                0x4d -> astore(2, inputStream)
                0x4e -> astore(3, inputStream)
                0x59 -> dup(inputStream)
                0x60 -> iadd(inputStream)
                0x64 -> isub(inputStream)
                0x7e -> iand(inputStream)
                0x99 -> ifeq(inputStream)
                0xa7 -> goto(inputStream)
                0xaa -> tableswitch(inputStream)
                0xac -> {
                    return@run operandStack.pop()
                }
                0xb0 -> {
                    return@run operandStack.pop()
                }
                0xb1 -> {
                    _return(inputStream)
                    return@run null
                }
                0xb2 -> getstatic(inputStream)
                0xb4 -> getfield(inputStream)
                0xb5 -> putfield(inputStream)
                0xb6 -> invokevirtual(inputStream)
                0xb7 -> invokespecial(inputStream)
                0xb8 -> invokestatic(inputStream)
                0xbb -> new(inputStream)
                0xc0 -> checkcast(inputStream)
                0xc7 -> ifnonnull(inputStream)
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
        logger.info("OPCODE: bipush")
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

    //0x2a, 0x2b
    private fun aload(n: Int, inputStream: ByteArrayInputStream): Frame? {
        operandStack.push(localVariables[n])
        return null
    }

    //0x3b
    private fun istore(n: Int, inputStream: ByteArrayInputStream): Frame? {
        localVariables[n] = operandStack.pop()
        return null
    }

    //0x4c
    private fun astore(n: Int, inputStream: ByteArrayInputStream): Frame? {
        localVariables[n] = operandStack.pop()
        return null
    }

    //0x59
    private fun dup(inputStream: MyByteArrayInputStream): Frame? {
        logger.info("OPCODE: dup")
        val value = operandStack.pop()
        operandStack.push(value)
        operandStack.push(value)
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

    //0x7e
    private fun iand(inputStream: MyByteArrayInputStream): Frame? {
        val value2 = operandStack.pop() as Int
        val value1 = operandStack.pop() as Int
        operandStack.push(value2 and value1)
        return null
    }

    //0x99
    private fun ifeq(inputStream: MyByteArrayInputStream): Frame? {
        val indexByte1 = inputStream.read()
        val indexByte2 = inputStream.read()
        val index = indexByte1 * 255 + indexByte2

        val value = operandStack.pop() as Int
        if (value == 0) {
            inputStream.setPos(index)
        }
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

        if (className == "java/io/PrintStream") {
            println(operandStack.pop())
            return null
        } else {
            val classFile = classLoader.get(className) ?: throw RuntimeException("class not found")
            val method = classFile.findMethod(methodName, methodDescriptor)!![0]
            val staticPlus = if (!method.isStatic()) 1 else 0

            val maxLocals = classFile.getBinaryCode(method)?.maxLocals ?: throw RuntimeException()

            val args = List(maxLocals) {
                if (it < DescriptorUtil.argTypes(methodDescriptor).size + staticPlus) {
                    operandStack.pop()
                } else {
                    0 //TODO how can i find "this" index
                }
            }.reversed()

            val frame = Frame(classLoader, classFile, method, args.toTypedArray(), heap)
            return frame
        }
    }

    //0xb4
    private fun getfield(inputStream: ByteArrayInputStream): Frame? {
        logger.info("OPCODE: getfield")
        val indexByte1 = inputStream.read()
        val indexByte2 = inputStream.read()
        val index = indexByte1 * 255 + indexByte2

        val cpFieldRef = classFile.constantPools[index - 1] as ConstantPoolFieldRef
        val cpNameAndType = classFile.constantPools[cpFieldRef.nameAndTypeIndex - 1] as ConstantPoolNameAndType
        val cpName = classFile.constantPools[cpNameAndType.nameIndex - 1] as ConstantPoolUtf8
        val fieldName = cpName.info.decodeToString()
        val objectref = operandStack.pop() as Int
        operandStack.push(ObjectGenerator.get(heap[objectref], fieldName))
        return null
    }

    //0xb5
    private fun putfield(inputStream: ByteArrayInputStream): Frame? {
        logger.info("OPCODE: putfield")
        val indexByte1 = inputStream.read()
        val indexByte2 = inputStream.read()
        val index = indexByte1 * 255 + indexByte2

        val cpFieldRef = classFile.constantPools[index - 1] as ConstantPoolFieldRef
        val cpNameAndType = classFile.constantPools[cpFieldRef.nameAndTypeIndex - 1] as ConstantPoolNameAndType
        val cpName = classFile.constantPools[cpNameAndType.nameIndex - 1] as ConstantPoolUtf8
        val fieldName = cpName.info.decodeToString()


        val value = operandStack.pop()
        val objectref = operandStack.pop() as Int
        heap[objectref] = ObjectGenerator.put(heap[objectref], fieldName, value)

        return null
    }

    //0xb7
    private fun invokespecial(inputStream: ByteArrayInputStream): Frame? {
        logger.info("OPCODE: invokespecial")
        val indexByte1 = inputStream.read()
        val indexByte2 = inputStream.read()
        val index = indexByte1 * 255 + indexByte2

        val cpMethodRef = classFile.constantPools[index - 1] as ConstantPoolMethodRef
        val cpClass = classFile.constantPools[cpMethodRef.classIndex - 1] as ConstantPoolClass
        val cpClassNameUtf8 = classFile.constantPools[cpClass.nameIndex - 1] as ConstantPoolUtf8
        val className = cpClassNameUtf8.info.decodeToString()

        val cpNameType = classFile.constantPools[cpMethodRef.nameAndTypeIndex - 1] as ConstantPoolNameAndType
        val cpMethodNameUtf8 = classFile.constantPools[cpNameType.nameIndex - 1] as ConstantPoolUtf8
        val methodName = cpMethodNameUtf8.info.decodeToString()
        val cpMethodDescriptorUtf8 = classFile.constantPools[cpNameType.descriptorIndex - 1] as ConstantPoolUtf8
        val methodDescriptor = cpMethodDescriptorUtf8.info.decodeToString()

        logger.debug("class: $className method :$methodName $methodDescriptor")
        val classFile = classLoader.get(className) ?: throw RuntimeException("class not found $className")
        val methods = classFile.findMethod(methodName, methodDescriptor)
        val targetMethod = methods[0]
        val maxLocals = classFile.getBinaryCode(targetMethod)?.maxLocals ?: throw RuntimeException("method size 0")

        val args = List(maxLocals) {
            if (it < DescriptorUtil.argTypes(methodDescriptor).size + 1) {
                operandStack.pop()
            } else {
                heap.size - 1 //TODO how can I get "this" index
            }
        }.reversed()

        return Frame(classLoader, classFile, targetMethod, args.toTypedArray(), heap)
    }

    //0xb8
    private fun invokestatic(inputStream: ByteArrayInputStream): Frame? {
        logger.info("OPCODE: invokestatic")
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

        //TODO
        if (className == "kotlin/jvm/internal/Intrinsics" && methodName == "checkNotNullParameter") {
            return null

        }
        //TODO
        if (className == "kotlin/jvm/internal/Intrinsics" && methodName == "stringPlus") {
            val str2 = operandStack.pop() as String
            val str1 = operandStack.pop() as String
            operandStack.push(str1 + str2)
            return null
        }

        val foundMethods = classLoader.findMethod(className, methodName, methodDescriptor)
        if (foundMethods.isEmpty()) {
            throw RuntimeException("$className $methodName $methodDescriptor not found")
        }

        logger.debug("desciptor $methodDescriptor ${DescriptorUtil.argTypes(methodDescriptor)}")
        //TODO miss design
        val targetClassFile = foundMethods[0].first
        val targetMethod = foundMethods[0].second
        val maxLocals = targetClassFile.getBinaryCode(targetMethod)!!.maxLocals
        val args = List(maxLocals) {
            if (it < DescriptorUtil.argTypes(methodDescriptor).size) {
                operandStack.pop()
            } else {
                null
            }
        }.reversed()

        val frame = Frame(classLoader, targetClassFile, targetMethod, args.toTypedArray(), heap)
        return frame
    }

    //0xbb
    private fun new(inputStream: ByteArrayInputStream): Frame? {
        logger.info("OPCODE: new")
        val indexByte1 = inputStream.read()
        val indexByte2 = inputStream.read()
        val index = indexByte1 * 255 + indexByte2 //TODO 256
        val cpClass = classFile.constantPools[index - 1] as ConstantPoolClass
        val cpClassNameUtf8 = classFile.constantPools[cpClass.nameIndex - 1] as ConstantPoolUtf8
        val className = cpClassNameUtf8.info.decodeToString()
        logger.debug("class : $className")
        val classFile = classLoader.get(className) ?: throw RuntimeException("class not found $className")
        heap.push(ObjectGenerator.new(classFile))

        operandStack.push(heap.size - 1)
        return null
    }

    //0xc0
    private fun checkcast(inputStream: ByteArrayInputStream): Frame? {
        val indexByte1 = inputStream.read()
        val indexByte2 = inputStream.read()
        //TODO
        return null
    }

    //0xc7
    private fun ifnonnull(inputStream: MyByteArrayInputStream): Frame? {
        logger.info("OPCODE: ifnonnull")
        val instructionPos = inputStream.getPos() - 1
        val branch1 = inputStream.read()
        val branch2 = inputStream.read()
        val offset = branch1 * 256 + branch2
        if (operandStack.pop() == null) {
            inputStream.setPos(instructionPos + offset)
        }
        return null
    }
}

object ObjectGenerator {
    fun new(classFile: ClassFile): Map<String, Any?> {
        return classFile.fields.associate {
            val cpUtf8 = classFile.constantPools[it.nameIndex - 1] as ConstantPoolUtf8
            cpUtf8.info.decodeToString() to null
        }
    }

    fun put(obj: Map<String, Any?>, field: String, value: Any?): Map<String, Any?> {
        return obj + Pair(field, value)
    }

    fun get(obj: Map<String, Any?>, field: String): Any? {
        return obj[field]
    }
}

class Executor(
    private val classLoader: MyClassLoader,
    private val mainClass: String
) {
    private val classFile: ClassFile =
        classLoader.get(mainClass) ?: throw RuntimeException("class not found $mainClass")
    private val logger = KotlinLogging.logger {}
    private val frameStack = Stack<Frame>()

    fun runMain() {
        val mainMethod = classFile.findMethod("main", "([Ljava/lang/String;)V")
        if (mainMethod.isEmpty()) {
            throw RuntimeException("main method not found")
        }
        val firstFrame = Frame(classLoader, classFile, mainMethod[0], Array<Any?>(0) {}, Stack())
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