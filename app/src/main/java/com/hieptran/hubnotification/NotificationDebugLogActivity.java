package com.hieptran.hubnotification;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class NotificationDebugLogActivity extends AppCompatActivity {
    private TextView txtLogs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_debug_log);

        txtLogs = findViewById(R.id.txtLogs);
        Button btnRefresh = findViewById(R.id.btnRefreshLogs);
        Button btnClear = findViewById(R.id.btnClearLogs);

        btnRefresh.setOnClickListener(v -> loadLogs());
        btnClear.setOnClickListener(v -> {
            NotificationDebugLogStore.clear(this);
            loadLogs();
        });

        loadLogs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLogs();
    }

    private void loadLogs() {
        txtLogs.setText(NotificationDebugLogStore.read(this));
    }
}
