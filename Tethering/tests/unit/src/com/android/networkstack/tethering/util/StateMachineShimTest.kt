/**
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
package com.android.networkstack.tethering.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.util.State
import com.android.net.module.util.SyncStateMachine
import com.android.net.module.util.SyncStateMachine.StateInfo
import com.android.networkstack.tethering.util.StateMachineShim.Dependencies
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoMoreInteractions

@RunWith(AndroidJUnit4::class)
@SmallTest
class StateMachineShimTest {
    private val mSyncSM = mock(SyncStateMachine::class.java)
    private val mState1 = mock(State::class.java)
    private val mState2 = mock(State::class.java)

    inner class MyDependencies() : Dependencies() {

        override fun makeSyncStateMachine(name: String, thread: Thread) = mSyncSM
    }

    @Test
    fun testUsingSyncStateMachine() {
        val inOrder = inOrder(mSyncSM)
        val shim = StateMachineShim("ShimTest", MyDependencies())
        shim.start(mState1)
        inOrder.verify(mSyncSM).start(mState1)

        val allStates = ArrayList<StateInfo>()
        allStates.add(StateInfo(mState1, null))
        allStates.add(StateInfo(mState2, mState1))
        shim.addAllStates(allStates)
        inOrder.verify(mSyncSM).addAllStates(allStates)

        shim.transitionTo(mState1)
        inOrder.verify(mSyncSM).transitionTo(mState1)

        val what = 10
        shim.sendMessage(what)
        inOrder.verify(mSyncSM).processMessage(what, 0, 0, null)
        val obj = Object()
        shim.sendMessage(what, obj)
        inOrder.verify(mSyncSM).processMessage(what, 0, 0, obj)
        val arg1 = 11
        shim.sendMessage(what, arg1)
        inOrder.verify(mSyncSM).processMessage(what, arg1, 0, null)
        val arg2 = 12
        shim.sendMessage(what, arg1, arg2, obj)
        inOrder.verify(mSyncSM).processMessage(what, arg1, arg2, obj)

        shim.sendSelfMessageToSyncSM(what, obj)
        inOrder.verify(mSyncSM).sendSelfMessage(what, 0, 0, obj)

        verifyNoMoreInteractions(mSyncSM)
    }
}
