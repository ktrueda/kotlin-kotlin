open class SuperClass(open val x: Int) {
    open fun greeting() = "hello"
}

class SubClass(override val x: Int, val y: Int) : SuperClass(x) {
    override fun greeting(): String {
        return "${super.greeting()} name"
    }

}

fun main() {
    val s = SubClass(1, 2)
    println(s.y)
    println(s.greeting())
}
