package de.danoeh.antennapod.event.settings

import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting

class VolumeAdaptionChangedEvent(val volumeAdaptionSetting: VolumeAdaptionSetting, val feedId: Long)
