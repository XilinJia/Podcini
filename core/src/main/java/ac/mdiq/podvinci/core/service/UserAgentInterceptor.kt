package ac.mdiq.podvinci.core.service

import ac.mdiq.podvinci.core.ClientConfig
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response
import java.io.IOException

class UserAgentInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Chain): Response {
        return chain.proceed(chain.request().newBuilder()
            .header("User-Agent", ClientConfig.USER_AGENT?:"")
            .build())
    }
}
