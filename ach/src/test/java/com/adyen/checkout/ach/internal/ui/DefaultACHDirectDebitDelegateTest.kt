/*
 * Copyright (c) 2023 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by onurk on 16/2/2023.
 */

package com.adyen.checkout.ach.internal.ui

import app.cash.turbine.test
import com.adyen.checkout.ach.ACHDirectDebitAddressConfiguration
import com.adyen.checkout.ach.ACHDirectDebitComponentState
import com.adyen.checkout.ach.ACHDirectDebitConfiguration
import com.adyen.checkout.ach.R
import com.adyen.checkout.ach.internal.ui.model.ACHDirectDebitComponentParamsMapper
import com.adyen.checkout.components.core.Amount
import com.adyen.checkout.components.core.OrderRequest
import com.adyen.checkout.components.core.PaymentComponentData
import com.adyen.checkout.components.core.PaymentMethod
import com.adyen.checkout.components.core.internal.PaymentObserverRepository
import com.adyen.checkout.components.core.internal.data.api.AnalyticsRepository
import com.adyen.checkout.components.core.internal.data.api.PublicKeyRepository
import com.adyen.checkout.components.core.internal.test.TestPublicKeyRepository
import com.adyen.checkout.components.core.internal.ui.model.FieldState
import com.adyen.checkout.components.core.internal.ui.model.Validation
import com.adyen.checkout.components.core.paymentmethod.ACHDirectDebitPaymentMethod
import com.adyen.checkout.core.Environment
import com.adyen.checkout.cse.internal.BaseGenericEncrypter
import com.adyen.checkout.cse.internal.test.TestGenericEncrypter
import com.adyen.checkout.test.TestDispatcherExtension
import com.adyen.checkout.test.extensions.test
import com.adyen.checkout.ui.core.internal.data.api.AddressRepository
import com.adyen.checkout.ui.core.internal.test.TestAddressRepository
import com.adyen.checkout.ui.core.internal.ui.AddressFormUIState
import com.adyen.checkout.ui.core.internal.ui.SubmitHandler
import com.adyen.checkout.ui.core.internal.ui.model.AddressInputModel
import com.adyen.checkout.ui.core.internal.ui.model.AddressListItem
import com.adyen.checkout.ui.core.internal.ui.model.AddressOutputData
import com.adyen.checkout.ui.core.internal.util.AddressFormUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockitoExtension::class, TestDispatcherExtension::class)
internal class DefaultACHDirectDebitDelegateTest(
    @Mock private val analyticsRepository: AnalyticsRepository,
    @Mock private val submitHandler: SubmitHandler<ACHDirectDebitComponentState>
) {

    private lateinit var publicKeyRepository: TestPublicKeyRepository
    private lateinit var addressRepository: TestAddressRepository
    private lateinit var genericEncrypter: TestGenericEncrypter
    private lateinit var delegate: DefaultACHDirectDebitDelegate

    @BeforeEach
    fun setUp() {
        publicKeyRepository = TestPublicKeyRepository()
        addressRepository = TestAddressRepository()
        genericEncrypter = TestGenericEncrypter()
        delegate = createAchDelegate()
    }

    @Test
    fun `when fetching the public key fails, then an error is propagated`() = runTest {
        publicKeyRepository.shouldReturnError = true
        delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
        val exception = delegate.exceptionFlow.first()
        assertEquals(publicKeyRepository.errorResult.exceptionOrNull(), exception.cause)
    }

    @Nested
    @DisplayName("when input data changes and")
    inner class InputDataChangedTest {

        @Test
        fun `when isStorePaymentFieldVisible in configuration  is null, isStorePaymentFieldVisible in outputdata should be true`() =
            runTest {
                delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

                runCurrent()

                val outputData = delegate.outputDataFlow.first()

                assertTrue(outputData.showStorePaymentField)
            }

        @Test
        fun `when isStorePaymentFieldVisible in configuration  is false, isStorePaymentFieldVisible in outputdata should be false`() =
            runTest {
                delegate = createAchDelegate(
                    configuration = getAchConfigurationBuilder()
                        .setShowStorePaymentField(false)
                        .build()
                )
                delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

                runCurrent()

                val outputData = delegate.outputDataFlow.first()

                assertFalse(outputData.showStorePaymentField)
            }

        @Test
        fun `when isStorePaymentFieldVisible in configuration  is true , isStorePaymentFieldVisible in outputdata should be true`() =
            runTest {
                delegate = createAchDelegate(
                    configuration = getAchConfigurationBuilder()
                        .setShowStorePaymentField(true)
                        .build()
                )
                delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

                runCurrent()

                val outputData = delegate.outputDataFlow.first()

                assertTrue(outputData.showStorePaymentField)
            }

        @Test
        fun `address configuration is none, then countries and states should not be fetched`() = runTest {
            delegate = createAchDelegate(
                configuration = getAchConfigurationBuilder()
                    .setAddressConfiguration(ACHDirectDebitAddressConfiguration.None)
                    .build()
            )

            val countriesTestFlow = addressRepository.countriesFlow.test(testScheduler)
            val statesTestFlow = addressRepository.statesFlow.test(testScheduler)

            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
            runCurrent()
            assertEquals(0, countriesTestFlow.values.size)
            assertEquals(0, statesTestFlow.values.size)

            countriesTestFlow.cancel()
            statesTestFlow.cancel()
        }

        @Test
        fun `address configuration is full address and shopper local is US, then countries and states should be emitted`() =
            runTest {
                val countriesTestFlow = addressRepository.countriesFlow.test(testScheduler)
                val statesTestFlow = addressRepository.statesFlow.test(testScheduler)
                delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

                runCurrent()

                assertEquals(TestAddressRepository.COUNTRIES, countriesTestFlow.values.firstOrNull())
                assertEquals(TestAddressRepository.STATES, statesTestFlow.values.firstOrNull())

                countriesTestFlow.cancel()
                statesTestFlow.cancel()
            }

        @Test
        fun `when address configuration is NONE, addressUIState in outputdata must be NONE`() = runTest {
            val configuration =
                getAchConfigurationBuilder().setAddressConfiguration(ACHDirectDebitAddressConfiguration.None).build()
            delegate = createAchDelegate(configuration = configuration)
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

            val outputData = delegate.outputDataFlow.first()

            assertEquals(AddressFormUIState.NONE, outputData.addressUIState)
        }

        @Test
        fun `when address configuration is FullAddress, addressUIState in outputdata must be FullAddress`() = runTest {
            val configuration =
                getAchConfigurationBuilder().setAddressConfiguration(
                    ACHDirectDebitAddressConfiguration.FullAddress(
                        supportedCountryCodes = DEFAULT_SUPPORTED_COUNTRY_LIST
                    )
                ).build()
            delegate = createAchDelegate(configuration = configuration)
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

            val outputData = delegate.outputDataFlow.first()

            assertEquals(AddressFormUIState.FULL_ADDRESS, outputData.addressUIState)
        }

        @Test
        fun `when the address is changed, addressOutputDataFlow should be notified with the same data`() = runTest {
            val configuration =
                getAchConfigurationBuilder().setAddressConfiguration(
                    ACHDirectDebitAddressConfiguration.FullAddress(
                        DEFAULT_SUPPORTED_COUNTRY_LIST
                    )
                ).build()
            val componentParams = ACHDirectDebitComponentParamsMapper(null, null).mapToParams(configuration, null)
            val countryOptions = AddressFormUtils.initializeCountryOptions(
                shopperLocale = componentParams.shopperLocale,
                addressParams = componentParams.addressParams,
                countryList = TestAddressRepository.COUNTRIES
            )

            val expectedCountries = AddressFormUtils.markAddressListItemSelected(
                list = countryOptions,
                code = componentParams.shopperLocale.country,
            )
            val addressInputModel =
                AddressInputModel(
                    postalCode = "34220",
                    street = "Street Name",
                    stateOrProvince = "province",
                    houseNumberOrName = "44",
                    apartmentSuite = "aparment",
                    city = "Istanbul",
                    country = "Turkey"
                )

            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

            delegate.updateInputData { address = addressInputModel }
            val addressOutputData = delegate.addressOutputDataFlow.first()

            with(addressOutputData) {
                assertEquals(addressInputModel.postalCode, postalCode.value)
                assertEquals(addressInputModel.street, street.value)
                assertEquals(addressInputModel.stateOrProvince, stateOrProvince.value)
                assertEquals(addressInputModel.houseNumberOrName, houseNumberOrName.value)
                assertEquals(addressInputModel.apartmentSuite, apartmentSuite.value)
                assertEquals(addressInputModel.city, city.value)
                assertEquals(addressInputModel.country, country.value)
                assertEquals(expectedCountries, countryOptions)
                assertEquals(
                    stateOptions,
                    AddressFormUtils.initializeStateOptions(TestAddressRepository.STATES)
                )
            }
        }

        @Test
        fun `when bank account number is valid, bankAccountNumber in outputdata must be valid`() = runTest {
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
            delegate.updateInputData { bankAccountNumber = TEST_BANK_ACCOUNT_NUMBER }
            val outputData = delegate.outputDataFlow.first()
            assertTrue(outputData.bankAccountNumber.validation.isValid())
        }

        @Test
        fun `when bank account number is not valid, bankAccountNumber in outputdata must be invalid`() = runTest {
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
            delegate.updateInputData { bankAccountNumber = "" }
            val outputData = delegate.outputDataFlow.first()
            assertEquals(
                Validation.Invalid(reason = R.string.checkout_ach_bank_account_number_invalid),
                outputData.bankAccountNumber.validation
            )
        }

        @Test
        fun `when bank bankLocationId number is valid, bankLocationId in outputdata must be valid`() = runTest {
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
            delegate.updateInputData { bankLocationId = TEST_BANK_BANK_LOCATION_ID }
            val outputData = delegate.outputDataFlow.first()
            assertTrue(outputData.bankLocationId.validation.isValid())
        }

        @Test
        fun `when bankLocationId is not valid, bankLocationId in outputdata must be invalid`() = runTest {
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
            delegate.updateInputData { bankLocationId = "" }
            val outputData = delegate.outputDataFlow.first()
            assertEquals(
                Validation.Invalid(reason = R.string.checkout_ach_bank_account_location_invalid),
                outputData.bankLocationId.validation
            )
        }

        @Test
        fun `when bank ownerName number is valid, ownerName in outputdata must be valid`() = runTest {
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
            delegate.updateInputData { ownerName = TEST_OWNER_NAME }
            val outputData = delegate.outputDataFlow.first()
            assertTrue(outputData.ownerName.validation.isValid())
        }

        @Test
        fun `when ownerName is not valid, ownerName in outputdata must be invalid`() = runTest {
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
            delegate.updateInputData { ownerName = "" }
            val outputData = delegate.outputDataFlow.first()
            assertEquals(
                Validation.Invalid(reason = R.string.checkout_ach_bank_account_holder_name_invalid),
                outputData.ownerName.validation
            )
        }
    }

    @Nested
    @DisplayName("when creating component state and")
    inner class CreateComponentStateTest {
        @Test
        fun `component is not initialized, then component state should not be ready`() = runTest {
            val componentState = delegate.componentStateFlow.first()
            assertFalse(componentState.isReady)
        }

        @Test
        fun `encryption fails, then component state should be invalid`() = runTest {
            genericEncrypter.shouldThrowException = true

            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

            delegate.updateInputData {
                bankLocationId = TEST_BANK_BANK_LOCATION_ID
                bankAccountNumber = TEST_BANK_ACCOUNT_NUMBER
                ownerName = TEST_OWNER_NAME
                address = AddressInputModel(
                    postalCode = "34220",
                    street = "Street Name",
                    stateOrProvince = "province",
                    houseNumberOrName = "44",
                    apartmentSuite = "aparment",
                    city = "Istanbul",
                    country = "Turkey"
                )
            }

            val componentState = delegate.componentStateFlow.first()

            assertFalse(componentState.isValid)
        }

        @Test
        fun `when bankLocationId is invalid, then component state should be invalid`() = runTest {
            val configuration =
                getAchConfigurationBuilder().setAddressConfiguration(ACHDirectDebitAddressConfiguration.None).build()
            delegate = createAchDelegate(configuration = configuration)

            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

            delegate.updateInputData {
                bankLocationId = ""
                bankAccountNumber = TEST_BANK_ACCOUNT_NUMBER
                ownerName = TEST_OWNER_NAME
                address = AddressInputModel()
            }

            val componentState = delegate.componentStateFlow.first()

            assertTrue(componentState.isReady)
            assertFalse(componentState.isInputValid)
        }

        @Test
        fun `when bankAccountNumber is invalid, then component state should be invalid`() = runTest {
            val configuration =
                getAchConfigurationBuilder().setAddressConfiguration(ACHDirectDebitAddressConfiguration.None).build()
            delegate = createAchDelegate(configuration = configuration)

            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

            delegate.updateInputData {
                bankLocationId = TEST_BANK_BANK_LOCATION_ID
                bankAccountNumber = ""
                ownerName = TEST_OWNER_NAME
                address = AddressInputModel()
            }

            val componentState = delegate.componentStateFlow.first()

            assertTrue(componentState.isReady)
            assertFalse(componentState.isInputValid)
        }

        @Test
        fun `when ownerName is invalid, then component state should be invalid`() = runTest {
            val configuration =
                getAchConfigurationBuilder().setAddressConfiguration(ACHDirectDebitAddressConfiguration.None).build()
            delegate = createAchDelegate(configuration = configuration)

            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

            delegate.updateInputData {
                bankLocationId = TEST_BANK_BANK_LOCATION_ID
                bankAccountNumber = TEST_BANK_ACCOUNT_NUMBER
                ownerName = ""
                address = AddressInputModel()
            }

            val componentState = delegate.componentStateFlow.first()

            assertTrue(componentState.isReady)
            assertFalse(componentState.isInputValid)
        }

        @Test
        fun `when address is invalid, then component state should be invalid`() = runTest {
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

            delegate.updateInputData {
                bankLocationId = TEST_BANK_BANK_LOCATION_ID
                bankAccountNumber = TEST_BANK_ACCOUNT_NUMBER
                ownerName = TEST_OWNER_NAME
                address = AddressInputModel()
            }

            val componentState = delegate.componentStateFlow.first()

            assertTrue(componentState.isReady)
            assertFalse(componentState.isInputValid)
        }

        @Test
        fun `when all fields in outputdata are valid and address is empty and not required, then component state should be valid`() =
            runTest {
                val configuration = getAchConfigurationBuilder()
                    .setAddressConfiguration(ACHDirectDebitAddressConfiguration.None)
                    .build()
                delegate = createAchDelegate(configuration = configuration)

                delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

                delegate.updateInputData {
                    bankLocationId = TEST_BANK_BANK_LOCATION_ID
                    bankAccountNumber = TEST_BANK_ACCOUNT_NUMBER
                    ownerName = TEST_OWNER_NAME
                    address = AddressInputModel()
                }

                val componentState = delegate.componentStateFlow.first()

                assertTrue(componentState.isValid)
            }

        @Test
        fun `when all fields in outputdata are valid, payment method in component state should be the same value in outputdata`() =
            runTest {
                val adddressInputModel = getValidAddressInputData()

                val addressOutputData = createAddressOutputData(
                    postalCode = FieldState(adddressInputModel.postalCode, Validation.Valid),
                    street = FieldState(adddressInputModel.street, Validation.Valid),
                    stateOrProvince = FieldState(adddressInputModel.stateOrProvince, Validation.Valid),
                    houseNumberOrName = FieldState(adddressInputModel.houseNumberOrName, Validation.Valid),
                    apartmentSuite = FieldState(adddressInputModel.apartmentSuite, Validation.Valid),
                    city = FieldState(adddressInputModel.city, Validation.Valid),
                    country = FieldState(adddressInputModel.country, Validation.Valid),
                    isOptional = false
                )

                val addressUIState = AddressFormUIState.FULL_ADDRESS
                val expectedAddress = AddressFormUtils.makeAddressData(addressOutputData, addressUIState)

                delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

                delegate.updateInputData {
                    bankLocationId = TEST_BANK_BANK_LOCATION_ID
                    bankAccountNumber = TEST_BANK_ACCOUNT_NUMBER
                    ownerName = TEST_OWNER_NAME
                    address = adddressInputModel
                }

                val componentState = delegate.componentStateFlow.first()

                val expectedPaymentMethod = ACHDirectDebitPaymentMethod(
                    type = ACHDirectDebitPaymentMethod.PAYMENT_METHOD_TYPE,
                    checkoutAttemptId = null,
                    encryptedBankAccountNumber = TEST_BANK_ACCOUNT_NUMBER,
                    encryptedBankLocationId = TEST_BANK_BANK_LOCATION_ID,
                    ownerName = TEST_OWNER_NAME,
                )

                val expectedPaymentComponentData = PaymentComponentData(
                    order = TEST_ORDER,
                    storePaymentMethod = false,
                    paymentMethod = expectedPaymentMethod,
                    amount = null,
                    billingAddress = expectedAddress,
                )

                val expectedComponentState = ACHDirectDebitComponentState(
                    data = expectedPaymentComponentData,
                    isInputValid = true,
                    isReady = true,
                )
                assertEquals(expectedComponentState, componentState)
            }

        @ParameterizedTest
        @MethodSource(
            "com.adyen.checkout.ach.internal.ui.DefaultACHDirectDebitDelegateTest#shouldStorePaymentMethodSource"
        )
        fun `storePaymentMethod in component state should match store switch visibility and state`(
            isStorePaymentMethodSwitchVisible: Boolean,
            isStorePaymentMethodSwitchChecked: Boolean,
            expectedStorePaymentMethod: Boolean?,
        ) = runTest {
            val configuration = getAchConfigurationBuilder()
                .setShowStorePaymentField(isStorePaymentMethodSwitchVisible)
                .build()
            delegate = createAchDelegate(configuration = configuration)

            val adddressInputModel = AddressInputModel(
                postalCode = "34220",
                street = "Street Name",
                stateOrProvince = "province",
                houseNumberOrName = "44",
                apartmentSuite = "aparment",
                city = "Istanbul",
                country = "Turkey"
            )

            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

            delegate.updateInputData {
                bankLocationId = TEST_BANK_BANK_LOCATION_ID
                bankAccountNumber = TEST_BANK_ACCOUNT_NUMBER
                ownerName = TEST_OWNER_NAME
                address = adddressInputModel
                this.isStorePaymentMethodSwitchChecked = isStorePaymentMethodSwitchChecked
            }

            val componentState = delegate.componentStateFlow.first()

            assertEquals(expectedStorePaymentMethod, componentState.data.storePaymentMethod)
        }

        @ParameterizedTest
        @MethodSource("com.adyen.checkout.ach.internal.ui.DefaultACHDirectDebitDelegateTest#amountSource")
        fun `when input data is valid then amount is propagated in component state if set`(
            configurationValue: Amount?,
            expectedComponentStateValue: Amount?,
        ) = runTest {
            if (configurationValue != null) {
                val configuration = getAchConfigurationBuilder()
                    .setAmount(configurationValue)
                    .build()
                delegate = createAchDelegate(configuration = configuration)
            }
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
            delegate.updateInputData {
                bankLocationId = TEST_BANK_BANK_LOCATION_ID
                bankAccountNumber = TEST_BANK_ACCOUNT_NUMBER
                ownerName = TEST_OWNER_NAME
                address = getValidAddressInputData()
            }

            val componentState = delegate.componentStateFlow.first()
            assertEquals(expectedComponentStateValue, componentState.data.amount)
        }
    }

    @Nested
    inner class SubmitButtonVisibilityTest {
        @Test
        fun `when submit button is configured to be hidden, then it should not show`() {
            delegate = createAchDelegate(
                configuration = getAchConfigurationBuilder()
                    .setSubmitButtonVisible(false)
                    .build()
            )

            assertFalse(delegate.shouldShowSubmitButton())
        }

        @Test
        fun `when submit button is configured to be visible, then it should show`() {
            delegate = createAchDelegate(
                configuration = getAchConfigurationBuilder()
                    .setSubmitButtonVisible(true)
                    .build()
            )

            assertTrue(delegate.shouldShowSubmitButton())
        }
    }

    @Nested
    inner class SubmitHandlerTest {

        @Test
        fun `when delegate is initialized then submit handler event is initialized`() = runTest {
            val coroutineScope = CoroutineScope(UnconfinedTestDispatcher())
            delegate.initialize(coroutineScope)
            verify(submitHandler).initialize(coroutineScope, delegate.componentStateFlow)
        }

        @Test
        fun `when delegate setInteractionBlocked is called then submit handler setInteractionBlocked is called`() =
            runTest {
                delegate.setInteractionBlocked(true)
                verify(submitHandler).setInteractionBlocked(true)
            }

        @Test
        fun `when delegate onSubmit is called then submit handler onSubmit is called`() = runTest {
            delegate.componentStateFlow.test {
                delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
                delegate.onSubmit()
                verify(submitHandler).onSubmit(expectMostRecentItem())
            }
        }
    }

    @Nested
    inner class AnalyticsTest {

        @Test
        fun `when component state is valid then PaymentMethodDetails should contain checkoutAttemptId`() = runTest {
            whenever(analyticsRepository.getCheckoutAttemptId()) doReturn TEST_CHECKOUT_ATTEMPT_ID

            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

            delegate.updateInputData {
                bankLocationId = TEST_BANK_BANK_LOCATION_ID
                bankAccountNumber = TEST_BANK_ACCOUNT_NUMBER
                ownerName = TEST_OWNER_NAME
                address = getValidAddressInputData()
            }

            val componentState = delegate.componentStateFlow.first()

            assertEquals(TEST_CHECKOUT_ATTEMPT_ID, componentState.data.paymentMethod?.checkoutAttemptId)
        }
    }

    @Suppress("LongParameterList")
    private fun createAddressOutputData(
        postalCode: FieldState<String> = FieldState("", Validation.Valid),
        street: FieldState<String> = FieldState("", Validation.Valid),
        stateOrProvince: FieldState<String> = FieldState("", Validation.Valid),
        houseNumberOrName: FieldState<String> = FieldState("", Validation.Valid),
        apartmentSuite: FieldState<String> = FieldState("", Validation.Valid),
        city: FieldState<String> = FieldState("", Validation.Valid),
        country: FieldState<String> = FieldState("", Validation.Valid),
        isOptional: Boolean = true,
        countryOptions: List<AddressListItem> = emptyList(),
        stateOptions: List<AddressListItem> = emptyList()
    ): AddressOutputData {
        return AddressOutputData(
            postalCode = postalCode,
            street = street,
            stateOrProvince = stateOrProvince,
            houseNumberOrName = houseNumberOrName,
            apartmentSuite = apartmentSuite,
            city = city,
            country = country,
            isOptional = isOptional,
            countryOptions = countryOptions,
            stateOptions = stateOptions
        )
    }

    @Suppress("LongParameterList")
    private fun createAchDelegate(
        paymentMethod: PaymentMethod = PaymentMethod(),
        analyticsRepository: AnalyticsRepository = this.analyticsRepository,
        publicKeyRepository: PublicKeyRepository = this.publicKeyRepository,
        addressRepository: AddressRepository = this.addressRepository,
        genericEncrypter: BaseGenericEncrypter = this.genericEncrypter,
        submitHandler: SubmitHandler<ACHDirectDebitComponentState> = this.submitHandler,
        configuration: ACHDirectDebitConfiguration = getAchConfigurationBuilder().build(),
        order: OrderRequest? = TEST_ORDER,
    ) = DefaultACHDirectDebitDelegate(
        observerRepository = PaymentObserverRepository(),
        paymentMethod = paymentMethod,
        analyticsRepository = analyticsRepository,
        publicKeyRepository = publicKeyRepository,
        addressRepository = addressRepository,
        submitHandler = submitHandler,
        genericEncrypter = genericEncrypter,
        componentParams = ACHDirectDebitComponentParamsMapper(null, null).mapToParams(configuration, null),
        order = order
    )

    private fun getAchConfigurationBuilder() = ACHDirectDebitConfiguration.Builder(
        shopperLocale = Locale.US,
        environment = Environment.TEST,
        clientKey = TEST_CLIENT_KEY,
    )

    private fun getValidAddressInputData(): AddressInputModel {
        return AddressInputModel(
            postalCode = "34220",
            street = "Street Name",
            stateOrProvince = "province",
            houseNumberOrName = "44",
            apartmentSuite = "aparment",
            city = "Istanbul",
            country = "Turkey"
        )
    }

    companion object {
        private const val TEST_CLIENT_KEY = "test_qwertyuiopasdfghjklzxcvbnmqwerty"
        private const val TEST_BANK_ACCOUNT_NUMBER = "123456"
        private const val TEST_BANK_BANK_LOCATION_ID = "123456789"
        private const val TEST_OWNER_NAME = "Joseph"
        private val TEST_ORDER = OrderRequest("PSP", "ORDER_DATA")
        private val DEFAULT_SUPPORTED_COUNTRY_LIST = listOf("US", "PR")
        private const val TEST_CHECKOUT_ATTEMPT_ID = "TEST_CHECKOUT_ATTEMPT_ID"

        @JvmStatic
        fun shouldStorePaymentMethodSource() = listOf(
            // isStorePaymentMethodSwitchVisible, isStorePaymentMethodSwitchChecked, expectedStorePaymentMethod
            arguments(false, false, null),
            arguments(false, true, null),
            arguments(true, false, false),
            arguments(true, true, true),
        )

        @JvmStatic
        fun amountSource() = listOf(
            // configurationValue, expectedComponentStateValue
            arguments(Amount("EUR", 100), Amount("EUR", 100)),
            arguments(Amount("USD", 0), Amount("USD", 0)),
            arguments(Amount.EMPTY, null),
            arguments(null, null),
        )
    }
}
