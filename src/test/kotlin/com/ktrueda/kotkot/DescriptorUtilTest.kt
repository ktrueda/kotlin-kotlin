package com.ktrueda.kotkot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class DescriptorUtilTest {

    @ParameterizedTest(name = "{0} => {1}")
    @MethodSource("numOfArgMethodSource")
    fun argTypes(descriptor: String, expected: List<String>) {
        assertThat(DescriptorUtil.argTypes(descriptor)).isEqualTo(expected)
    }

    companion object {
        @JvmStatic
        fun numOfArgMethodSource(): List<Arguments> {
            return listOf<Arguments>(
                Arguments.of("(I)I", listOf("Int")),
                Arguments.of("(II)I", listOf("Int", "Int")),
                Arguments.of("()V", emptyList<String>()),
                Arguments.of("()I", emptyList<String>()),
                Arguments.of("([Ljava/lang/String;)V", listOf("java/lang/String")),
                Arguments.of(
                    "([Ljava/lang/String;Ljava/lang/String;)V",
                    listOf("java/lang/String", "java/lang/String")
                ),
                Arguments.of("([I)V", listOf("Int[]")),
                Arguments.of("([I[I)V", listOf("Int[]", "Int[]")),
                Arguments.of("(Ljava/lang/String;I)V", listOf("java/lang/String", "Int"))
            )
        }
    }
}