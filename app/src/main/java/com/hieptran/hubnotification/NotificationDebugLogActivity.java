package com.hieptran.hubnotification;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class NotificationDebugLogActivity extends AppCompatActivity {
    private EditText edtPayload;
    private ArrayAdapter<String> txHistoryAdapter;
    private ArrayAdapter<String> logEntriesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_debug_log);

        edtPayload = findViewById(R.id.edtPayload);
        ListView listRecentTx = findViewById(R.id.listRecentTx);
        ListView listNotificationLogs = findViewById(R.id.listNotificationLogs);
        Button btnRefresh = findViewById(R.id.btnRefreshLogs);
        Button btnClear = findViewById(R.id.btnClearLogs);
        Button btnResendLast = findViewById(R.id.btnResendLast);
        Button btnSendPayload = findViewById(R.id.btnSendPayload);
        Button btnStopHud = findViewById(R.id.btnStopHudFromDebug);

        txHistoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        listRecentTx.setAdapter(txHistoryAdapter);
        listRecentTx.setOnItemClickListener((parent, view, position, id) -> {
            String payload = txHistoryAdapter.getItem(position);
            if (!TextUtils.isEmpty(payload)) {
                edtPayload.setText(payload);
                sendPayload(payload);
            }
        });

        logEntriesAdapter = new ArrayAdapter<>(
                this,
                R.layout.item_log_entry,
                R.id.txtLogEntryItem
        );
        listNotificationLogs.setAdapter(logEntriesAdapter);
        listNotificationLogs.setOnItemClickListener((parent, view, position, id) -> {
            String entry = logEntriesAdapter.getItem(position);
            String payload = extractPayloadFromLogEntry(entry);
            if (!TextUtils.isEmpty(payload)) {
                edtPayload.setText(payload);
                sendPayload(payload);
            } else {
                Toast.makeText(this, "Log item nay khong co JSON de gui lai", Toast.LENGTH_SHORT).show();
            }
        });

        btnRefresh.setOnClickListener(v -> loadLogs());
        btnClear.setOnClickListener(v -> {
            NotificationDebugLogStore.clear(this);
            edtPayload.setText("");
            loadLogs();
        });
        btnResendLast.setOnClickListener(v -> {
            String payload = NotificationDebugLogStore.getLastTxPayload(this);
            if (!TextUtils.isEmpty(payload)) {
                edtPayload.setText(payload);
                sendPayload(payload);
            }
        });
        btnSendPayload.setOnClickListener(v -> {
            String payload = edtPayload.getText() == null ? "" : edtPayload.getText().toString().trim();
            if (!payload.isEmpty()) {
                sendPayload(payload);
            }
        });
        btnStopHud.setOnClickListener(v -> {
            Intent intent = new Intent(this, CarHudService.class);
            intent.setAction(CarHudConstants.ACTION_STOP_HUD);
            startService(intent);
        });

        loadLogs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLogs();
    }

    private void loadLogs() {
        if (TextUtils.isEmpty(edtPayload.getText())) {
            edtPayload.setText(NotificationDebugLogStore.getLastTxPayload(this));
        }

        txHistoryAdapter.clear();
        txHistoryAdapter.addAll(NotificationDebugLogStore.getTxHistory(this));
        txHistoryAdapter.notifyDataSetChanged();

        logEntriesAdapter.clear();
        logEntriesAdapter.addAll(NotificationDebugLogStore.getLogEntries(this));
        logEntriesAdapter.notifyDataSetChanged();
    }

    private void sendPayload(String payload) {
        Intent intent = new Intent(this, CarHudService.class);
        intent.setAction(CarHudConstants.ACTION_SEND_TEST_PAYLOAD);
        intent.putExtra(CarHudConstants.EXTRA_TEST_PAYLOAD, payload);
        startService(intent);
        loadLogs();
    }

    private String extractPayloadFromLogEntry(String entry) {
        if (TextUtils.isEmpty(entry)) {
            return "";
        }

        String marker = "json :";
        int idx = entry.indexOf(marker);
        if (idx < 0) {
            return "";
        }
        return entry.substring(idx + marker.length()).trim();
    }
}
