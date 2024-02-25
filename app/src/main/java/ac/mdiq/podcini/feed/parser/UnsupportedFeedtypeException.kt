package ac.mdiq.podcini.feed.parser

import ac.mdiq.podcini.feed.parser.util.TypeGetter

class UnsupportedFeedtypeException : Exception {
    val type: TypeGetter.Type
    var rootElement: String? = null
        private set
    override var message: String? = null
        get() {
            return if (field != null) {
                field!!
            } else if (type == TypeGetter.Type.INVALID) {
                "Invalid type"
            } else {
                "Type $type not supported"
            }
        }

    constructor(type: TypeGetter.Type) : super() {
        this.type = type
    }

    constructor(type: TypeGetter.Type, rootElement: String?) {
        this.type = type
        this.rootElement = rootElement
    }

    constructor(message: String?) {
        this.message = message
        type = TypeGetter.Type.INVALID
    }

//    fun getMessage(): String? {
//        return if (message != null) {
//            message!!
//        } else if (type == TypeGetter.Type.INVALID) {
//            "Invalid type"
//        } else {
//            "Type $type not supported"
//        }
//    }

    companion object {
        private const val serialVersionUID = 9105878964928170669L
    }
}
