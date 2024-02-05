package de.danoeh.antennapod.core.util.download;

import android.content.Context;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.core.storage.DBTasks;
import de.danoeh.antennapod.core.util.NetworkUtils;

@UnstableApi public abstract class NetworkConnectionChangeHandler {
    private static final String TAG = "NetConnectChangeHandler";
    private static Context context;

    public static void init(Context context) {
        NetworkConnectionChangeHandler.context = context;
    }

    public static void networkChangedDetected() {
        if (NetworkUtils.isAutoDownloadAllowed()) {
            Log.d(TAG, "auto-dl network available, starting auto-download");
            DBTasks.autodownloadUndownloadedItems(context);
        } else { // if new network is Wi-Fi, finish ongoing downloads,
            // otherwise cancel all downloads
            if (NetworkUtils.isNetworkRestricted()) {
                Log.i(TAG, "Device is no longer connected to Wi-Fi. Cancelling ongoing downloads");
                DownloadServiceInterface.get().cancelAll(context);
            }
        }
    }
}
