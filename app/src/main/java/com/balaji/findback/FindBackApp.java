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
        
        // Apply theme globally
        ThemeManager.applyTheme(this);
        
        FirebaseApp.initializeApp(this);
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        
        // Use Play Integrity for production
        firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance());
                
        // Use Debug Provider for development/emulators
        firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance());
    }
}
