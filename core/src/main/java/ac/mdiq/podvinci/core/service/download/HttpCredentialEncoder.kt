package ac.mdiq.podvinci.core.service.download

import okio.ByteString;
import java.io.UnsupportedEncodingException

object HttpCredentialEncoder {
    @JvmStatic
    fun encode(username: String, password: String, charset: String?): String {
        try {
            val credentials = "$username:$password"
            val bytes = credentials.toByteArray(charset(charset!!))
            val encoded: String = ByteString.of(*bytes).base64()
            return "Basic $encoded"
        } catch (e: UnsupportedEncodingException) {
            throw AssertionError(e)
        }
    }
}
