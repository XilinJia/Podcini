package ac.mdiq.podvinci.event

import android.content.Context
import androidx.core.util.Consumer

class MessageEvent @JvmOverloads constructor(@JvmField val message: String,
                                             @JvmField val action: Consumer<Context>? = null,
                                             @JvmField val actionText: String? = null
)
