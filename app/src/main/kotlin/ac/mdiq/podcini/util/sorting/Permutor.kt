package ac.mdiq.podcini.util.sorting

/**
 * Interface for passing around list permutor method. This is used for cases where a simple comparator
 * won't work (e.g. Random, Smart Shuffle, etc).
 *
 * @param <E> the type of elements in the list
</E> */
interface Permutor<E> {
    /**
     * Reorders the specified list.
     * @param queue A (modifiable) list of elements to be reordered
     */
    fun reorder(queue: MutableList<E>?)
}
