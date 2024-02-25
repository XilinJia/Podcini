package android.text

/**
 * A slim-down version of standard [android.text.TextUtils] to be used in unit tests.
 */
object TextUtils {
    /**
     * Returns true if a and b are equal, including if they are both null.
     *
     * *Note: In platform versions 1.1 and earlier, this method only worked well if
     * both the arguments were instances of String.*
     * @param a first CharSequence to check
     * @param b second CharSequence to check
     * @return true if a and b are equal
     */
    fun equals(a: CharSequence?, b: CharSequence?): Boolean {
        if (a === b) return true
        var length: Int = 0
        if ((a != null && b != null) && (a.length.also { length = it }) == b.length) {
            if (a is String && b is String) {
                return a == b
            } else {
                for (i in 0 until length) {
                    if (a[i] != b[i]) return false
                }
                return true
            }
        }
        return false
    }

    /**
     * Returns `true` if the string is `null` or has zero length.
     *
     * @param str The string to be examined, can be `null`.
     * @return `true` if the string is `null` or has zero length.
     */
    fun isEmpty(str: CharSequence?): Boolean {
        return str.isNullOrEmpty()
    }
}
