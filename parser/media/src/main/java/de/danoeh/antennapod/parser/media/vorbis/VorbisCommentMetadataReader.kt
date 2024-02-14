package de.danoeh.antennapod.parser.media.vorbis

import java.io.InputStream

class VorbisCommentMetadataReader(input: InputStream?) : VorbisCommentReader(input!!) {
    var description: String? = null
        private set

    public override fun handles(key: String?): Boolean {
        return KEY_DESCRIPTION == key || KEY_COMMENT == key
    }

    public override fun onContentVectorValue(key: String?, value: String?) {
        if (KEY_DESCRIPTION == key || KEY_COMMENT == key) {
            if (description == null || value!!.length > description!!.length) {
                description = value
            }
        }
    }

    companion object {
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_COMMENT = "comment"
    }
}
