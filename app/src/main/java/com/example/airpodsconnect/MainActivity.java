package com.example.airpodsconnect;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
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
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<String> seen=new HashSet<>();
    private LinearLayout devices;
    private TextView status,battery,deviceTitle;
    private Button scanButton,disconnectButton;
    private int bg=Color.rgb(9,15,31), panel=Color.rgb(20,29,51), muted=Color.rgb(151,165,190), blue=Color.rgb(73,145,255);

    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
    GradientDrawable shape(int color,int radius){
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t;
    }
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    void gap(LinearLayout l,int h){View v=new View(this);l.addView(v,lp(1,h));}
    LinearLayout card(){LinearLayout c=column();c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(shape(panel,22));return c;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(0);
        makeUi();
        BluetoothManager m=(BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        adapter=m==null?null:m.getAdapter();
        status.setText(adapter==null?"Bluetooth পাওয়া যায়নি":adapter.isEnabled()?"Ready to connect":"Turn on your phone Bluetooth");
        if(adapter==null)scanButton.setEnabled(false);
    }

    private void makeUi(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(bg);
        LinearLayout root=column();root.setPadding(dp(20),dp(18),dp(20),dp(24));scroll.addView(root);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=text("◉",27,blue,true);head.addView(logo,lp(42,48));
        LinearLayout names=column();names.addView(text("AirPods Connect",22,Color.WHITE,true));
        names.addView(text("YOUR AUDIO, CONNECTED",10,muted,true));head.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        TextView dots=text("⋮",27,Color.WHITE,false);dots.setGravity(Gravity.CENTER);head.addView(dots,lp(30,45));root.addView(head);
        gap(root,18);

        LinearLayout hero=card();hero.setGravity(Gravity.CENTER);
        TextView art=text("🎧",74,Color.WHITE,false);art.setGravity(Gravity.CENTER);
        hero.addView(art,lp(-1,132));
        hero.addView(text("Your AirPods",21,Color.WHITE,true));
        gap(hero,6);status=text("Not connected",13,Color.rgb(119,207,164),true);
        status.setGravity(Gravity.CENTER);hero.addView(status);
        root.addView(hero,lp(-1,220));gap(root,18);

        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);
        row.addView(text("BATTERY STATUS",13,Color.WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
        row.addView(text("LIVE DEVICE DATA",9,muted,true));root.addView(row);gap(root,10);
        LinearLayout cards=new LinearLayout(this);cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.addView(batteryCard("LEFT","L", "—"),new LinearLayout.LayoutParams(0,dp(112),1));
        View spacer=new View(this);cards.addView(spacer,lp(9,1));
        cards.addView(batteryCard("RIGHT","R","—"),new LinearLayout.LayoutParams(0,dp(112),1));
        View spacer2=new View(this);cards.addView(spacer2,lp(9,1));
        cards.addView(batteryCard("CASE","▣","—"),new LinearLayout.LayoutParams(0,dp(112),1));
        root.addView(cards);gap(root,12);
        LinearLayout single=card();single.setOrientation(LinearLayout.HORIZONTAL);single.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout info=column();info.addView(text("Bluetooth battery reading",13,muted,false));
        battery=text("—",25,Color.WHITE,true);info.addView(battery);
        single.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        single.addView(text("BLE  •  STANDARD",9,blue,true));root.addView(single);gap(root,18);

        scanButton=new Button(this);scanButton.setText("⌕   SCAN DEVICES");scanButton.setAllCaps(false);
        scanButton.setTextColor(Color.WHITE);scanButton.setTextSize(15);scanButton.setTypeface(null,1);
        scanButton.setBackground(shape(blue,16));scanButton.setOnClickListener(v->startScan());
        root.addView(scanButton,lp(-1,54));gap(root,9);
        disconnectButton=new Button(this);disconnectButton.setText("Disconnect");disconnectButton.setAllCaps(false);
        disconnectButton.setTextColor(Color.WHITE);disconnectButton.setBackground(shape(Color.rgb(39,49,72),16));
        disconnectButton.setOnClickListener(v->disconnect());root.addView(disconnectButton,lp(-1,48));gap(root,22);

        root.addView(text("NEARBY DEVICES",13,Color.WHITE,true));gap(root,8);
        devices=column();root.addView(devices);
        TextView note=text("Left / Right / Case-এর আলাদা চার্জ দেখাতে earbuds-কে সেই তথ্য Bluetooth-এ পাঠাতে হবে। অনেক মডেল শুধু একটি combined battery level দেয়।",11,muted,false);
        note.setPadding(dp(2),dp(14),dp(2),0);root.addView(note);
        setContentView(scroll);
    }

    private LinearLayout batteryCard(String label,String icon,String value){
        LinearLayout c=card();c.setGravity(Gravity.CENTER);
        c.addView(text(icon,22,blue,true));gap(c,5);c.addView(text(value,20,Color.WHITE,true));
        c.addView(text(label,10,muted,true));return c;
    }

    private boolean permitted(){
        if(Build.VERSION.SDK_INT>=31)return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==0&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==0;
        return Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==0;
    }
    private void askPermission(){
        if(Build.VERSION.SDK_INT>=31)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},REQ);
        else if(Build.VERSION.SDK_INT>=23)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ);
    }
    private void startScan(){
        if(adapter==null)return;
        if(!adapter.isEnabled()){status.setText("Turn on Bluetooth first");return;}
        if(!permitted()){askPermission();return;}
        try{
            scanner=adapter.getBluetoothLeScanner();if(scanner==null){status.setText("BLE scanner unavailable");return;}
            seen.clear();devices.removeAllViews();battery.setText("—");status.setText("Scanning nearby devices…");scanButton.setEnabled(false);
            scanner.startScan(callback);
            handler.postDelayed(()->{try{if(scanner!=null)scanner.stopScan(callback);}catch(Exception ignored){}
                scanButton.setEnabled(true);if(seen.isEmpty())status.setText("No BLE devices found. Try again.");else status.setText("Choose your earbuds below");},10000);
        }catch(Exception e){status.setText("Could not start scan");scanButton.setEnabled(true);}
    }
    private final ScanCallback callback=new ScanCallback(){
        @Override public void onScanResult(int type,ScanResult r){BluetoothDevice d=r.getDevice();if(d!=null)runOnUiThread(()->addDevice(d,r.getRssi()));}
        @Override public void onScanFailed(int code){runOnUiThread(()->{status.setText("Scan failed");scanButton.setEnabled(true);});}
    };
    private void addDevice(BluetoothDevice d,int rssi){
        if(!permitted())return;
        try{
            String address=d.getAddress();if(!seen.add(address))return;
            String name=d.getName();if(name==null||name.trim().isEmpty())name="Unknown device";
            Button item=new Button(this);item.setAllCaps(false);item.setTextColor(Color.WHITE);item.setGravity(Gravity.CENTER_VERTICAL);
            item.setText("◉   "+name+"     "+rssi+" dBm");item.setBackground(shape(panel,14));
            LinearLayout.LayoutParams p=lp(-1,58);p.bottomMargin=dp(7);devices.addView(item,p);
            String n=name;item.setOnClickListener(v->connect(d,n));
        }catch(SecurityException ignored){}
    }
    private void connect(BluetoothDevice d,String name){
        if(!permitted()){askPermission();return;}
        try{if(activeGatt!=null){activeGatt.close();activeGatt=null;}
            deviceTitle=null;status.setText("Connecting to "+name+"…");activeGatt=d.connectGatt(this,false,gattCallback);
        }catch(Exception e){status.setText("Connection failed");}
    }
    private final BluetoothGattCallback gattCallback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int s,int state){
            if(state==BluetoothProfile.STATE_CONNECTED){runOnUiThread(()->status.setText("●  Connected"));g.discoverServices();}
            else if(state==BluetoothProfile.STATE_DISCONNECTED){runOnUiThread(()->status.setText("Disconnected"));g.close();if(activeGatt==g)activeGatt=null;}
        }
        @Override public void onServicesDiscovered(BluetoothGatt g,int s){
            BluetoothGattService service=g.getService(BS);BluetoothGattCharacteristic ch=service==null?null:service.getCharacteristic(BL);
            if(ch==null)runOnUiThread(()->{battery.setText("Not supported");status.setText("Connected • battery data unavailable");});
            else g.readCharacteristic(ch);
        }
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic ch,int s){
            if(BL.equals(ch.getUuid())&&s==BluetoothGatt.GATT_SUCCESS){byte[] v=ch.getValue();if(v!=null&&v.length>0)showBattery(v[0]&255);}
        }
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic ch,byte[] v,int s){
            if(BL.equals(ch.getUuid())&&s==BluetoothGatt.GATT_SUCCESS&&v!=null&&v.length>0)showBattery(v[0]&255);
        }
    };
    private void showBattery(int value){runOnUiThread(()->{battery.setText(value+"%");status.setText("●  Connected • battery received");});}
    private void disconnect(){
        try{if(activeGatt!=null){activeGatt.disconnect();activeGatt.close();activeGatt=null;}status.setText("Disconnected");battery.setText("—");}
        catch(Exception e){status.setText("Disconnect requested");}
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);if(r==REQ){boolean ok=true;for(int x:g)if(x!=PackageManager.PERMISSION_GRANTED)ok=false;if(ok)startScan();else status.setText("Bluetooth permission required");}
    }
    @Override protected void onDestroy(){super.onDestroy();try{if(scanner!=null&&permitted())scanner.stopScan(callback);if(activeGatt!=null)activeGatt.close();}catch(Exception ignored){}}
}
