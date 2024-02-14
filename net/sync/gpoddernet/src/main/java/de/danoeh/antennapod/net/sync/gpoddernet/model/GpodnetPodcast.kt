package de.danoeh.antennapod.net.sync.gpoddernet.model

class GpodnetPodcast(val url: String,
                     val title: String,
                     val description: String,
                     val subscribers: Int,
                     val logoUrl: String,
                     val website: String,
                     val mygpoLink: String,
                     val author: String
) {
    override fun toString(): String {
        return ("GpodnetPodcast [url=" + url + ", title=" + title
                + ", description=" + description + ", subscribers="
                + subscribers + ", logoUrl=" + logoUrl + ", website=" + website
                + ", mygpoLink=" + mygpoLink + "]")
    }
}
