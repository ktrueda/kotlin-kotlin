package com.ktrueda.kotkot

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class DescriptorUtilTest {

    @ParameterizedTest(name = "{0} => {1}")
    @MethodSource("numOfArgMethodSource")
    fun numOfArg(descriptor: String, num: Int) {
        assert(DescriptorUtil.numOfArg(descriptor) == num)
    }

    companion object {
        @JvmStatic
        fun numOfArgMethodSource(): List<Arguments> {
            return listOf<Arguments>(
                Arguments.of("(I)I", 1),
                Arguments.of("(II)I", 2),
                Arguments.of("()V", 0),
                Arguments.of("()I", 0),
                Arguments.of("([Ljava/lang/String;)V", 1),
                Arguments.of("([Ljava/lang/String;Ljava/lang/String;)V", 2),
                Arguments.of("([I)V", 1),
                Arguments.of("([I[I)V", 2),
                Arguments.of("(Ljava/lang/String;I)V", 2)
            )
        }
    }
}