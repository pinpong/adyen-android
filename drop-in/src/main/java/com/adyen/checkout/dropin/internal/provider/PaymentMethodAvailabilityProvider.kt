/*
 * Copyright (c) 2023 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by oscars on 8/12/2023.
 */

package com.adyen.checkout.dropin.internal.provider

import android.app.Application
import com.adyen.checkout.components.core.Amount
import com.adyen.checkout.components.core.CheckoutConfiguration
import com.adyen.checkout.components.core.ComponentAvailableCallback
import com.adyen.checkout.components.core.PaymentMethod
import com.adyen.checkout.components.core.PaymentMethodTypes
import com.adyen.checkout.components.core.internal.AlwaysAvailablePaymentMethod
import com.adyen.checkout.components.core.internal.NotAvailablePaymentMethod
import com.adyen.checkout.components.core.internal.PaymentMethodAvailabilityCheck
import com.adyen.checkout.core.AdyenLogLevel
import com.adyen.checkout.core.exception.CheckoutException
import com.adyen.checkout.core.internal.util.adyenLog
import com.adyen.checkout.core.internal.util.runCompileOnly
import com.adyen.checkout.dropin.internal.ui.model.DropInOverrideParamsFactory
import com.adyen.checkout.googlepay.internal.provider.GooglePayComponentProvider
import com.adyen.checkout.sessions.core.internal.data.model.SessionDetails
import com.adyen.checkout.wechatpay.WeChatPayProvider

@Suppress("LongParameterList")
internal fun checkPaymentMethodAvailability(
    application: Application,
    paymentMethod: PaymentMethod,
    checkoutConfiguration: CheckoutConfiguration,
    overrideAmount: Amount?,
    sessionDetails: SessionDetails?,
    callback: ComponentAvailableCallback,
) {
    try {
        adyenLog(AdyenLogLevel.VERBOSE, "checkPaymentMethodAvailability") {
            "Checking availability for type - ${paymentMethod.type}"
        }

        val type = paymentMethod.type ?: throw CheckoutException("PaymentMethod type is null")

        val availabilityCheck = getPaymentMethodAvailabilityCheck(type, overrideAmount, sessionDetails)

        availabilityCheck.isAvailable(application, paymentMethod, checkoutConfiguration, callback)
    } catch (e: CheckoutException) {
        adyenLog(AdyenLogLevel.ERROR, "checkPaymentMethodAvailability", e) {
            "Unable to initiate ${paymentMethod.type}"
        }
        callback.onAvailabilityResult(false, paymentMethod)
    }
}

/**
 * Provides the [PaymentMethodAvailabilityCheck] class for the specified [paymentMethodType], if available.
 */
private fun getPaymentMethodAvailabilityCheck(
    paymentMethodType: String,
    overrideAmount: Amount?,
    sessionDetails: SessionDetails?,
): PaymentMethodAvailabilityCheck<*> {
    val availabilityCheck = when (paymentMethodType) {
        PaymentMethodTypes.GOOGLE_PAY,
        PaymentMethodTypes.GOOGLE_PAY_LEGACY -> runCompileOnly {
            val dropInOverrideParams = DropInOverrideParamsFactory.create(overrideAmount, sessionDetails)
            GooglePayComponentProvider(dropInOverrideParams)
        }

        PaymentMethodTypes.WECHAT_PAY_SDK -> runCompileOnly { WeChatPayProvider() }
        else -> AlwaysAvailablePaymentMethod()
    }

    return availabilityCheck ?: NotAvailablePaymentMethod()
}
