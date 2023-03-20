/*
 * Copyright (c) 2022 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by oscars on 18/7/2022.
 */

package com.adyen.checkout.giftcard.internal.ui

import app.cash.turbine.test
import com.adyen.checkout.components.core.Amount
import com.adyen.checkout.components.core.OrderRequest
import com.adyen.checkout.components.core.OrderResponse
import com.adyen.checkout.components.core.PaymentMethod
import com.adyen.checkout.components.core.internal.PaymentObserverRepository
import com.adyen.checkout.components.core.internal.data.api.AnalyticsRepository
import com.adyen.checkout.components.core.internal.test.TestPublicKeyRepository
import com.adyen.checkout.components.core.internal.ui.model.ButtonComponentParamsMapper
import com.adyen.checkout.core.Environment
import com.adyen.checkout.cse.internal.test.TestCardEncrypter
import com.adyen.checkout.giftcard.GiftCardAction
import com.adyen.checkout.giftcard.GiftCardComponentState
import com.adyen.checkout.giftcard.GiftCardConfiguration
import com.adyen.checkout.giftcard.GiftCardException
import com.adyen.checkout.giftcard.internal.ui.model.GiftCardOutputData
import com.adyen.checkout.giftcard.internal.util.GiftCardBalanceStatus
import com.adyen.checkout.test.TestDispatcherExtension
import com.adyen.checkout.ui.core.internal.ui.SubmitHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
internal class DefaultGiftCardDelegateTest(
    @Mock private val analyticsRepository: AnalyticsRepository,
    @Mock private val submitHandler: SubmitHandler<GiftCardComponentState>,
) {

    private lateinit var cardEncrypter: TestCardEncrypter
    private lateinit var publicKeyRepository: TestPublicKeyRepository
    private lateinit var delegate: DefaultGiftCardDelegate

    @BeforeEach
    fun before() {
        cardEncrypter = TestCardEncrypter()
        publicKeyRepository = TestPublicKeyRepository()
        delegate = createGiftCardDelegate()
    }

    @Test
    fun `when fetching the public key fails, then an error is propagated`() = runTest {
        publicKeyRepository.shouldReturnError = true

        delegate.exceptionFlow.test {
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

            val exception = expectMostRecentItem()

            assertEquals(publicKeyRepository.errorResult.exceptionOrNull(), exception.cause)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Nested
    @DisplayName("when creating component state and")
    inner class CreateComponentStateTest {

        @Test
        fun `public key is null, then component state should not be ready`() = runTest {
            delegate.componentStateFlow.test {
                delegate.updateComponentState(GiftCardOutputData("5555444433330000", "737"))

                val componentState = expectMostRecentItem()

                assertFalse(componentState.isReady)
                assertEquals(null, componentState.lastFourDigits)
                assertEquals(GiftCardAction.Idle, componentState.giftCardAction)
            }
        }

        @Test
        fun `output data is invalid, then component state should be invalid`() = runTest {
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

            delegate.componentStateFlow.test {
                delegate.updateComponentState(GiftCardOutputData("123", "737"))

                val componentState = expectMostRecentItem()

                assertTrue(componentState.isReady)
                assertFalse(componentState.isInputValid)
                assertEquals(null, componentState.lastFourDigits)
                assertEquals(GiftCardAction.Idle, componentState.giftCardAction)
            }
        }

        @Test
        fun `encryption fails, then component state should be invalid`() = runTest {
            cardEncrypter.shouldThrowException = true

            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

            delegate.componentStateFlow.test {
                delegate.updateComponentState(GiftCardOutputData("5555444433330000", "737"))

                val componentState = expectMostRecentItem()

                assertTrue(componentState.isReady)
                assertFalse(componentState.isInputValid)
                assertEquals(null, componentState.lastFourDigits)
                assertEquals(GiftCardAction.Idle, componentState.giftCardAction)
            }
        }

        @Test
        fun `everything is valid, then component state should be good`() = runTest {
            delegate.initialize(CoroutineScope(UnconfinedTestDispatcher()))

            delegate.componentStateFlow.test {
                delegate.updateComponentState(GiftCardOutputData("5555444433330000", "737"))

                val componentState = expectMostRecentItem()

                assertNotNull(componentState.data.paymentMethod)
                assertTrue(componentState.isInputValid)
                assertTrue(componentState.isReady)
                assertEquals("0000", componentState.lastFourDigits)
                assertEquals(TEST_ORDER, componentState.data.order)
                assertEquals(GiftCardAction.CheckBalance, componentState.giftCardAction)
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
            delegate = createGiftCardDelegate(
                configuration = getDefaultGiftCardConfigurationBuilder()
                    .setSubmitButtonVisible(false)
                    .build()
            )

            assertFalse(delegate.shouldShowSubmitButton())
        }

        @Test
        fun `when submit button is configured to be visible, then it should show`() {
            delegate = createGiftCardDelegate(
                configuration = getDefaultGiftCardConfigurationBuilder()
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
    inner class BalanceStatusTest {

        @Test
        fun `when delegate is initialized then initial giftCardAction should be Idle`() = runTest {
            delegate.initialize(CoroutineScope((UnconfinedTestDispatcher())))
            delegate.componentStateFlow.test {
                val state = expectMostRecentItem()
                assertEquals(GiftCardAction.Idle, state.giftCardAction)
            }
        }

        @Test
        fun `when balance result is FullPayment then giftCardAction should be SendPayment`() = runTest {
            val fullPaymentBalanceStatus = GiftCardBalanceStatus.FullPayment(
                amountPaid = Amount(value = 50_00, currency = "EUR"),
                remainingBalance = Amount(value = 0L, currency = "EUR")
            )
            delegate.resolveBalanceStatus(fullPaymentBalanceStatus)

            delegate.componentStateFlow.test {
                val state = expectMostRecentItem()

                verify(submitHandler).onSubmit(state)
                assertEquals(GiftCardAction.SendPayment, state.giftCardAction)
            }
        }

        @Test
        fun `when balance result is PartialPayment and order is null then giftCardAction should be CreateOrder`() =
            runTest {
                delegate = createGiftCardDelegate(order = null)
                val partialPaymentBalanceStatus = GiftCardBalanceStatus.PartialPayment(
                    amountPaid = Amount(value = 50_00, currency = "EUR"),
                    remainingBalance = Amount(value = 0L, currency = "EUR")
                )
                delegate.resolveBalanceStatus(partialPaymentBalanceStatus)

                delegate.componentStateFlow.test {
                    val state = expectMostRecentItem()

                    verify(submitHandler).onSubmit(state)
                    assertEquals(GiftCardAction.CreateOrder, state.giftCardAction)
                }
            }

        @Test
        fun `when balance result is PartialPayment and order is not null then giftCardAction should be SendPayment`() =
            runTest {
                val partialPaymentBalanceStatus = GiftCardBalanceStatus.PartialPayment(
                    amountPaid = Amount(value = 50_00, currency = "EUR"),
                    remainingBalance = Amount(value = 0L, currency = "EUR")
                )
                delegate.resolveBalanceStatus(partialPaymentBalanceStatus)

                delegate.componentStateFlow.test {
                    val state = expectMostRecentItem()

                    verify(submitHandler).onSubmit(state)
                    assertEquals(GiftCardAction.SendPayment, state.giftCardAction)
                }
            }

        @Test
        fun `when balance result is NonMatchingCurrencies then an exception should be thrown`() = runTest {
            val nonMatchingCurrenciesBalanceStatus = GiftCardBalanceStatus.NonMatchingCurrencies
            delegate.resolveBalanceStatus(nonMatchingCurrenciesBalanceStatus)

            delegate.exceptionFlow.test {
                val exception = expectMostRecentItem()
                assert(exception is GiftCardException)
            }
        }

        @Test
        fun `when balance result is ZeroAmountToBePaid then an exception should be thrown`() = runTest {
            val zeroAmountToBePaidBalanceStatus = GiftCardBalanceStatus.ZeroAmountToBePaid
            delegate.resolveBalanceStatus(zeroAmountToBePaidBalanceStatus)

            delegate.exceptionFlow.test {
                val exception = expectMostRecentItem()
                assert(exception is GiftCardException)
            }
        }

        @Test
        fun `when balance result is ZeroBalance then an exception should be thrown`() = runTest {
            val zeroBalanceStatus = GiftCardBalanceStatus.ZeroBalance
            delegate.resolveBalanceStatus(zeroBalanceStatus)

            delegate.exceptionFlow.test {
                val exception = expectMostRecentItem()
                assert(exception is GiftCardException)
            }
        }
    }

    @Test
    fun `when resolveOrderResponse is called giftCardAction should be SendPayment`() = runTest {
        val orderResponse = OrderResponse(
            pspReference = "test_psp",
            orderData = "test_order_data",
            amount = null,
            remainingAmount = null
        )
        delegate.resolveOrderResponse(orderResponse)
        delegate.componentStateFlow.test {
            val state = expectMostRecentItem()

            val expectedOrderRequest = OrderRequest(
                orderData = "test_order_data",
                pspReference = "test_psp"
            )
            assertEquals(expectedOrderRequest, state.data.order)
            assertEquals(GiftCardAction.SendPayment, state.giftCardAction)
            verify(submitHandler).onSubmit(state)
        }
    }

    private fun createGiftCardDelegate(
        configuration: GiftCardConfiguration = getDefaultGiftCardConfigurationBuilder().build(),
        order: OrderRequest? = TEST_ORDER
    ) = DefaultGiftCardDelegate(
        observerRepository = PaymentObserverRepository(),
        paymentMethod = PaymentMethod(),
        order = order,
        publicKeyRepository = publicKeyRepository,
        componentParams = ButtonComponentParamsMapper(null, null).mapToParams(configuration, null),
        cardEncrypter = cardEncrypter,
        analyticsRepository = analyticsRepository,
        submitHandler = submitHandler,
    )

    private fun getDefaultGiftCardConfigurationBuilder() = GiftCardConfiguration.Builder(
        Locale.US,
        Environment.TEST,
        TEST_CLIENT_KEY
    )

    companion object {
        private const val TEST_CLIENT_KEY = "test_qwertyuiopasdfghjklzxcvbnmqwerty"
        private val TEST_ORDER = OrderRequest("PSP", "ORDER_DATA")
    }
}
