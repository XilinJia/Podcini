package ac.mdiq.podcini.util.event.settings

import ac.mdiq.podcini.storage.model.feed.VolumeAdaptionSetting

class VolumeAdaptionChangedEvent(val volumeAdaptionSetting: VolumeAdaptionSetting, val feedId: Long)
