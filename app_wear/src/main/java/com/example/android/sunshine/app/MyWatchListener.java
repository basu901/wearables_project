package com.example.android.sunshine.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by shaunak basu on 31-10-2016.
 */
public class MyWatchListener  extends WearableListenerService
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener {

    public final String LOG_TAG = MyWatchListener.class.getSimpleName();
    private static final String WEARABLE_PATH = "/sunshine_watch_data";


    GoogleApiClient mGoogleApiClient;

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(LOG_TAG, "Google API Client was connected");
        Wearable.DataApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(LOG_TAG, "Connection to Google API client was suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.e(LOG_TAG, "Connection to Google API client has failed");
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.connect();
    }


    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
        .addApi(Wearable.API)
        .build();
        mGoogleApiClient.connect();

    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        Log.i(LOG_TAG, "onMessageReceived");
    }


    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.i(LOG_TAG, "onDataChanged");

        DataMap dataMap;
        for (DataEvent event : dataEvents) {

            if (event.getType() == DataEvent.TYPE_CHANGED) {

                dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                Log.i(LOG_TAG, "DataMap received on watch: " + dataMap);

                String sunshine_temp_high = dataMap.getString("sunshine_temp_high");
                String sunshine_temp_low = dataMap.getString("sunshine_temp_low");
                String  sunshine_temp_desc = dataMap.get("sunshine_temp_desc");

                int sunshine_weather_id = dataMap.get("sunshine_weather_id");
                Long sunshine_time_millis = dataMap.getLong("sunshine_time_millis");

                Log.i(LOG_TAG, "Temperature: " + sunshine_temp_high + ", " + sunshine_temp_low);
                Log.i(LOG_TAG, "Weather id: " + sunshine_weather_id);

                Intent send_watch = new Intent("ACTION_WEATHER_CHANGED");
                send_watch.putExtra("sunshine_temp_high", sunshine_temp_high);
                send_watch.putExtra("sunshine_temp_low", sunshine_temp_low);
                send_watch.putExtra("sunshine_temp_desc", sunshine_temp_desc);
                send_watch.putExtra("sunshine_weather_id", sunshine_weather_id);
                send_watch.putExtra("sunshine_time_millis", sunshine_time_millis);
                sendBroadcast(send_watch);

            }

        }
    }
}
