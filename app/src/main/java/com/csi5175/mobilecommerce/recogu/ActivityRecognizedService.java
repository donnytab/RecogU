package com.csi5175.mobilecommerce.recogu;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import java.util.List;

public class ActivityRecognizedService extends IntentService{

    public static final String ACTIVITY_RECOGNITION_TYPE_NAME = "TYPE_NAME";
    public static final String ACTIVITY_RECOGNITION_TYPE_ICON = "TYPE_ICON";
    public static final String ACTIVITY_RECOGNITION_TYPE_CONFIDENCE = "TYPE_CONFIDENCE";
    public static final String ACTION = ActivityRecognizedService.class.getName();

    public ActivityRecognizedService() {
        super("ActivityRecognizedService");
    }

    public ActivityRecognizedService(String name) {
        super(name);
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
//            Toast toast;

            String activityName = "";
            String confidence = Integer.toString(activity.getConfidence());
            int icon = 0;
            switch( activity.getType() ) {
                case DetectedActivity.IN_VEHICLE: {
                    activityName = getString(R.string.activity_in_vehicle);
                    icon = R.drawable.ic_in_vehicle;
                    break;
                }
                case DetectedActivity.RUNNING: {
                    activityName = getString(R.string.activity_running);
                    icon = R.drawable.ic_running;
                    break;
                }
                case DetectedActivity.STILL: {
                    activityName = getString(R.string.activity_still);
                    icon = R.drawable.ic_still;
                    break;
                }

                case DetectedActivity.WALKING: {
                    activityName = getString(R.string.activity_walking);
                    icon = R.drawable.ic_walking;
//                    if( activity.getConfidence() >= 75 ) {
//                        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
//                        builder.setContentText( "Are you walking?" );
//                        builder.setSmallIcon( R.mipmap.ic_launcher );
//                        builder.setContentTitle( getString( R.string.app_name ) );
//                        NotificationManagerCompat.from(this).notify(0, builder.build());
//                    }
                    break;
                }
                case DetectedActivity.UNKNOWN: {
                    activityName = getString(R.string.activity_unknown);
                    icon = R.drawable.ic_unknown;
                    break;
                }
            }
            Log.e( "ActivityRecogition", activityName + activity.getConfidence() );

            Intent intent = new Intent(ACTION);
            intent.putExtra(ACTIVITY_RECOGNITION_TYPE_NAME, activityName);
            intent.putExtra(ACTIVITY_RECOGNITION_TYPE_CONFIDENCE, confidence);
            intent.putExtra(ACTIVITY_RECOGNITION_TYPE_ICON, icon);
            intent.setFlags(intent.FLAG_RECEIVER_FOREGROUND);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }
}
