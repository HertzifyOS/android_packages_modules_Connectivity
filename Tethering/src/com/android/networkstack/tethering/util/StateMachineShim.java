/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.networkstack.tethering.util;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.State;
import com.android.net.module.util.SyncStateMachine;
import com.android.net.module.util.SyncStateMachine.StateInfo;

import java.util.List;

/** A wrapper class that uses synchronous state machine for tethering. */
public class StateMachineShim {
    private final SyncStateMachine mSyncSM;

    /**
     * Create a StateMachineShim with the given name.
     *
     * @param name The name of the state machine.
     */
    public StateMachineShim(final String name) {
        this(name, new Dependencies());
    }

    @VisibleForTesting
    public StateMachineShim(final String name, final Dependencies deps) {
        mSyncSM = deps.makeSyncStateMachine(name, Thread.currentThread());
    }

    /** A dependencies class which used for testing injection. */
    @VisibleForTesting
    public static class Dependencies {
        /** Create SyncSM instance, for injection. */
        public SyncStateMachine makeSyncStateMachine(final String name, final Thread thread) {
            return new SyncStateMachine(name, thread);
        }
    }

    /** Start the state machine */
    public void start(final State initialState) {
        mSyncSM.start(initialState);
    }

    /** Add states to state machine. */
    public void addAllStates(final List<StateInfo> stateInfos) {
        mSyncSM.addAllStates(stateInfos);
    }

    /**
     * Transition to given state.
     *
     * SyncSM doesn't allow this be called during state transition (#enter() or #exit() methods),
     * or multiple times while processing a single message.
     */
    public void transitionTo(final State state) {
        mSyncSM.transitionTo(state);
    }

    /** Send message to state machine. */
    public void sendMessage(int what) {
        sendMessage(what, 0, 0, null);
    }

    /** Send message to state machine. */
    public void sendMessage(int what, Object obj) {
        sendMessage(what, 0, 0, obj);
    }

    /** Send message to state machine. */
    public void sendMessage(int what, int arg1) {
        sendMessage(what, arg1, 0, null);
    }

    /**
     * Send message to state machine.
     */
    public void sendMessage(int what, int arg1, int arg2, Object obj) {
        mSyncSM.processMessage(what, arg1, arg2, obj);
    }

    /**
     * Send self message to the synchronous state machine.
     */
    public void sendSelfMessageToSyncSM(final int what, final Object obj) {
        mSyncSM.sendSelfMessage(what, 0, 0, obj);
    }
}
