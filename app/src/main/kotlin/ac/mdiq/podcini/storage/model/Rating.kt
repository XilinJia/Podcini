package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.R

enum class Rating(val code: Int, val res: Int) {
    UNRATED(-3, R.drawable.ic_questionmark),
    TRASH(-2, R.drawable.ic_delete),
    BAD(-1, androidx.media3.session.R.drawable.media3_icon_thumb_down_filled),
    NEUTRAL(0, R.drawable.baseline_sentiment_neutral_24),
    GOOD(1, androidx.media3.session.R.drawable.media3_icon_thumb_up_filled),
    FAVORITE(2, R.drawable.ic_star);

    companion object {
        fun fromCode(code: Int): Rating {
            return enumValues<Rating>().firstOrNull { it.code == code } ?: NEUTRAL
        }
    }
}