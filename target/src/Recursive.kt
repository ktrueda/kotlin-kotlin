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