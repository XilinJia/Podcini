package ac.mdiq.podcini.core.util

/**
 * Fast and memory efficient long list
 */
class LongList @JvmOverloads constructor(initialCapacity: Int = 4) {
    private var values: LongArray
    private var size: Int

    /**
     * Constructs an empty instance.
     *
     * @param initialCapacity `>= 0;` initial capacity of the list
     */
    /**
     * Constructs an empty instance with a default initial capacity.
     */
    init {
        require(initialCapacity >= 0) { "initial capacity must be 0 or higher" }
        values = LongArray(initialCapacity)
        size = 0
    }

    override fun hashCode(): Int {
        var hashCode = 1
        for (i in 0 until size) {
            val value = values[i]
            hashCode = 31 * hashCode + (value xor (value ushr 32)).toInt()
        }
        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is LongList) {
            return false
        }
        val otherList = other
        if (size != otherList.size) {
            return false
        }
        for (i in 0 until size) {
            if (values[i] != otherList.values[i]) {
                return false
            }
        }
        return true
    }

    override fun toString(): String {
        val sb = StringBuilder(size * 5 + 10)
        sb.append("LongList{")
        for (i in 0 until size) {
            if (i != 0) {
                sb.append(", ")
            }
            sb.append(values[i])
        }
        sb.append("}")
        return sb.toString()
    }

    /**
     * Gets the number of elements in this list.
     */
    fun size(): Int {
        return size
    }

    /**
     * Gets the indicated value.
     *
     * @param n `>= 0, < size();` which element
     * @return the indicated element's value
     */
    fun get(n: Int): Long {
        if (n >= size) {
            throw IndexOutOfBoundsException("n >= size()")
        } else if (n < 0) {
            throw IndexOutOfBoundsException("n < 0")
        }
        return values[n]
    }

    /**
     * Sets the value at the given index.
     *
     * @param index the index at which to put the specified object.
     * @param value the object to add.
     * @return the previous element at the index.
     */
    fun set(index: Int, value: Long): Long {
        if (index >= size) {
            throw IndexOutOfBoundsException("n >= size()")
        } else if (index < 0) {
            throw IndexOutOfBoundsException("n < 0")
        }
        val result = values[index]
        values[index] = value
        return result
    }

    /**
     * Adds an element to the end of the list. This will increase the
     * list's capacity if necessary.
     *
     * @param value the value to add
     */
    fun add(value: Long) {
        growIfNeeded()
        values[size++] = value
    }

    /**
     * Inserts element into specified index, moving elements at and above
     * that index up one. May not be used to insert at an index beyond the
     * current size (that is, insertion as a last element is legal but
     * no further).
     *
     * @param n `>= 0, <=size();` index of where to insert
     * @param value value to insert
     */
    fun insert(n: Int, value: Int) {
        if (n > size) {
            throw IndexOutOfBoundsException("n > size()")
        } else if (n < 0) {
            throw IndexOutOfBoundsException("n < 0")
        }

        growIfNeeded()

        System.arraycopy(values, n, values, n + 1, size - n)
        values[n] = value.toLong()
        size++
    }

    /**
     * Removes value from this list.
     *
     * @param value  value to remove
     * return `true` if the value was removed, `false` otherwise
     */
    fun remove(value: Long): Boolean {
        for (i in 0 until size) {
            if (values[i] == value) {
                size--
                System.arraycopy(values, i + 1, values, i, size - i)
                return true
            }
        }
        return false
    }

    /**
     * Removes values from this list.
     *
     * @param values  values to remove
     */
    fun removeAll(values: LongArray) {
        for (value in values) {
            remove(value)
        }
    }

    /**
     * Removes values from this list.
     *
     * @param list List with values to remove
     */
    fun removeAll(list: LongList) {
        for (value in list.values) {
            remove(value)
        }
    }

    /**
     * Removes an element at a given index, shifting elements at greater
     * indicies down one.
     *
     * @param index index of element to remove
     */
    fun removeIndex(index: Int) {
        if (index >= size) {
            throw IndexOutOfBoundsException("n >= size()")
        } else if (index < 0) {
            throw IndexOutOfBoundsException("n < 0")
        }
        size--
        System.arraycopy(values, index + 1, values, index, size - index)
    }

    /**
     * Increases size of array if needed
     */
    private fun growIfNeeded() {
        if (size == values.size) {
            // Resize.
            val newArray = LongArray(size * 3 / 2 + 10)
            System.arraycopy(values, 0, newArray, 0, size)
            values = newArray
        }
    }

    /**
     * Returns the index of the given value, or -1 if the value does not
     * appear in the list.
     *
     * @param value value to find
     * @return index of value or -1
     */
    fun indexOf(value: Long): Int {
        for (i in 0 until size) {
            if (values[i] == value) {
                return i
            }
        }
        return -1
    }

    /**
     * Removes all values from this list.
     */
    fun clear() {
        values = LongArray(4)
        size = 0
    }


    /**
     * Returns true if the given value is contained in the list
     *
     * @param value value to look for
     * @return `true` if this list contains `value`, `false` otherwise
     */
    fun contains(value: Long): Boolean {
        return indexOf(value) >= 0
    }

    /**
     * Returns an array with a copy of this list's values
     *
     * @return array with a copy of this list's values
     */
    fun toArray(): LongArray {
        return values.copyOf(size)
    }

    companion object {
        fun of(vararg values: Long): LongList {
            if (values == null || values.size == 0) {
                return LongList(0)
            }
            val result = LongList(values.size)
            for (value in values) {
                result.add(value)
            }
            return result
        }
    }
}
