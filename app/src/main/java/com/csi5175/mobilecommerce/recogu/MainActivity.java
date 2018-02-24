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
import android.location.LocationListener;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
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
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;

import org.w3c.dom.Text;

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
    private Cursor cursor;

    @SuppressLint("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationList = new ArrayList<Location>();

        mapFragment = (SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        txtActivity = findViewById(R.id.txt_activity);
        txtConfidence = findViewById(R.id.txt_confidence);
        imgActivity = (ImageView) findViewById(R.id.img_activity);

        displayGreeting();

        sqlDB = openOrCreateDatabase(DATABASE_NAME, SQLiteDatabase.CREATE_IF_NECESSARY, null);
//        cursor = sqlDB.query(TABLE_NAME, null, null, null, null, null, null);
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

        startLocationUpdates();


        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String activityName = intent.getStringExtra(ActivityRecognizedService.ACTIVITY_RECOGNITION_TYPE_NAME);
                String confidence = intent.getStringExtra(ActivityRecognizedService.ACTIVITY_RECOGNITION_TYPE_CONFIDENCE);
                int icon = intent.getIntExtra(ActivityRecognizedService.ACTIVITY_RECOGNITION_TYPE_ICON, 0);
                String timestamp = intent.getStringExtra(ActivityRecognizedService.ACTIVITY_RECOGNITION_TYPE_TIMESTAMP);
                int mapStatus = intent.getIntExtra(ActivityRecognizedService.ACTIVITY_RECOGNITION_MAP_STATUS, 0);

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
//                    int duration = (Integer.valueOf(lastTimestamp)-Integer.valueOf(timestamp))/1000;
                    long duration = 0;
                    try {
                        duration = (simpleDateFormat.parse(timestamp).getTime() - simpleDateFormat.parse(lastTimestamp).getTime()) / 1000;
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    if (duration != 0 && !lastActivity.equals("")) {
                        String toastText = "You have been " + lastActivity + " for " + Long.toString(duration) + " seconds";
                        Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_LONG).show();
//                        Toast.makeText(getApplicationContext(), timestamp, Toast.LENGTH_LONG).show();
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
//        displayGreeting();
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
//        Location location = LocationServices.FusedLocationApi.getLastLocation(mApiClient);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Log.e("permission","no permission");
            return;
        }
        googleMap.setMyLocationEnabled(true);
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            googleMap.clear();
                            Log.e("location","has location");
                            LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                            googleMap.addMarker(new MarkerOptions().position(currentLocation).title("You are here"));
                            googleMap.moveCamera(CameraUpdateFactory.newLatLng(currentLocation));
//                            googleMap.animateCamera(CameraUpdateFactory.zoomIn());
                            googleMap.getUiSettings().setZoomControlsEnabled(true);

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

        if(permissions.length != 0 && grantResults.length != 0) {
            if(Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[0]) && (grantResults[0]==PackageManager.PERMISSION_GRANTED)) {
                mapFragment.getMapAsync(this);
            }
        }
    }

    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
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

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Log.e("permission","no permission");
            System.exit(0);
//            return;
        }
        createLocationCallback();
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.getMainLooper());
    }

    private void updateMapRoute(GoogleMap googleMap) {
        for(int i=0; i<locationList.size()-1; i++) {
            Location locationHead = locationList.get(i);
            Location locationNext = locationList.get(i+1);
            googleMap.addPolyline(new PolylineOptions()
                    .clickable(true)
                    .add(new LatLng(locationHead.getLatitude(), locationHead.getLongitude()), new LatLng(locationNext.getLatitude(), locationNext.getLongitude()))
                    .width(20)
                    .color(Color.BLUE));
        }
    }

    private void callMapAsync() {
        mapFragment.getMapAsync(this);
    }
}
