package ac.mdiq.podcini.feed

import ac.mdiq.podcini.storage.model.feed.VolumeAdaptionSetting
import ac.mdiq.podcini.storage.model.feed.VolumeAdaptionSetting.Companion.fromInteger
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Test

class VolumeAdaptionSettingTest {
    @Test
    fun mapOffToInteger() {
        val setting = VolumeAdaptionSetting.OFF
        MatcherAssert.assertThat(setting.toInteger(), Matchers.`is`(Matchers.equalTo(0)))
    }

    @Test
    fun mapLightReductionToInteger() {
        val setting = VolumeAdaptionSetting.LIGHT_REDUCTION

        MatcherAssert.assertThat(setting.toInteger(), Matchers.`is`(Matchers.equalTo(1)))
    }

    @Test
    fun mapHeavyReductionToInteger() {
        val setting = VolumeAdaptionSetting.HEAVY_REDUCTION

        MatcherAssert.assertThat(setting.toInteger(), Matchers.`is`(Matchers.equalTo(2)))
    }

    @Test
    fun mapLightBoostToInteger() {
        val setting = VolumeAdaptionSetting.LIGHT_BOOST

        MatcherAssert.assertThat(setting.toInteger(), Matchers.`is`(Matchers.equalTo(3)))
    }

    @Test
    fun mapMediumBoostToInteger() {
        val setting = VolumeAdaptionSetting.MEDIUM_BOOST

        MatcherAssert.assertThat(setting.toInteger(), Matchers.`is`(Matchers.equalTo(4)))
    }

    @Test
    fun mapHeavyBoostToInteger() {
        val setting = VolumeAdaptionSetting.HEAVY_BOOST

        MatcherAssert.assertThat(setting.toInteger(), Matchers.`is`(Matchers.equalTo(5)))
    }

    @Test
    fun mapIntegerToVolumeAdaptionSetting() {
        MatcherAssert.assertThat(fromInteger(0), Matchers.`is`(Matchers.equalTo(VolumeAdaptionSetting.OFF)))
        MatcherAssert.assertThat(fromInteger(1), Matchers.`is`(Matchers.equalTo(VolumeAdaptionSetting.LIGHT_REDUCTION)))
        MatcherAssert.assertThat(fromInteger(2), Matchers.`is`(Matchers.equalTo(VolumeAdaptionSetting.HEAVY_REDUCTION)))
        MatcherAssert.assertThat(fromInteger(3), Matchers.`is`(Matchers.equalTo(VolumeAdaptionSetting.LIGHT_BOOST)))
        MatcherAssert.assertThat(fromInteger(4), Matchers.`is`(Matchers.equalTo(VolumeAdaptionSetting.MEDIUM_BOOST)))
        MatcherAssert.assertThat(fromInteger(5), Matchers.`is`(Matchers.equalTo(VolumeAdaptionSetting.HEAVY_BOOST)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotMapNegativeValues() {
        fromInteger(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotMapValuesOutOfRange() {
        fromInteger(6)
    }

    @Test
    fun noAdaptionIfTurnedOff() {
        val adaptionFactor: Float = VolumeAdaptionSetting.OFF.adaptionFactor
        Assert.assertEquals(1.0f, adaptionFactor, 0.01f)
    }

    @Test
    fun lightReductionYieldsHigherValueThanHeavyReduction() {
        val lightReductionFactor: Float = VolumeAdaptionSetting.LIGHT_REDUCTION.adaptionFactor

        val heavyReductionFactor: Float = VolumeAdaptionSetting.HEAVY_REDUCTION.adaptionFactor

        Assert.assertTrue("Light reduction must have higher factor than heavy reduction",
            lightReductionFactor > heavyReductionFactor)
    }

    @Test
    fun lightBoostYieldsHigherValueThanLightReduction() {
        val lightReductionFactor: Float = VolumeAdaptionSetting.LIGHT_REDUCTION.adaptionFactor

        val lightBoostFactor: Float = VolumeAdaptionSetting.LIGHT_BOOST.adaptionFactor

        Assert.assertTrue("Light boost must have higher factor than light reduction",
            lightBoostFactor > lightReductionFactor)
    }

    @Test
    fun mediumBoostYieldsHigherValueThanLightBoost() {
        val lightBoostFactor: Float = VolumeAdaptionSetting.LIGHT_BOOST.adaptionFactor

        val mediumBoostFactor: Float = VolumeAdaptionSetting.MEDIUM_BOOST.adaptionFactor

        Assert.assertTrue("Medium boost must have higher factor than light boost", mediumBoostFactor > lightBoostFactor)
    }

    @Test
    fun heavyBoostYieldsHigherValueThanMediumBoost() {
        val mediumBoostFactor: Float = VolumeAdaptionSetting.MEDIUM_BOOST.adaptionFactor

        val heavyBoostFactor: Float = VolumeAdaptionSetting.HEAVY_BOOST.adaptionFactor

        Assert.assertTrue("Heavy boost must have higher factor than medium boost", heavyBoostFactor > mediumBoostFactor)
    }
}
