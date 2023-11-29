package com.example.ble_keyboard;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.bluetooth.BluetoothDevice;
import android.location.Location;
import android.view.WindowManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;
//=======================================================================================================================
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.fragment.NavHostFragment;

import android.view.View;
import android.view.Menu;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.firestore.DocumentSnapshot;
import com.clj.fastble.utils.HexUtil;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import java.text.SimpleDateFormat;
import java.util.Collection;

public class MainActivity extends AppCompatActivity {

    SupportMapFragment supportMapFragment;
    FusedLocationProviderClient fusedLocationProviderClient;
    private FirebaseFirestore firestore;
    private ImageView batteryImage;
    private TextView batteryText;
    private TextView rangeText;
    private String CHANNEL_ID = "group_5";
    final private int REQUEST_CODE_PERMISSION_LOCATION = 0;
    private AlertDialog.Builder dialogBuilder;
    private BleDeviceAdapter bleDeviceAdapter;
    private BleDevice activeBleDevice;
    private String temp;
    private String humid;
    private String solarText;
    private String rainText;
    private String batteryNum;

    //Notification when the Bluetooth is out of range==============================================================================
    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                updateBatteryLevel(0);
                rangeText.setText("Wella is out of range, you may have forgotten to bring it");
                sendNotification();
                Toast.makeText(MainActivity.this, "Wella is out of range, you may have forgotten to bring it", Toast.LENGTH_SHORT).show();
            }
        }
    };
    //=============================================================================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setSupportActionBar(toolbar);

        // Wella is out of range
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetoothStateReceiver, filter);

        // Battery Level
        batteryImage = findViewById(R.id.battery_image);
        batteryText = findViewById(R.id.BatteryNum);
        updateBatteryLevel(0);

        //out of range
        rangeText = findViewById(R.id.textView2);
        createNotificationChannel();

        //firestore set up=======================================================================================================
        firestore = FirebaseFirestore.getInstance();

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        supportMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.google_map);
        fusedLocationProviderClient = (FusedLocationProviderClient) LocationServices.getFusedLocationProviderClient(this);
        Dexter.withContext(getApplicationContext()).withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {

                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {

                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {
                        permissionToken.cancelPermissionRequest();
                    }
                }).check();
        //=======================================================================================================================

        bleDeviceAdapter = new BleDeviceAdapter(MainActivity.this, android.R.layout.select_dialog_singlechoice);

        //build dialog for scanning BLE devices
        dialogBuilder = new AlertDialog.Builder(MainActivity.this);
        dialogBuilder.setIcon(R.drawable.ic_launcher_foreground);
        dialogBuilder.setTitle("Select a BLE device");

        dialogBuilder.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        dialogBuilder.setAdapter(bleDeviceAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                BleDevice bleDevice = bleDeviceAdapter.getItem(which);
                AlertDialog.Builder builderInner = new AlertDialog.Builder(MainActivity.this);
                builderInner.setMessage(bleDevice.getName() + ", " + bleDevice.getMac());
                builderInner.setTitle("Your selected BLE device is");
                builderInner.setPositiveButton("Connect and Subscribe", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        connect(bleDevice);
                    }
                });
                builderInner.show();
            }
        });

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startScan();
            }
        });

        checkPermissions();

        BleManager.getInstance().init(getApplication());
        BleManager.getInstance()
                .enableLog(true)
                .setReConnectCount(1, 5000)
                .setConnectOverTime(20000)
                .setOperateTimeout(5000);
    }

    // update battery level==========================================================================
    private void updateBatteryLevel(float batteryLevel) {

        if (batteryLevel > 100) {
            batteryText.setText("100%");
        } else {
            batteryText.setText(batteryLevel + "%");
        }

        if (batteryLevel > 80) {
            batteryImage.setImageResource(R.drawable.battery_full);
        } else if (batteryLevel <= 80 & batteryLevel > 60) {
            batteryImage.setImageResource(R.drawable.battery_80);
        } else if (batteryLevel <= 60 & batteryLevel > 40) {
            batteryImage.setImageResource(R.drawable.battery_60);
        } else if (batteryLevel <= 40 & batteryLevel > 20) {
            batteryImage.setImageResource(R.drawable.battery_40);
        } else if (batteryLevel <= 20 & batteryLevel > 5) {
            batteryImage.setImageResource(R.drawable.battery_20);
        } else if (batteryLevel <= 5 & batteryLevel > 0) {
            batteryImage.setImageResource(R.drawable.battery_empty);
        } else if (batteryLevel == 0) {
            batteryText.setText("is not connected...");
            batteryImage.setImageResource(R.drawable.battery_empty);
        }
    }
    // ==============================================================================================
    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BleManager.getInstance().disconnectAllDevice();
        BleManager.getInstance().destroy();
        unregisterReceiver(bluetoothStateReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    //BLE
    private void startScan() {
        BleManager.getInstance().scan(new BleScanCallback() {
            @Override
            public void onScanStarted(boolean success) {
                bleDeviceAdapter.clear();
                bleDeviceAdapter.notifyDataSetChanged();
                dialogBuilder.show();
            }

            @Override
            public void onLeScan(BleDevice bleDevice) {
                super.onLeScan(bleDevice);
            }

            @Override
            public void onScanning(BleDevice bleDevice) {
                bleDeviceAdapter.add(bleDevice);
                bleDeviceAdapter.notifyDataSetChanged();
            }

            @Override
            public void onScanFinished(List<BleDevice> scanResultList) {

            }
        });
    }

    private void connect(final BleDevice bleDevice) {
        BleManager.getInstance().connect(bleDevice, new BleGattCallback() {
            @Override
            public void onStartConnect() {
            }

            @Override
            public void onConnectFail(BleDevice bleDevice, BleException exception) {
                Toast.makeText(MainActivity.this, "Failed to connect.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                activeBleDevice = bleDevice;

                Toast.makeText(MainActivity.this, "Connected.", Toast.LENGTH_LONG).show();

                NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                MainFragment fragment = (MainFragment) navHostFragment.getChildFragmentManager().getFragments().get(0);
                fragment.updateDeviceTextView(activeBleDevice.getName() + ", " + activeBleDevice.getMac());

                BluetoothGattCharacteristic notifyCharacteristic = null;

                for (BluetoothGattService bgs : gatt.getServices()) {
                    for (BluetoothGattCharacteristic bgc : bgs.getCharacteristics()) {
                        int property = bgc.getProperties();
                        if ((property & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            notifyCharacteristic = bgc;
                            break;
                        }
                    }
                }

                BleManager.getInstance().notify(
                        bleDevice,
                        notifyCharacteristic.getService().getUuid().toString(),
                        notifyCharacteristic.getUuid().toString(),
                        new BleNotifyCallback() {
                            private boolean isDataCollectionEnable = true;
                            private Timer dataCollectionTimer;

                            @Override
                            public void onCharacteristicChanged(byte[] data) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (isDataCollectionEnable) {
                                            String check_data = new String(data);

                                            if (check_data.substring(0, 4).equals("000 ")) {
                                                batteryNum = check_data.substring(4, 9);
                                                updateBatteryLevel(Float.parseFloat(batteryNum));
                                            }

                                            if (check_data.substring(0, 4).equals("999 ")) {
                                                temp = check_data.substring(4, 9);
                                                TextView textView1 = (TextView) findViewById(R.id.TempNum);
                                                textView1.setText(temp);

                                                humid = check_data.substring(10, 15);
                                                TextView textView2 = (TextView) findViewById(R.id.HumidNum);
                                                textView2.setText(humid);
                                            }

                                            if (check_data.substring(0, 4).equals("111 ")) {
                                                String solar = check_data.substring(4, 5);
                                                TextView textView3 = (TextView) findViewById(R.id.SolarNum);
                                                if (solar.equals("1")) {
                                                    solarText = "Dark";
                                                } else if (solar.equals("2")) {
                                                    solarText = "Dim";
                                                } else if (solar.equals("3")) {
                                                    solarText = "Nomal";
                                                } else if (solar.equals("4")) {
                                                    solarText = "Bright";
                                                } else if (solar.equals("5")) {
                                                    solarText = "Very Bright";
                                                }
                                                textView3.setText(solarText);

                                                String rain = check_data.substring(6, 7);
                                                TextView textView4 = (TextView) findViewById(R.id.RainNum);
                                                if (rain.equals("1")) {
                                                    rainText = "Yes, bring your Wella";
                                                } else if (rain.equals("2")) {
                                                    rainText = "No, it is not raining";
                                                }
                                                textView4.setText(rainText);

                                                // Sending and Fetching data to Firebase
                                                getCurrentLocation();
                                                //======================================

                                                isDataCollectionEnable = false;
                                                scheduleDataCollectionTimer();
                                            }
                                        }
                                    }
                                });
                            }

                            private void scheduleDataCollectionTimer() {
                                if (dataCollectionTimer != null) {
                                    dataCollectionTimer.cancel();
                                }
                                dataCollectionTimer = new Timer();
                                dataCollectionTimer.schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        isDataCollectionEnable = true;
                                    }
                                }, 1 * 60 * 1000); // 60 sec (1 min)
                            }

                            @Override
                            public void onNotifySuccess() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, "notify success", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            @Override
                            public void onNotifyFailure(final BleException exception) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, "notify failed", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                        });

            }

            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice bleDevice, BluetoothGatt gatt, int status) {
                if (!isActiveDisConnected) {
                    // The disconnection is not actively triggered by the app, could be out of range
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateBatteryLevel(0);
                            rangeText.setText("Wella is out of range, you may have forgotten to bring it");
                            sendNotification();
                            Toast.makeText(MainActivity.this, "Wella is out of range, you may have forgotten to bring it", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    //Permission
    private void checkPermissions() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, getString(R.string.please_open_blue), Toast.LENGTH_LONG).show();
            return;
        }

        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};
        List<String> permissionDeniedList = new ArrayList<>();
        for (String permission : permissions) {
            int permissionCheck = ContextCompat.checkSelfPermission(this, permission);
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                onPermissionGranted(permission);
            } else {
                permissionDeniedList.add(permission);
            }
        }
        if (!permissionDeniedList.isEmpty()) {
            String[] deniedPermissions = permissionDeniedList.toArray(new String[permissionDeniedList.size()]);
            ActivityCompat.requestPermissions(this, deniedPermissions, REQUEST_CODE_PERMISSION_LOCATION);
        }
    }

    @Override
    public final void onRequestPermissionsResult(int requestCode,
                                                 @NonNull String[] permissions,
                                                 @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE_PERMISSION_LOCATION:
                if (grantResults.length > 0) {
                    for (int i = 0; i < grantResults.length; i++) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            onPermissionGranted(permissions[i]);
                        }
                    }
                }
                break;
        }
    }

    private void onPermissionGranted(String permission) {
        switch (permission) {
            case Manifest.permission.ACCESS_FINE_LOCATION:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !checkGPSIsOpen()) {
                    Toast.makeText(getApplicationContext(), "Permissions are granted", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Permissions are granted", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    private boolean checkGPSIsOpen() {
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null)
            return false;
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
    }

    //=======================================================================================================================
    public void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Task<Location> task = fusedLocationProviderClient.getLastLocation();
        task.addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                supportMapFragment.getMapAsync(new OnMapReadyCallback() {
                    @Override
                    public void onMapReady(@NonNull GoogleMap googleMap) {
                        if (location != null) {
                            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

                            // Sending the current location coordinate with Timestamp to Firebase=======================================
                            Map<String, Object> data = new HashMap<>();
                            data.put("coordinate", latLng);
                            data.put("timestamp", FieldValue.serverTimestamp());
                            data.put("temperature", temp);
                            data.put("humidity", humid);
                            data.put("solar", solarText);
                            data.put("rain", rainText);

                            firestore.collection("test").add(data).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                                @Override
                                public void onSuccess(DocumentReference documentReference) {
                                    Toast.makeText(getApplicationContext(), "Success uploading on firebase", Toast.LENGTH_LONG).show();
                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Toast.makeText(getApplicationContext(), "Fail uploading on firebase", Toast.LENGTH_LONG).show();
                                }
                            });
                            MarkerOptions markerOptions = new MarkerOptions().position(latLng).title("Current Location!");
                            googleMap.addMarker(markerOptions);
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
                        } else {
                            Toast.makeText(MainActivity.this, "Please on your Location App Permissions", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });

        // Fetching some coordinate from Firebase=====================================================
        task.addOnSuccessListener(new OnSuccessListener<Location>() {
            public void onSuccess(Location location) {
                supportMapFragment.getMapAsync(new OnMapReadyCallback() {
                    public void onMapReady(@NonNull GoogleMap googleMap) {
                        Calendar calendar = Calendar.getInstance();
                        calendar.add(Calendar.MINUTE, -10);
                        Date limitTime = calendar.getTime();
                        CollectionReference collectionReference = firestore.collection("test");
                        collectionReference.whereGreaterThan("timestamp", limitTime).orderBy("timestamp", Query.Direction.DESCENDING)
                                .get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                    @Override
                                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                                        int i = 1;
                                        for (QueryDocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                                            Map<String, Object> data = documentSnapshot.getData();
                                            Map<String, Object> coordinate = (Map<String, Object>) data.get("coordinate");
                                            LatLng latLng1 = new LatLng((Double) coordinate.get("latitude"), (Double) coordinate.get("longitude"));

                                            MarkerOptions markerOptions = new MarkerOptions().position(latLng1).title(i + " location from firestore");
                                            Marker marker = googleMap.addMarker(markerOptions);
                                            marker.setTag(data);
                                            i++;
                                        }
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Toast.makeText(getApplicationContext(), "Fail to fetch from firestore", Toast.LENGTH_LONG).show();
                                    }
                                });
                        googleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                            @Override
                            public boolean onMarkerClick(@NonNull Marker marker) {
                                Map<String, Object> data = (Map<String, Object>) marker.getTag();
                                if (data != null) {
                                    if (data.get("temperature") != null) {
                                        TextView textView1 = (TextView) findViewById(R.id.TempNum);
                                        textView1.setText(data.get("temperature").toString());
                                    }
                                    if (data.get("humidity") != null) {
                                        TextView textView2 = (TextView) findViewById(R.id.HumidNum);
                                        textView2.setText(data.get("humidity").toString());
                                    }
                                    if (data.get("solar") != null) {
                                        TextView textView3 = (TextView) findViewById(R.id.SolarNum);
                                        textView3.setText(data.get("solar").toString());
                                    }
                                    if (data.get("rain") != null) {
                                        TextView textView4 = (TextView) findViewById(R.id.RainNum);
                                        textView4.setText(data.get("rain").toString());
                                    }
                                }
                                return false;
                            }
                        });
                    }
                });
            }
        });
        // ===========================================================================================
    }

    // Locked screen Notification=====================================================================
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    private void sendNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Wella is disconnected")
                .setContentText("You may have forgotten your Wella!!!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        notificationManager.notify(1, builder.build());
    }
    // ===============================================================================================
}