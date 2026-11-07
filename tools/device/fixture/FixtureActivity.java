package org.example.a14fixture;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
        notice.setOnClickListener(view -> notices.notify(14,
            new Notification.Builder(this, "a14").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("A14 verification notice").setContentText("Offline fixture payload")
                .setTimeoutAfter(15000).build()));
        root.addView(notice);
        setContentView(root);
    }

    @Override public void onDestroy() {
        if (notices != null) notices.cancelAll();
        super.onDestroy();
    }
}
