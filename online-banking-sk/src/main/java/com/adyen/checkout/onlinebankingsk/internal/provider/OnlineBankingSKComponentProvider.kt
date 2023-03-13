/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by atef on 23/9/2022.
 */

package com.adyen.checkout.onlinebankingsk.internal.provider

import androidx.annotation.RestrictTo
import com.adyen.checkout.action.internal.DefaultActionHandlingComponent
import com.adyen.checkout.action.internal.ui.GenericActionDelegate
import com.adyen.checkout.components.core.PaymentComponentData
import com.adyen.checkout.components.core.internal.ComponentEventHandler
import com.adyen.checkout.components.core.internal.ui.model.ComponentParams
import com.adyen.checkout.components.core.paymentmethod.OnlineBankingSKPaymentMethod
import com.adyen.checkout.onlinebankingcore.internal.provider.OnlineBankingComponentProvider
import com.adyen.checkout.onlinebankingcore.internal.ui.OnlineBankingDelegate
import com.adyen.checkout.onlinebankingsk.OnlineBankingSKComponent
import com.adyen.checkout.onlinebankingsk.OnlineBankingSKComponentState
import com.adyen.checkout.onlinebankingsk.OnlineBankingSKConfiguration
import com.adyen.checkout.components.core.internal.ui.model.SessionParams

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class OnlineBankingSKComponentProvider(
    overrideComponentParams: ComponentParams? = null,
    overrideSessionParams: SessionParams? = null,
) : OnlineBankingComponentProvider<
    OnlineBankingSKComponent,
    OnlineBankingSKConfiguration,
    OnlineBankingSKPaymentMethod,
    OnlineBankingSKComponentState>(
    componentClass = OnlineBankingSKComponent::class.java,
    overrideComponentParams = overrideComponentParams
) {

    override fun createPaymentMethod(): OnlineBankingSKPaymentMethod {
        return OnlineBankingSKPaymentMethod()
    }

    override fun getSupportedPaymentMethods(): List<String> {
        return OnlineBankingSKComponent.PAYMENT_METHOD_TYPES
    }

    override fun getTermsAndConditionsUrl(): String {
        return OnlineBankingSKComponent.TERMS_CONDITIONS_URL
    }

    override fun createComponentState(
        data: PaymentComponentData<OnlineBankingSKPaymentMethod>,
        isInputValid: Boolean,
        isReady: Boolean
    ) = OnlineBankingSKComponentState(data, isInputValid, isReady)

    override fun createComponent(
        delegate: OnlineBankingDelegate<OnlineBankingSKPaymentMethod, OnlineBankingSKComponentState>,
        genericActionDelegate: GenericActionDelegate,
        actionHandlingComponent: DefaultActionHandlingComponent,
        componentEventHandler: ComponentEventHandler<OnlineBankingSKComponentState>
    ): OnlineBankingSKComponent {
        return OnlineBankingSKComponent(
            delegate = delegate,
            genericActionDelegate = genericActionDelegate,
            actionHandlingComponent = actionHandlingComponent,
            componentEventHandler = componentEventHandler
        )
    }
}
