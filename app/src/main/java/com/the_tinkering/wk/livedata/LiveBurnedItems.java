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
import com.the_tinkering.wk.model.SrsSystemRepository;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.the_tinkering.wk.Constants.MONTH;

/**
 * LiveData that records up to the last 10 items burned in the last 30 days.
 */
public final class LiveBurnedItems extends ConservativeLiveData<List<Subject>> {
    /**
     * The singleton instance.
     */
    private static final LiveBurnedItems instance = new LiveBurnedItems();

    /**
     * Get the singleton instance.
     *
     * @return the instance
     */
    public static LiveBurnedItems getInstance() {
        return instance;
    }

    /**
     * Private constructor.
     */
    private LiveBurnedItems() {
        //
    }

    @Override
    protected void updateLocal() {
        if (GlobalSettings.Dashboard.getShowBurnedItems() || hasNullValue()) {
            final AppDatabase db = WkApplication.getDatabase();
            final Date cutoff = new Date(System.currentTimeMillis() - MONTH);
            final List<Subject> items = db.subjectCollectionsDao().getBurnedItems(SrsSystemRepository.getBurnedFilter(), cutoff);
            instance.postValue(items);
        }
        else {
            ping();
        }
    }

    @Override
    public List<Subject> getDefaultValue() {
        return Collections.emptyList();
    }
}