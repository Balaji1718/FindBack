package com.balaji.findback.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;

public class NetworkUtils {

    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;
        
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network network = connectivityManager.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(network);
                return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
                                       actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || 
                                       actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            } else {
                android.net.NetworkInfo nwInfo = connectivityManager.getActiveNetworkInfo();
                return nwInfo != null && nwInfo.isConnected();
            }
        }
        return false;
    }
}
