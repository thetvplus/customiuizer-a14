package org.example.a14fixture;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Offline deterministic input only. No service, receiver, networking or sampling. */
public final class FixtureActivity extends Activity {
    private NotificationManager notices;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        notices = getSystemService(NotificationManager.class);
        notices.createNotificationChannel(new NotificationChannel("a14", "A14 fixture", NotificationManager.IMPORTANCE_HIGH));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 100, 32, 32);
        TextView marker = new TextView(this);
        marker.setText("A14 FIXTURE READY");
        marker.setTextSize(28);
        root.addView(marker);
        Button notice = new Button(this);
        notice.setText("SHOW A14 NOTICE");
        notice.setOnClickListener(view -> publish("A14 verification notice"));
        root.addView(notice);
        setContentView(root);
        event(getIntent());
    }

    private void publish(String title) {
        PendingIntent open = PendingIntent.getActivity(this, 14,
            new Intent(this, FixtureActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        notices.notify(14,
            new Notification.Builder(this, "a14").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText("Offline fixture payload").setContentIntent(open)
                .setTimeoutAfter(15000).build());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        event(intent);
    }

    private void event(Intent intent) {
        String action = intent.getStringExtra("event");
        if ("post".equals(action)) publish("A14 verification notice");
        if ("update".equals(action)) publish("A14 updated notice");
        if ("cancel".equals(action)) notices.cancel(14);
    }

    @Override public void onDestroy() {
        if (notices != null) notices.cancelAll();
        super.onDestroy();
    }
}
