package com.csi5175.mobilecommerce.recogu;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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

import org.w3c.dom.Text;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    public GoogleApiClient mApiClient;
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

        txtActivity = findViewById(R.id.txt_activity);
        txtConfidence = findViewById(R.id.txt_confidence);
        imgActivity = (ImageView) findViewById(R.id.img_activity);

        displayGreeting();

        sqlDB = openOrCreateDatabase(DATABASE_NAME, SQLiteDatabase.CREATE_IF_NECESSARY, null);
//        cursor = sqlDB.query(TABLE_NAME, null, null, null, null, null, null);
        try{
            String createTableQuery = "CREATE TABLE " + TABLE_NAME + "(" + ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + TYPE + " TEXT NOT NULL, " + TIMESTAMP + " TEXT NOT NULL)";
            sqlDB.execSQL(createTableQuery);
        }catch(Exception e) {
            e.printStackTrace();
        }

        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(ActivityRecognition.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mApiClient.connect();

        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String activityName = intent.getStringExtra(ActivityRecognizedService.ACTIVITY_RECOGNITION_TYPE_NAME);
                String confidence = intent.getStringExtra(ActivityRecognizedService.ACTIVITY_RECOGNITION_TYPE_CONFIDENCE);
                int icon = intent.getIntExtra(ActivityRecognizedService.ACTIVITY_RECOGNITION_TYPE_ICON, 0);
                String timestamp = intent.getStringExtra(ActivityRecognizedService.ACTIVITY_RECOGNITION_TYPE_TIMESTAMP);

                txtActivity.setText(activityName);
                txtConfidence.setText("Confidence: " + confidence);
                imgActivity.setImageResource(icon);

                // Query for last activity
                String selectQuery = "SELECT * FROM " + TABLE_NAME + " ORDER BY "+ TIMESTAMP + " DESC LIMIT 1";
                Cursor cursor = sqlDB.rawQuery(selectQuery, null);
                if (cursor.moveToFirst()) {
                    String lastTimestamp = cursor.getString(cursor.getColumnIndex(TIMESTAMP));
                    String lastActivity = cursor.getString(cursor.getColumnIndex(TYPE));
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//                    int duration = (Integer.valueOf(lastTimestamp)-Integer.valueOf(timestamp))/1000;
                    long duration = 0;
                    try {
                        duration = (simpleDateFormat.parse(timestamp).getTime() - simpleDateFormat.parse(lastTimestamp).getTime())/1000;
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    if(duration != 0 && !lastActivity.equals("")) {
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
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mApiClient, 1000, pendingIntent);
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
//        stopService(new Intent(this, ActivityRecognizedService.class));
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopService(new Intent(this, ActivityRecognizedService.class));
        sqlDB.delete(TABLE_NAME, null,null);
    }

    private void displayGreeting() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String time = simpleDateFormat.format(calendar.getTime());

        imgActivity.setImageResource(R.drawable.ic_frontpage);
        txtActivity.setGravity(Gravity.CENTER);
        txtActivity.setText("Welcome back! \nCurrent time is \n" + time);
    }
}
