open class SuperClass(open val x: Int)

class SubClass(override val x: Int, val y: Int) : SuperClass(x)

fun main() {
    val s = SubClass(1, 2)
    print(s.x)
}
