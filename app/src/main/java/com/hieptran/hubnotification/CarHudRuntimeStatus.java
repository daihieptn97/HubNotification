package com.hieptran.hubnotification;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class CarHudRuntimeStatus {
    public interface Listener {
        void onStatusChanged(Snapshot snapshot);
    }

    public static final class Snapshot {
        public final String status;
        public final boolean connected;
        public final boolean ready;
        public final boolean running;

        private Snapshot(String status, boolean connected, boolean ready, boolean running) {
            this.status = status;
            this.connected = connected;
            this.ready = ready;
            this.running = running;
        }
    }

    private static volatile String status = "BLE: idle";
    private static volatile boolean connected;
    private static volatile boolean ready;
    private static volatile boolean running;
    private static final Set<Listener> listeners = new CopyOnWriteArraySet<>();

    private CarHudRuntimeStatus() {
    }

    public static void update(String nextStatus, boolean nextConnected, boolean nextReady, boolean nextRunning) {
        status = nextStatus == null || nextStatus.trim().isEmpty() ? "BLE: idle" : nextStatus.trim();
        connected = nextConnected;
        ready = nextReady;
        running = nextRunning;
        Snapshot snapshot = snapshot();
        for (Listener listener : listeners) {
            listener.onStatusChanged(snapshot);
        }
    }

    public static Snapshot snapshot() {
        return new Snapshot(status, connected, ready, running);
    }

    public static void registerListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static void unregisterListener(Listener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }
}
