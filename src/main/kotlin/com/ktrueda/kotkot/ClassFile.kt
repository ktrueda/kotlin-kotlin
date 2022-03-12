package com.ktrueda.kotkot

import java.io.File
import java.io.RandomAccessFile


fun ByteArray.toHex(): String =
    joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun Int.pow(n: Int): Int = if (n == 0) 1 else n * this.pow(n - 1)

fun ByteArray.toInt(): Int {
    return List(this.size) {
        255.pow(it) * this[it]
    }.sum()
}

fun RandomAccessFile.read(n: Int): ByteArray {
    val ba = ByteArray(n)
    repeat(n) {
        ba[it] = this.readByte()
    }
    return ba
}

open class ConstantPool() {
    var type: Int = 0

    constructor(randomAccessFile: RandomAccessFile) : this() {
        this.type = randomAccessFile.read()
        when (type) {
            1 -> ConstantPoolUtf8(randomAccessFile)
            3 -> ConstantPoolInteger(randomAccessFile)
            7 -> ConstantPoolClass(randomAccessFile)
            8 -> ConstantPoolString(randomAccessFile)
            9 -> ConstantPoolFieldRef(randomAccessFile)
            10 -> ConstantPoolMethodRef(randomAccessFile)
            12 -> ConstantPoolNameAndType(randomAccessFile)
            else -> throw RuntimeException("Not implemented constant pool type $type")
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

class ConstantPoolInteger : ConstantPool {
    var value: Int = 0

    constructor(randomAccessFile: RandomAccessFile) {
        value = randomAccessFile.read(4).toInt()
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

class ClassFile(
    val magic: String,
    val minorVersion: Int,
    val majorVersion: Int,
    val constantPoolCount: Int,
    val constantPools: List<ConstantPool>
) {
    override fun toString(): String {
        return """
            ## Class File ###
            magic: ${magic}
            minorVersion: $minorVersion
            majorVersion: $majorVersion
            constantPoolCount: $constantPoolCount
            constantPools: (omitted)
            #################
        """.trimIndent()
    }

    companion object {
        fun load(path: File): ClassFile {
            val raf = RandomAccessFile(path, "r")
            val magic = raf.read(4).toHex()
            val minorVersion = raf.read(2).toInt()
            val majorVersion = raf.read(2).toInt()
            val constantPoolCount = raf.read(2).toInt() - 1
            val constantPools = List(constantPoolCount) {
                val cp = ConstantPool(raf)
//                println("read constant pool $cp")
                cp
            }

            return ClassFile(magic, minorVersion, majorVersion, constantPoolCount, constantPools)
        }
    }

}