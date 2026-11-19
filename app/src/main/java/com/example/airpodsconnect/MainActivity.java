package com.example.airpodsconnect;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int REQ=41;
    private static final UUID BS=UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID BL=UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt activeGatt;
    private BluetoothA2dp a2dp;
    private BluetoothHeadset headset;
    private boolean receiverRegistered=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<String> seen=new HashSet<>();
    private LinearLayout devices,paired;
    private TextView status,battery,connectionHint,leftValue,rightValue,caseValue;
    private Button scanButton;
    private int bg=Color.rgb(9,15,31), panel=Color.rgb(20,29,51), muted=Color.rgb(151,165,190), blue=Color.rgb(73,145,255);
    private final BroadcastReceiver btReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            String action=i.getAction();
            if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                BluetoothDevice d=i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                updateConnection(d,true);
            }else if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
                BluetoothDevice d=i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                updateConnection(d,false);
            }else if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
                int s=i.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR);
                if(s==BluetoothAdapter.STATE_ON){status.setText("Bluetooth ready");refreshPaired();}
                else if(s==BluetoothAdapter.STATE_OFF){status.setText("Bluetooth is off");battery.setText("—");}
            }
        }
    };
    private final BluetoothProfile.ServiceListener profileListener=new BluetoothProfile.ServiceListener(){
        @Override public void onServiceConnected(int profile,BluetoothProfile proxy){
            if(profile==BluetoothProfile.A2DP)a2dp=(BluetoothA2dp)proxy;
            if(profile==BluetoothProfile.HEADSET)headset=(BluetoothHeadset)proxy;
            refreshPaired();
        }
        @Override public void onServiceDisconnected(int profile){
            if(profile==BluetoothProfile.A2DP)a2dp=null;
            if(profile==BluetoothProfile.HEADSET)headset=null;
        }
    };

    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView text(String v,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(v);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    void gap(LinearLayout l,int h){View v=new View(this);l.addView(v,lp(1,h));}
    LinearLayout card(){LinearLayout c=column();c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(shape(panel,22));return c;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        makeUi();
        BluetoothManager m=(BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        adapter=m==null?null:m.getAdapter();
        if(adapter==null){status.setText("Bluetooth পাওয়া যায়নি");scanButton.setEnabled(false);return;}
        status.setText(adapter.isEnabled()?"Bluetooth ready":"Turn on Bluetooth");
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED){
            adapter.getProfileProxy(this,profileListener,BluetoothProfile.A2DP);
            adapter.getProfileProxy(this,profileListener,BluetoothProfile.HEADSET);
        }
        refreshPaired();
    }

    private void makeUi(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(bg);
        LinearLayout root=column();root.setPadding(dp(20),dp(18),dp(20),dp(26));scroll.addView(root);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("◉",28,blue,true),lp(42,48));
        LinearLayout names=column();names.addView(text("AirPods Connect",22,Color.WHITE,true));names.addView(text("SMART BLUETOOTH COMPANION",10,muted,true));
        head.addView(names,new LinearLayout.LayoutParams(0,-2,1));root.addView(head);gap(root,18);
        LinearLayout hero=card();hero.setGravity(Gravity.CENTER);
        TextView art=text("🎧",76,Color.WHITE,false);art.setGravity(Gravity.CENTER);hero.addView(art,lp(-1,124));
        hero.addView(text("AirPods",22,Color.WHITE,true));gap(hero,7);
        status=text("Checking Bluetooth…",13,Color.rgb(119,207,164),true);hero.addView(status);
        connectionHint=text("Pair your earbuds to get started",11,muted,false);gap(hero,5);hero.addView(connectionHint);
        root.addView(hero,lp(-1,205));gap(root,18);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text("BATTERY STATUS",13,Color.WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
        row.addView(text("LIVE • WHEN SUPPORTED",9,muted,true));root.addView(row);gap(root,10);
        LinearLayout cards=new LinearLayout(this);cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.addView(batteryCard("LEFT","L",0),new LinearLayout.LayoutParams(0,dp(105),1));
        View s1=new View(this);cards.addView(s1,lp(8,1));
        cards.addView(batteryCard("RIGHT","R",1),new LinearLayout.LayoutParams(0,dp(105),1));
        View s2=new View(this);cards.addView(s2,lp(8,1));
        cards.addView(batteryCard("CASE","▣",2),new LinearLayout.LayoutParams(0,dp(105),1));
        root.addView(cards);gap(root,10);
        LinearLayout single=card();single.setOrientation(LinearLayout.HORIZONTAL);single.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout info=column();info.addView(text("Standard Bluetooth battery",12,muted,false));
        battery=text("Not received",20,Color.WHITE,true);info.addView(battery);
        single.addView(info,new LinearLayout.LayoutParams(0,-2,1));single.addView(text("BLE  •  0x180F",9,blue,true));root.addView(single);gap(root,18);

        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);
        scanButton=new Button(this);scanButton.setText("Scan nearby");scanButton.setAllCaps(false);scanButton.setTextColor(Color.WHITE);scanButton.setBackground(shape(blue,15));scanButton.setOnClickListener(v->startScan());
        buttons.addView(scanButton,new LinearLayout.LayoutParams(0,dp(52),1));
        Button settings=new Button(this);settings.setText("Bluetooth settings");settings.setAllCaps(false);settings.setTextColor(Color.WHITE);settings.setBackground(shape(Color.rgb(39,49,72),15));
        settings.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        LinearLayout.LayoutParams sp=lp(0,52);sp.width=0;sp.weight=1;sp.leftMargin=dp(8);buttons.addView(settings,sp);root.addView(buttons);gap(root,20);
        root.addView(text("PAIRED DEVICES",13,Color.WHITE,true));gap(root,8);paired=column();root.addView(paired);gap(root,16);
        root.addView(text("NEARBY BLE DEVICES",13,Color.WHITE,true));gap(root,8);devices=column();root.addView(devices);
        TextView note=text("নোট: আলাদা Left / Right / Case চার্জ এবং কেসের charging status এই earbuds-এর Bluetooth protocol সমর্থন না করলে Android-এ পাওয়া যাবে না। অ্যাপ কোনো ভুয়া শতাংশ দেখাবে না। অটো কানেকশন Android-এর Bluetooth system নিয়ন্ত্রণ করে।",11,muted,false);
        note.setPadding(dp(2),dp(14),dp(2),0);root.addView(note);
        setContentView(scroll);
    }
    private LinearLayout batteryCard(String label,String icon,int which){
        LinearLayout c=card();c.setGravity(Gravity.CENTER);c.addView(text(icon,22,blue,true));gap(c,5);
        TextView value=text("—",19,Color.WHITE,true);
        if(which==0)leftValue=value;else if(which==1)rightValue=value;else caseValue=value;
        c.addView(value);c.addView(text(label,10,muted,true));return c;
    }
    private boolean permitted(){
        if(Build.VERSION.SDK_INT>=31)return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==0&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==0;
        return Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==0;
    }
    private void askPermission(){
        if(Build.VERSION.SDK_INT>=31)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},REQ);
        else if(Build.VERSION.SDK_INT>=23)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ);
    }
    private void refreshPaired(){
        if(adapter==null||paired==null)return;
        if(!permitted()){paired.removeAllViews();paired.addView(text("Bluetooth permission needed to list paired devices.",12,muted,false));return;}
        paired.removeAllViews();
        try{
            Set<BluetoothDevice> bonded=adapter.getBondedDevices();
            boolean found=false;
            for(BluetoothDevice d:bonded){
                String name=d.getName();if(name==null)name="Bluetooth device";
                if(!name.toLowerCase(Locale.ROOT).contains("airpod")&&!name.equalsIgnoreCase("my headphone 2"))continue;
                found=true;boolean connected=isConnected(d);
                LinearLayout item=card();item.setOrientation(LinearLayout.HORIZONTAL);item.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout info=column();info.addView(text("🎧  "+name,15,Color.WHITE,true));
                info.addView(text(connected?"Connected":"Paired • not connected",11,connected?Color.rgb(119,207,164):muted,false));
                item.addView(info,new LinearLayout.LayoutParams(0,-2,1));
                Button open=new Button(this);open.setText("Manage");open.setAllCaps(false);open.setTextColor(Color.WHITE);open.setBackground(shape(blue,12));
                open.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
                item.addView(open,lp(90,42));LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=dp(8);paired.addView(item,p);
                if(connected){status.setText("●  "+name+" connected");connectionHint.setText("Classic Bluetooth connection detected");}
            }
            if(!found)paired.addView(text("No paired AirPods found. Pair it in Bluetooth settings first.",12,muted,false));
        }catch(SecurityException e){paired.addView(text("Bluetooth permission required.",12,muted,false));}
    }
    private boolean isConnected(BluetoothDevice d){
        try{
            if(a2dp!=null&&a2dp.getConnectedDevices().contains(d))return true;
            if(headset!=null&&headset.getConnectedDevices().contains(d))return true;
        }catch(SecurityException ignored){}
        return false;
    }
    private void updateConnection(BluetoothDevice d,boolean connected){
        if(d==null)return;
        try{
            String n=d.getName();if(n==null)n="";
            if(!n.toLowerCase(Locale.ROOT).contains("airpod"))return;
            status.setText(connected?"●  "+n+" connected":"AirPods disconnected");
            connectionHint.setText(connected?"Classic Bluetooth connection detected":"Open Bluetooth settings to reconnect");
            refreshPaired();
        }catch(SecurityException ignored){}
    }
    @Override protected void onResume(){
        super.onResume();
        IntentFilter f=new IntentFilter();f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        try{if(Build.VERSION.SDK_INT>=33)registerReceiver(btReceiver,f,Context.RECEIVER_EXPORTED);else registerReceiver(btReceiver,f);receiverRegistered=true;}catch(Exception ignored){}
        if(adapter!=null)refreshPaired();
    }
    @Override protected void onPause(){super.onPause();if(receiverRegistered){try{unregisterReceiver(btReceiver);}catch(Exception ignored){}receiverRegistered=false;}}
    private void startScan(){
        if(adapter==null)return;
        if(!adapter.isEnabled()){status.setText("Turn on phone Bluetooth first");startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));return;}
        if(!permitted()){askPermission();return;}
        try{
            scanner=adapter.getBluetoothLeScanner();if(scanner==null){status.setText("BLE scanner unavailable");return;}
            seen.clear();devices.removeAllViews();battery.setText("Not received");status.setText("Scanning BLE devices…");scanButton.setEnabled(false);
            scanner.startScan(callback);
            handler.postDelayed(()->{try{if(scanner!=null)scanner.stopScan(callback);}catch(Exception ignored){}
                scanButton.setEnabled(true);if(seen.isEmpty())devices.addView(text("No BLE advertisements found. Your AirPods may use Classic Bluetooth only.",12,muted,false));
                status.setText("Scan finished");},10000);
        }catch(Exception e){status.setText("Could not start BLE scan");scanButton.setEnabled(true);}
    }
    private final ScanCallback callback=new ScanCallback(){
        @Override public void onScanResult(int type,ScanResult r){BluetoothDevice d=r.getDevice();if(d!=null)runOnUiThread(()->addDevice(d,r.getRssi()));}
        @Override public void onScanFailed(int code){runOnUiThread(()->{status.setText("Scan failed ("+code+")");scanButton.setEnabled(true);});}
    };
    private void addDevice(BluetoothDevice d,int rssi){
        if(!permitted())return;
        try{
            String address=d.getAddress();if(!seen.add(address))return;
            String name=d.getName();if(name==null||name.trim().isEmpty())name="Unknown BLE device";final String deviceName=name;
            LinearLayout item=card();item.setOrientation(LinearLayout.HORIZONTAL);item.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout info=column();info.addView(text(name,13,Color.WHITE,true));info.addView(text(rssi+" dBm",10,muted,false));
            item.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            Button read=new Button(this);read.setText("Read battery");read.setAllCaps(false);read.setTextColor(Color.WHITE);read.setBackground(shape(blue,12));read.setOnClickListener(v->connectGatt(d,deviceName));
            item.addView(read,lp(112,42));LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=dp(7);devices.addView(item,p);
        }catch(SecurityException ignored){}
    }
    private void connectGatt(BluetoothDevice d,String name){
        if(!permitted()){askPermission();return;}
        try{if(activeGatt!=null){activeGatt.close();activeGatt=null;}status.setText("Reading BLE battery…");activeGatt=d.connectGatt(this,false,gattCallback);}
        catch(Exception e){status.setText("BLE connection failed");}
    }
    private final BluetoothGattCallback gattCallback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int s,int state){
            if(state==BluetoothProfile.STATE_CONNECTED)g.discoverServices();
            else if(state==BluetoothProfile.STATE_DISCONNECTED){g.close();if(activeGatt==g)activeGatt=null;}
        }
        @Override public void onServicesDiscovered(BluetoothGatt g,int s){
            BluetoothGattService service=g.getService(BS);BluetoothGattCharacteristic ch=service==null?null:service.getCharacteristic(BL);
            if(ch==null)runOnUiThread(()->{battery.setText("Not supported");status.setText("No standard BLE battery service");});
            else g.readCharacteristic(ch);
        }
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic ch,int s){
            if(BL.equals(ch.getUuid())&&s==BluetoothGatt.GATT_SUCCESS){byte[] v=ch.getValue();if(v!=null&&v.length>0)showBattery(v[0]&255);}
        }
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic ch,byte[] v,int s){
            if(BL.equals(ch.getUuid())&&s==BluetoothGatt.GATT_SUCCESS&&v!=null&&v.length>0)showBattery(v[0]&255);
        }
    };
    private void showBattery(int value){
        runOnUiThread(()->{
            battery.setText(value+"%");
            status.setText("BLE battery received");
            connectionHint.setText("One battery value received from device");
        });
    }
    private void showConnectionPopup(String name){
        if(isFinishing()||isDestroyed())return;
        new android.app.AlertDialog.Builder(this)
            .setTitle("AirPods connected")
            .setMessage(name+"\\n\\nBluetooth audio connection detected. Battery details will appear when the earbuds provide them.")
            .setPositiveButton("OK",(d,w)->d.dismiss()).show();
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);if(r==REQ){boolean ok=true;for(int x:g)if(x!=PackageManager.PERMISSION_GRANTED)ok=false;if(ok){if(adapter!=null){adapter.getProfileProxy(this,profileListener,BluetoothProfile.A2DP);adapter.getProfileProxy(this,profileListener,BluetoothProfile.HEADSET);}refreshPaired();startScan();}else status.setText("Bluetooth permission required");}
    }
    @Override protected void onDestroy(){
        super.onDestroy();
        try{if(scanner!=null&&permitted())scanner.stopScan(callback);if(activeGatt!=null)activeGatt.close();if(adapter!=null){if(a2dp!=null)adapter.closeProfileProxy(BluetoothProfile.A2DP,a2dp);if(headset!=null)adapter.closeProfileProxy(BluetoothProfile.HEADSET,headset);}}catch(Exception ignored){}
    }
}