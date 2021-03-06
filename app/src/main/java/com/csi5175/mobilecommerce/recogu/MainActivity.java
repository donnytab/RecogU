package com.csi5175.mobilecommerce.recogu;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.location.Location;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, OnMapReadyCallback {

    public GoogleApiClient mApiClient;
    private FusedLocationProviderClient mFusedLocationClient;
    private SupportMapFragment mapFragment;
    private LocationCallback mLocationCallback;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest locationSettingsRequest;
    ArrayList<Location> locationList;

    private String mainName = MainActivity.class.getSimpleName();
    private TextView txtActivity, txtConfidence;
    private ImageView imgActivity;
    private static final String DATABASE_NAME = "RecogDB";
    private static final String TABLE_NAME = "time";
    private static final String ID = "_id";
    private static final String TYPE = "type";
    private static final String TIMESTAMP = "timestamp";
    private SQLiteDatabase sqlDB;

    @SuppressLint("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize FusedLocationClient
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationList = new ArrayList<Location>();

        // Initialize fragment for Google Maps
        mapFragment = (SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.map);
        callMapAsync();

        txtActivity = findViewById(R.id.txt_activity);
        txtConfidence = findViewById(R.id.txt_confidence);
        imgActivity = (ImageView) findViewById(R.id.img_activity);

        // Show greeting
        displayGreeting();

        // Create database
        sqlDB = openOrCreateDatabase(DATABASE_NAME, SQLiteDatabase.CREATE_IF_NECESSARY, null);

        // Create table for all detected activities
        try {
            String createTableQuery = "CREATE TABLE " + TABLE_NAME + "(" + ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + TYPE + " TEXT NOT NULL, " + TIMESTAMP + " TEXT NOT NULL)";
            sqlDB.execSQL(createTableQuery);
        } catch (Exception e) {
            e.printStackTrace();
        }

        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(ActivityRecognition.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mApiClient.connect();

        // Check location permission & async map
        startLocationUpdates();

        // Create BroadcastReceiver for ActivityRecognizedService
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String activityName = intent.getStringExtra(ActivityRecognizedService.ACTIVITY_RECOGNITION_TYPE_NAME);
                String confidence = intent.getStringExtra(ActivityRecognizedService.ACTIVITY_RECOGNITION_TYPE_CONFIDENCE);
                int icon = intent.getIntExtra(ActivityRecognizedService.ACTIVITY_RECOGNITION_TYPE_ICON, 0);
                String timestamp = intent.getStringExtra(ActivityRecognizedService.ACTIVITY_RECOGNITION_TYPE_TIMESTAMP);
                int mapStatus = intent.getIntExtra(ActivityRecognizedService.ACTIVITY_RECOGNITION_MAP_STATUS, 0);

                // Display result
                txtActivity.setText(activityName);
                txtConfidence.setText("Confidence: " + confidence);
                imgActivity.setImageResource(icon);
                mapFragment.getView().setVisibility(mapStatus);

                // Query for last activity
                String selectQuery = "SELECT * FROM " + TABLE_NAME + " ORDER BY " + TIMESTAMP + " DESC LIMIT 1";
                Cursor cursor = sqlDB.rawQuery(selectQuery, null);
                if (cursor.moveToFirst()) {
                    String lastTimestamp = cursor.getString(cursor.getColumnIndex(TIMESTAMP));
                    String lastActivity = cursor.getString(cursor.getColumnIndex(TYPE));
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                    long duration = 0;
                    try {
                        // Calculate duration time between last activity and current activity
                        duration = (simpleDateFormat.parse(timestamp).getTime() - simpleDateFormat.parse(lastTimestamp).getTime()) / 1000;
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }

                    // Display duration time with toast text
                    if (duration != 0 && !lastActivity.equals("")) {
                        String toastText = "You have been " + lastActivity + " for " + Long.toString(duration) + " seconds";
                        Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_LONG).show();
                    }
                }
                cursor.close();

                // Insert start time for each new activity
                ContentValues values = new ContentValues();
                values.put(TYPE, activityName);
                values.put(TIMESTAMP, timestamp);
                sqlDB.insert(TABLE_NAME, null, values);
            }
        };


        // Setup LocalBroadcastManager
        LocalBroadcastManager.getInstance(this).registerReceiver(
                broadcastReceiver,
                new IntentFilter(ActivityRecognizedService.ACTION)
        );
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Intent intent = new Intent(this, ActivityRecognizedService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mApiClient, 5000, pendingIntent);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }


    @Override
    protected void onResume() {
        super.onResume();
        startService(new Intent(this, ActivityRecognizedService.class));
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopService(new Intent(this, ActivityRecognizedService.class));
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        startService(new Intent(this, ActivityRecognizedService.class));
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopService(new Intent(this, ActivityRecognizedService.class));
        sqlDB.delete(TABLE_NAME, null, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(new Intent(this, ActivityRecognizedService.class));
    }

    private void displayGreeting() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String time = simpleDateFormat.format(calendar.getTime());

        imgActivity.setImageResource(R.drawable.ic_frontpage);
        txtActivity.setGravity(Gravity.CENTER);
        txtActivity.setText("Welcome back! \nCurrent time is \n" + time);
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            Log.e("permission","no permission");
            return;
        }
        googleMap.setMyLocationEnabled(true);
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            // Clear previous markers
                            googleMap.clear();
                            Log.e("location","has location");

                            // Display current location with marker
                            LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                            googleMap.addMarker(new MarkerOptions().position(currentLocation).title("You are here"));
                            googleMap.moveCamera(CameraUpdateFactory.newLatLng(currentLocation));
                            googleMap.getUiSettings().setZoomControlsEnabled(true);

                            // The initial location
                            if(locationList.size() == 0) {
                                locationList.add(location);
                            }

                            updateMapRoute(googleMap);
                        }
                    }
                });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if(requestCode != 1) {
            return;
        }

        // Permission granted
        if(permissions.length != 0 && grantResults.length != 0) {
            if(Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[0]) && (grantResults[0]==PackageManager.PERMISSION_GRANTED)) {
                callMapAsync();
            }
        }
    }

    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);

                // Retrieve all location results
                for(Location location : locationResult.getLocations()) {
                    Log.e("location: ", location.getLatitude() + "," + location.getLongitude());
                    locationList.add(location);
                }
                callMapAsync();

            }
        };
    }

    private void startLocationUpdates() {
        // Create location request
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        locationSettingsRequest = builder.build();

        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        settingsClient.checkLocationSettings(locationSettingsRequest);

        // Check location permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            Log.e("permission","no permission");

            // Exit current activity
            System.exit(0);
        }

        // Create periodical callbacks
        createLocationCallback();
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.getMainLooper());
    }

    // Update route in map
    private void updateMapRoute(GoogleMap googleMap) {
        for(int i=0; i<locationList.size()-1; i++) {
            Location locationHead = locationList.get(i);
            Location locationNext = locationList.get(i+1);

            // Draw lines between locations
            googleMap.addPolyline(new PolylineOptions()
                    .clickable(true)
                    .add(new LatLng(locationHead.getLatitude(), locationHead.getLongitude()), new LatLng(locationNext.getLatitude(), locationNext.getLongitude()))
                    .width(20)
                    .color(Color.BLUE));
        }
    }

    // Update Google maps
    private void callMapAsync() {
        mapFragment.getMapAsync(this);
    }
}
