package com.hieptran.hubnotification;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotificationAppSettingsActivity extends AppCompatActivity {
    private static final int FILTER_ALL = 0;
    private static final int FILTER_ENABLED = 1;
    private static final int FILTER_DISABLED = 2;

    private final List<NotificationAppConfig.AppEntry> allApps = new ArrayList<>();

    private LinearLayout appListContainer;
    private TextView txtStats;
    private String query = "";
    private int filterMode = FILTER_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_app_settings);

        appListContainer = findViewById(R.id.appListContainer);
        txtStats = findViewById(R.id.txtStats);
        EditText edtSearch = findViewById(R.id.edtSearch);
        RadioGroup rgFilter = findViewById(R.id.rgFilter);

        allApps.clear();
        allApps.addAll(NotificationAppConfig.getInstalledApps(this));

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s == null ? "" : s.toString();
                renderAppSwitches();
            }
        });

        rgFilter.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbEnabled) {
                filterMode = FILTER_ENABLED;
            } else if (checkedId == R.id.rbDisabled) {
                filterMode = FILTER_DISABLED;
            } else {
                filterMode = FILTER_ALL;
            }
            renderAppSwitches();
        });

        renderAppSwitches();
    }

    private void renderAppSwitches() {
        appListContainer.removeAllViews();

        int shown = 0;
        for (NotificationAppConfig.AppEntry entry : allApps) {
            String label = entry.label;
            String packageName = entry.packageName;
            boolean enabled = NotificationAppConfig.isPackageEnabled(this, packageName);

            if (!matchFilter(enabled) || !matchSearch(label, packageName)) {
                continue;
            }

            SwitchCompat toggle = new SwitchCompat(this);
            toggle.setText(label + " (" + packageName + ")");
            toggle.setTextSize(15f);
            toggle.setPadding(8, 16, 8, 16);
            toggle.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            toggle.setChecked(enabled);
            toggle.setOnCheckedChangeListener((buttonView, isChecked) ->
                    NotificationAppConfig.setPackageEnabled(this, packageName, isChecked));

            appListContainer.addView(toggle);
            shown++;
        }

        txtStats.setText(shown + " / " + allApps.size() + " app");
    }

    private boolean matchFilter(boolean enabled) {
        if (filterMode == FILTER_ENABLED) {
            return enabled;
        }
        if (filterMode == FILTER_DISABLED) {
            return !enabled;
        }
        return true;
    }

    private boolean matchSearch(@NonNull String label, @NonNull String packageName) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.US).trim();
        return label.toLowerCase(Locale.US).contains(needle)
                || packageName.toLowerCase(Locale.US).contains(needle);
    }
}
