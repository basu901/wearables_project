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
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    public final String LOG_TAG = MyWatchFace.class.getSimpleName();

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;


        Paint high_weather_paint;
        Paint low_weather_paint;
        Paint weather_desc_paint;
        Paint time_paint;

        Paint mTextPaint;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        float mXOffset;
        float mYOffset;

        String weather_temperature_high = "";
        String weather_temperature_low = "";
        String weather_description = "";
        Bitmap weatherIcon;
        int weather_id;


        final BroadcastReceiver mWeatherReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(LOG_TAG, "onReceive - Weather Receiver");
                weather_id = intent.getIntExtra("sunshine_weather_id", 0);
                weather_temperature_high = intent.getStringExtra("sunshine_temp_high");
                weather_temperature_low = intent.getStringExtra("sunshine_temp_low");
                weather_description = intent.getStringExtra("sunshine_temp_desc");
                //Log.v(LOG_TAG, weather_temperature_high + " " + weather_temperature_low);
            }
        };

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            time_paint = new Paint();
            time_paint = createTextPaint(resources.getColor(R.color.digital_text));

            //Creating weather paint objects
            low_weather_paint = new Paint();
            low_weather_paint = createTextPaint(resources.getColor(R.color.weather_low_text));

            high_weather_paint = new Paint();
            high_weather_paint = createTextPaint(resources.getColor(R.color.digital_text));

            weather_desc_paint = new Paint();
            weather_desc_paint = createTextPaint(resources.getColor(R.color.weather_description_text));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mTime = new Time();
        }

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

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
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
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);

            IntentFilter weatherFilter = new IntentFilter("ACTION_WEATHER_CHANGED");
            MyWatchFace.this.registerReceiver(mWeatherReceiver, weatherFilter );
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            float weatherHighTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_weather_high_text_size : R.dimen.digital_weather_high_text_size);

            float weatherLowTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_weather_low_text_size_round : R.dimen.digital_weather_low_text_size);

            float weatherDescriptionTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_weather_description_text_size_round : R.dimen.digital_weather_description_text_size);

            time_paint.setTextSize(textSize);
            high_weather_paint.setTextSize(weatherHighTextSize);
            low_weather_paint.setTextSize(weatherLowTextSize);
            weather_desc_paint.setTextSize(weatherDescriptionTextSize);

            //mTextPaint.setTextSize(textSize);
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
                    //mTextPaint.setAntiAlias(!inAmbientMode);
                    time_paint.setAntiAlias(!inAmbientMode);
                    high_weather_paint.setAntiAlias(!inAmbientMode);
                    low_weather_paint.setAntiAlias(!inAmbientMode);
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
            Resources resources = MyWatchFace.this.getResources();
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
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
            //canvas.drawText(text, mXOffset, mYOffset, mTextPaint);
            try {
                final SimpleDateFormat simpleDate = new SimpleDateFormat("h:mm", Locale.getDefault());
                final Date time = simpleDate.parse(text);
                text = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(time);

            } catch (final ParseException e) {
                e.printStackTrace();
            }


            float mTimeHeight = time_paint.getTextSize();
            float mTimeXCenter = bounds.centerX() - time_paint.measureText(text) / 2;

            // Measuring temperature texts and padding them
            float mWeatherHighMeasure = high_weather_paint.measureText(weather_temperature_high) + 4;
            float mWeatherLowMeasure = low_weather_paint.measureText(weather_temperature_low) + 4;

            float mWeatherMeasure = mWeatherHighMeasure + mWeatherLowMeasure;

            float mWeatherHeight = high_weather_paint.getTextSize();


            float mWeatherDescriptionMeasure = weather_desc_paint.measureText(weather_description);

            // Relative x offsets
            float mWeatherHighXCenter = bounds.centerX() - mWeatherMeasure / 2;
            float mWeatherLowXCenter = bounds.centerX() - mWeatherMeasure / 2 + mWeatherHighMeasure;
            float mWeatherIconXCenter = 0;
            float mWeatherDescriptionXCenter =  bounds.centerX() - mWeatherDescriptionMeasure / 2 ;

            // Relative y offsets
            float mWeatherTemperatureYOffset = mYOffset + mTimeHeight;
            float mWeatherTemperatureDisplayYOffset = mYOffset + mTimeHeight + mWeatherHeight;


            int mWeatherIcon = MyWatchWeatherIcon.getIconForWeather(weather_id);
            if ( mWeatherIcon != -1 ) {
                weatherIcon = BitmapFactory.decodeResource(getResources(), mWeatherIcon);
                mWeatherIconXCenter = bounds.centerX() - weatherIcon.getWidth() / 2;
            }

            canvas.drawText(text, mTimeXCenter, mYOffset, time_paint);

            canvas.drawText(weather_temperature_high, mWeatherHighXCenter, mWeatherTemperatureYOffset, high_weather_paint);
            canvas.drawText(weather_temperature_low, mWeatherLowXCenter,mWeatherTemperatureYOffset, low_weather_paint);
            canvas.drawText(weather_description, mWeatherDescriptionXCenter, mWeatherTemperatureDisplayYOffset , weather_desc_paint);


            if ( mWeatherIcon != -1 && mWeatherIconXCenter != 0) {
                canvas.drawBitmap(weatherIcon, mWeatherIconXCenter, mWeatherTemperatureDisplayYOffset, time_paint);
            }

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
    }
}
