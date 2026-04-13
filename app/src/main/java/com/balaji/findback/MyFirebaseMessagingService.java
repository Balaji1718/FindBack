package com.balaji.findback;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCM_TEST";
    private static final String CHANNEL_ID = "findback_notifications";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "FCM Message Received");

        String title = "FindBack";
        String message = "You have a new notification";

        // Notification payload
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            message = remoteMessage.getNotification().getBody();
        }

        // Data payload (important for foreground notifications)
        if (!remoteMessage.getData().isEmpty()) {

            Log.d(TAG, "Data Payload: " + remoteMessage.getData());

            if (remoteMessage.getData().containsKey("title")) {
                title = remoteMessage.getData().get("title");
            }

            if (remoteMessage.getData().containsKey("message")) {
                message = remoteMessage.getData().get("message");
            }
        }

        Log.d(TAG, "Notification: " + title + " | " + message);

        showNotification(title, message);
    }

    private void showNotification(String title, String message) {

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Android 8+ notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "FindBack Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription("Notifications for claim updates");

            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    // Called when Firebase creates a new device token
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);

        Log.d(TAG, "New FCM Token: " + token);

        // Optional: send token to server if user already logged in
    }
}