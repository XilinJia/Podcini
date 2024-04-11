package ac.mdiq.podcini.util

fun printStackTrace() {
    val stackTraceElements = Thread.currentThread().stackTrace
    stackTraceElements.forEach { element ->
        println(element)
    }
}