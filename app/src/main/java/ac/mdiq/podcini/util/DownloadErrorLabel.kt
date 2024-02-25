package ac.mdiq.podcini.util

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import androidx.annotation.StringRes
import ac.mdiq.podcini.storage.model.download.DownloadError

/**
 * Provides user-visible labels for download errors.
 */
object DownloadErrorLabel {
    @JvmStatic
    @StringRes
    fun from(error: DownloadError?): Int {
        when (error) {
            DownloadError.SUCCESS -> return R.string.download_successful
            DownloadError.ERROR_PARSER_EXCEPTION -> return R.string.download_error_parser_exception
            DownloadError.ERROR_UNSUPPORTED_TYPE -> return R.string.download_error_unsupported_type
            DownloadError.ERROR_CONNECTION_ERROR -> return R.string.download_error_connection_error
            DownloadError.ERROR_MALFORMED_URL -> return R.string.download_error_error_unknown
            DownloadError.ERROR_IO_ERROR -> return R.string.download_error_io_error
            DownloadError.ERROR_FILE_EXISTS -> return R.string.download_error_error_unknown
            DownloadError.ERROR_DOWNLOAD_CANCELLED -> return R.string.download_canceled_msg
            DownloadError.ERROR_DEVICE_NOT_FOUND -> return R.string.download_error_device_not_found
            DownloadError.ERROR_HTTP_DATA_ERROR -> return R.string.download_error_http_data_error
            DownloadError.ERROR_NOT_ENOUGH_SPACE -> return R.string.download_error_insufficient_space
            DownloadError.ERROR_UNKNOWN_HOST -> return R.string.download_error_unknown_host
            DownloadError.ERROR_REQUEST_ERROR -> return R.string.download_error_request_error
            DownloadError.ERROR_DB_ACCESS_ERROR -> return R.string.download_error_db_access
            DownloadError.ERROR_UNAUTHORIZED -> return R.string.download_error_unauthorized
            DownloadError.ERROR_FILE_TYPE -> return R.string.download_error_file_type_type
            DownloadError.ERROR_FORBIDDEN -> return R.string.download_error_forbidden
            DownloadError.ERROR_IO_WRONG_SIZE -> return R.string.download_error_wrong_size
            DownloadError.ERROR_IO_BLOCKED -> return R.string.download_error_blocked
            DownloadError.ERROR_UNSUPPORTED_TYPE_HTML -> return R.string.download_error_unsupported_type_html
            DownloadError.ERROR_NOT_FOUND -> return R.string.download_error_not_found
            DownloadError.ERROR_CERTIFICATE -> return R.string.download_error_certificate
            DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE -> return R.string.download_error_parser_exception
            else -> {
                require(!BuildConfig.DEBUG) { "No mapping from download error to label" }
                return R.string.download_error_error_unknown
            }
        }
    }
}
