
import java.io.File
import java.util.concurrent.Executors


class Main {
}
fun main(){
    val command = "pwd"
    ProcessBuilder().command(command)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start();

    print("hello world")
}
