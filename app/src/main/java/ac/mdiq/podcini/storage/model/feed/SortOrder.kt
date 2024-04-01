package ac.mdiq.podcini.storage.model.feed

/**
 * Provides sort orders to sort a list of episodes.
 */
enum class SortOrder(@JvmField val code: Int, @JvmField val scope: Scope) {
    DATE_OLD_NEW(1, Scope.INTRA_FEED),
    DATE_NEW_OLD(2, Scope.INTRA_FEED),
    EPISODE_TITLE_A_Z(3, Scope.INTRA_FEED),
    EPISODE_TITLE_Z_A(4, Scope.INTRA_FEED),
    DURATION_SHORT_LONG(5, Scope.INTRA_FEED),
    DURATION_LONG_SHORT(6, Scope.INTRA_FEED),
    EPISODE_FILENAME_A_Z(7, Scope.INTRA_FEED),
    EPISODE_FILENAME_Z_A(8, Scope.INTRA_FEED),
    SIZE_SMALL_LARGE(9, Scope.INTRA_FEED),
    SIZE_LARGE_SMALL(10, Scope.INTRA_FEED),
    FEED_TITLE_A_Z(101, Scope.INTER_FEED),
    FEED_TITLE_Z_A(102, Scope.INTER_FEED),

    RANDOM(103, Scope.INTER_FEED),
    SMART_SHUFFLE_OLD_NEW(104, Scope.INTER_FEED),
    SMART_SHUFFLE_NEW_OLD(105, Scope.INTER_FEED);

    enum class Scope {
        INTRA_FEED,
        INTER_FEED
    }

    companion object {
        /**
         * Converts the string representation to its enum value. If the string value is unknown,
         * the given default value is returned.
         */
        fun parseWithDefault(value: String?, defaultValue: SortOrder): SortOrder {
            return try {
                valueOf(value!!)
            } catch (e: IllegalArgumentException) {
                defaultValue
            }
        }

        @JvmStatic
        fun fromCodeString(codeStr: String?): SortOrder? {
            if (codeStr.isNullOrEmpty()) {
                return null
            }
            val code = codeStr.toInt()
            for (sortOrder in entries) {
                if (sortOrder.code == code) {
                    return sortOrder
                }
            }
            throw IllegalArgumentException("Unsupported code: $code")
        }

        @JvmStatic
        fun toCodeString(sortOrder: SortOrder?): String? {
            return sortOrder?.code?.toString()
        }

        fun valuesOf(stringValues: Array<String?>): Array<SortOrder?> {
            val values = arrayOfNulls<SortOrder>(stringValues.size)
            for (i in stringValues.indices) {
                values[i] = valueOf(stringValues[i]!!)
            }
            return values
        }
    }
}
