fun main() {
    Printer.greetings()
    Printer.greetings("tom")
}

object Printer {
    fun greetings(name: String = "jeff") {
        println("hello $name")
    }
}