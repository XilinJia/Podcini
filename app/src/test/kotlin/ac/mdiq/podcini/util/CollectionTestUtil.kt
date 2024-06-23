package ac.mdiq.podcini.util

import java.util.*

object CollectionTestUtil {
    fun <T> concat(item: T, list: List<T>?): List<T> {
        val res: MutableList<T> = ArrayList(list)
        res.add(0, item)
        return res
    }

    fun <T> concat(list: List<T>?, item: T): List<T> {
        val res: MutableList<T> = ArrayList(list)
        res.add(item)
        return res
    }

    fun <T> concat(list1: List<T>?, list2: List<T>?): List<T> {
        val res: MutableList<T> = ArrayList(list1)
        res.addAll(list2!!)
        return res
    }

    fun <T> list(vararg a: T): List<T> {
        return listOf(*a)
    }
}
