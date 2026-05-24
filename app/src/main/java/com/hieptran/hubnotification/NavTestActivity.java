package com.hieptran.hubnotification;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class NavTestActivity extends AppCompatActivity {
    private TextView txtLastPayload;
    private final List<TestItem> testItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nav_test);

        txtLastPayload = findViewById(R.id.txtLastPayload);
        ListView listCommands = findViewById(R.id.listNavCommands);

        setupItems();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                buildDisplayRows()
        );
        listCommands.setAdapter(adapter);
        listCommands.setOnItemClickListener((parent, view, position, id) -> {
            TestItem item = testItems.get(position);
            sendRaw(item.payload);
        });

        Button btnStartHud = findViewById(R.id.btnStartHudFromNavTest);
        btnStartHud.setOnClickListener(v -> {
            Intent intent = new Intent(this, CarHudService.class);
            intent.setAction(CarHudConstants.ACTION_START_HUD);
            startService(intent);
        });

        Button btnStopHud = findViewById(R.id.btnStopHudFromNavTest);
        btnStopHud.setOnClickListener(v -> {
            Intent intent = new Intent(this, CarHudService.class);
            intent.setAction(CarHudConstants.ACTION_STOP_HUD);
            startService(intent);
        });
    }

    private void setupItems() {
        testItems.clear();
        testItems.add(new TestItem("Right", buildNavPayload("right", 250, "m", "Turn right onto Le Loi")));
        testItems.add(new TestItem("Left", buildNavPayload("left", 220, "m", "Turn left onto Tran Hung Dao")));
        testItems.add(new TestItem("U-turn", buildNavPayload("uturn", 120, "m", "Make a U-turn")));
        testItems.add(new TestItem("U-turn right", buildNavPayload("uturn-right", 140, "m", "Make a right U-turn")));
        testItems.add(new TestItem("Straight", buildNavPayload("straight", 1, "km", "Continue on Nguyen Trai")));
        testItems.add(new TestItem("Arrive", buildNavPayload("arrive", 0, "m", "Arrived at destination")));
        testItems.add(new TestItem("Slight right", buildNavPayload("slight-right", 300, "m", "Bear right onto Ring Road")));
        testItems.add(new TestItem("Slight left", buildNavPayload("slight-left", 300, "m", "Bear left onto Ring Road")));
        testItems.add(new TestItem("Sharp right", buildNavPayload("sharp-right", 80, "m", "Sharp right ahead")));
        testItems.add(new TestItem("Sharp left", buildNavPayload("sharp-left", 80, "m", "Sharp left ahead")));
        testItems.add(new TestItem("Variant arr=turn_left", buildNavPayload("turn_left", 180, "m", "Variant: turn_left")));
        testItems.add(new TestItem("Variant arr=TURN-RIGHT", buildNavPayload("TURN-RIGHT", 180, "m", "Variant: TURN-RIGHT")));
        testItems.add(new TestItem("Clear (t=clr)", "{\"t\":\"clr\"}"));
    }

    private List<String> buildDisplayRows() {
        List<String> rows = new ArrayList<>();
        for (TestItem item : testItems) {
            rows.add(item.label + "\n" + item.payload);
        }
        return rows;
    }

    private String buildNavPayload(String arr, int distance, String unit, String street) {
        JSONObject json = new JSONObject();
        try {
            json.put("t", "nav");
            json.put("arr", arr);
            json.put("d", distance);
            json.put("u", unit);
            json.put("s", street);
            return json.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    private void sendRaw(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            txtLastPayload.setText("Payload error: empty payload");
            return;
        }
        Intent intent = new Intent(this, CarHudService.class);
        intent.setAction(CarHudConstants.ACTION_SEND_TEST_PAYLOAD);
        intent.putExtra(CarHudConstants.EXTRA_TEST_PAYLOAD, payload);
        startService(intent);
        txtLastPayload.setText("Sent: " + payload);
    }

    private static final class TestItem {
        final String label;
        final String payload;

        TestItem(String label, String payload) {
            this.label = label;
            this.payload = payload;
        }
    }
}
