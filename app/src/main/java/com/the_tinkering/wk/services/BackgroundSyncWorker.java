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

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.the_tinkering.wk.GlobalSettings;
import com.the_tinkering.wk.WkApplication;
import com.the_tinkering.wk.api.ApiState;
import com.the_tinkering.wk.db.AppDatabase;
import com.the_tinkering.wk.jobs.Job;
import com.the_tinkering.wk.livedata.LiveApiState;
import com.the_tinkering.wk.livedata.LiveWorkInfos;
import com.the_tinkering.wk.tasks.ApiTask;
import com.the_tinkering.wk.util.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * The background worker that implements background sync.
 */
public final class BackgroundSyncWorker extends Worker {
    private static final Logger LOGGER = Logger.get(BackgroundSyncWorker.class);

    /**
     * A tag used to identify work in the work manager.
     */
    public static final String JOB_TAG = "bgsync";

    /**
     * The constructor.
     *
     * @param context Android context
     * @param workerParams parameters for this instance, managed by the work manager
     */
    @SuppressWarnings({"WeakerAccess", "RedundantSuppression"})
    public BackgroundSyncWorker(final Context context, final WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * Do the work. If background sync is eanbled, clear any pending API error state,
     * check for tasks that need to run, and do a run of all tasks that can run.
     *
     * @return the result of the work, always Success for this work.
     */
    @Override
    public Result doWork() {
        try {
            if (GlobalSettings.Api.getEnableBackgroundSync()) {
                LOGGER.info("Background sync starts: %s %s", ApiState.getCurrentApiState(), ApiTask.getOnlineStatus());
                if (LiveApiState.getInstance().get() == ApiState.ERROR) {
                    final AppDatabase db = WkApplication.getDatabase();
                    db.propertiesDao().setApiInError(false);
                    LiveApiState.getInstance().forceUpdate();
                }
                Job.assertDueTasks();
                ApiTaskService.runTasks();
            }
            else {
                cancelWork();
            }
        } catch (final Exception e) {
            LOGGER.uerr(e);
        }

        return Result.success();
    }

    /**
     * Prepare the work request for the background sync, and schedule it with the work manager.
     */
    private static void scheduleWork() {
        final Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        final PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(BackgroundSyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(JOB_TAG)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build();

        WorkManager.getInstance(WkApplication.getInstance()).enqueue(request);
    }

    /**
     * Cancel all work for the background sync.
     */
    private static void cancelWork() {
        WorkManager.getInstance(WkApplication.getInstance()).cancelAllWorkByTag(JOB_TAG);
    }

    /**
     * Cancel one specific job for the background sync.
     *
     * @param id the ID for the work to cancel
     */
    private static void cancelWork(final UUID id) {
        WorkManager.getInstance(WkApplication.getInstance()).cancelWorkById(id);
    }

    /**
     * Depending on user settings, either schedule or abandon work requests,
     * to make sure what is scheduled matches what the user wants.
     */
    public static void scheduleOrCancelWork() {
        try {
            if (LiveWorkInfos.getInstance().hasNullValue()) {
                return;
            }
            final List<WorkInfo> infos = LiveWorkInfos.getInstance().get();

            if (GlobalSettings.Api.getEnableBackgroundSync()) {
                if (infos.isEmpty()) {
                    scheduleWork();
                }
                else if (LiveWorkInfos.getInstance().get().size() > 1) {
                    cancelWork(infos.get(0).getId());
                }
            }
            else {
                if (!LiveWorkInfos.getInstance().get().isEmpty()) {
                    cancelWork();
                }
            }
        } catch (final Exception e) {
            LOGGER.uerr(e);
        }
    }
}