package ac.mdiq.podcini.storage.model

import ac.mdiq.vista.extractor.stream.AudioStream
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class AStream  {
    var averageBitrate: Int = 0

    var bitrate: Int = 0

    var quality: String? = null

    var codec: String? = null

    var format: String? = null

    var audioTrackId: String? = null

    var audioTrackName: String? = null

    var audioLocale: String? = null

    var deliveryMethod: String? = null

    var url: String? = null

    var isvalid: Boolean = true

    constructor() {}

    constructor(s: AudioStream) {
        averageBitrate = s.averageBitrate
        bitrate = s.bitrate
        quality = s.quality
        codec = s.codec
        format = s.format?.name
        audioTrackId = s.audioTrackId
        audioTrackName = s.audioTrackName
        audioLocale = s.audioLocale?.toLanguageTag()
        deliveryMethod = s.deliveryMethod.name
        url = if (s.isUrl) s.content else null
    }
}