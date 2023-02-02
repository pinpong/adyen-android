/*
 * Copyright (c) 2023 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by josephj on 13/1/2023.
 */

package com.adyen.checkout.components.base

import androidx.annotation.RestrictTo
import com.adyen.checkout.components.PaymentComponentEvent
import com.adyen.checkout.components.PaymentComponentState
import com.adyen.checkout.core.exception.CheckoutException
import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.log.Logger
import kotlinx.coroutines.CoroutineScope

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class DefaultComponentEventHandler<T : PaymentComponentState<*>> : ComponentEventHandler<T> {

    // no ops
    override fun initialize(coroutineScope: CoroutineScope) = Unit

    // no ops
    override fun onCleared() = Unit

    override fun onPaymentComponentEvent(event: PaymentComponentEvent<T>, componentCallback: BaseComponentCallback) {
        @Suppress("UNCHECKED_CAST")
        val callback = componentCallback as? ComponentCallback<T>
            ?: throw CheckoutException("Callback must be type of ${ComponentCallback::class.java.canonicalName}")
        Logger.v(TAG, "Event received $event")
        when (event) {
            is PaymentComponentEvent.ActionDetails -> callback.onAdditionalDetails(event.data)
            is PaymentComponentEvent.Error -> callback.onError(event.error)
            is PaymentComponentEvent.StateChanged -> callback.onStateChanged(event.state)
            is PaymentComponentEvent.Submit -> callback.onSubmit(event.state)
        }
    }

    companion object {
        private val TAG = LogUtil.getTag()
    }
}
