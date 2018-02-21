package com.csi5175.mobilecommerce.recogu;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.Nullable;

import java.util.ArrayList;

public class BackgroundMusicService extends Service {
    private MediaPlayer player;
    protected ArrayList<String> songUriList;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, null,
                MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
        if(cursor.moveToFirst()) {
            while(!cursor.isAfterLast()) {
                String uri = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
                songUriList.add(uri);
                cursor.moveToNext();
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        for(String uri : songUriList) {
            try{
                Uri songUri = Uri.parse(uri);
                if(songUri != null) {
                    player = MediaPlayer.create(this, songUri);
                    player.setDataSource(uri);
                    player.prepare();
                    player.start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        player.stop();
    }
}
