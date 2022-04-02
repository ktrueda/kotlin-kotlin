fun main{
    val f = Foo()
    print(f.bar)
}

class Foo{
    val bar :String by lazy{
        "hello"
    }
}