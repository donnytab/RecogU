package com.csi5175.mobilecommerce.recogu;

import android.app.IntentService;
import android.content.Intent;
import android.media.MediaPlayer;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
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
    public static final String ACTIVITY_RECOGNITION_MAP_STATUS = "MAP_STATUS";
    public static final String ACTION = ActivityRecognizedService.class.getName();
    private static int LAST_ACTIVITY = 5;  // TILTING Constant Value: 3
    private static boolean hasMusic = false;

    private static MediaPlayer player;

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

    @Override
    public void onDestroy() {}

    private void handleDetectedActivities(List<DetectedActivity> probableActivities) {
        // Handle all detected results
        for( DetectedActivity activity : probableActivities ) {
            int activityType = activity.getType();
            int activityConfidence = activity.getConfidence();

            // Display activity when the result activity is not the same with the last one & with high confidence of 40
            if(activityType != LAST_ACTIVITY && activityConfidence > 40) {
                String activityName = "";
                String confidence = Integer.toString(activityConfidence);
                int icon = 0;
                int mapStatus = View.VISIBLE;

                // Change last activity
                LAST_ACTIVITY = activityType;

                // Get current time
                Calendar calendar = Calendar.getInstance();
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String timestamp = simpleDateFormat.format(calendar.getTime());

                // Handle 4 types of activities
                switch(activityType) {
                    case DetectedActivity.IN_VEHICLE: {
                        activityName = getString(R.string.activity_in_vehicle);
                        icon = R.drawable.ic_in_vehicle;
                        stopMusicService(activityName);
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
                        stopMusicService(activityName);
                        mapStatus = View.GONE;
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

                // Case for non-empty result
                if(!activityName.equals("") && !confidence.equals("")) {
                    Intent intent = new Intent(ACTION);
                    intent.putExtra(ACTIVITY_RECOGNITION_TYPE_NAME, activityName);
                    intent.putExtra(ACTIVITY_RECOGNITION_TYPE_CONFIDENCE, confidence);
                    intent.putExtra(ACTIVITY_RECOGNITION_TYPE_ICON, icon);
                    intent.putExtra(ACTIVITY_RECOGNITION_TYPE_TIMESTAMP, timestamp);
                    intent.putExtra(ACTIVITY_RECOGNITION_MAP_STATUS, mapStatus);
                    intent.setFlags(intent.FLAG_RECEIVER_FOREGROUND);

                    // Broadcast intent message
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                }
            }
        }
    }

    // Play background music
    private void startMusicService() {
        if(!hasMusic) {
            try
            {
                player = MediaPlayer.create(this, R.raw.zayn);
                player.start();
                Log.e("music", "music starts...");
            } catch (Exception e) {
                e.printStackTrace();
            }
            hasMusic = true;
        }
    }

    // Stop background music
    private void stopMusicService(String name) {
        if(hasMusic) {
            player.release();
            Log.e("music", "music stopped..." + name);
            hasMusic = false;
        }
    }
}
