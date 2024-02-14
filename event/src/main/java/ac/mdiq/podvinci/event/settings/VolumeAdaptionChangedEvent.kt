package ac.mdiq.podvinci.event.settings

import ac.mdiq.podvinci.model.feed.VolumeAdaptionSetting

class VolumeAdaptionChangedEvent(val volumeAdaptionSetting: VolumeAdaptionSetting, val feedId: Long)
