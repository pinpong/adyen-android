/*
 * Copyright (c) 2021 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by ozgur on 29/11/2021.
 */

package com.adyen.checkout.voucher

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.adyen.checkout.components.ActionComponentData
import com.adyen.checkout.components.ActionComponentProvider
import com.adyen.checkout.components.ViewableComponent
import com.adyen.checkout.components.base.BaseActionComponent
import com.adyen.checkout.components.model.payments.response.Action
import com.adyen.checkout.components.model.payments.response.VoucherAction
import com.adyen.checkout.core.exception.ComponentException

class VoucherComponent(
    savedStateHandle: SavedStateHandle,
    application: Application,
    configuration: VoucherConfiguration,
    private val voucherDelegate: VoucherDelegate,
) : BaseActionComponent<VoucherConfiguration>(savedStateHandle, application, configuration),
    ViewableComponent<VoucherConfiguration, ActionComponentData> {

    override fun canHandleAction(action: Action): Boolean {
        return PROVIDER.canHandleAction(action)
    }

    override fun sendAnalyticsEvent(context: Context) {
        // no ops
    }

    override fun handleActionInternal(action: Action, activity: Activity) {
        if (action !is VoucherAction) {
            notifyException(ComponentException("Unsupported action"))
            return
        }
        voucherDelegate.handleAction(action, activity)
    }

    companion object {
        @JvmField
        val PROVIDER: ActionComponentProvider<VoucherComponent, VoucherConfiguration, VoucherDelegate> =
            VoucherComponentProvider()
    }
}
