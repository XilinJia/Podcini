package ac.mdiq.podcini.storage.model

enum class VolumeAdaptionSetting(private val value: Int, @JvmField val adaptionFactor: Float) {
    OFF(0, 1.0f),
    LIGHT_REDUCTION(1, 0.5f),
    HEAVY_REDUCTION(2, 0.2f),
    LIGHT_BOOST(3, 1.5f),
    MEDIUM_BOOST(4, 2f),
    HEAVY_BOOST(5, 2.5f);

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
