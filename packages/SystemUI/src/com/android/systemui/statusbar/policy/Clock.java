/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import android.app.ActivityManagerNative;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.provider.AlarmClock;
import android.os.Handler;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import com.android.internal.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import com.android.internal.R;

/**
 * This widget display an analogic clock with two hands for hours and
 * minutes.
 */
public class Clock extends TextView implements OnClickListener {
    private boolean mAttached;
    private Calendar mCalendar;
    private static String mClockFormatString;
    private static String mExpandedClockFormatString;
    private static SimpleDateFormat mClockFormat;
    private static SimpleDateFormat mExpandedClockFormat;

    private static final int AM_PM_STYLE_NORMAL  = 0;
    private static final int AM_PM_STYLE_SMALL   = 1;
    private static final int AM_PM_STYLE_GONE    = 2;
    private static final int PROTEKK_O_CLOCK     = 3;
    private static int AM_PM_STYLE = AM_PM_STYLE_GONE;


    private static final char MAGIC1 = '\uEF00';
    private static final char MAGIC2 = '\uEF01';

    public static final int STYLE_HIDE_CLOCK    = 0;
    public static final int STYLE_CLOCK_RIGHT   = 1;
    public static final int STYLE_CLOCK_CENTER  = 2;

    private static int mClockStyle = STYLE_CLOCK_RIGHT;
    private static int mAmPmStyle;
    private boolean mShowClock;

    Handler mHandler;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_AM_PM), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CLOCK), false, this);
            updateSettings();
        }

        @Override public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public Clock(Context context) {
        this(context, null);
    }

    public Clock(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Clock(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    
        if(isClickable()){
	            setOnClickListener(this);
	    }
	 }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();

            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);

            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());
        }

        // NOTE: It's safe to do these after registering the receiver since the receiver always runs
        // in the main thread, therefore the receiver can't run before this method returns.

        // The time zone may have changed while the receiver wasn't registered, so update the Time
        mCalendar = Calendar.getInstance(TimeZone.getDefault());

        // Make sure we update to the current time
        //updateClock();
        SettingsObserver settingsObserver = new SettingsObserver(new Handler());
        settingsObserver.observe();
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                mCalendar = Calendar.getInstance(TimeZone.getTimeZone(tz));
                if (mClockFormat != null) {
                    mClockFormat.setTimeZone(mCalendar.getTimeZone());
                }
            }
            updateClock();
        }
    };

    final void updateClock() {
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        setText(getSmallTime());
    }

    private final CharSequence getSmallTime() {
        Context context = getContext();
        boolean b24 = DateFormat.is24HourFormat(context);
        int res;

        if (b24) {
            res = R.string.twenty_four_hour_time_format;
        } else {
            res = R.string.twelve_hour_time_format;
        }
        
        String format = context.getString(res);
        SimpleDateFormat sdf = updateFormatString(IsShade(), format);

        String result = sdf.format(mCalendar.getTime());

        if (IsShade() || AM_PM_STYLE != AM_PM_STYLE_NORMAL) {
            int magic1 = result.indexOf(MAGIC1);
            int magic2 = result.indexOf(MAGIC2);
            if (magic1 >= 0 && magic2 > magic1) {
                SpannableStringBuilder formatted = new SpannableStringBuilder(result);
                if (IsShade() || AM_PM_STYLE == AM_PM_STYLE_GONE) {
                    formatted.delete(magic1, magic2+1);
                } else {
                    if (AM_PM_STYLE == AM_PM_STYLE_SMALL) {
                        CharacterStyle style = new RelativeSizeSpan(0.7f);
                        formatted.setSpan(style, magic1, magic2,
                                          Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                    }
                    formatted.delete(magic2, magic2 + 1);
                    formatted.delete(magic1, magic1 + 1);
                }
                return formatted;
            }
        }
        return result;
    }

    private SimpleDateFormat updateFormatString(boolean shade, String format)
    {
        SimpleDateFormat sdf = (shade ? mExpandedClockFormat : mClockFormat);

        if (!format.equals(shade ? mExpandedClockFormatString : mClockFormatString)) {

            if (shade || AM_PM_STYLE != AM_PM_STYLE_NORMAL) {
                int a = -1;
                boolean quoted = false;
                for (int i = 0; i < format.length(); i++) {
                    char c = format.charAt(i);

                    if (c == '\'') {
                        quoted = !quoted;
                    }
                    if (!quoted && c == 'a') {
                        a = i;
                        break;
                    }
                }

                if (a >= 0) {
                    // Move a back so any whitespace before AM/PM is also in the alternate size.
                    final int b = a;
                    while (a > 0 && Character.isWhitespace(format.charAt(a-1))) {
                        a--;
                    }
                    format = format.substring(0, a) + MAGIC1 + format.substring(a, b)
                        + "a" + MAGIC2 + format.substring(b + 1);
                }
            }
            if (shade)
            {
                mExpandedClockFormat = sdf = new SimpleDateFormat(format);
                mExpandedClockFormatString = format;
            }
            else
            {
                mClockFormat = sdf = new SimpleDateFormat(format);
                mClockFormatString = format;
            }
        } else {
            sdf = shade ? mExpandedClockFormat : mClockFormat;
        }
        return sdf;

    }

    private void updateSettings(){
        ContentResolver resolver = mContext.getContentResolver();

        mAmPmStyle = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_AM_PM, 2));
        mClockStyle = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_CLOCK, STYLE_CLOCK_RIGHT);

        if (mAmPmStyle != AM_PM_STYLE) {
            AM_PM_STYLE = mAmPmStyle;
            mClockFormatString = "";
        }

        updateClockVisibility();
        updateClock();
    }

    public boolean IsCenter()
    {
        Object o = getTag();
        return (o != null && o.toString().equals("center"));
    }
  
    public boolean IsShade()
    {
        Object o = getTag();
        return (o != null && o.toString().equals("expanded"));
    }

    public void forceUpdate()
    {
        updateSettings();
    }

    protected void updateClockVisibility() {
        boolean c = IsCenter();
        if (mClockStyle == STYLE_CLOCK_RIGHT && !c || mClockStyle == STYLE_CLOCK_CENTER && c || IsShade())
            setVisibility(View.VISIBLE);
        else
            setVisibility(View.GONE);
    }

    private void collapseStartActivity(Intent what) {
	        // collapse status bar
	        StatusBarManager statusBarManager = (StatusBarManager) getContext().getSystemService(
	                Context.STATUS_BAR_SERVICE);
	        statusBarManager.collapsePanels();
	
	        // dismiss keyguard in case it was active and no passcode set
	        try {
	            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
	        } catch (Exception ex) {
	            // no action needed here
	        }
	
	        // start activity
	        what.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	        mContext.startActivity(what);
	    }
	
	    @Override
	    public void onClick(View v) {
	        // start com.android.deskclock/.DeskClock
        ComponentName clock = new ComponentName("com.android.deskclock",
                "com.android.deskclock.DeskClock");
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(clock);
	        collapseStartActivity(intent);
	    }
}
