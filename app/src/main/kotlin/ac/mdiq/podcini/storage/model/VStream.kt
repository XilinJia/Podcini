package ac.mdiq.podcini.storage.model

import ac.mdiq.vista.extractor.stream.VideoStream
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class VStream : RealmObject {
    @PrimaryKey
    var id: Long = 0L

    var isVideoOnly: Boolean = false

    var bitrate: Int = 0

    var fps: Int = 0

    var width: Int = 0

    var height: Int = 0

    var quality: String? = null

    var codec: String? = null

    var deliveryMethod: String? = null

    var url: String? = null

    var resolution: String? = null

    var isvalid: Boolean = true

    constructor() {}

    constructor(s: VideoStream) {
        isVideoOnly = s.isVideoOnly
        bitrate = s.bitrate
        fps = s.fps
        width = s.width
        height = s.height
        quality = s.quality
        codec = s.codec
        deliveryMethod = s.deliveryMethod.name
        resolution = s.resolution
        url = if (s.isUrl) s.content else null
    }
}