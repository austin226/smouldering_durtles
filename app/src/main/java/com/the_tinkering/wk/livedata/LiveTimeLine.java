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

package com.the_tinkering.wk.livedata;

import com.the_tinkering.wk.GlobalSettings;
import com.the_tinkering.wk.WkApplication;
import com.the_tinkering.wk.db.AppDatabase;
import com.the_tinkering.wk.db.model.Subject;
import com.the_tinkering.wk.model.TimeLine;
import com.the_tinkering.wk.services.NotificationAlarmReceiver;
import com.the_tinkering.wk.util.AudioUtil;
import com.the_tinkering.wk.util.PitchInfoUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import javax.annotation.Nullable;

import static com.the_tinkering.wk.Constants.DAY;
import static com.the_tinkering.wk.Constants.HOUR;

/**
 * LiveData that tracks available and uncoming lessons and reviews, for the dashboard.
 */
public final class LiveTimeLine extends ConservativeLiveData<TimeLine> {
    /**
     * The singleton instance.
     */
    private static final LiveTimeLine instance = new LiveTimeLine();

    /**
     * Get the singleton instance.
     *
     * @return the instance
     */
    public static LiveTimeLine getInstance() {
        return instance;
    }

    /**
     * Private constructor.
     */
    private LiveTimeLine() {
        //
    }

    @Override
    protected void updateLocal() {
        final AppDatabase db = WkApplication.getDatabase();
        final int maxLevel = db.propertiesDao().getUserMaxLevelGranted();
        final int userLevel = db.propertiesDao().getUserLevel();
        final boolean vacationMode = db.propertiesDao().getVacationMode();

        final Collection<Long> levelUpIds = db.subjectCollectionsDao().getLevelUpIds(userLevel, maxLevel);

        final int size = GlobalSettings.Dashboard.getTimeLineChartSize();
        final TimeLine timeLine = new TimeLine(size);
        final Collection<Subject> scanSubjects = new ArrayList<>();

        if (!vacationMode) {
            final Collection<Subject> lessonSubjects = db.subjectCollectionsDao().getAvailableLessonItems(maxLevel, userLevel);
            for (final Subject subject: lessonSubjects) {
                timeLine.addLesson(subject);
                scanSubjects.add(subject);
            }

            final long ahead = size * HOUR;
            final Date cutoff = new Date(System.currentTimeMillis() + ahead);
            final Collection<Subject> reviewSubjects = db.subjectCollectionsDao().getUpcomingReviewItems(maxLevel, userLevel, cutoff);
            for (final Subject subject: reviewSubjects) {
                timeLine.addReview(subject, !subject.isPassed() && levelUpIds.contains(subject.getId()));
                scanSubjects.add(subject);
            }

            final @Nullable Date longDate = db.subjectAggregatesDao().getNextLongTermReviewDate(maxLevel, userLevel, cutoff);
            timeLine.setLongTermUpcomingReviewDate(longDate);
            if (longDate == null) {
                timeLine.setNumLongTermUpcomingReviews(0);
            }
            else {
                timeLine.setNumLongTermUpcomingReviews(db.subjectAggregatesDao().getNextLongTermReviewCount(maxLevel, userLevel, longDate));
            }
        }

        instance.postValue(timeLine);

        if (db.propertiesDao().getNotificationSet() && !timeLine.hasAvailableLessons() && !timeLine.hasAvailableReviews()) {
            NotificationAlarmReceiver.cancelNotification();
        }

        if (GlobalSettings.Api.getAutoDownloadAudio()) {
            final @Nullable Date lastAudioScanDate = db.propertiesDao().getLastAudioScanDate();
            if (lastAudioScanDate == null || System.currentTimeMillis() - lastAudioScanDate.getTime() > DAY/2) {
                scanSubjects.addAll(db.subjectCollectionsDao().getByLevelRange(userLevel, userLevel));
                AudioUtil.scheduleDownloadTasks(scanSubjects, 100);
                db.propertiesDao().setLastAudioScanDate(new Date());
            }
        }

        if (GlobalSettings.Display.getShowPitchInfo()) {
            final @Nullable Date lastPitchInfoScanDate = db.propertiesDao().getLastPitchInfoScanDate();
            if (lastPitchInfoScanDate == null || System.currentTimeMillis() - lastPitchInfoScanDate.getTime() > DAY/2) {
                PitchInfoUtil.scheduleDownloadTasks(100);
                db.propertiesDao().setLastPitchInfoScanDate(new Date());
            }
        }
    }

    @Override
    public TimeLine getDefaultValue() {
        return new TimeLine(24);
    }
}