package com.example.airpodsconnect;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.Gravity;
import android.widget.*;
import java.util.*;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int REQ = 41;
    private static final UUID BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt activeGatt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> seen = new HashSet<>();
    private LinearLayout devices;
    private TextView status, battery;
    private Button scanButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        makeUi();
        BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) status.setText("এই ফোনে Bluetooth পাওয়া যায়নি।");
        else if (!adapter.isEnabled()) status.setText("ফোনের Bluetooth চালু করুন।");
        else status.setText("Bluetooth প্রস্তুত। Scan চাপুন।");
        if (adapter == null) scanButton.setEnabled(false);
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }

    private void makeUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(16));
        root.setBackgroundColor(Color.rgb(246,248,252));
        TextView title = new TextView(this);
        title.setText("AirPods Connect"); title.setTextSize(28);
        title.setTextColor(Color.rgb(20,28,48)); title.setGravity(Gravity.CENTER);
        title.setTypeface(null,1);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(55)));
        TextView sub = new TextView(this);
        sub.setText("Bluetooth earbuds battery checker"); sub.setGravity(Gravity.CENTER);
        sub.setTextColor(Color.DKGRAY);
        root.addView(sub,new LinearLayout.LayoutParams(-1,dp(28)));
        status = new TextView(this); status.setTextSize(15); status.setGravity(Gravity.CENTER);
        status.setTextColor(Color.rgb(40,60,90)); status.setPadding(dp(8),dp(12),dp(8),dp(12));
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));
        scanButton = new Button(this); scanButton.setText("SCAN DEVICES");
        scanButton.setOnClickListener(v -> startScan());
        root.addView(scanButton,new LinearLayout.LayoutParams(-1,dp(52)));
        battery = new TextView(this); battery.setText("Battery level\n--");
        battery.setTextSize(22); battery.setGravity(Gravity.CENTER);
        battery.setTextColor(Color.rgb(20,28,48)); battery.setPadding(dp(10),dp(18),dp(10),dp(18));
        root.addView(battery,new LinearLayout.LayoutParams(-1,-2));
        TextView label = new TextView(this); label.setText("Nearby Bluetooth devices");
        label.setTextSize(17); label.setTypeface(null,1); label.setTextColor(Color.BLACK);
        root.addView(label);
        ScrollView scroll = new ScrollView(this);
        devices = new LinearLayout(this); devices.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(devices); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView note = new TextView(this);
        note.setText("মনে রাখুন: Left, Right ও Case-এর আলাদা battery দেখাতে earbuds-কে সেই তথ্য Bluetooth-এ দিতে হয়। সব AirPods/clone এই তথ্য দেয় না।");
        note.setTextSize(12); note.setTextColor(Color.GRAY); note.setPadding(0,dp(12),0,0);
        root.addView(note); setContentView(root);
    }

    private boolean permitted() {
        if (Build.VERSION.SDK_INT >= 31)
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED &&
                   checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
        return Build.VERSION.SDK_INT < 23 ||
               checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }

    private void askPermission() {
        if (Build.VERSION.SDK_INT >= 31)
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},REQ);
        else if (Build.VERSION.SDK_INT >= 23)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ);
    }

    private void startScan() {
        if (adapter == null) return;
        if (!adapter.isEnabled()) { status.setText("Bluetooth চালু করে আবার চেষ্টা করুন।"); return; }
        if (!permitted()) { askPermission(); return; }
        try {
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) { status.setText("BLE scanner পাওয়া যায়নি।"); return; }
            seen.clear(); devices.removeAllViews();
            battery.setText("Battery level\n--");
            status.setText("ডিভাইস খোঁজা হচ্ছে..."); scanButton.setEnabled(false);
            scanner.startScan(callback);
            handler.postDelayed(() -> {
                try { if (scanner != null) scanner.stopScan(callback); } catch (SecurityException ignored) {}
                scanButton.setEnabled(true); status.setText("Scan শেষ। ডিভাইসে চাপ দিয়ে connect করুন।");
            },10000);
        } catch (SecurityException e) { status.setText("Bluetooth permission প্রয়োজন।"); scanButton.setEnabled(true); }
    }

    private final ScanCallback callback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) {
            BluetoothDevice d = result.getDevice();
            if (d != null) runOnUiThread(() -> addDevice(d,result.getRssi()));
        }
        @Override public void onScanFailed(int code) {
            runOnUiThread(() -> { status.setText("Scan ব্যর্থ। Bluetooth পরীক্ষা করুন।"); scanButton.setEnabled(true); });
        }
    };

    private void addDevice(BluetoothDevice d,int rssi) {
        if (!permitted()) return;
        try {
            String address=d.getAddress();
            if (!seen.add(address)) return;
            String name=d.getName();
            if (name==null || name.trim().isEmpty()) name="Unknown Bluetooth device";
            Button item=new Button(this); item.setAllCaps(false);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setText(name+"  •  "+rssi+" dBm\n"+address);
            item.setOnClickListener(v -> connect(d,name));
            devices.addView(item,new LinearLayout.LayoutParams(-1,dp(68)));
        } catch (SecurityException ignored) {}
    }

    private void connect(BluetoothDevice d,String name) {
        if (!permitted()) { askPermission(); return; }
        try {
            if (activeGatt!=null) { activeGatt.close(); activeGatt=null; }
            status.setText(name+" এর সাথে connect হচ্ছে...");
            activeGatt=d.connectGatt(this,false,gattCallback);
        } catch (SecurityException e) { status.setText("Bluetooth connect permission দিন।"); }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g,int s,int state) {
            if (state==BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(() -> status.setText("Connected। Battery service খোঁজা হচ্ছে..."));
                g.discoverServices();
            } else if (state==BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread(() -> status.setText("Device disconnected।")); g.close();
            }
        }
        @Override public void onServicesDiscovered(BluetoothGatt g,int s) {
            BluetoothGattService service=g.getService(BATTERY_SERVICE);
            BluetoothGattCharacteristic ch=service==null?null:service.getCharacteristic(BATTERY_LEVEL);
            if (ch==null) {
                runOnUiThread(() -> { battery.setText("Battery level\nNot supported"); status.setText("এই device standard battery service দেয়নি।"); });
            } else g.readCharacteristic(ch);
        }
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic ch,int s) {
            if (BATTERY_LEVEL.equals(ch.getUuid()) && s==BluetoothGatt.GATT_SUCCESS) {
                byte[] value=ch.getValue(); if (value!=null && value.length>0) showBattery(value[0]&255);
            }
        }
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic ch,byte[] value,int s) {
            if (BATTERY_LEVEL.equals(ch.getUuid()) && s==BluetoothGatt.GATT_SUCCESS && value!=null && value.length>0)
                showBattery(value[0]&255);
        }
    };

    private void showBattery(int level) {
        runOnUiThread(() -> { battery.setText("Battery level\n"+level+"%"); status.setText("Battery data পাওয়া গেছে।"); });
    }

    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if (request==REQ) {
            boolean ok=true; for(int r:results) if(r!=PackageManager.PERMISSION_GRANTED) ok=false;
            if(ok) startScan(); else status.setText("Scan করতে Bluetooth permission দিতে হবে।");
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { if(scanner!=null && permitted()) scanner.stopScan(callback); if(activeGatt!=null) activeGatt.close(); }
        catch(Exception ignored) {}
    }
}
