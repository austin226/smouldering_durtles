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

package com.the_tinkering.wk.activities;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;

import androidx.lifecycle.Observer;

import com.the_tinkering.wk.GlobalSettings;
import com.the_tinkering.wk.R;
import com.the_tinkering.wk.api.ApiState;
import com.the_tinkering.wk.jobs.RetryApiErrorJob;
import com.the_tinkering.wk.livedata.LiveApiState;
import com.the_tinkering.wk.livedata.LiveBurnedItems;
import com.the_tinkering.wk.livedata.LiveCriticalCondition;
import com.the_tinkering.wk.livedata.LiveJlptProgress;
import com.the_tinkering.wk.livedata.LiveJoyoProgress;
import com.the_tinkering.wk.livedata.LiveLevelDuration;
import com.the_tinkering.wk.livedata.LiveLevelProgress;
import com.the_tinkering.wk.livedata.LiveRecentUnlocks;
import com.the_tinkering.wk.livedata.LiveSrsBreakDown;
import com.the_tinkering.wk.livedata.LiveTimeLine;
import com.the_tinkering.wk.model.Session;
import com.the_tinkering.wk.model.TimeLine;
import com.the_tinkering.wk.proxy.ViewProxy;
import com.the_tinkering.wk.services.BackgroundSyncWorker;
import com.the_tinkering.wk.services.JobRunnerService;
import com.the_tinkering.wk.services.NotificationAlarmReceiver;
import com.the_tinkering.wk.services.SessionWidgetProvider;
import com.the_tinkering.wk.util.Logger;
import com.the_tinkering.wk.util.WeakLcoRef;
import com.the_tinkering.wk.views.AvailableSessionsView;
import com.the_tinkering.wk.views.FirstTimeSetupView;
import com.the_tinkering.wk.views.JlptProgressView;
import com.the_tinkering.wk.views.JoyoProgressView;
import com.the_tinkering.wk.views.LessonReviewBreakdownView;
import com.the_tinkering.wk.views.LevelDurationView;
import com.the_tinkering.wk.views.LevelProgressView;
import com.the_tinkering.wk.views.LiveBurnedItemsSubjectTableView;
import com.the_tinkering.wk.views.LiveCriticalConditionSubjectTableView;
import com.the_tinkering.wk.views.LiveRecentUnlocksSubjectTableView;
import com.the_tinkering.wk.views.Post60ProgressView;
import com.the_tinkering.wk.views.SessionButtonsView;
import com.the_tinkering.wk.views.SrsBreakDownView;
import com.the_tinkering.wk.views.SyncProgressView;
import com.the_tinkering.wk.views.TimeLineBarChart;
import com.the_tinkering.wk.views.UpcomingReviewsView;

import javax.annotation.Nullable;

/**
 * The dashboard activity.
 *
 * <p>
 *     This has by far the most complex layout. The activity contains a multitude
 *     of views that display database state. It is informed of changes via LiveData
 *     instances.
 * </p>
 */
public final class MainActivity extends AbstractActivity {
    private static final Logger LOGGER = Logger.get(MainActivity.class);

    private final ViewProxy apiErrorView = new ViewProxy();
    private final ViewProxy apiKeyRejectedView = new ViewProxy();
    private final ViewProxy keyboardHelpView = new ViewProxy();

    /**
     * The constructor.
     */
    public MainActivity() {
        super(R.layout.activity_main, R.menu.main_options_menu);
    }

    @Override
    protected void onCreateLocal(final @Nullable Bundle savedInstanceState) {
        apiErrorView.setDelegate(this, R.id.apiErrorView);
        apiKeyRejectedView.setDelegate(this, R.id.apiKeyRejectedView);
        keyboardHelpView.setDelegate(this, R.id.keyboardHelpView);

        LiveApiState.getInstance().observe(this, new Observer<ApiState>() {
            @Override
            public void onChanged(final ApiState t) {
                try {
                    apiErrorView.setVisibility(t == ApiState.ERROR);
                    apiKeyRejectedView.setVisibility(t == ApiState.API_KEY_REJECTED);
                } catch (final Exception e) {
                    LOGGER.uerr(e);
                }
            }
        });

        final @Nullable AvailableSessionsView availableSessionsView = findViewById(R.id.availableSessionsView);
        if (availableSessionsView != null) {
            availableSessionsView.setLifecycleOwner(this);
        }

        final @Nullable LessonReviewBreakdownView lessonReviewBreakdownView = findViewById(R.id.lessonReviewBreakdownView);
        if (lessonReviewBreakdownView != null) {
            lessonReviewBreakdownView.setLifecycleOwner(this);
        }

        final @Nullable FirstTimeSetupView firstTimeSetupView = findViewById(R.id.firstTimeSetupView);
        if (firstTimeSetupView != null) {
            firstTimeSetupView.setLifecycleOwner(this);
        }

        final @Nullable LevelDurationView levelDurationView = findViewById(R.id.levelDurationView);
        if (levelDurationView != null) {
            levelDurationView.setLifecycleOwner(this);
        }

        final @Nullable LevelProgressView levelProgressView = findViewById(R.id.levelProgressView);
        if (levelProgressView != null) {
            levelProgressView.setLifecycleOwner(this);
        }

        final @Nullable Post60ProgressView post60ProgressView = findViewById(R.id.post60ProgressView);
        if (post60ProgressView != null) {
            post60ProgressView.setLifecycleOwner(this);
        }

        final @Nullable JoyoProgressView joyoProgressView = findViewById(R.id.joyoProgressView);
        if (joyoProgressView != null) {
            joyoProgressView.setLifecycleOwner(this);
        }

        final @Nullable JlptProgressView jlptProgressView = findViewById(R.id.jlptProgressView);
        if (jlptProgressView != null) {
            jlptProgressView.setLifecycleOwner(this);
        }

        final @Nullable LiveRecentUnlocksSubjectTableView recentUnlocksView = findViewById(R.id.recentUnlocksView);
        if (recentUnlocksView != null) {
            recentUnlocksView.setLifecycleOwner(this);
        }

        final @Nullable LiveCriticalConditionSubjectTableView criticalConditionView = findViewById(R.id.criticalConditionView);
        if (criticalConditionView != null) {
            criticalConditionView.setLifecycleOwner(this);
        }

        final @Nullable LiveBurnedItemsSubjectTableView burnedItemsView = findViewById(R.id.burnedItemsView);
        if (burnedItemsView != null) {
            burnedItemsView.setLifecycleOwner(this);
        }

        final @Nullable SessionButtonsView sessionButtonsView = findViewById(R.id.sessionButtonsView);
        if (sessionButtonsView != null) {
            sessionButtonsView.setLifecycleOwner(this);
        }

        final @Nullable SrsBreakDownView srsBreakDownView = findViewById(R.id.srsBreakDownView);
        if (srsBreakDownView != null) {
            srsBreakDownView.setLifecycleOwner(this);
        }

        final @Nullable SyncProgressView syncProgressView = findViewById(R.id.syncProgressView);
        if (syncProgressView != null) {
            syncProgressView.setLifecycleOwner(this);
        }

        final @Nullable UpcomingReviewsView upcomingReviewsView = findViewById(R.id.upcomingReviewsView);
        if (upcomingReviewsView != null) {
            upcomingReviewsView.setLifecycleOwner(this);
        }

        final @Nullable TimeLineBarChart timeLineBarChart = findViewById(R.id.timeLineBarChart);
        if (timeLineBarChart != null) {
            timeLineBarChart.setLifecycleOwner(this);
        }
    }

    @Override
    protected void onResumeLocal() {
        NotificationAlarmReceiver.scheduleOrCancelAlarm();
        BackgroundSyncWorker.scheduleOrCancelWork();

        new OnResumeTask().execute();

        keyboardHelpView.setVisibility(!GlobalSettings.Tutorials.getKeyboardHelpDismissed());

        collapseSearchBox();
    }

    @Override
    protected void onPauseLocal() {
        //
    }

    @Override
    protected void enableInteractionLocal() {
        final @Nullable SessionButtonsView view = findViewById(R.id.sessionButtonsView);
        if (view != null) {
            view.enableInteraction();
        }
    }

    @Override
    protected void disableInteractionLocal() {
        final @Nullable SessionButtonsView view = findViewById(R.id.sessionButtonsView);
        if (view != null) {
            view.disableInteraction();
        }
    }

    /**
     * Handler for the API error retry button.
     *
     * @param view the button
     */
    @SuppressWarnings("MethodMayBeStatic")
    public void retryApiError(@SuppressWarnings("unused") final View view) {
        try {
            JobRunnerService.schedule(RetryApiErrorJob.class, "");
        } catch (final Exception e) {
            LOGGER.uerr(e);
        }
    }

    /**
     * Handler for the API error settings button.
     *
     * @param view the button
     */
    public void goToSettings(@SuppressWarnings("unused") final View view) {
        try {
            goToPreferencesActivity("api_settings");
        } catch (final Exception e) {
            LOGGER.uerr(e);
        }
    }

    /**
     * Handler for the keyboard help button.
     *
     * @param view the button
     */
    public void viewKeyboardHelp(@SuppressWarnings("unused") final View view) {
        try {
            goToActivity(KeyboardHelpActivity.class);
        } catch (final Exception e) {
            LOGGER.uerr(e);
        }
    }

    /**
     * Handler for dismissing the keyboard help view.
     *
     * @param view the button
     */
    public void dismissKeyboardHelp(@SuppressWarnings("unused") final View view) {
        try {
            GlobalSettings.Tutorials.setKeyboardHelpDismissed(true);
            keyboardHelpView.setVisibility(false);
        } catch (final Exception e) {
            LOGGER.uerr(e);
        }
    }

    /**
     * Handler for the start lessons button.
     *
     * @param view the button
     */
    public void startLessonSession(@SuppressWarnings("unused") final View view) {
        try {
            if (!interactionEnabled) {
                return;
            }
            disableInteraction();
            final TimeLine timeLine = LiveTimeLine.getInstance().get();
            if (timeLine.hasAvailableLessons()) {
                new StartLessonTask(this, timeLine).execute();
            }
            else {
                enableInteraction();
            }
        } catch (final Exception e) {
            LOGGER.uerr(e);
        }
    }

    /**
     * Handler for the start reviews button.
     *
     * @param view the button
     */
    public void startReviewSession(@SuppressWarnings("unused") final View view) {
        try {
            if (!interactionEnabled) {
                return;
            }
            disableInteraction();
            final TimeLine timeLine = LiveTimeLine.getInstance().get();
            if (timeLine.hasAvailableReviews()) {
                new StartReviewTask(this, timeLine).execute();
            }
            else {
                enableInteraction();
            }
        } catch (final Exception e) {
            LOGGER.uerr(e);
        }
    }

    /**
     * Handler for the resume session button.
     *
     * @param view the button
     */
    public void resumeSession(@SuppressWarnings("unused") final View view) {
        try {
            if (!interactionEnabled) {
                return;
            }
            disableInteraction();
            if (Session.getInstance().isInactive()) {
                enableInteraction();
            }
            else {
                goToActivity(SessionActivity.class);
            }
        } catch (final Exception e) {
            LOGGER.uerr(e);
        }
    }

    private static final class OnResumeTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(final Void... params) {
            try {
                LiveBurnedItems.getInstance().forceUpdate();
                LiveCriticalCondition.getInstance().forceUpdate();
                LiveLevelDuration.getInstance().forceUpdate();
                LiveLevelProgress.getInstance().forceUpdate();
                LiveRecentUnlocks.getInstance().forceUpdate();
                LiveSrsBreakDown.getInstance().forceUpdate();
                LiveTimeLine.getInstance().forceUpdate();
                LiveJoyoProgress.getInstance().forceUpdate();
                LiveJlptProgress.getInstance().forceUpdate();
                SessionWidgetProvider.checkAndUpdateWidgets();
            } catch (final Exception e) {
                LOGGER.uerr(e);
            }
            return null;
        }
    }

    private static final class StartLessonTask extends AsyncTask<Void, Void, Void> {
        private final WeakLcoRef<MainActivity> activityRef;
        private final TimeLine timeLine;

        private StartLessonTask(final MainActivity activity, final TimeLine timeLine) {
            activityRef = new WeakLcoRef<>(activity);
            this.timeLine = timeLine;
        }

        @Override
        protected Void doInBackground(final Void... params) {
            try {
                Session.getInstance().startNewLessonSession(timeLine.getAvailableLessons());
            } catch (final Exception e) {
                LOGGER.uerr(e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(final Void result) {
            try {
                activityRef.get().goToActivity(SessionActivity.class);
            } catch (final Exception e) {
                LOGGER.uerr(e);
            }
        }
    }

    private static final class StartReviewTask extends AsyncTask<Void, Void, Void> {
        private final WeakLcoRef<MainActivity> activityRef;
        private final TimeLine timeLine;

        private StartReviewTask(final MainActivity activity, final TimeLine timeLine) {
            activityRef = new WeakLcoRef<>(activity);
            this.timeLine = timeLine;
        }

        @Override
        protected Void doInBackground(final Void... params) {
            try {
                Session.getInstance().startNewReviewSession(timeLine.getAvailableReviews());
            } catch (final Exception e) {
                LOGGER.uerr(e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(final Void result) {
            try {
                activityRef.get().goToActivity(SessionActivity.class);
            } catch (final Exception e) {
                LOGGER.uerr(e);
            }
        }
    }
}