package com.example.android.sunshine.app;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Random;
import java.util.concurrent.TimeUnit;


public class WeatherListenerService extends WearableListenerService implements
        GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks {

    private static final String TAG = WeatherListenerService.class.getSimpleName();
    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
        }
        mGoogleApiClient.connect();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        if (!mGoogleApiClient.isConnected() || !mGoogleApiClient.isConnecting()) {
            ConnectionResult connectionResult = mGoogleApiClient
                    .blockingConnect(30, TimeUnit.SECONDS);

            if (!connectionResult.isSuccess()) {
                Log.e(TAG, "DataLayerListenerService !!! failed to connect to GoogleApiClient, "
                        + "error code: " + connectionResult.getErrorCode());
                return;
            }
        }

        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                continue;
            }

            DataItem dataItem = dataEvent.getDataItem();
            if (dataItem.getUri().getPath().equals(WeatherConstants.PATH_WEATHER_DATA)) {
                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap dataMap = dataMapItem.getDataMap();
                Intent intent = new Intent(WeatherConstants.PATH_WEATHER_DATA);
                intent.putExtra(WeatherConstants.KEY_TIMESTAMP, dataMap.getLong(WeatherConstants.KEY_TIMESTAMP));
                intent.putExtra(WeatherConstants.KEY_LOW_TEMP, dataMap.getString(WeatherConstants.KEY_LOW_TEMP));
                intent.putExtra(WeatherConstants.KEY_HIGH_TEMP, dataMap.getString(WeatherConstants.KEY_HIGH_TEMP));
                intent.putExtra(WeatherConstants.KEY_WEATHER_ICON, dataMap.getInt(WeatherConstants.KEY_WEATHER_ICON));
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
            }
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed");
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "onConnected");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getStringExtra(WeatherConstants.KEY_SERVICECOMMAND).equals(WeatherConstants.PATH_WEATHER_DATA)) {
            requestWeatherData();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void requestWeatherData() {
        PutDataMapRequest weatherData = PutDataMapRequest.create(WeatherConstants.PATH_WEATHER_DATA_REQUEST);
        // add random number to data so Data API sees data change
        Random r = new Random();
        weatherData.getDataMap().putInt(WeatherConstants.KEY_RANDOM, r.nextInt());
        PutDataRequest request = weatherData.asPutDataRequest();
        request.setUrgent();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                      //  Log.d(TAG, "requestWeatherData ---- onResult: " + dataItemResult.getStatus());
                    }
                });
    }
}

