/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by oscars on 4/8/2022.
 */

package com.adyen.checkout.components.status

import com.adyen.checkout.components.status.api.StatusResponseUtils
import com.adyen.checkout.components.status.api.StatusService
import com.adyen.checkout.components.status.model.StatusRequest
import com.adyen.checkout.components.status.model.StatusResponse
import com.adyen.checkout.core.util.runSuspendCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class StatusRepository constructor(
    private val statusService: StatusService,
    private val clientKey: String,
) {

    private var delay: Long = 0

    fun poll(paymentData: String): Flow<Result<StatusResponse>> {
        val startTime = System.currentTimeMillis()

        return flow {
            while (currentCoroutineContext().isActive) {
                val result = withContext(Dispatchers.IO) {
                    runSuspendCatching {
                        statusService.checkStatus(clientKey, StatusRequest(paymentData))
                    }
                }
                emit(result)

                if (result.isSuccess && StatusResponseUtils.isFinalResult(result.getOrThrow()))
                    currentCoroutineContext().cancel()

                if (!updateDelay(startTime)) currentCoroutineContext().cancel()

                delay(delay)
            }
        }
    }

    private fun updateDelay(startTime: Long): Boolean {
        val elapsedTime = System.currentTimeMillis() - startTime
        return when {
            elapsedTime <= POLLING_THRESHOLD -> {
                delay = POLLING_DELAY_FAST
                true
            }
            elapsedTime <= MAX_POLLING_DURATION_MILLIS -> {
                delay = POLLING_DELAY_SLOW
                true
            }
            else -> false
        }
    }

    companion object {
        private val MAX_POLLING_DURATION_MILLIS = TimeUnit.MINUTES.toMillis(15)
        private val POLLING_DELAY_FAST = TimeUnit.SECONDS.toMillis(2)
        private val POLLING_DELAY_SLOW = TimeUnit.SECONDS.toMillis(10)
        private val POLLING_THRESHOLD = TimeUnit.SECONDS.toMillis(60)
    }
}
