package ac.mdiq.podcini.storage.model.feed

import org.apache.commons.lang3.StringUtils

class FeedFunding(@JvmField var url: String?, @JvmField var content: String?) {
    fun setContent(content: String?) {
        this.content = content
    }

    fun setUrl(url: String?) {
        this.url = url
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null || obj.javaClass != this.javaClass) return false
        val funding = obj as FeedFunding
        if (url == null && funding.url == null && content == null && funding.content == null) return true
        if (url != null && url == funding.url && content != null && content == funding.content) return true

        return false
    }

    override fun hashCode(): Int {
        return (url + FUNDING_TITLE_SEPARATOR + content).hashCode()
    }

    companion object {
        const val FUNDING_ENTRIES_SEPARATOR: String = "\u001e"
        const val FUNDING_TITLE_SEPARATOR: String = "\u001f"

        @JvmStatic
        fun extractPaymentLinks(payLinks: String?): ArrayList<FeedFunding> {
            if (payLinks.isNullOrBlank()) return arrayListOf()

            // old format before we started with PodcastIndex funding tag
            val funding = ArrayList<FeedFunding>()
            if (!payLinks.contains(FUNDING_ENTRIES_SEPARATOR) && !payLinks.contains(FUNDING_TITLE_SEPARATOR)) {
                funding.add(FeedFunding(payLinks, ""))
                return funding
            }
            val list = payLinks.split(FUNDING_ENTRIES_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (list.isEmpty()) return arrayListOf()

            for (str in list) {
                val linkContent = str.split(FUNDING_TITLE_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (StringUtils.isBlank(linkContent[0])) continue

                val url = linkContent[0]
                var title = ""
                if (linkContent.size > 1 && !StringUtils.isBlank(linkContent[1])) title = linkContent[1]
                funding.add(FeedFunding(url, title))
            }
            return funding
        }

        fun getPaymentLinksAsString(fundingList: ArrayList<FeedFunding>?): String? {
            val result = StringBuilder()
            if (fundingList == null) return null

            for (fund in fundingList) {
                result.append(fund.url).append(FUNDING_TITLE_SEPARATOR).append(fund.content)
                result.append(FUNDING_ENTRIES_SEPARATOR)
            }
            return StringUtils.removeEnd(result.toString(), FUNDING_ENTRIES_SEPARATOR)
        }
    }
}
