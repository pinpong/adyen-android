/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by caiof on 12/4/2022.
 */

package com.adyen.checkout.bacs.internal.provider

import android.app.Application
import androidx.annotation.RestrictTo
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.adyen.checkout.action.core.internal.DefaultActionHandlingComponent
import com.adyen.checkout.action.core.internal.provider.GenericActionComponentProvider
import com.adyen.checkout.bacs.BacsDirectDebitComponent
import com.adyen.checkout.bacs.BacsDirectDebitComponentState
import com.adyen.checkout.bacs.BacsDirectDebitConfiguration
import com.adyen.checkout.bacs.internal.ui.DefaultBacsDirectDebitDelegate
import com.adyen.checkout.components.core.ComponentCallback
import com.adyen.checkout.components.core.Order
import com.adyen.checkout.components.core.PaymentMethod
import com.adyen.checkout.components.core.internal.DefaultComponentEventHandler
import com.adyen.checkout.components.core.internal.PaymentObserverRepository
import com.adyen.checkout.components.core.internal.data.api.AnalyticsMapper
import com.adyen.checkout.components.core.internal.data.api.AnalyticsService
import com.adyen.checkout.components.core.internal.data.api.DefaultAnalyticsRepository
import com.adyen.checkout.components.core.internal.data.model.AnalyticsSource
import com.adyen.checkout.components.core.internal.provider.PaymentComponentProvider
import com.adyen.checkout.components.core.internal.ui.model.ButtonComponentParamsMapper
import com.adyen.checkout.components.core.internal.ui.model.ComponentParams
import com.adyen.checkout.components.core.internal.ui.model.SessionParams
import com.adyen.checkout.components.core.internal.util.get
import com.adyen.checkout.components.core.internal.util.viewModelFactory
import com.adyen.checkout.core.exception.ComponentException
import com.adyen.checkout.core.internal.data.api.HttpClientFactory
import com.adyen.checkout.sessions.core.CheckoutSession
import com.adyen.checkout.sessions.core.SessionComponentCallback
import com.adyen.checkout.sessions.core.internal.SessionComponentEventHandler
import com.adyen.checkout.sessions.core.internal.SessionInteractor
import com.adyen.checkout.sessions.core.internal.SessionSavedStateHandleContainer
import com.adyen.checkout.sessions.core.internal.data.api.SessionRepository
import com.adyen.checkout.sessions.core.internal.data.api.SessionService
import com.adyen.checkout.sessions.core.internal.provider.SessionPaymentComponentProvider
import com.adyen.checkout.sessions.core.internal.ui.model.SessionParamsFactory
import com.adyen.checkout.ui.core.internal.ui.SubmitHandler

class BacsDirectDebitComponentProvider
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
constructor(
    overrideComponentParams: ComponentParams? = null,
    overrideSessionParams: SessionParams? = null,
) :
    PaymentComponentProvider<
        BacsDirectDebitComponent,
        BacsDirectDebitConfiguration,
        BacsDirectDebitComponentState,
        ComponentCallback<BacsDirectDebitComponentState>
        >,
    SessionPaymentComponentProvider<
        BacsDirectDebitComponent,
        BacsDirectDebitConfiguration,
        BacsDirectDebitComponentState,
        SessionComponentCallback<BacsDirectDebitComponentState>
        > {

    private val componentParamsMapper = ButtonComponentParamsMapper(overrideComponentParams, overrideSessionParams)

    override fun get(
        savedStateRegistryOwner: SavedStateRegistryOwner,
        viewModelStoreOwner: ViewModelStoreOwner,
        lifecycleOwner: LifecycleOwner,
        paymentMethod: PaymentMethod,
        configuration: BacsDirectDebitConfiguration,
        application: Application,
        componentCallback: ComponentCallback<BacsDirectDebitComponentState>,
        order: Order?,
        key: String?
    ): BacsDirectDebitComponent {
        assertSupported(paymentMethod)

        val genericFactory = viewModelFactory(savedStateRegistryOwner, null) { savedStateHandle ->
            val componentParams = componentParamsMapper.mapToParams(configuration, null)
            val analyticsService = AnalyticsService(
                HttpClientFactory.getAnalyticsHttpClient(componentParams.environment)
            )
            val analyticsRepository = DefaultAnalyticsRepository(
                packageName = application.packageName,
                locale = componentParams.shopperLocale,
                source = AnalyticsSource.PaymentComponent(componentParams.isCreatedByDropIn, paymentMethod),
                analyticsService = analyticsService,
                analyticsMapper = AnalyticsMapper(),
                clientKey = componentParams.clientKey,
            )

            val bacsDelegate = DefaultBacsDirectDebitDelegate(
                observerRepository = PaymentObserverRepository(),
                componentParams = componentParams,
                paymentMethod = paymentMethod,
                order = order,
                analyticsRepository = analyticsRepository,
                submitHandler = SubmitHandler(savedStateHandle),
            )

            val genericActionDelegate = GenericActionComponentProvider(componentParams).getDelegate(
                configuration = configuration.genericActionConfiguration,
                savedStateHandle = savedStateHandle,
                application = application,
            )

            BacsDirectDebitComponent(
                bacsDelegate = bacsDelegate,
                genericActionDelegate = genericActionDelegate,
                actionHandlingComponent = DefaultActionHandlingComponent(genericActionDelegate, bacsDelegate),
                componentEventHandler = DefaultComponentEventHandler()
            )
        }

        return ViewModelProvider(viewModelStoreOwner, genericFactory)[key, BacsDirectDebitComponent::class.java]
            .also { component ->
                component.observe(lifecycleOwner) {
                    component.componentEventHandler.onPaymentComponentEvent(it, componentCallback)
                }
            }
    }

    override fun get(
        savedStateRegistryOwner: SavedStateRegistryOwner,
        viewModelStoreOwner: ViewModelStoreOwner,
        lifecycleOwner: LifecycleOwner,
        checkoutSession: CheckoutSession,
        paymentMethod: PaymentMethod,
        configuration: BacsDirectDebitConfiguration,
        application: Application,
        componentCallback: SessionComponentCallback<BacsDirectDebitComponentState>,
        key: String?
    ): BacsDirectDebitComponent {
        assertSupported(paymentMethod)

        val genericFactory = viewModelFactory(savedStateRegistryOwner, null) { savedStateHandle ->
            val componentParams = componentParamsMapper.mapToParams(
                configuration = configuration,
                sessionParams = SessionParamsFactory.create(checkoutSession)
            )
            val httpClient = HttpClientFactory.getHttpClient(componentParams.environment)
            val analyticsService = AnalyticsService(
                HttpClientFactory.getAnalyticsHttpClient(componentParams.environment)
            )
            val analyticsRepository = DefaultAnalyticsRepository(
                packageName = application.packageName,
                locale = componentParams.shopperLocale,
                source = AnalyticsSource.PaymentComponent(componentParams.isCreatedByDropIn, paymentMethod),
                analyticsService = analyticsService,
                analyticsMapper = AnalyticsMapper(),
                clientKey = componentParams.clientKey,
            )

            val bacsDelegate = DefaultBacsDirectDebitDelegate(
                observerRepository = PaymentObserverRepository(),
                componentParams = componentParams,
                paymentMethod = paymentMethod,
                order = checkoutSession.order,
                analyticsRepository = analyticsRepository,
                submitHandler = SubmitHandler(savedStateHandle),
            )

            val genericActionDelegate = GenericActionComponentProvider(componentParams).getDelegate(
                configuration = configuration.genericActionConfiguration,
                savedStateHandle = savedStateHandle,
                application = application,
            )

            val sessionSavedStateHandleContainer = SessionSavedStateHandleContainer(
                savedStateHandle = savedStateHandle,
                checkoutSession = checkoutSession,
            )
            val sessionInteractor = SessionInteractor(
                sessionRepository = SessionRepository(
                    sessionService = SessionService(httpClient),
                    clientKey = componentParams.clientKey,
                ),
                sessionModel = sessionSavedStateHandleContainer.getSessionModel(),
                isFlowTakenOver = sessionSavedStateHandleContainer.isFlowTakenOver ?: false
            )
            val sessionComponentEventHandler = SessionComponentEventHandler<BacsDirectDebitComponentState>(
                sessionInteractor = sessionInteractor,
                sessionSavedStateHandleContainer = sessionSavedStateHandleContainer
            )

            BacsDirectDebitComponent(
                bacsDelegate = bacsDelegate,
                genericActionDelegate = genericActionDelegate,
                actionHandlingComponent = DefaultActionHandlingComponent(genericActionDelegate, bacsDelegate),
                componentEventHandler = sessionComponentEventHandler
            )
        }
        return ViewModelProvider(viewModelStoreOwner, genericFactory)[key, BacsDirectDebitComponent::class.java]
            .also { component ->
                component.observe(lifecycleOwner) {
                    component.componentEventHandler.onPaymentComponentEvent(it, componentCallback)
                }
            }
    }

    private fun assertSupported(paymentMethod: PaymentMethod) {
        if (!isPaymentMethodSupported(paymentMethod)) {
            throw ComponentException("Unsupported payment method ${paymentMethod.type}")
        }
    }

    override fun isPaymentMethodSupported(paymentMethod: PaymentMethod): Boolean {
        return BacsDirectDebitComponent.PAYMENT_METHOD_TYPES.contains(paymentMethod.type)
    }
}
