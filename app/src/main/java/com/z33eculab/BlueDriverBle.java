package com.z33eculab;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * Read-only diagnostic bridge for the independently documented BlueDriver BLE protocol.
 * One outstanding ELM command at a time. Automatic BLE data-fragment credit ACKs.
 * Intentionally provides no arbitrary command entry and NO flash/programming functionality.
 */
@SuppressLint("MissingPermission") // Permission gating is performed in MainActivity.
public final class BlueDriverBle {
    public interface Listener {
        void onStatus(String message);
        void onLog(String message);
        void onReady(boolean ready);
        void onError(String message);
    }
    public interface Reply { void received(String response, boolean complete); }
    /** Immutable, privacy-safe command transport facts; payload remains with the caller. */
    public static final class CommandResult {
        public final String command, response, classification;
        public final boolean complete;
        public final long roundTripMs;
        public final int dataFragments, decodedBytes;
        private CommandResult(String c, String r, boolean ok, long ms, int fragments, int bytes) {
            command=c; response=r; complete=ok; roundTripMs=ms; dataFragments=fragments; decodedBytes=bytes;
            classification=BlueProtocol.classifyResponse(r,ok);
        }
    }
    public interface ResultReply { void received(CommandResult result); }

    private static final UUID SVC = UUID.fromString(BlueProtocol.SERVICE_UUID);
    private static final UUID CMD = UUID.fromString(BlueProtocol.CMD_UUID);
    private static final UUID DATA = UUID.fromString(BlueProtocol.DATA_UUID);
    private static final UUID DESC_CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<GattOp> queue = new ArrayDeque<>();
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic cmdChar, dataChar;
    private GattOp current;
    private Runnable opFallback, commandTimeout;
    private ScanCallback scanCallback;
    private boolean ready = false;
    private int initIndex = 0;
    private boolean firstLmiRetryUsed = false;
    private long connectionEpoch = 0;

    private Reply pending;
    private String pendingName = "";
    private final StringBuilder buffer = new StringBuilder();
    private boolean promptSeen = false;
    private long commandStarted;
    private int dataFragments, decodedBytes;
    private CommandResult latestResult;
    private ResultReply pendingResult;

    private static final class GattOp {
        final BluetoothGattCharacteristic characteristic;
        final BluetoothGattDescriptor descriptor;
        final byte[] bytes;
        final Runnable after;
        GattOp(BluetoothGattCharacteristic c, byte[] b, Runnable next) {
            characteristic = c; descriptor = null; bytes = b; after = next;
        }
        GattOp(BluetoothGattDescriptor d, byte[] b, Runnable next) {
            descriptor = d; characteristic = null; bytes = b; after = next;
        }
    }
    public BlueDriverBle(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }
    private void status(String message) { listener.onStatus(message); }
    private void log(String message) { listener.onLog(message); }
    public boolean isReady() { return ready; }

    /** Bonded devices are preferred: the user's scanner was already paired in nRF Connect. */
    public void connect() {
        close();
        BluetoothManager manager = (BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) { listener.onError("Turn on Bluetooth first."); return; }
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            if (matching(device.getName())) {
                status("Connecting to paired " + device.getName());
                connectDevice(device);
                return;
            }
        }
        scan();
    }
    private boolean matching(String name) {
        if (name == null) return false;
        String n = name.toUpperCase(Locale.US);
        return n.startsWith("BLUEDRIV") || n.startsWith("LSB2-");
    }
    private void scan() {
        status("Scanning for BlueDriver…");
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { listener.onError("Bluetooth scanner unavailable."); return; }
        scanCallback = new ScanCallback() {
            @Override public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                String name = result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
                if (name == null) name = device.getName();
                if (matching(name)) handler.post(() -> {
                    if (scanCallback == null) return;
                    stopScan(); status("Found " + device.getAddress() + " — connecting"); connectDevice(device);
                });
            }
            @Override public void onScanFailed(int code) {
                handler.post(() -> { stopScan(); listener.onError("BLE scan error " + code); });
            }
        };
        scanner.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
        final ScanCallback started = scanCallback;
        handler.postDelayed(() -> {
            if (scanCallback == started && gatt == null) {
                stopScan(); listener.onError("BlueDriver not found. Close BlueDriver and nRF Connect, then try again.");
            }
        }, 12000);
    }
    private void stopScan() {
        if (scanner != null && scanCallback != null) {
            try { scanner.stopScan(scanCallback); } catch (Exception ignored) { }
        }
        scanCallback = null;
    }
    private void connectDevice(BluetoothDevice device) {
        try { gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE); }
        catch (Exception e) { listener.onError("Connection failed: " + e.getMessage()); }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            handler.post(() -> {
                if (gatt != g) return;
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    log("GATT connection state: " + status + " / " + newState);
                    listener.onError("Disconnected (GATT " + status + "). Tap Connect to retry.");
                    close(); return;
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    status("Connected — discovering services");
                    if (!g.discoverServices()) listener.onError("Unable to discover BLE services.");
                }
            });
        }
        @Override public void onServicesDiscovered(BluetoothGatt g, int result) {
            handler.post(() -> {
                if (g != gatt) return;
                if (result != BluetoothGatt.GATT_SUCCESS || g.getService(SVC) == null) {
                    listener.onError("BlueDriver service not found (GATT " + result + ")."); return;
                }
                cmdChar = g.getService(SVC).getCharacteristic(CMD);
                dataChar = g.getService(SVC).getCharacteristic(DATA);
                if (cmdChar == null || dataChar == null) { listener.onError("Missing command/data characteristics."); return; }
                status("Enabling receive notifications");
                // This scanner has been observed delivering DATA successfully under its
                // combined NOTIFY/INDICATE characteristic. Preserve the working CCC mode.
                final long epoch = connectionEpoch;
                subscribe(cmdChar, () -> subscribe(dataChar, () -> {
                    if (gatt != g || epoch != connectionEpoch) return;
                    status("Initializing adapter");
                    firstLmiRetryUsed = false;
                    enqueue(new GattOp(dataChar, Arrays.copyOf(BlueProtocol.CREDIT, 3), () -> {
                        handler.postDelayed(() -> {
                            if (epoch != connectionEpoch || gatt != g) return;
                            initIndex = 0;
                            initNext();
                        }, 300);
                    }), false);
                }));
            });
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            final byte[] value = c.getValue() == null ? new byte[0] : c.getValue().clone();
            handler.post(() -> { if (g == gatt) handleNotification(c.getUuid(), value); });
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
            final byte[] bytes = value.clone();
            handler.post(() -> { if (g == gatt) handleNotification(c.getUuid(), bytes); });
        }
        @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            handler.post(() -> {
                if (g != gatt || current == null || current.characteristic == null || !current.characteristic.getUuid().equals(c.getUuid())) return;
                if (status != BluetoothGatt.GATT_SUCCESS) log("WARN   BLE write status " + status);
                finishOp();
            });
        }
        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            handler.post(() -> {
                if (g != gatt || current == null || current.descriptor == null ||
                    !current.descriptor.getUuid().equals(d.getUuid()) ||
                    !current.descriptor.getCharacteristic().getUuid().equals(d.getCharacteristic().getUuid())) return;
                if (status != BluetoothGatt.GATT_SUCCESS) { listener.onError("Notification subscription failed: " + status); return; }
                finishOp();
            });
        }
    };

    private void subscribe(BluetoothGattCharacteristic ch, Runnable next) {
        if (gatt == null || !gatt.setCharacteristicNotification(ch, true)) { listener.onError("Cannot enable notifications."); return; }
        BluetoothGattDescriptor ccc = ch.getDescriptor(DESC_CCC);
        if (ccc == null) { listener.onError("Missing notification descriptor."); return; }
        final boolean useNotify = (ch.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
        final byte[] value = useNotify
                ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
        log("BLE    Subscribe " + (ch.getUuid().equals(CMD) ? "CMD" : "DATA")
                + " via " + (useNotify ? "NOTIFY" : "INDICATE"));
        enqueue(new GattOp(ccc, value, next), false);
    }
    private void enqueue(GattOp op, boolean highPriority) {
        if (highPriority) queue.addFirst(op); else queue.addLast(op);
        pump();
    }
    private void pump() {
        if (current != null || queue.isEmpty() || gatt == null) { if (queue.isEmpty() && current == null) maybeCompleteReply(); return; }
        current = queue.removeFirst();
        boolean started = false;
        try {
            if (current.characteristic != null) {
                BluetoothGattCharacteristic ch = current.characteristic;
                int type = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
                if (Build.VERSION.SDK_INT >= 33) {
                    started = gatt.writeCharacteristic(ch, current.bytes, type) == BluetoothGatt.GATT_SUCCESS;
                } else {
                    ch.setWriteType(type); ch.setValue(current.bytes); started = gatt.writeCharacteristic(ch);
                }
            } else {
                if (Build.VERSION.SDK_INT >= 33) {
                    started = gatt.writeDescriptor(current.descriptor, current.bytes) == BluetoothGatt.GATT_SUCCESS;
                } else {
                    current.descriptor.setValue(current.bytes);
                    started = gatt.writeDescriptor(current.descriptor);
                }
            }
        } catch (Exception e) { log("WARN   GATT write exception " + e.getMessage()); }
        if (!started) {
            listener.onError("Bluetooth write could not start. Reconnect and retry.");
            queue.clear(); current = null;
            return;
        }
        GattOp sent = current;
        opFallback = () -> {
            if (current != sent) return;
            if (sent.descriptor != null) {
                listener.onError("Notification setup timed out. Reconnect and retry.");
                queue.clear(); current = null; return;
            }
            // Some Android stacks do not invoke a callback for WRITE_TYPE_NO_RESPONSE.
            log("INFO   Non-response write queued (no callback)");
            finishOp();
        };
        handler.postDelayed(opFallback, sent.descriptor != null ? 4000 : 400);
    }
    private void finishOp() {
        if (current == null) return;
        if (opFallback != null) handler.removeCallbacks(opFallback);
        Runnable after = current.after;
        current = null;
        if (after != null) handler.post(after);
        handler.post(this::pump);
    }
    private void handleNotification(UUID uuid, byte[] value) {
        if (uuid.equals(CMD)) {
            log("CMD ACK  " + BlueProtocol.hex(value));
            return;
        }
        if (!uuid.equals(DATA) || value.length == 0) return;
        // Priority ACK: without this the scanner stalls after a fragment.
        enqueue(new GattOp(dataChar, BlueProtocol.ack(value.length), null), true);
        String chunk = BlueProtocol.decoded(value);
        log("RX " + BlueProtocol.hex(value) + "  →  " + chunk.replace("\r", " ⏎ ").replace("\n", " "));
        if (pending != null) {
            dataFragments++; decodedBytes += chunk.getBytes(StandardCharsets.US_ASCII).length;
            buffer.append(chunk);
            if (buffer.length() > 8192) {
                failReply("Response exceeded 8 KB"); return;
            }
            if (buffer.indexOf(">") >= 0) promptSeen = true;
            maybeCompleteReply();
        }
    }
    private void maybeCompleteReply() {
        if (!promptSeen || pending == null || current != null || !queue.isEmpty()) return;
        Reply done = pending; String answer = buffer.toString();
        CommandResult result = new CommandResult(pendingName, answer, true,
                android.os.SystemClock.elapsedRealtime()-commandStarted, dataFragments, decodedBytes);
        latestResult=result;
        if (commandTimeout != null) handler.removeCallbacks(commandTimeout);
        pending = null; pendingName = ""; promptSeen = false; buffer.setLength(0);
        ResultReply resultDone=pendingResult; pendingResult=null;
        handler.postDelayed(() -> { done.received(result.response, result.complete); if(resultDone!=null) resultDone.received(result); }, 90);
    }
    private void failReply(String reason) {
        if (pending == null) return;
        Reply done = pending;
        String partial = buffer.toString();
        CommandResult result = new CommandResult(pendingName, partial, false,
                android.os.SystemClock.elapsedRealtime()-commandStarted, dataFragments, decodedBytes);
        latestResult=result;
        if (commandTimeout != null) handler.removeCallbacks(commandTimeout);
        pending = null; pendingName = ""; promptSeen = false; buffer.setLength(0);
        log("TIMEOUT " + reason + " partial=" + partial);
        ResultReply resultDone=pendingResult; pendingResult=null;
        done.received(result.response, result.complete); if(resultDone!=null) resultDone.received(result);
    }
    private void transmit(String command, int ms, Reply callback) {
        if (pending != null || gatt == null) { listener.onError("Scanner busy or disconnected."); return; }
        pending = callback; pendingName = command; promptSeen = false; buffer.setLength(0);
        commandStarted=android.os.SystemClock.elapsedRealtime(); dataFragments=decodedBytes=0;
        log("TX     " + command);
        enqueue(new GattOp(cmdChar, BlueProtocol.command(command), null), false);
        commandTimeout = () -> { if (pending != null) failReply("No complete response to " + pendingName); };
        handler.postDelayed(commandTimeout, ms);
    }
    private void initNext() {
        if (gatt == null) return;
        if (initIndex >= BlueProtocol.INIT.length) {
            ready = true; status("READY — diagnostics only"); listener.onReady(true); return;
        }
        String step = BlueProtocol.INIT[initIndex];
        status("Initializing " + (initIndex + 1) + "/" + BlueProtocol.INIT.length + " • " + step);
        final long epoch = connectionEpoch;
        transmit(step, step.equals("LMDP") ? 9000 : 5500, (response, complete) -> {
            if (epoch != connectionEpoch || gatt == null) return;
            log("INIT   " + step + " => " + response.replace("\r", " ").replace(">", " ").trim());
            if (!complete) {
                // On this particular BlueDriver the first cold LMI timed out,
                // while reconnecting and sending the SAME sequence worked.
                // Reopen the DATA credit window and retry first LMI once in-place.
                if (initIndex == 0 && !firstLmiRetryUsed) {
                    firstLmiRetryUsed = true;
                    status("First LMI timed out — automatic retry (1/1)");
                    log("RECOVERY Reopen DATA credits, retry LMI once");
                    enqueue(new GattOp(dataChar, Arrays.copyOf(BlueProtocol.CREDIT, 3), () -> {
                        handler.postDelayed(() -> {
                            if (epoch == connectionEpoch && gatt != null) initNext();
                        }, 650);
                    }), false);
                    return;
                }
                listener.onError("Initialization stopped at " + step
                        + (firstLmiRetryUsed && initIndex == 0 ? " after retry." : ".")
                        + " Disconnect and reconnect; see log.");
                return;
            }
            initIndex++;
            handler.postDelayed(() -> {
                if (epoch == connectionEpoch && gatt != null) initNext();
            }, 100);
        });
    }
    /** Switches only between documented adapter-side diagnostic timing modes.
     * LMX1/LMX0 are interface commands, not ECU firmware or tuning writes.
     * Calls must remain serialized with requests and the normal initialization.
     */
    public void requestTimingMode(String mode, Reply callback) {
        if (!ready) { listener.onError("Wait for READY before changing scanner timing."); return; }
        if (!mode.equals("LMX1") && !mode.equals("LMX0")) {
            listener.onError("Blocked scanner configuration request."); return;
        }
        transmit(mode, 5500, callback);
    }
    public void requestPid(String pid, Reply callback) {
        if (!ready) { listener.onError("Wait until the adapter says READY."); return; }
        if (!BlueProtocol.allowedPid(pid)) { listener.onError("Blocked: unsupported or non-read-only request."); return; }
        transmit(pid, pid.startsWith("09") ? 9500 : 5000, callback);
    }
    public void requestPidResult(String pid, ResultReply callback) {
        if (!ready) { listener.onError("Wait until the adapter says READY."); return; }
        if (!BlueProtocol.allowedPid(pid)) { listener.onError("Blocked: unsupported or non-read-only request."); return; }
        if (pendingResult != null || pending != null || gatt == null) { listener.onError("Scanner busy or disconnected."); return; }
        pendingResult=callback;
        transmit(pid, pid.startsWith("09") ? 9500 : 5000, (response, complete) -> { });
    }
    public CommandResult latestResult() { return latestResult; }
    public void close() {
        connectionEpoch++;
        stopScan();
        ready = false; listener.onReady(false);
        if (opFallback != null) handler.removeCallbacks(opFallback);
        if (commandTimeout != null) handler.removeCallbacks(commandTimeout);
        current = null; queue.clear(); pending = null; pendingResult=null; promptSeen = false; buffer.setLength(0);
        firstLmiRetryUsed = false;
        initIndex = 0;
        BluetoothGatt old = gatt; gatt = null;
        if (old != null) {
            try { old.disconnect(); } catch (Exception ignored) { }
            try { old.close(); } catch (Exception ignored) { }
        }
        cmdChar = dataChar = null;
    }
}
