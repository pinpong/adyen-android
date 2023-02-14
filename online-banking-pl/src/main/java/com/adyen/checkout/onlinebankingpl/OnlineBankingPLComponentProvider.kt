/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by atef on 10/8/2022.
 */

package com.adyen.checkout.onlinebankingpl

import androidx.annotation.RestrictTo
import com.adyen.checkout.action.DefaultActionHandlingComponent
import com.adyen.checkout.action.GenericActionDelegate
import com.adyen.checkout.components.PaymentComponentState
import com.adyen.checkout.components.base.ComponentEventHandler
import com.adyen.checkout.components.base.ComponentParams
import com.adyen.checkout.components.model.payments.request.OnlineBankingPLPaymentMethod
import com.adyen.checkout.issuerlist.IssuerListComponentProvider
import com.adyen.checkout.issuerlist.IssuerListDelegate
import com.adyen.checkout.sessions.model.setup.SessionSetupConfiguration

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class OnlineBankingPLComponentProvider(
    overrideComponentParams: ComponentParams? = null,
    private val sessionSetupConfiguration: SessionSetupConfiguration? = null
) : IssuerListComponentProvider<OnlineBankingPLComponent, OnlineBankingPLConfiguration, OnlineBankingPLPaymentMethod>(
    componentClass = OnlineBankingPLComponent::class.java,
    overrideComponentParams = overrideComponentParams,
) {

    override fun createComponent(
        delegate: IssuerListDelegate<OnlineBankingPLPaymentMethod>,
        genericActionDelegate: GenericActionDelegate,
        actionHandlingComponent: DefaultActionHandlingComponent,
        componentEventHandler: ComponentEventHandler<PaymentComponentState<OnlineBankingPLPaymentMethod>>
    ) = OnlineBankingPLComponent(
        delegate = delegate,
        genericActionDelegate = genericActionDelegate,
        actionHandlingComponent = actionHandlingComponent,
        componentEventHandler = componentEventHandler,
    )

    override fun createPaymentMethod() = OnlineBankingPLPaymentMethod()

    override fun getSupportedPaymentMethods(): List<String> = OnlineBankingPLComponent.PAYMENT_METHOD_TYPES
}
