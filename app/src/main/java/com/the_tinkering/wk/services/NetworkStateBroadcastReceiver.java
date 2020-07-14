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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.the_tinkering.wk.jobs.NetworkStateChangedJob;
import com.the_tinkering.wk.util.Logger;

/**
 * A receiver that listens for network state change events. This triggers a background job
 * that can kick off API tasks if needed.
 */
public final class NetworkStateBroadcastReceiver extends BroadcastReceiver {
    private static final Logger LOGGER = Logger.get(NetworkStateBroadcastReceiver.class);

    @Override
    public void onReceive(final Context context, final Intent intent) {
        try {
            JobRunnerService.schedule(NetworkStateChangedJob.class, "");
        } catch (final Exception e) {
            LOGGER.uerr(e);
        }
    }
}