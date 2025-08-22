/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.net;

import android.annotation.FlaggedApi;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;

import com.android.tethering.flags.Flags;

/**
 * Tracks UIDs of applications actively in calls and notifies ConnectivityService.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
@FlaggedApi(Flags.FLAG_ENABLE_INCALL_SERVICE_API)
public class ConnectivityCallListenerService extends InCallService {
    private static final String TAG = "ConnCallListenerSvc";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    @Override
    public void onCreate() {
        super.onCreate();
        if (DBG) Log.d(TAG, "onCreate() called");
    }

    @Override
    public void onCallAdded(@Nullable Call call) {
        super.onCallAdded(call);
        if (DBG) Log.d(TAG, "onCallAdded called");
    }

    @Override
    public void onCallRemoved(@Nullable Call call) {
        super.onCallRemoved(call);
        if (DBG) Log.d(TAG, "onCallRemoved called");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DBG) Log.d(TAG, "onDestroy() called");
    }
}
