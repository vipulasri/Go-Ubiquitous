package com.example.android.sunshine.app.wear;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

/*
    Sync adapter calls this intent to send weather data to wear device
 */
public class WeatherDataService extends IntentService implements
        GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks {

    public final String TAG = WeatherDataService.class.getSimpleName();
    private GoogleApiClient mGoogleApiClient;
    private Intent mIntent;

    public WeatherDataService() {
        super("WeatherDataService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mIntent = intent;
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
        }

        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed");    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (mIntent != null) {
            PutDataMapRequest weatherData = PutDataMapRequest.create(WeatherConstants.PATH_WEATHER_DATA);
            weatherData.getDataMap().putLong(WeatherConstants.KEY_TIMESTAMP, mIntent.getLongExtra(WeatherConstants.KEY_TIMESTAMP, 0));
            weatherData.getDataMap().putString(WeatherConstants.KEY_LOW_TEMP, mIntent.getStringExtra(WeatherConstants.KEY_LOW_TEMP));
            weatherData.getDataMap().putString(WeatherConstants.KEY_HIGH_TEMP, mIntent.getStringExtra(WeatherConstants.KEY_HIGH_TEMP));
            weatherData.getDataMap().putInt(WeatherConstants.KEY_WEATHER_ICON, mIntent.getIntExtra(WeatherConstants.KEY_WEATHER_ICON, 0));
            PutDataRequest request = weatherData.asPutDataRequest();
            request.setUrgent();

            Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                    .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                           // Log.d(TAG, "onConnected ---- onResult: " + dataItemResult.getStatus());
                            mGoogleApiClient.disconnect();
                        }
                    });
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended");
    }
}
