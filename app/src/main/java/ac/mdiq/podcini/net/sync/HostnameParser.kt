package ac.mdiq.podcini.net.sync

import org.apache.commons.lang3.StringUtils
import java.net.IDN
import java.util.regex.Pattern

class HostnameParser(hosturl: String?) {
    @JvmField
    var scheme: String? = null
    @JvmField
    var port: Int = 0
    @JvmField
    var host: String? = null
    @JvmField
    var subfolder: String? = null

    init {
        val m = URLSPLIT_REGEX.matcher(hosturl)
        if (m.matches()) {
            scheme = m.group(1)
            host = IDN.toASCII(m.group(2))
            // regex -> can only be digits
            port = if (m.group(3) == null) -1 else m.group(3).toInt()
            subfolder = if (m.group(4) == null) "" else StringUtils.stripEnd(m.group(4), "/")
        } else {
            // URL does not match regex: use it anyway -> this will cause an exception on connect
            scheme = "https"
            host = IDN.toASCII(hosturl)
            port = 443
        }

        when {
            scheme == null && port == 80 -> scheme = "http"
            scheme == null -> scheme = "https" // assume https
        }
        when {
            scheme == "https" && port == -1 -> port = 443
            scheme == "http" && port == -1 -> port = 80
        }
    }

    companion object {
        // split into schema, host and port - missing parts are null
        private val URLSPLIT_REGEX: Pattern = Pattern.compile("(?:(https?)://)?([^:/]+)(?::(\\d+))?(.+)?")
    }
}
