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

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import androidx.work.WorkManager;

import com.the_tinkering.wk.GlobalSettings;
import com.the_tinkering.wk.WkApplication;
import com.the_tinkering.wk.api.ApiState;
import com.the_tinkering.wk.db.AppDatabase;
import com.the_tinkering.wk.enums.OnlineStatus;
import com.the_tinkering.wk.jobs.Job;
import com.the_tinkering.wk.livedata.LiveApiState;
import com.the_tinkering.wk.util.Logger;

import java.util.concurrent.Semaphore;

import javax.annotation.Nullable;

import static com.the_tinkering.wk.Constants.MINUTE;
import static com.the_tinkering.wk.Constants.SECOND;
import static com.the_tinkering.wk.util.ObjectSupport.getTopOfHour;
import static com.the_tinkering.wk.util.ObjectSupport.safe;

/**
 * Background sync worker. This used to be a Worker for WorkManager, but now it's just
 * a utility class used by the BackgroundAlarmReceiver.
 */
public final class BackgroundSyncWorker {
    private static final Logger LOGGER = Logger.get(BackgroundSyncWorker.class);

    private BackgroundSyncWorker() {
        //
    }

    /**
     * A tag used to identify work in the work manager.
     */
    private static final String JOB_TAG = "bgsync";

    private static void processAlarmHelper() throws InterruptedException {
        if (GlobalSettings.Api.getEnableBackgroundSync()) {
            boolean idle = false;
            final @Nullable PowerManager pm = (PowerManager) WkApplication.getInstance().getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (pm.isPowerSaveMode()) {
                        LOGGER.info("Device is in power save mode - background sync cancelled");
                        return;
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    idle = pm.isDeviceIdleMode();
                }
            }

            final long topOfHour1 = getTopOfHour(System.currentTimeMillis());
            final long topOfHour2 = WkApplication.getDatabase().propertiesDao().getLastBackgroundSync();
            if (topOfHour1 != topOfHour2) {
                LOGGER.info("Background sync starts: %s %s", ApiState.getCurrentApiState(), WkApplication.getInstance().getOnlineStatus());
                final AppDatabase db = WkApplication.getDatabase();
                WkApplication.getDatabase().propertiesDao().setLastBackgroundSync(topOfHour1);
                if (WkApplication.getInstance().getOnlineStatus() == OnlineStatus.NO_CONNECTION) {
                    LOGGER.info("Online status is NO_CONNECTION - wait for the network status callback to settle");
                    Thread.sleep(2 * SECOND);
                }
                if (LiveApiState.getInstance().get() == ApiState.ERROR) {
                    db.propertiesDao().setApiInError(false);
                    LiveApiState.getInstance().forceUpdate();
                }
                Job.assertDueTasks(5 * MINUTE);
                if (idle && db.hasPendingApiTasks()) {
                    LOGGER.info("Idle mode is active and there are background tasks to run - schedule another alarm and bail out");
                    BackgroundAlarmReceiver.scheduleAlarmClock();
                    return;
                }
                ApiTaskService.runTasks();
                LOGGER.info("Background sync ends");
            }
        }
    }

    /**
     * Process a background alarm event, whether triggered by an actual system alarm, or a database update that can affect
     * widgets and/or notifications. Always runs on a background thread.
     *
     * @param semaphore the method will call release() on this semaphone when the work is done
     */
    public static void processAlarm(final Semaphore semaphore) {
        safe(BackgroundSyncWorker::processAlarmHelper);
        semaphore.release();
    }

    /**
     * Cancel all work for the background sync.
     */
    public static void cancelWork() {
        WorkManager.getInstance(WkApplication.getInstance()).cancelAllWorkByTag(JOB_TAG);
    }
}
