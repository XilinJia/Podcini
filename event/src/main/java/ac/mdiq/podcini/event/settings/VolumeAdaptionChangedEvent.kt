package ac.mdiq.podcini.event.settings

import ac.mdiq.podcini.model.feed.VolumeAdaptionSetting

class VolumeAdaptionChangedEvent(val volumeAdaptionSetting: VolumeAdaptionSetting, val feedId: Long)
