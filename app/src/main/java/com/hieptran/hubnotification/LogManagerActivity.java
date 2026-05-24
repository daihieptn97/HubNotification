package com.hieptran.hubnotification;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

public class LogManagerActivity extends AppCompatActivity {
    private TextView txtSummary;
    private TextView txtExportPath;
    private File lastExportFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_manager);

        txtSummary = findViewById(R.id.txtLogSummary);
        txtExportPath = findViewById(R.id.txtExportPath);
        Button btnRefresh = findViewById(R.id.btnRefreshSummary);
        Button btnExport = findViewById(R.id.btnExportTxt);
        Button btnShare = findViewById(R.id.btnShareExport);
        Button btnClear = findViewById(R.id.btnClearAllLogs);

        btnRefresh.setOnClickListener(v -> refreshSummary());

        btnExport.setOnClickListener(v -> {
            try {
                lastExportFile = NotificationDebugLogStore.exportAnalysisTxt(this);
                txtExportPath.setText("Exported: " + lastExportFile.getAbsolutePath());
                Toast.makeText(this, "Export TXT thanh cong", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                txtExportPath.setText("Export failed: " + e.getMessage());
                Toast.makeText(this, "Export that bai", Toast.LENGTH_SHORT).show();
            }
        });

        btnShare.setOnClickListener(v -> shareLastExport());

        btnClear.setOnClickListener(v -> {
            NotificationDebugLogStore.clear(this);
            txtExportPath.setText("Logs cleared");
            refreshSummary();
        });

        refreshSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSummary();
    }

    private void refreshSummary() {
        txtSummary.setText(NotificationDebugLogStore.buildAnalysisReport(this));
    }

    private void shareLastExport() {
        if (lastExportFile == null || !lastExportFile.exists()) {
            Toast.makeText(this, "Chua co file export. Bam Export TXT truoc.", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                lastExportFile
        );

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share log TXT"));
    }
}
