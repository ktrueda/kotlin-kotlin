package com.ktrueda.kotkot

object DescriptorUtil {
    fun argTypes(descriptor: String): List<String> {
        val startIndex = descriptor.indexOf("(") + 1
        val endIndex = descriptor.indexOf(")")
        val argExp = descriptor.subSequence(startIndex, endIndex).toString()

        fun rec(exp: String, acc: List<String>, inArr: Boolean, inClass: Boolean): List<String> {
            if (exp.isEmpty()) {
                return acc
            }
            if (inClass) {
                val end = exp.indexOf(';')
                val clz = exp.subSequence(0, end).toString()
                return rec(exp.substring(end + 1), acc + clz, inArr = false, inClass = false)
            }
            return when (exp[0]) {
                'I' -> rec(exp.substring(1), acc + (if (inArr) "Int[]" else "Int"), inArr = false, inClass = false)
                '[' -> rec(exp.substring(1), acc, true, inClass = false)
                'L' -> rec(exp.substring(1), acc, inArr, true)
                else -> throw Exception("Unexpected ${exp[0]}")
            }
        }
        return rec(argExp, emptyList<String>(), false, inClass = false)
    }
}