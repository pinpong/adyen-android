/*
 * Copyright (c) 2023 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by onurk on 24/1/2023.
 */

package com.adyen.checkout.ach

import androidx.lifecycle.LifecycleOwner
import com.adyen.checkout.components.PaymentComponentEvent
import com.adyen.checkout.components.PaymentComponentState
import com.adyen.checkout.components.analytics.AnalyticsRepository
import com.adyen.checkout.components.channel.bufferedChannel
import com.adyen.checkout.components.model.AddressListItem
import com.adyen.checkout.components.model.paymentmethods.PaymentMethod
import com.adyen.checkout.components.model.payments.request.AchPaymentMethod
import com.adyen.checkout.components.model.payments.request.PaymentComponentData
import com.adyen.checkout.components.repository.AddressRepository
import com.adyen.checkout.components.repository.PaymentObserverRepository
import com.adyen.checkout.components.repository.PublicKeyRepository
import com.adyen.checkout.components.ui.AddressFormUIState
import com.adyen.checkout.components.ui.AddressInputModel
import com.adyen.checkout.components.ui.AddressOutputData
import com.adyen.checkout.components.ui.AddressParams
import com.adyen.checkout.components.ui.PaymentComponentUIEvent
import com.adyen.checkout.components.ui.PaymentComponentUIState
import com.adyen.checkout.components.ui.SubmitHandler
import com.adyen.checkout.components.ui.util.AddressFormUtils
import com.adyen.checkout.components.ui.util.AddressValidationUtils
import com.adyen.checkout.components.ui.view.ButtonComponentViewType
import com.adyen.checkout.components.ui.view.ComponentViewType
import com.adyen.checkout.components.util.PaymentMethodTypes
import com.adyen.checkout.core.exception.CheckoutException
import com.adyen.checkout.core.exception.ComponentException
import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.log.Logger
import com.adyen.checkout.cse.GenericEncrypter
import com.adyen.checkout.cse.exception.EncryptionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Suppress("LongParameterList", "TooManyFunctions")
internal class DefaultAchDelegate(
    private val observerRepository: PaymentObserverRepository,
    private val paymentMethod: PaymentMethod,
    private val analyticsRepository: AnalyticsRepository,
    private val publicKeyRepository: PublicKeyRepository,
    private val addressRepository: AddressRepository,
    private val submitHandler: SubmitHandler,
    private val genericEncrypter: GenericEncrypter,
    override val componentParams: AchComponentParams
) : AchDelegate {

    private val inputData: AchInputData = AchInputData()

    private val _outputDataFlow = MutableStateFlow(createOutputData())
    override val outputDataFlow: Flow<AchOutputData> = _outputDataFlow

    override val outputData: AchOutputData
        get() = _outputDataFlow.value

    override val addressOutputData: AddressOutputData
        get() = outputData.addressState

    override val addressOutputDataFlow: Flow<AddressOutputData> by lazy {
        outputDataFlow.map {
            it.addressState
        }.stateIn(coroutineScope, SharingStarted.Lazily, outputData.addressState)
    }

    private val exceptionChannel: Channel<CheckoutException> = bufferedChannel()
    override val exceptionFlow: Flow<CheckoutException> = exceptionChannel.receiveAsFlow()

    private val _componentStateFlow = MutableStateFlow(createComponentState())
    override val componentStateFlow: Flow<PaymentComponentState<AchPaymentMethod>> = _componentStateFlow

    private val _uiStateFlow = MutableStateFlow<PaymentComponentUIState>(PaymentComponentUIState.Idle)
    override val uiStateFlow: Flow<PaymentComponentUIState> = _uiStateFlow

    private val _uiEventChannel: Channel<PaymentComponentUIEvent> = bufferedChannel()
    override val uiEventFlow: Flow<PaymentComponentUIEvent> = _uiEventChannel.receiveAsFlow()

    private var publicKey: String? = null

    private var _coroutineScope: CoroutineScope? = null
    private val coroutineScope: CoroutineScope get() = requireNotNull(_coroutineScope)

    private val submitChannel: Channel<PaymentComponentState<AchPaymentMethod>> = bufferedChannel()
    override val submitFlow: Flow<PaymentComponentState<AchPaymentMethod>> = submitChannel.receiveAsFlow()

    private val _viewFlow: MutableStateFlow<ComponentViewType?> = MutableStateFlow(AchComponentViewType)
    override val viewFlow: Flow<ComponentViewType?> = _viewFlow

    override fun updateAddressInputData(update: AddressInputModel.() -> Unit) {
        updateInputData {
            this.address.update()
        }
    }

    override fun updateInputData(update: AchInputData.() -> Unit) {
        inputData.update()
        onInputDataChanged()
    }

    private fun onInputDataChanged() {
        val outputData = createOutputData(
            countryOptions = outputData.addressState.countryOptions,
            stateOptions = outputData.addressState.stateOptions
        )
        _outputDataFlow.tryEmit(outputData)
        updateComponentState(outputData)
        requestStateList(inputData.address.country)
    }

    override fun getPaymentMethodType(): String {
        return paymentMethod.type ?: PaymentMethodTypes.UNKNOWN
    }

    private fun createOutputData(
        countryOptions: List<AddressListItem> = emptyList(),
        stateOptions: List<AddressListItem> = emptyList(),
    ): AchOutputData {
        val updatedCountryOptions = AddressFormUtils.markAddressListItemSelected(
            countryOptions,
            inputData.address.country
        )
        val updatedStateOptions = AddressFormUtils.markAddressListItemSelected(
            stateOptions,
            inputData.address.stateOrProvince
        )

        val addressFormUIState = AddressFormUIState.fromAddressParams(componentParams.addressParams)

        return AchOutputData(
            bankAccountNumber = AchValidationUtils.validateBankAccountNumber(inputData.bankAccountNumber),
            bankLocationId = AchValidationUtils.validateBankLocationId(inputData.bankLocationId),
            ownerName = AchValidationUtils.validateOwnerName(inputData.ownerName),
            addressState = AddressValidationUtils.validateAddressInput(
                inputData.address,
                addressFormUIState,
                updatedCountryOptions,
                updatedStateOptions,
                false
            ),
            addressUIState = addressFormUIState
        )
    }

    private fun fetchPublicKey(coroutineScope: CoroutineScope) {
        Logger.d(TAG, "fetchPublicKey")
        coroutineScope.launch {
            publicKeyRepository.fetchPublicKey(
                environment = componentParams.environment,
                clientKey = componentParams.clientKey
            ).fold(
                onSuccess = { key ->
                    Logger.d(TAG, "Public key fetched")
                    publicKey = key
                    updateComponentState(outputData)
                },
                onFailure = { e ->
                    Logger.e(TAG, "Unable to fetch public key")
                    exceptionChannel.trySend(ComponentException("Unable to fetch publicKey.", e))
                }
            )
        }
    }

    private fun subscribeToCountryList() {
        addressRepository.countriesFlow
            .distinctUntilChanged()
            .onEach { countries ->
                Logger.d(TAG, "New countries emitted - countries: ${countries.size}")
                val countryOptions = AddressFormUtils.initializeCountryOptions(
                    shopperLocale = componentParams.shopperLocale,
                    addressParams = componentParams.addressParams,
                    countryList = countries
                )
                countryOptions.firstOrNull { it.selected }?.let {
                    inputData.address.country = it.code
                    requestStateList(it.code)
                }
                updateOutputData(countryOptions = countryOptions)
            }
            .launchIn(coroutineScope)
    }

    private fun subscribeToStatesList() {
        addressRepository.statesFlow
            .distinctUntilChanged()
            .onEach { states ->
                Logger.d(TAG, "New states emitted - states: ${states.size}")
                updateOutputData(stateOptions = AddressFormUtils.initializeStateOptions(states))
            }
            .launchIn(coroutineScope)
    }

    private fun updateOutputData(
        countryOptions: List<AddressListItem> = outputData.addressState.countryOptions,
        stateOptions: List<AddressListItem> = outputData.addressState.stateOptions,
    ) {
        val newOutputData = createOutputData(countryOptions, stateOptions)
        _outputDataFlow.tryEmit(newOutputData)
        updateComponentState(newOutputData)
    }

    private fun requestStateList(countryCode: String?) {
        addressRepository.getStateList(
            shopperLocale = componentParams.shopperLocale,
            countryCode = countryCode,
            coroutineScope = coroutineScope
        )
    }

    private fun requestCountryList() {
        addressRepository.getCountryList(
            shopperLocale = componentParams.shopperLocale,
            coroutineScope = coroutineScope
        )
    }

    private fun sendAnalyticsEvent(coroutineScope: CoroutineScope) {
        Logger.v(TAG, "sendAnalyticsEvent")
        coroutineScope.launch {
            analyticsRepository.sendAnalyticsEvent()
        }
    }

    private fun updateComponentState(outputData: AchOutputData) {
        Logger.v(TAG, "updateComponentState")
        val componentState = createComponentState(outputData)
        _componentStateFlow.tryEmit(componentState)
    }

    @Suppress("ReturnCount")
    private fun createComponentState(
        outputData: AchOutputData = this.outputData
    ): PaymentComponentState<AchPaymentMethod> {
        val paymentComponentData = PaymentComponentData<AchPaymentMethod>()
        val publicKey = publicKey
        if (!outputData.isValid || publicKey == null) {
            return PaymentComponentState(
                data = PaymentComponentData(),
                isInputValid = outputData.isValid,
                isReady = publicKey != null
            )
        }

        try {
            val encryptedBankAccountNumber = genericEncrypter.encryptField(
                encryptionKey = BANK_ACCOUNT_NUMBER,
                fieldToEncrypt = outputData.bankAccountNumber.value,
                publicKey = publicKey
            )
            val encryptedBankLocationId = genericEncrypter.encryptField(
                encryptionKey = BANK_LOCATION_ID,
                fieldToEncrypt = outputData.bankLocationId.value,
                publicKey = publicKey
            )

            val achPaymentMethod = AchPaymentMethod(
                type = AchPaymentMethod.PAYMENT_METHOD_TYPE,
                encryptedBankAccountNumber = encryptedBankAccountNumber,
                encryptedBankLocationId = encryptedBankLocationId,
                ownerName = outputData.ownerName.value
            )
            paymentComponentData.apply {
                paymentMethod = achPaymentMethod
                if (isAddressRequired(outputData.addressUIState)) {
                    billingAddress = AddressFormUtils.makeAddressData(
                        addressOutputData = outputData.addressState,
                        addressFormUIState = outputData.addressUIState
                    )
                }
            }
        } catch (e: EncryptionException) {
            exceptionChannel.trySend(e)
            return PaymentComponentState(
                data = PaymentComponentData(),
                isInputValid = false,
                isReady = true
            )
        }

        return PaymentComponentState(paymentComponentData, isInputValid = true, isReady = true)
    }

    private fun isAddressRequired(addressFormUIState: AddressFormUIState): Boolean {
        return AddressFormUtils.isAddressRequired(addressFormUIState)
    }

    override fun observe(
        lifecycleOwner: LifecycleOwner,
        coroutineScope: CoroutineScope,
        callback: (PaymentComponentEvent<PaymentComponentState<AchPaymentMethod>>) -> Unit
    ) {
        observerRepository.addObservers(
            stateFlow = componentStateFlow,
            exceptionFlow = exceptionFlow,
            submitFlow = submitFlow,
            lifecycleOwner = lifecycleOwner,
            coroutineScope = coroutineScope,
            callback = callback
        )
    }

    override fun removeObserver() {
        observerRepository.removeObservers()
    }

    override fun initialize(coroutineScope: CoroutineScope) {
        _coroutineScope = coroutineScope
        componentStateFlow.onEach {
            onState(it)
        }.launchIn(coroutineScope)

        sendAnalyticsEvent(coroutineScope)
        fetchPublicKey(coroutineScope)

        if (componentParams.addressParams is AddressParams.FullAddress) {
            subscribeToStatesList()
            subscribeToCountryList()
            requestCountryList()
        }
    }

    private fun onState(state: PaymentComponentState<AchPaymentMethod>) {
        val uiState = _uiStateFlow.value
        if (uiState == PaymentComponentUIState.Loading) {
            if (state.isValid) {
                submitChannel.trySend(state)
            } else {
                _uiStateFlow.tryEmit(PaymentComponentUIState.Idle)
            }
        }
    }

    override fun onCleared() {
        removeObserver()
        _coroutineScope = null
    }

    override fun onSubmit() {
        val state = _componentStateFlow.value
        submitHandler.onSubmit(
            state = state,
            submitChannel = submitChannel,
            uiEventChannel = _uiEventChannel,
            uiStateChannel = _uiStateFlow
        )
    }

    override fun isConfirmationRequired(): Boolean = _viewFlow.value is ButtonComponentViewType

    override fun shouldShowSubmitButton(): Boolean = isConfirmationRequired() && componentParams.isSubmitButtonVisible

    companion object {
        private val TAG = LogUtil.getTag()
        const val BANK_ACCOUNT_NUMBER = "bankAccountNumber"
        const val BANK_LOCATION_ID = "bankLocationId"
    }
}
