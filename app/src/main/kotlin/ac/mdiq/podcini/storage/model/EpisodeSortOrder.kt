package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.R

enum class EpisodeSortOrder(val code: Int, val res: Int) {
    DATE_OLD_NEW(1, R.string.publish_date),
    DATE_NEW_OLD(2, R.string.publish_date),
    EPISODE_TITLE_A_Z(3, R.string.episode_title),
    EPISODE_TITLE_Z_A(4, R.string.episode_title),
    DURATION_SHORT_LONG(5, R.string.duration),
    DURATION_LONG_SHORT(6, R.string.duration),
    EPISODE_FILENAME_A_Z(7, R.string.filename),
    EPISODE_FILENAME_Z_A(8, R.string.filename),
    SIZE_SMALL_LARGE(9, R.string.size),
    SIZE_LARGE_SMALL(10, R.string.size),
    PLAYED_DATE_OLD_NEW(11, R.string.last_played_date),
    PLAYED_DATE_NEW_OLD(12, R.string.last_played_date),
    COMPLETED_DATE_OLD_NEW(13, R.string.completed_date),
    COMPLETED_DATE_NEW_OLD(14, R.string.completed_date),
    DOWNLOAD_DATE_OLD_NEW(15, R.string.download_date),
    DOWNLOAD_DATE_NEW_OLD(16, R.string.download_date),
    VIEWS_LOW_HIGH(17, R.string.view_count),
    VIEWS_HIGH_LOW(18, R.string.view_count),

    FEED_TITLE_A_Z(101, R.string.feed_title),
    FEED_TITLE_Z_A(102, R.string.feed_title),
    RANDOM(103, R.string.random),
    RANDOM1(104, R.string.random),
    SMART_SHUFFLE_OLD_NEW(105, R.string.smart_shuffle),
    SMART_SHUFFLE_NEW_OLD(106, R.string.smart_shuffle);

    companion object {
        /**
         * Converts the string representation to its enum value. If the string value is unknown,
         * the given default value is returned.
         */
        fun parseWithDefault(value: String?, defaultValue: EpisodeSortOrder): EpisodeSortOrder {
            return try { valueOf(value!!) } catch (e: IllegalArgumentException) { defaultValue }
        }

        fun fromCodeString(codeStr: String?): EpisodeSortOrder? {
            if (codeStr.isNullOrEmpty()) return null
            val code = codeStr.toInt()
            for (sortOrder in entries) {
                if (sortOrder.code == code) return sortOrder
            }
            throw IllegalArgumentException("Unsupported code: $code")
        }

        fun fromCode(code: Int): EpisodeSortOrder? {
            return enumValues<EpisodeSortOrder>().firstOrNull { it.code == code }
        }

        fun toCodeString(sortOrder: EpisodeSortOrder?): String? {
            return sortOrder?.code?.toString()
        }

        fun valuesOf(stringValues: Array<String?>): Array<EpisodeSortOrder?> {
            val values = arrayOfNulls<EpisodeSortOrder>(stringValues.size)
            for (i in stringValues.indices) values[i] = valueOf(stringValues[i]!!)
            return values
        }
    }
}
