package ac.mdiq.podcini.parser.media.id3.model

class FrameHeader(id: String?, size: Int, flags: Short) : Header(
    id!!, size)
