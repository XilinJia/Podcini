package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.R

enum class VolumeAdaptionSetting(private val value: Int, @JvmField val adaptionFactor: Float, val resId: Int) {
    OFF(0, 1.0f, R.string.feed_volume_reduction_off),
    LIGHT_REDUCTION(1, 0.5f, R.string.feed_volume_reduction_light),
    HEAVY_REDUCTION(2, 0.2f, R.string.feed_volume_reduction_heavy),
    LIGHT_BOOST(3, 1.6f, R.string.feed_volume_boost_light),
    MEDIUM_BOOST(4, 2.4f, R.string.feed_volume_boost_medium),
    HEAVY_BOOST(5, 3.6f, R.string.feed_volume_boost_heavy);

    fun toInteger(): Int {
        return value
    }

    companion object {
        @JvmStatic
        fun fromInteger(value: Int): VolumeAdaptionSetting {
            return enumValues<VolumeAdaptionSetting>().firstOrNull { it.value == value } ?: throw IllegalArgumentException("Cannot map value to VolumeAdaptionSetting: $value")
        }
    }
}
