package com.csi5175.mobilecommerce.recogu;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

public class ActivityRecognizedService extends IntentService{

    public static final String ACTIVITY_RECOGNITION_TYPE_NAME = "TYPE_NAME";
    public static final String ACTIVITY_RECOGNITION_TYPE_ICON = "TYPE_ICON";
    public static final String ACTIVITY_RECOGNITION_TYPE_CONFIDENCE = "TYPE_CONFIDENCE";
    public static final String ACTIVITY_RECOGNITION_TYPE_TIMESTAMP = "TYPE_TIMESTAMP";
    public static final String ACTION = ActivityRecognizedService.class.getName();
    private static int LAST_ACTIVITY = 5;  // TILTING Constant Value: 3
    private static boolean hasMusic = false;

    public ActivityRecognizedService() {
        super("ActivityRecognizedService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if(ActivityRecognitionResult.hasResult(intent)) {
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            handleDetectedActivities( result.getProbableActivities() );
        }
    }

    private void handleDetectedActivities(List<DetectedActivity> probableActivities) {
        for( DetectedActivity activity : probableActivities ) {
            int activityType = activity.getType();
            int activityConfidence = activity.getConfidence();

            if(activityType != LAST_ACTIVITY && activityConfidence > 30) {
                String activityName = "";
                String confidence = Integer.toString(activityConfidence);
                int icon = 0;

                LAST_ACTIVITY = activityType;

                Calendar calendar = Calendar.getInstance();
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String timestamp = simpleDateFormat.format(calendar.getTime());
//                int timestamp = Integer.parseInt(simpleDateFormat.format(calendar.getTime()));

                switch(activityType) {
                    case DetectedActivity.IN_VEHICLE: {
                        activityName = getString(R.string.activity_in_vehicle);
                        icon = R.drawable.ic_in_vehicle;
                        stopMusicService();
                        break;
                    }
                    case DetectedActivity.RUNNING: {
                        activityName = getString(R.string.activity_running);
                        icon = R.drawable.ic_running;
                        startMusicService();
                        break;
                    }
                    case DetectedActivity.STILL: {
                        activityName = getString(R.string.activity_still);
                        icon = R.drawable.ic_still;
                        stopMusicService();
                        break;
                    }

                    case DetectedActivity.WALKING: {
                        activityName = getString(R.string.activity_walking);
                        icon = R.drawable.ic_walking;
                        startMusicService();
                        break;
                    }
                    default: {
                        break;
                    }
                }
                Log.e( "ActivityRecogition", activityName + activity.getConfidence() );

                if(!activityName.equals("") && !confidence.equals("")) {
                    Intent intent = new Intent(ACTION);
                    intent.putExtra(ACTIVITY_RECOGNITION_TYPE_NAME, activityName);
                    intent.putExtra(ACTIVITY_RECOGNITION_TYPE_CONFIDENCE, confidence);
                    intent.putExtra(ACTIVITY_RECOGNITION_TYPE_ICON, icon);
                    intent.putExtra(ACTIVITY_RECOGNITION_TYPE_TIMESTAMP, timestamp);
                    intent.setFlags(intent.FLAG_RECEIVER_FOREGROUND);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                }
            }
        }
    }

    private void startMusicService() {
        if(!hasMusic) {
            startService(new Intent(ActivityRecognizedService.this, BackgroundMusicService.class));
            hasMusic = true;
        }
    }

    private void stopMusicService() {
        if(hasMusic) {
            stopService(new Intent(ActivityRecognizedService.this, BackgroundMusicService.class));
            hasMusic = false;
        }
    }
}
