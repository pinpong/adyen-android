/*
 * Copyright (c) 2020 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by josephj on 4/12/2020.
 */
package com.adyen.checkout.blik

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.adyen.checkout.components.PaymentComponentState
import com.adyen.checkout.components.StoredPaymentComponentProvider
import com.adyen.checkout.components.base.BasePaymentComponent
import com.adyen.checkout.components.base.GenericPaymentMethodDelegate
import com.adyen.checkout.components.base.PaymentMethodDelegateOld
import com.adyen.checkout.components.model.payments.request.BlikPaymentMethod
import com.adyen.checkout.components.util.PaymentMethodTypes
import com.adyen.checkout.core.log.LogUtil.getTag
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class BlikComponent(
    savedStateHandle: SavedStateHandle,
    paymentMethodDelegate: PaymentMethodDelegateOld,
    private val blikDelegate: BlikDelegate,
    configuration: BlikConfiguration
) : BasePaymentComponent<
    BlikConfiguration,
    BlikInputData,
    BlikOutputData,
    PaymentComponentState<BlikPaymentMethod>>(savedStateHandle, paymentMethodDelegate, configuration) {

    override val inputData: BlikInputData = BlikInputData()

    init {
        observeOutputData()
        observeComponentState()
    }

    override fun requiresInput(): Boolean {
        return paymentMethodDelegate is GenericPaymentMethodDelegate
    }

    override fun onInputDataChanged(inputData: BlikInputData) {
        blikDelegate.onInputDataChanged(inputData)
    }

    private fun observeOutputData() {
        blikDelegate.outputDataFlow
            .filterNotNull()
            .onEach { notifyOutputDataChanged(it) }
            .launchIn(viewModelScope)
    }

    private fun observeComponentState() {
        blikDelegate.componentStateFlow
            .filterNotNull()
            .onEach { notifyStateChanged(it) }
            .launchIn(viewModelScope)
    }

    override fun getSupportedPaymentMethodTypes(): Array<String> = PAYMENT_METHOD_TYPES

    companion object {
        private val TAG = getTag()

        @JvmField
        val PROVIDER: StoredPaymentComponentProvider<BlikComponent, BlikConfiguration> = BlikComponentProvider()
        val PAYMENT_METHOD_TYPES = arrayOf(PaymentMethodTypes.BLIK)
    }
}
