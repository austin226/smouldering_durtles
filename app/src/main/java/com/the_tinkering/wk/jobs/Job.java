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

package com.the_tinkering.wk.jobs;

import com.the_tinkering.wk.GlobalSettings;
import com.the_tinkering.wk.WkApplication;
import com.the_tinkering.wk.api.ApiState;
import com.the_tinkering.wk.db.AppDatabase;
import com.the_tinkering.wk.enums.OnlineStatus;
import com.the_tinkering.wk.livedata.LiveApiState;
import com.the_tinkering.wk.livedata.LiveFirstTimeSetup;
import com.the_tinkering.wk.livedata.LiveTimeLine;
import com.the_tinkering.wk.model.TimeLine;
import com.the_tinkering.wk.services.ApiTaskService;
import com.the_tinkering.wk.services.SessionWidgetProvider;
import com.the_tinkering.wk.tasks.ApiTask;
import com.the_tinkering.wk.util.DbLogger;
import com.the_tinkering.wk.util.Logger;

import java.util.Calendar;
import java.util.Date;

import javax.annotation.Nullable;

import static com.the_tinkering.wk.Constants.DAY;
import static com.the_tinkering.wk.Constants.HOUR;
import static com.the_tinkering.wk.Constants.REFERENCE_DATA_VERSION;
import static com.the_tinkering.wk.Constants.WEEK;
import static com.the_tinkering.wk.enums.OnlineStatus.NO_CONNECTION;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MINUTE;

/**
 * Abstract base class for background jobs. These are actions that need to
 * be pushed to a background thread, but don't do network communication and
 * don't need to be retried or persisted across restarts.
 */
public abstract class Job {
    private static final Logger LOGGER = Logger.get(Job.class);

    private static int currentHour = -1;
    private static int currentMinute = -1;

    /**
     * The parameters for this job, encoded in a string in a class-specific format.
     */
    protected final String data;

    /**
     * The constructor.
     *
     * @param data parameters for this job, encoded in a string in a class-specific format
     */
    protected Job(final String data) {
        this.data = data;
    }

    /**
     * Make sure that any tasks that need to be executed are scheduled in the database.
     */
    public static void assertDueTasks() {
        final AppDatabase db = WkApplication.getDatabase();

        final OnlineStatus onlineStatus = ApiTask.getOnlineStatus();
        boolean canTriggerApiTasks = onlineStatus != NO_CONNECTION;
        final ApiState currentApiState = ApiState.getCurrentApiState();

        switch (currentApiState) {
            case API_KEY_MISSING:
            case API_KEY_REJECTED:
            case ERROR:
                db.assertGetUserTask();
                canTriggerApiTasks = false;
                break;
            case UNKNOWN:
            case EXPIRED:
            case REFRESH_USER_DATA:
                db.assertGetUserTask();
                break;
            case OK:
                break;
        }

        final @Nullable Date lastGetSrsSystemsSuccess = db.propertiesDao().getLastSrsSystemSyncSuccessDate();
        if (lastGetSrsSystemsSuccess == null
                || System.currentTimeMillis() - lastGetSrsSystemsSuccess.getTime() > WEEK) {
            db.assertGetSrsSystemsTask();
        }

        final @Nullable Date lastGetLevelProgressionSuccess = db.propertiesDao().getLastLevelProgressionSyncSuccessDate(0);
        if (lastGetLevelProgressionSuccess == null
                || System.currentTimeMillis() - lastGetLevelProgressionSuccess.getTime() > WEEK) {
            db.assertGetLevelProgressionTask();
        }

        final @Nullable Date lastGetSubjectsSuccess = db.propertiesDao().getLastSubjectSyncSuccessDate(0);
        if (lastGetSubjectsSuccess == null
                || System.currentTimeMillis() - lastGetSubjectsSuccess.getTime() > DAY) {
            db.assertGetSubjectsTask();
        }

        final @Nullable Date lastGetAssignmentsSuccess = db.propertiesDao().getLastAssignmentSyncSuccessDate(0);
        if (lastGetAssignmentsSuccess == null
                || System.currentTimeMillis() - lastGetAssignmentsSuccess.getTime() > HOUR) {
            db.assertGetAssignmentsTask();
        }

        final @Nullable Date lastGetReviewStatisticsSuccess = db.propertiesDao().getLastReviewStatisticSyncSuccessDate(0);
        if (lastGetReviewStatisticsSuccess == null
                || System.currentTimeMillis() - lastGetReviewStatisticsSuccess.getTime() > HOUR) {
            db.assertGetReviewStatisticsTask();
        }

        final @Nullable Date lastGetStudyMaterialsSuccess = db.propertiesDao().getLastStudyMaterialSyncSuccessDate(0);
        if (lastGetStudyMaterialsSuccess == null
                || System.currentTimeMillis() - lastGetStudyMaterialsSuccess.getTime() > HOUR) {
            db.assertGetStudyMaterialsTask();
        }

        final @Nullable Date lastGetSummarySuccess = db.propertiesDao().getLastSummarySyncSuccessDate();
        if (lastGetSummarySuccess == null
                || System.currentTimeMillis() - lastGetSummarySuccess.getTime() > HOUR) {
            db.assertGetSummaryTask();
        }

        if (db.propertiesDao().getReferenceDataVersion() != REFERENCE_DATA_VERSION) {
            db.loadReferenceData();
        }

        LiveApiState.getInstance().post(currentApiState);

        if (canTriggerApiTasks) {
            ApiTaskService.schedule();
        }
    }

    /**
     * Regular housekeeping tasks that should be run on every or nearly every background job.
     */
    protected static void houseKeeping() {
        final AppDatabase db = WkApplication.getDatabase();

        assertDueTasks();

        if (GlobalSettings.getFirstTimeSetup() == 0 && db.taskDefinitionDao().getApiCount() == 0) {
            GlobalSettings.setFirstTimeSetup(1);
            LiveFirstTimeSetup.getInstance().forceUpdate();
        }

        boolean timeLineNeedsUpdate = LiveTimeLine.getInstance().hasNullValue();
        final TimeLine timeLine = LiveTimeLine.getInstance().get();
        final Calendar now = Calendar.getInstance();
        if (currentHour != now.get(HOUR_OF_DAY)) {
            timeLineNeedsUpdate = true;
        }
        if (currentMinute != now.get(MINUTE) && !timeLine.hasAvailableReviews() && timeLine.hasUpcomingReviews()) {
            timeLineNeedsUpdate = true;
        }
        if (timeLineNeedsUpdate) {
            LiveTimeLine.getInstance().update();
            SessionWidgetProvider.checkAndUpdateWidgets();
            currentHour = now.get(HOUR_OF_DAY);
            currentMinute = now.get(MINUTE);
        }

        DbLogger.trim();
    }

    /**
     * Run this job. This handles start/end logging and captures any exceptions thrown,
     * then delegates to the implementing subclass.
     */
    public final void run() {
        LOGGER.info("%s started with data: %s", DbLogger.getSimpleClassName(getClass()), data);
        try {
            runLocal();
        } catch (final Exception e) {
            LOGGER.uerr(e);
        }
        finally {
            LOGGER.info("%s finished", DbLogger.getSimpleClassName(getClass()));
        }
    }

    /**
     * The actual action to be performed by this job. This is implemented by every concrete job class.
     */
    protected abstract void runLocal();
}