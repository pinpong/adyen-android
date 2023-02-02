/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by caiof on 12/4/2022.
 */

package com.adyen.checkout.eps

import androidx.annotation.RestrictTo
import com.adyen.checkout.action.DefaultActionHandlingComponent
import com.adyen.checkout.action.GenericActionDelegate
import com.adyen.checkout.components.PaymentComponentState
import com.adyen.checkout.components.base.ComponentEventHandler
import com.adyen.checkout.components.base.ComponentParams
import com.adyen.checkout.components.model.payments.request.EPSPaymentMethod
import com.adyen.checkout.issuerlist.IssuerListComponentProvider
import com.adyen.checkout.issuerlist.IssuerListDelegate

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class EPSComponentProvider(
    overrideComponentParams: ComponentParams? = null,
) : IssuerListComponentProvider<EPSComponent, EPSConfiguration, EPSPaymentMethod>(
    componentClass = EPSComponent::class.java,
    overrideComponentParams = overrideComponentParams,
    hideIssuerLogosDefaultValue = true
) {

    override fun createComponent(
        delegate: IssuerListDelegate<EPSPaymentMethod>,
        genericActionDelegate: GenericActionDelegate,
        actionHandlingComponent: DefaultActionHandlingComponent,
        componentEventHandler: ComponentEventHandler<PaymentComponentState<EPSPaymentMethod>>
    ) = EPSComponent(
        delegate = delegate,
        genericActionDelegate = genericActionDelegate,
        actionHandlingComponent = actionHandlingComponent,
        componentEventHandler = componentEventHandler,
    )

    override fun createPaymentMethod() = EPSPaymentMethod()

    override fun getSupportedPaymentMethods(): List<String> = EPSComponent.PAYMENT_METHOD_TYPES
}
