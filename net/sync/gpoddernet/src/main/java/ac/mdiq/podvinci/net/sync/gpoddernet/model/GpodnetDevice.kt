package ac.mdiq.podvinci.net.sync.gpoddernet.model

class GpodnetDevice(val id: String,
                    val caption: String,
                    type: String?,
                    val subscriptions: Int
) {
    val type: DeviceType

    init {
        this.type = DeviceType.fromString(type)
    }

    override fun toString(): String {
        return ("GpodnetDevice [id=" + id + ", caption=" + caption + ", type="
                + type + ", subscriptions=" + subscriptions + "]")
    }

    enum class DeviceType {
        DESKTOP, LAPTOP, MOBILE, SERVER, OTHER;

        override fun toString(): String {
            return super.toString().lowercase()
        }

        companion object {
            fun fromString(s: String?): DeviceType {
                if (s == null) {
                    return OTHER
                }

                return when (s) {
                    "desktop" -> DESKTOP
                    "laptop" -> LAPTOP
                    "mobile" -> MOBILE
                    "server" -> SERVER
                    else -> OTHER
                }
            }
        }
    }
}
