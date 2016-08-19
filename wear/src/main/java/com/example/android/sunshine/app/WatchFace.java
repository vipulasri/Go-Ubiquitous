/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class WatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final String TAG = WatchFace.class.getSimpleName();

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<WatchFace.Engine> mWeakReference;

        public EngineHandler(WatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            WatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks{
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        boolean mRegisteredWeatherDataReceiver = false;

        Paint mBackgroundPaint;
        Paint mTimePaint;
        Paint mWeatherPaint;
        Paint mWeatherLowPaint;

        boolean mAmbient;
        Calendar mTime;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                setCurrentTime();
            }
        };
        int mTapCount;

        String mLowText;
        String mHighText;
        int mWeatherId;
        Bitmap mIconBitmap;
        int mIconWidth;
        Boolean mWeatherDataAvailable = false;

        float mXOffset;
        float mYOffset;

        float mTimeY;
        float mTimeX;
        Rect mTimeBounds;

        float mWeatherX;
        float mWeatherY;
        float mWeatherHighX;
        float mWeatherLowX;
        float mWeatherTextY;

        int mCanvasWidth;
        float mSpacer;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        private GoogleApiClient mGoogleApiClient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = WatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTimePaint = new Paint();
            mTimePaint = createTextPaint(resources.getColor(R.color.digital_text));

            mWeatherPaint = new Paint();
            mWeatherPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mWeatherLowPaint = new Paint();
            mWeatherLowPaint = createTextPaint(resources.getColor(R.color.low_temp_text));

            mTime = Calendar.getInstance();

            mTimeBounds = new Rect();

            mSpacer = resources.getDimension(R.dimen.weather_text_spacer);

            if (mGoogleApiClient == null) {
                mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApi(Wearable.API)
                        .build();
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            getWeatherData();
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d(TAG, "onConnectionFailed");
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "onConnectionFailed");
        }

        final BroadcastReceiver mWeatherDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mLowText = intent.getStringExtra(WeatherConstants.KEY_LOW_TEMP);
                mHighText = intent.getStringExtra(WeatherConstants.KEY_HIGH_TEMP);
                mWeatherId = intent.getIntExtra(WeatherConstants.KEY_WEATHER_ICON, -1);
                mIconBitmap = getImage(getIconResourceForWeatherCondition(mWeatherId), mIconWidth, mIconWidth);
                calcWeatherDataLayout();
                mWeatherDataAvailable = true;
                invalidate();
            }
        };

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                registerWeatherDataReceiver();

                // Update time zone in case it changed while we weren't visible.
                setCurrentTime();

                // this will trigger a request for weather data from handheld device
                mGoogleApiClient.connect();

            } else {
                unregisterReceiver();
                unregisterWeatherDataReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void registerWeatherDataReceiver() {
            if (mRegisteredWeatherDataReceiver) {
                return;
            }
            Log.d(TAG, "registerWeatherDataReceiver");
            mRegisteredWeatherDataReceiver = true;
            IntentFilter filter = new IntentFilter(WeatherConstants.PATH_WEATHER_DATA);
            LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mWeatherDataReceiver, filter);
        }

        private void unregisterWeatherDataReceiver() {
            if (!mRegisteredWeatherDataReceiver) {
                return;
            }
            Log.d(TAG, "unregisterWeatherDataReceiver");
            mRegisteredWeatherDataReceiver = false;
            LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mWeatherDataReceiver);
        }

        private void setCurrentTime() {
            mTime.setTimeZone(TimeZone.getDefault());
            mTime.setTimeInMillis(System.currentTimeMillis());
        }

        private void getWeatherData() {
            Intent intent = new Intent(getApplicationContext(), WeatherListenerService.class);
            intent.putExtra(WeatherConstants.KEY_SERVICECOMMAND, WeatherConstants.PATH_WEATHER_DATA);
            startService(intent);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = WatchFace.this.getResources();
            boolean isRound = insets.isRound();

            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float weatherTextSize = resources.getDimension(isRound
                    ? R.dimen.weather_text_size_round : R.dimen.weather_text_size);

            mTimePaint.setTextSize(textSize);
            mWeatherPaint.setTextSize(weatherTextSize);
            mWeatherLowPaint.setTextSize(weatherTextSize);

            mIconWidth = resources.getInteger(isRound ? R.integer.weather_icon_width_round : R.integer.weather_icon_width);

            mTimeY = resources.getDimension(isRound ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);
            mWeatherY = resources.getDimension(isRound ? R.dimen.weather_y_offset_round : R.dimen.weather_y_offset);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = WatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mCanvasWidth = width;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                if (mWeatherDataAvailable) {
                    drawWeatherData(canvas);
                }
            }
            drawTime(canvas, bounds);
        }

        private void drawTime(Canvas canvas, Rect bounds) {
            setCurrentTime();
            String text = String.format("%d:%02d",
                    mTime.get(Calendar.HOUR_OF_DAY),
                    mTime.get(Calendar.MINUTE));

            mTimePaint.getTextBounds(text, 0, text.length(), mTimeBounds);
            mTimeX = (bounds.width() - mTimeBounds.width()) / 2;
            canvas.drawText(text, mTimeX, mTimeY, mTimePaint);
        }

        private void drawWeatherData(Canvas canvas) {
            canvas.drawBitmap(mIconBitmap, mWeatherX, mWeatherY, null);
            canvas.drawText(mHighText, mWeatherHighX, mWeatherTextY, mWeatherPaint);
            canvas.drawText(mLowText, mWeatherLowX, mWeatherTextY, mWeatherLowPaint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        public Bitmap getImage(int id, int width, int height) {
            Bitmap bmp = BitmapFactory.decodeResource(getResources(), id);
            Bitmap img = Bitmap.createScaledBitmap(bmp, width, height, true);
            bmp.recycle();
            return img;
        }

        private int getIconResourceForWeatherCondition(int weatherId) {
            if (weatherId >= 200 && weatherId <= 232) {
                return R.drawable.ic_storm;
            } else if (weatherId >= 300 && weatherId <= 321) {
                return R.drawable.ic_light_rain;
            } else if (weatherId >= 500 && weatherId <= 504) {
                return R.drawable.ic_rain;
            } else if (weatherId == 511) {
                return R.drawable.ic_snow;
            } else if (weatherId >= 520 && weatherId <= 531) {
                return R.drawable.ic_rain;
            } else if (weatherId >= 600 && weatherId <= 622) {
                return R.drawable.ic_snow;
            } else if (weatherId >= 701 && weatherId <= 761) {
                return R.drawable.ic_fog;
            } else if (weatherId == 761 || weatherId == 781) {
                return R.drawable.ic_storm;
            } else if (weatherId == 800) {
                return R.drawable.ic_clear;
            } else if (weatherId == 801) {
                return R.drawable.ic_light_clouds;
            } else if (weatherId >= 802 && weatherId <= 804) {
                return R.drawable.ic_cloudy;
            }
            return -1;
        }

        private void calcWeatherDataLayout() {
            Rect weatherHighBounds = new Rect();
            Rect weatherLowBounds = new Rect();
            mWeatherPaint.getTextBounds(mHighText, 0, mHighText.length(), weatherHighBounds);
            mWeatherPaint.getTextBounds(mLowText, 0, mLowText.length(), weatherLowBounds);

            // draw weather data centered in face
            float weatherDataWidth =
                    mIconWidth + mSpacer  +
                            weatherHighBounds.width() +  mSpacer  +
                            weatherLowBounds.width();

            mWeatherX = (mCanvasWidth - weatherDataWidth) / 2;

            // draw weather text centered vertically in icon height to the right of icon
            mWeatherTextY =
                    mWeatherY +
                            mIconWidth / 2 +
                            (weatherHighBounds.height() / 2);

            mWeatherHighX = mWeatherX + mIconWidth + mSpacer;
            mWeatherLowX = mWeatherHighX + weatherHighBounds.width() + mSpacer;
        }

    }
}
