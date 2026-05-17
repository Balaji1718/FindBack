package com.balaji.findback;

import android.app.Application;
import com.balaji.findback.utils.ThemeManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

public class FindBackApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        ThemeManager.applyTheme(this);
        FirebaseApp.initializeApp(this);
        
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        
        // 🔥 FIX: Correctly switch between providers to prevent release APK hanging
        if (BuildConfig.DEBUG) {
            firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance());
        } else {
            firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance());
        }
    }
}
