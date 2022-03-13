fun main() {
    print(fib(4))
}

fun fib(n: Int): Int {
    return when (n) {
        0 -> 1
        1 -> 1
        else -> fib(n - 1) + fib(n - 2)
    }
}
// 0,1,else      | default      | low        | high       | offset 1   | offset 2
// 1a aa 00 00    00 00 00 1f   00 00 00 00  00 00 00 01  00 00 00 17  00 00 00 1b