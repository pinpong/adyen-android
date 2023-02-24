/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by josephj on 24/8/2022.
 */

package com.adyen.checkout.components.core.internal

import androidx.annotation.RestrictTo
import androidx.lifecycle.SavedStateHandle
import com.adyen.checkout.core.internal.util.LogUtil

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class PaymentDataRepository(
    private val savedStateHandle: SavedStateHandle,
) {
    var paymentData: String?
        get() = savedStateHandle.get(PAYMENT_DATA_KEY)
        set(paymentData) {
            savedStateHandle.set(PAYMENT_DATA_KEY, paymentData)
        }

    companion object {
        private val TAG = LogUtil.getTag()
        private const val PAYMENT_DATA_KEY = "payment_data"
    }
}
