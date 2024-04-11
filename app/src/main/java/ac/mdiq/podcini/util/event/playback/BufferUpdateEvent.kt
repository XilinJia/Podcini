package ac.mdiq.podcini.util.event.playback

class BufferUpdateEvent private constructor(@JvmField val progress: Float) {
    fun hasStarted(): Boolean {
        return progress == PROGRESS_STARTED
    }

    fun hasEnded(): Boolean {
        return progress == PROGRESS_ENDED
    }

    companion object {
        private const val PROGRESS_STARTED = -1f
        private const val PROGRESS_ENDED = -2f
        fun started(): BufferUpdateEvent {
            return BufferUpdateEvent(PROGRESS_STARTED)
        }

        fun ended(): BufferUpdateEvent {
            return BufferUpdateEvent(PROGRESS_ENDED)
        }

        fun progressUpdate(progress: Float): BufferUpdateEvent {
            return BufferUpdateEvent(progress)
        }
    }
}
