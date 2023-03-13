/*
 * Copyright (c) 2021 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by josephj on 7/6/2021.
 */

package com.adyen.checkout.wechatpay.internal.provider

import android.app.Application
import androidx.annotation.RestrictTo
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.adyen.checkout.components.core.action.Action
import com.adyen.checkout.components.core.action.SdkAction
import com.adyen.checkout.components.core.internal.ActionComponentCallback
import com.adyen.checkout.components.core.internal.ActionObserverRepository
import com.adyen.checkout.components.core.internal.DefaultActionComponentEventHandler
import com.adyen.checkout.components.core.internal.PaymentDataRepository
import com.adyen.checkout.components.core.internal.provider.ActionComponentProvider
import com.adyen.checkout.components.core.internal.ui.model.ComponentParams
import com.adyen.checkout.components.core.internal.ui.model.GenericComponentParamsMapper
import com.adyen.checkout.components.core.internal.ui.model.SessionParams
import com.adyen.checkout.components.core.internal.util.PaymentMethodTypes
import com.adyen.checkout.components.core.internal.util.get
import com.adyen.checkout.components.core.internal.util.viewModelFactory
import com.adyen.checkout.wechatpay.WeChatPayActionComponent
import com.adyen.checkout.wechatpay.WeChatPayActionConfiguration
import com.adyen.checkout.wechatpay.internal.ui.DefaultWeChatDelegate
import com.adyen.checkout.wechatpay.internal.ui.WeChatDelegate
import com.adyen.checkout.wechatpay.internal.util.WeChatPayRequestGenerator
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.WXAPIFactory

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class WeChatPayActionComponentProvider(
    overrideComponentParams: ComponentParams? = null,
    overrideSessionParams: SessionParams? = null,
) : ActionComponentProvider<WeChatPayActionComponent, WeChatPayActionConfiguration, WeChatDelegate> {

    private val componentParamsMapper = GenericComponentParamsMapper(overrideComponentParams, overrideSessionParams)

    override fun get(
        savedStateRegistryOwner: SavedStateRegistryOwner,
        viewModelStoreOwner: ViewModelStoreOwner,
        lifecycleOwner: LifecycleOwner,
        application: Application,
        configuration: WeChatPayActionConfiguration,
        callback: ActionComponentCallback,
        key: String?,
    ): WeChatPayActionComponent {
        val weChatFactory = viewModelFactory(savedStateRegistryOwner, null) { savedStateHandle ->
            val weChatDelegate = getDelegate(configuration, savedStateHandle, application)
            WeChatPayActionComponent(
                delegate = weChatDelegate,
                actionComponentEventHandler = DefaultActionComponentEventHandler(callback)
            )
        }

        return ViewModelProvider(viewModelStoreOwner, weChatFactory)[key, WeChatPayActionComponent::class.java]
            .also { component ->
                component.observe(lifecycleOwner, component.actionComponentEventHandler::onActionComponentEvent)
            }
    }

    override fun getDelegate(
        configuration: WeChatPayActionConfiguration,
        savedStateHandle: SavedStateHandle,
        application: Application,
    ): WeChatDelegate {
        val componentParams = componentParamsMapper.mapToParams(configuration, null)
        val iwxApi: IWXAPI = WXAPIFactory.createWXAPI(application, null, true)
        val requestGenerator = WeChatPayRequestGenerator()
        val paymentDataRepository = PaymentDataRepository(savedStateHandle)
        return DefaultWeChatDelegate(
            observerRepository = ActionObserverRepository(),
            componentParams = componentParams,
            iwxApi = iwxApi,
            payRequestGenerator = requestGenerator,
            paymentDataRepository = paymentDataRepository
        )
    }

    override val supportedActionTypes: List<String>
        get() = listOf(SdkAction.ACTION_TYPE)

    override fun canHandleAction(action: Action): Boolean {
        return supportedActionTypes.contains(action.type) && PAYMENT_METHODS.contains(action.paymentMethodType)
    }

    override fun providesDetails(action: Action): Boolean {
        return true
    }

    companion object {
        private val PAYMENT_METHODS = listOf(PaymentMethodTypes.WECHAT_PAY_SDK)
    }
}
