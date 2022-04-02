package com.ktrueda.kotkot

import mu.KotlinLogging
import java.io.DataInput
import java.io.DataInputStream
import java.io.File
import java.io.RandomAccessFile


fun ByteArray.toHex(): String =
    joinToString(separator = " ") { eachByte -> "%02x".format(eachByte) }

fun Int.pow(n: Int): Int = if (n == 0) 1 else this * this.pow(n - 1)

fun ByteArray.toInt(): Int {
    return List(this.size) {
        256.pow(it) * this[this.size - it - 1].toUByte().toInt() // overflow
    }.sum()
}

fun DataInput.read(n: Int): ByteArray {
    val ba = ByteArray(n)
    repeat(n) {
        ba[it] = this.readByte()
    }
    return ba
}

open class ConstantPool {
    var type: Int = 0

    companion object {
        fun load(randomAccessFile: RandomAccessFile): ConstantPool {
            return when (val type = randomAccessFile.read()) {
                1 -> ConstantPoolUtf8(randomAccessFile)
                3 -> ConstantPoolInteger(randomAccessFile)
                5 -> ConstantPoolLong(randomAccessFile)
                7 -> ConstantPoolClass(randomAccessFile)
                8 -> ConstantPoolString(randomAccessFile)
                9 -> ConstantPoolFieldRef(randomAccessFile)
                10 -> ConstantPoolMethodRef(randomAccessFile)
                11 -> ConstantPoolInterfaceMethodRef(randomAccessFile)
                12 -> ConstantPoolNameAndType(randomAccessFile)
                else -> throw RuntimeException("Not implemented constant pool type $type")
            }
        }
    }

    override fun toString(): String {
        return """
            ## Constant Pool ###
            type: $type
            class: ${this.javaClass}
            #################
        """.trimIndent()

    }
}

class ConstantPoolUtf8(randomAccessFile: RandomAccessFile) : ConstantPool() {
    var length: Int = 0
    val info: ByteArray

    init {
        this.length = randomAccessFile.read(2).toInt()
        this.info = randomAccessFile.read(this.length)
    }
}

class ConstantPoolClass : ConstantPool {
    var nameIndex: Int = 0

    constructor(randomAccessFile: RandomAccessFile) {
        nameIndex = randomAccessFile.read(2).toInt()
    }
}

class ConstantPoolInterfaceMethodRef(randomAccessFile: RandomAccessFile) : ConstantPool() {
    var classIndex: Int = 0;
    var nameAndTypeIndex: Int = 0;

    init {
        classIndex = randomAccessFile.read(2).toInt()
        nameAndTypeIndex = randomAccessFile.read(2).toInt()
    }
}

class ConstantPoolInteger : ConstantPool {
    var value: Int = 0

    constructor(randomAccessFile: RandomAccessFile) {
        value = randomAccessFile.read(4).toInt()
    }
}

class ConstantPoolLong : ConstantPool {
    var highByte: Int = 0
    var lowByte: Int = 0

    constructor(randomAccessFile: RandomAccessFile) {
        highByte = randomAccessFile.read(4).toInt()
        lowByte = randomAccessFile.read(4).toInt()
    }
}

class ConstantPoolString : ConstantPool {
    var stringIndex: Int = 0

    constructor(randomAccessFile: RandomAccessFile) {
        stringIndex = randomAccessFile.read(2).toInt()
    }
}

class ConstantPoolMethodRef(randomAccessFile: RandomAccessFile) : ConstantPool() {
    var classIndex: Int = 0
    var nameAndTypeIndex: Int = 0

    init {
        classIndex = randomAccessFile.read(2).toInt()
        nameAndTypeIndex = randomAccessFile.read(2).toInt()
    }
}

class ConstantPoolFieldRef : ConstantPool {
    var classIndex: Int = 0
    var nameAndTypeIndex: Int = 0

    constructor(randomAccessFile: RandomAccessFile) {
        classIndex = randomAccessFile.read(2).toInt()
        nameAndTypeIndex = randomAccessFile.read(2).toInt()
    }
}

class ConstantPoolNameAndType : ConstantPool {
    var nameIndex: Int = 0
    var descriptorIndex: Int = 0

    constructor(randomAccessFile: RandomAccessFile) {
        nameIndex = randomAccessFile.read(2).toInt()
        descriptorIndex = randomAccessFile.read(2).toInt()
    }
}

class Field(randomAccessFile: RandomAccessFile) {
    val accessFlag: Int
    val nameIndex: Int
    val descriptorIndex: Int
    val attributesCount: Int
    val attributes: List<Attribute>

    init {
        accessFlag = randomAccessFile.read(2).toInt()
        nameIndex = randomAccessFile.read(2).toInt()
        descriptorIndex = randomAccessFile.read(2).toInt()
        attributesCount = randomAccessFile.read(2).toInt()
        attributes = List(attributesCount) {
            Attribute(randomAccessFile)
        }
    }
}

class Method(randomAccessFile: RandomAccessFile) {
    val accessFlag: Int
    val nameIndex: Int
    val descriptorIndex: Int
    val attributesCount: Int
    val attributes: List<Attribute>

    init {
        accessFlag = randomAccessFile.read(2).toInt()
        nameIndex = randomAccessFile.read(2).toInt()
        descriptorIndex = randomAccessFile.read(2).toInt()
        attributesCount = randomAccessFile.read(2).toInt()
        attributes = List(attributesCount) {
            Attribute(randomAccessFile)
        }
    }

    fun isStatic() = (accessFlag and 0x0008) > 0 //TODO

}

class Code(input: DataInput) {
    val maxStack: Int
    val maxLocals: Int
    val codeLength: Int
    val binaryCode: ByteArray

    init {
        maxStack = input.read(2).toInt()
        maxLocals = input.read(2).toInt()
        codeLength = input.read(4).toInt()
        binaryCode = input.read(codeLength)
    }

    override fun toString(): String {
        return """
            ## Code #######
            maxStack: $maxStack
            maxLocals: $maxLocals
            codeLength: $codeLength
            code: ${binaryCode.toHex()}
            ###############
        """.trimIndent()
    }
}

class Attribute(randomAccessFile: RandomAccessFile) {
    val attributeNameIndex: Int
    val attributeLength: Int
    val info: ByteArray

    init {
        attributeNameIndex = randomAccessFile.read(2).toInt()
        attributeLength = randomAccessFile.read(4).toInt()
        info = randomAccessFile.read(attributeLength)
    }
}

class ClassFile(
    val magic: String,
    val minorVersion: Int,
    val majorVersion: Int,
    val constantPoolCount: Int,
    val constantPools: List<ConstantPool>,
    val accessFlags: Int,
    val thisClass: Int,
    val superClass: Int,
    val interfacesCount: Int,
    val interfaces: List<ConstantPoolClass>,
    val fieldsCount: Int,
    val fields: List<Field>,
    val methodsCount: Int,
    val methods: List<Method>,
    val attributesCount: Int,
    val attributes: List<Attribute>
) {
    override fun toString(): String {
        val methodsExp = methods.map { constantPools[it.nameIndex - 1] as ConstantPoolUtf8 }
            .map { it.info.decodeToString() }
            .joinToString { it }
        return """
            ## Class File ###
                magic: ${magic}
                minorVersion: $minorVersion
                majorVersion: $majorVersion
                constantPoolCount: $constantPoolCount
                constantPools: (omitted)
                accessFlag: $accessFlags
                thisClass: $thisClass
                superClass: $superClass
                interfacesCount: $interfacesCount
                fieldsCount: $fieldsCount
                fields: (omitted)
                methodsCount: $methodsCount
                methods: (omitted)
                attributesCount: $attributesCount
                attributes: (omitted)
                
            parse result
                className: ${getThisClassName()}
                superClass: ${getSuperClassName()}
                methods: $methodsExp
            #################
        """.trimIndent()
    }

    fun getThisClassName(): String {
        val constantPoolClass = constantPools[thisClass - 1] as ConstantPoolClass
        val constantPoolUtf8 = constantPools[constantPoolClass.nameIndex - 1] as ConstantPoolUtf8
        return constantPoolUtf8.info.decodeToString()
    }

    fun getSuperClassName(): String {
        val constantPoolClass = constantPools[superClass - 1] as ConstantPoolClass
        val constantPoolUtf8 = constantPools[constantPoolClass.nameIndex - 1] as ConstantPoolUtf8
        return constantPoolUtf8.info.decodeToString()
    }

    fun findMethod(methodName: String, methodDescriptor: String): List<Method> {
        return methods
            .filter {
                val cpNameUtf8 = constantPools[it.nameIndex - 1] as ConstantPoolUtf8
                cpNameUtf8.info.decodeToString() == methodName
            }
            .filter {
                val cpDescriptorUtf8 = constantPools[it.descriptorIndex - 1] as ConstantPoolUtf8
                cpDescriptorUtf8.info.decodeToString() == methodDescriptor
            }
    }

    fun getBinaryCode(method: Method): Code? {
        val codeAttr = method.attributes.find {
            val attrName = constantPools[it.attributeNameIndex - 1] as ConstantPoolUtf8
            attrName.info.decodeToString() == "Code"
        }!!

        return Code(DataInputStream(codeAttr.info.inputStream()))
    }

    companion object {
        fun load(path: File): ClassFile {
            val raf = RandomAccessFile(path, "r")
            val magic = raf.read(4).toHex()
            val minorVersion = raf.read(2).toInt()
            val majorVersion = raf.read(2).toInt()
            val constantPoolCount = raf.read(2).toInt() - 1
            val constantPools = List(constantPoolCount) {
                val cp = ConstantPool.load(raf)
                cp
            }
            val accessFlags = raf.read(2).toInt()
            val thisClass = raf.read(2).toInt()
            val superClass = raf.read(2).toInt()
            val interfacesCount = raf.read(2).toInt()
            val interfaces = List(interfacesCount) {
                ConstantPoolClass(raf)
            }
            val fieldsCount = raf.read(2).toInt()
            val fields = List(fieldsCount) {
                Field(raf)
            }
            val methodsCount = raf.read(2).toInt()
            val methods = List(methodsCount) {
                val m = Method(raf)
                println((constantPools[m.nameIndex - 1] as ConstantPoolUtf8).info.decodeToString())
                m
            }
            val attributeCount = raf.read(2).toInt()
            val attributes = List(attributeCount) {
                Attribute(raf)
            }


            return ClassFile(
                magic,
                minorVersion,
                majorVersion,
                constantPoolCount,
                constantPools,
                accessFlags,
                thisClass,
                superClass,
                interfacesCount,
                interfaces,
                fieldsCount,
                fields,
                methodsCount,
                methods,
                attributeCount,
                attributes
            )
        }
    }
}

class MyClassLoader private constructor(private val map: Map<String, ClassFile>) {

    fun get(className: String): ClassFile? = map[className]

    fun allClasses() = map.keys

    fun findMethod(className: String, methodName: String, methodDescriptor: String): List<Pair<ClassFile, Method>> {

        fun rec(cf: ClassFile, acc: MutableList<Pair<ClassFile, Method>>): MutableList<Pair<ClassFile, Method>> {
            return when (cf.getThisClassName()) {
                "java/lang/Object" -> acc
                else -> {
                    val methods = cf.findMethod(methodName, methodDescriptor).map { cf to it }
                    val superClassName = cf.getSuperClassName()
                    val superClassFile =
                        get(superClassName) ?: throw RuntimeException("class not found $superClassName")
                    return rec(superClassFile, (acc + methods).toMutableList())
                }
            }
        }

        val classFile = get(className) ?: throw RuntimeException("class not found $className")
        return rec(classFile, mutableListOf()).toList()

    }


    companion object {
        private val logger = KotlinLogging.logger {}
        fun load(path: File): MyClassLoader {

            val map = path.walk()
                .filter { it.toPath().toAbsolutePath().toString().endsWith(".class") }
                .filter {
                    // currently kotkot can load some kind of class file.
                    setOf(
                        "./target/src/Object.class",
                        "./target/src/ByLazy.class",
                        "./target/src/ByLazyKt.class",
                        "./target/src/MathKt.class",
                        "./target/src/RecursiveKt.class",
                        "./target/src/Printer.class",
                        "./target/src/Person.class",
                        "./target/src/ExtendKt.class",
                        "./target/src/SuperClass.class",
                        "./target/src/SubClass.class",
                        "./target/src/kotlin/LazyKt.class",
                        "./target/src/kotlin/LazyKt__LazyKt.class",
                        "./target/src/kotlin/LazyKt__LazyJVMKt.class",
                        "./target/src/kotlin/SynchronizedLazyImpl.class",
                        "./target/src/HelloWorldKt.class",
                        "./target/src/OtherClassKt.class",
                        "./target/src/NewObjectKt.class",
                    ).contains(
                        it.toPath().toString()
                    )
                }
                .map {
                    logger.debug("try to load $it")
                    return@map try {
                        val classFile = ClassFile.load(it)
                        classFile.getThisClassName() to classFile
                    } catch (e: Exception) {
                        val fileName = it.toPath().toString()
                        throw ClassFileLoadException("failed to parse $fileName", e)
                    }
                }.toMap()
            return MyClassLoader(map)
        }
    }
}

class ClassFileLoadException(message: String, cause: Throwable) : Exception(message, cause)