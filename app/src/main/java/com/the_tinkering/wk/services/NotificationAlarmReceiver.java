/*
 * Copyright 2019-2020 Ernst Jan Plugge <rmc@dds.nl>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.the_tinkering.wk.services;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.the_tinkering.wk.Constants;
import com.the_tinkering.wk.GlobalSettings;
import com.the_tinkering.wk.R;
import com.the_tinkering.wk.WkApplication;
import com.the_tinkering.wk.activities.MainActivity;
import com.the_tinkering.wk.db.AppDatabase;
import com.the_tinkering.wk.livedata.LiveTimeLine;
import com.the_tinkering.wk.model.NotificationContext;
import com.the_tinkering.wk.util.Logger;

import java.util.Date;
import java.util.Locale;

import javax.annotation.Nullable;

import static com.the_tinkering.wk.util.ObjectSupport.getTopOfHour;

/**
 * The alarm receiver that gets triggered once per hour, and is responsible for
 * setting a notification if needed.
 */
public final class NotificationAlarmReceiver extends BroadcastReceiver {
    private static final Logger LOGGER = Logger.get(NotificationAlarmReceiver.class);

    @Override
    public void onReceive(final Context context, final Intent intent) {
        try {
            LOGGER.info("Notification alarm received");
            if (GlobalSettings.Other.getEnableNotifications() || SessionWidgetProvider.hasWidgets()) {
                @Nullable PowerManager.WakeLock wl = null;
                final @Nullable PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "wk:wk");
                    wl.acquire(Constants.MINUTE);
                }

                new Task().execute(context, wl);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    scheduleOrCancelAlarm();
                }
            }
        } catch (final Exception e) {
            LOGGER.uerr(e);
        }
    }

    /**
     * Schedule the alarm for notifications. It is scheduled for the top
     * of each hour, but depending on circumstances, the delivery of the alarm
     * can be delayed a bit by the device.
     */
    private static void scheduleAlarm() {
        final Date topOfThisHour = getTopOfHour(System.currentTimeMillis());
        final @Nullable AlarmManager alarmManager = (AlarmManager) WkApplication.getInstance().getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            final Intent intent = new Intent(WkApplication.getInstance(), NotificationAlarmReceiver.class);
            final PendingIntent pendingIntent = PendingIntent.getBroadcast(WkApplication.getInstance(),
                    0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, topOfThisHour.getTime() + Constants.HOUR, pendingIntent);
            }
            else {
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, topOfThisHour.getTime(), AlarmManager.INTERVAL_HOUR, pendingIntent);
            }
        }
    }

    /**
     * Cancel the notification alarm.
     */
    private static void cancelAlarm() {
        final @Nullable AlarmManager alarmManager = (AlarmManager) WkApplication.getInstance().getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            final Intent intent = new Intent(WkApplication.getInstance(), NotificationAlarmReceiver.class);
            final PendingIntent pendingIntent = PendingIntent.getBroadcast(WkApplication.getInstance(),
                    0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            alarmManager.cancel(pendingIntent);
        }
    }

    /**
     * Schedule or cancel depending on user settings.
     */
    public static void scheduleOrCancelAlarm() {
        try {
            if (GlobalSettings.Other.getEnableNotifications() || SessionWidgetProvider.hasWidgets()) {
                scheduleAlarm();
            }
            else {
                cancelAlarm();
            }
        } catch (final Exception e) {
            LOGGER.uerr(e);
        }
    }

    /**
     * When there are no lessons or reviews available anymore, cancel the notification if it's still there.
     */
    public static void cancelNotification() {
        try {
            final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(WkApplication.getInstance());
            notificationManager.cancel(1);
            final AppDatabase db = WkApplication.getDatabase();
            db.propertiesDao().setNotificationSet(false);
            LOGGER.info("Notification canceled");
        } catch (final Exception e) {
            LOGGER.uerr(e);
        }
    }

    /**
     * The actucal task that fetches the notification data, determines if and what notification must be shown,
     * and builds the notification.
     */
    private static final class Task extends AsyncTask<Object, Void, Void> {
        /**
         * After fetching the notification context from the database, actually show the notification.
         *
         * @param context Android context
         * @param ctx notification context from the database
         */
        @SuppressWarnings("MethodMayBeStatic")
        private void postNotification(final Context context, final NotificationContext ctx) {
            final String title;
            final String text;
            if (ctx.getNumLessons() > 0) {
                title = "New lessons and reviews available";
                text = String.format(Locale.ROOT, "%d lessons and %d reviews available", ctx.getNumLessons(), ctx.getNumReviews());
            }
            else {
                title = "New reviews available";
                text = String.format(Locale.ROOT, "%d reviews available", ctx.getNumReviews());
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                final String name = "NewReviewsChannel";
                final String description = "New reviews available";
                final int importance = GlobalSettings.Other.getNotificationPriority().getManagerImportance();
                final NotificationChannel channel = new NotificationChannel(name, name, importance);
                channel.setDescription(description);
                final @Nullable NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(channel);
                }
            }

            final Intent intent2 = new Intent(context, MainActivity.class);
            intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            final PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent2, 0);

            final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "NewReviewsChannel");
            builder.setSmallIcon(R.drawable.ic_notification);
            builder.setContentTitle(title);
            builder.setContentText(text);
            builder.setPriority(GlobalSettings.Other.getNotificationPriority().getCompatPriority());
            builder.setCategory(GlobalSettings.Other.getNotificationCategory().getCompatCategory());
            builder.setContentIntent(pendingIntent);
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            builder.setAutoCancel(true);

            final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.notify(1, builder.build());

            final AppDatabase db = WkApplication.getDatabase();
            db.propertiesDao().setNotificationSet(true);

            LOGGER.info("Notification posted");
        }

        @Override
        protected Void doInBackground(final Object... params) {
            try {
                final Context context = (Context) params[0];
                final @Nullable PowerManager.WakeLock wl = (PowerManager.WakeLock) params[1];

                final AppDatabase db = WkApplication.getDatabase();
                final int maxLevel = db.propertiesDao().getUserMaxLevelGranted();
                final int userLevel = db.propertiesDao().getUserLevel();
                final boolean vacationMode = db.propertiesDao().getVacationMode();
                @Nullable Date lastDate = db.propertiesDao().getLastNotifiedReviewDate();
                if (lastDate == null) {
                    lastDate = getTopOfHour(System.currentTimeMillis() - Constants.HOUR);
                }
                final NotificationContext ctx = db.subjectAggregatesDao().getNotificationContext(maxLevel, userLevel, lastDate, new Date());

                if (GlobalSettings.Other.getEnableNotifications()) {
                    if (vacationMode) {
                        cancelNotification();
                    }
                    else if (ctx.getNumNewReviews() > 0) {
                        postNotification(context, ctx);
                        db.propertiesDao().setLastNotifiedReviewDate(ctx.getNewestAvailableAt());
                    }
                    else if (ctx.getNumLessons() == 0 && ctx.getNumReviews() == 0) {
                        cancelNotification();
                    }
                }

                if (SessionWidgetProvider.hasWidgets()) {
                    final @Nullable Date upcoming = db.subjectAggregatesDao().getNextLongTermReviewDate(maxLevel, userLevel, new Date());
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            SessionWidgetProvider.checkAndUpdateWidgets(ctx, upcoming);
                        }
                    });
                }

                LiveTimeLine.getInstance().update();

                if (wl != null) {
                    wl.release();
                }
            } catch (final Exception e) {
                LOGGER.uerr(e);
            }

            return null;
        }
    }
}