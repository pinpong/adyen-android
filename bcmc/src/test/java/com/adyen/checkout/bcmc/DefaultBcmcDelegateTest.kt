/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by atef on 22/8/2022.
 */

package com.adyen.checkout.bcmc

import app.cash.turbine.test
import com.adyen.checkout.card.CardValidationMapper
import com.adyen.checkout.card.R
import com.adyen.checkout.card.data.ExpiryDate
import com.adyen.checkout.components.PaymentComponentState
import com.adyen.checkout.components.analytics.AnalyticsRepository
import com.adyen.checkout.components.model.paymentmethods.PaymentMethod
import com.adyen.checkout.components.model.payments.request.CardPaymentMethod
import com.adyen.checkout.components.model.payments.request.OrderRequest
import com.adyen.checkout.components.repository.PaymentObserverRepository
import com.adyen.checkout.components.test.TestPublicKeyRepository
import com.adyen.checkout.components.ui.FieldState
import com.adyen.checkout.components.ui.SubmitHandler
import com.adyen.checkout.components.ui.Validation
import com.adyen.checkout.core.api.Environment
import com.adyen.checkout.cse.test.TestCardEncrypter
import com.adyen.checkout.test.TestDispatcherExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockitoExtension::class, TestDispatcherExtension::class)
internal class DefaultBcmcDelegateTest(
    @Mock private val analyticsRepository: AnalyticsRepository,
    @Mock private val submitHandler: SubmitHandler<PaymentComponentState<CardPaymentMethod>>,
) {

    private lateinit var testPublicKeyRepository: TestPublicKeyRepository
    private lateinit var cardEncrypter: TestCardEncrypter
    private lateinit var cardValidationMapper: CardValidationMapper
    private lateinit var delegate: DefaultBcmcDelegate

    @BeforeEach
    fun setup() {
        testPublicKeyRepository = TestPublicKeyRepository()
        cardEncrypter = TestCardEncrypter()
        cardValidationMapper = CardValidationMapper()
        delegate = createBcmcDelegate()
    }

    @Test
    fun `when fetching the public key fails, then an error is propagated`() = runTest {
        testPublicKeyRepository.shouldReturnError = true

        delegate.exceptionFlow.test {
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

            val exception = expectMostRecentItem()
            assertEquals(testPublicKeyRepository.errorResult.exceptionOrNull(), exception.cause)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Nested
    @DisplayName("when input data changes and")
    inner class InputDataChangedTest {
        @Test
        fun `card number is empty, then output should be invalid`() = runTest {
            delegate.outputDataFlow.test {
                delegate.updateInputData {
                    cardNumber = ""
                    expiryDate = TEST_EXPIRY_DATE
                }

                with(expectMostRecentItem()) {
                    assertTrue(cardNumberField.validation is Validation.Invalid)
                    assertTrue(expiryDateField.validation is Validation.Valid)
                    assertFalse(isValid)
                }
            }
        }

        @Test
        fun `card number is invalid, then output should be invalid`() = runTest {
            delegate.outputDataFlow.test {
                delegate.updateInputData {
                    cardNumber = "12345678"
                    expiryDate = TEST_EXPIRY_DATE
                }

                with(expectMostRecentItem()) {
                    assertTrue(cardNumberField.validation is Validation.Invalid)
                    assertTrue(expiryDateField.validation is Validation.Valid)
                    assertFalse(isValid)
                }
            }
        }

        @Test
        fun `expiry date is invalid, then output should be invalid`() =
            runTest {
                delegate.outputDataFlow.test {
                    delegate.updateInputData {
                        cardNumber = TEST_CARD_NUMBER
                        expiryDate = ExpiryDate.INVALID_DATE
                    }

                    with(expectMostRecentItem()) {
                        assertTrue(cardNumberField.validation is Validation.Valid)
                        assertTrue(expiryDateField.validation is Validation.Invalid)
                        assertFalse(isValid)
                    }
                }
            }

        @Test
        fun `expiry date is empty, then output should be invalid`() = runTest {
            delegate.outputDataFlow.test {
                delegate.updateInputData {
                    cardNumber = TEST_CARD_NUMBER
                    expiryDate = ExpiryDate.EMPTY_DATE
                }

                with(expectMostRecentItem()) {
                    assertTrue(cardNumberField.validation is Validation.Valid)
                    assertTrue(expiryDateField.validation is Validation.Invalid)
                    assertFalse(isValid)
                }
            }
        }

        @Test
        fun `input is valid, then output should be valid`() = runTest {
            delegate.outputDataFlow.test {
                delegate.updateInputData {
                    cardNumber = TEST_CARD_NUMBER
                    expiryDate = TEST_EXPIRY_DATE
                }

                with(expectMostRecentItem()) {
                    assertTrue(cardNumberField.validation is Validation.Valid)
                    assertTrue(expiryDateField.validation is Validation.Valid)
                    assertTrue(isValid)
                }
            }
        }
    }

    @Nested
    @DisplayName("when creating component state and")
    inner class CreateComponentStateTest {

        @Test
        fun `component is not initialized, then component state should not be ready`() = runTest {
            delegate.componentStateFlow.test {
                delegate.updateComponentState(
                    createOutputData(
                        cardNumber = FieldState(TEST_CARD_NUMBER, Validation.Valid),
                        expiryDate = FieldState(TEST_EXPIRY_DATE, Validation.Valid),
                        cardHolder = FieldState("Name", Validation.Valid)
                    )
                )

                with(expectMostRecentItem()) {
                    assertFalse(isReady)
                }
            }
        }

        @Test
        fun `encryption fails, then component state should be invalid`() = runTest {
            cardEncrypter.shouldThrowException = true

            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

            delegate.componentStateFlow.test {
                delegate.updateComponentState(
                    createOutputData(
                        cardNumber = FieldState(TEST_CARD_NUMBER, Validation.Valid),
                        expiryDate = FieldState(TEST_EXPIRY_DATE, Validation.Valid),
                        cardHolder = FieldState("Name", Validation.Valid)
                    )
                )

                with(expectMostRecentItem()) {
                    assertTrue(isReady)
                    assertFalse(isInputValid)
                }
            }
        }

        @Test
        fun `card number in output data is invalid, then component state should be invalid`() = runTest {
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
            delegate.componentStateFlow.test {
                delegate.updateComponentState(
                    createOutputData(
                        cardNumber = FieldState(
                            "12345678", Validation.Invalid(R.string.checkout_card_number_not_valid)
                        ),
                        expiryDate = FieldState(TEST_EXPIRY_DATE, Validation.Valid),
                        cardHolder = FieldState("Name", Validation.Valid)
                    )
                )

                with(expectMostRecentItem()) {
                    assertFalse(isValid)
                    assertFalse(isInputValid)
                }
            }
        }

        @Test
        fun `expiry date in output is invalid, then component state should be invalid`() = runTest {
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
            delegate.componentStateFlow.test {
                delegate.updateComponentState(
                    createOutputData(
                        cardNumber = FieldState(TEST_CARD_NUMBER, Validation.Valid),
                        expiryDate = FieldState(
                            ExpiryDate.INVALID_DATE,
                            Validation.Invalid(R.string.checkout_expiry_date_not_valid)
                        ),
                        cardHolder = FieldState("Name", Validation.Valid),
                    )
                )

                with(expectMostRecentItem()) {
                    assertFalse(isValid)
                    assertFalse(isInputValid)
                }
            }
        }

        @Test
        fun `holder name in output is invalid, then component state should be invalid`() = runTest {
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
            delegate.componentStateFlow.test {
                delegate.updateComponentState(
                    createOutputData(
                        cardNumber = FieldState(TEST_CARD_NUMBER, Validation.Valid),
                        expiryDate = FieldState(TEST_EXPIRY_DATE, Validation.Valid),
                        cardHolder = FieldState("", Validation.Invalid(R.string.checkout_holder_name_not_valid)),
                    )
                )

                with(expectMostRecentItem()) {
                    assertFalse(isValid)
                    assertFalse(isInputValid)
                }
            }
        }

        @Test
        fun `output data is valid, then component state should be valid`() = runTest {
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
            delegate.componentStateFlow.test {
                delegate.updateComponentState(
                    createOutputData(
                        cardNumber = FieldState(TEST_CARD_NUMBER, Validation.Valid),
                        expiryDate = FieldState(TEST_EXPIRY_DATE, Validation.Valid),
                        cardHolder = FieldState("Name", Validation.Valid)
                    )
                )
                with(expectMostRecentItem()) {
                    assertTrue(isValid)
                    assertTrue(isInputValid)
                    assertEquals(TEST_ORDER, data.order)
                }
            }
        }
    }

    @Test
    fun `when delegate is initialized then analytics event is sent`() = runTest {
        delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))
        verify(analyticsRepository).sendAnalyticsEvent()
    }

    @Nested
    inner class SubmitButtonVisibilityTest {

        @Test
        fun `when submit button is configured to be hidden, then it should not show`() {
            delegate = createBcmcDelegate(
                configuration = getDefaultBcmcConfigurationBuilder()
                    .setSubmitButtonVisible(false)
                    .build()
            )

            assertFalse(delegate.shouldShowSubmitButton())
        }

        @Test
        fun `when submit button is configured to be visible, then it should show`() {
            delegate = createBcmcDelegate(
                configuration = getDefaultBcmcConfigurationBuilder()
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

    private fun createOutputData(
        cardNumber: FieldState<String>,
        expiryDate: FieldState<ExpiryDate>,
        cardHolder: FieldState<String>,
        isStoredPaymentMethodEnable: Boolean = false
    ): BcmcOutputData {
        return BcmcOutputData(
            cardNumber,
            expiryDate,
            cardHolder,
            isStoredPaymentMethodEnable
        )
    }

    private fun createBcmcDelegate(
        configuration: BcmcConfiguration = getDefaultBcmcConfigurationBuilder().build()
    ) = DefaultBcmcDelegate(
        observerRepository = PaymentObserverRepository(),
        paymentMethod = PaymentMethod(),
        order = TEST_ORDER,
        publicKeyRepository = testPublicKeyRepository,
        componentParams = BcmcComponentParamsMapper().mapToParams(configuration),
        cardValidationMapper = cardValidationMapper,
        cardEncrypter = cardEncrypter,
        analyticsRepository = analyticsRepository,
        submitHandler = submitHandler,
    )

    private fun getDefaultBcmcConfigurationBuilder() = BcmcConfiguration.Builder(
        Locale.US,
        Environment.TEST,
        TEST_CLIENT_KEY
    )

    companion object {
        private const val TEST_CLIENT_KEY = "test_qwertyuiopasdfghjklzxcvbnmqwerty"
        private const val TEST_CARD_NUMBER = "5555444433331111"
        private val TEST_EXPIRY_DATE = ExpiryDate(3, 2030)
        private val TEST_ORDER = OrderRequest("PSP", "ORDER_DATA")
    }
}
