/*
 * Copyright (c) 2024 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by josephj on 23/8/2024.
 */

package com.adyen.checkout.example.ui.settings

import com.adyen.checkout.example.R
import com.adyen.checkout.example.data.storage.KeyValueStorage
import com.adyen.checkout.example.ui.compose.UIText
import com.adyen.checkout.example.ui.theme.UIThemeRepository
import javax.inject.Inject

internal class SettingsUIMapper @Inject constructor(
    private val keyValueStorage: KeyValueStorage,
    private val uiThemeRepository: UIThemeRepository,
) {

    fun getSettingsCategories(): List<SettingsCategory> {
        return listOf(
            SettingsCategory(
                R.string.settings_category_integration_parameters,
                listOf(
                    getAmount(),
                    getCurrency(),
                    getCountry(),
                    getIntegrationFlow(),
                    getMerchantAccount(),
                ),
            ),
            SettingsCategory(
                R.string.settings_category_shopper_information,
                listOf(
                    getShopperLocale(),
                    getShopperReference(),
                    getShopperEmail(),
                ),
            ),
            SettingsCategory(
                R.string.settings_category_card,
                listOf(
                    getThreeDSMode(),
                    getAddressMode(),
                    getInstallmentOptionsMode(),
                    getInstallmentAmountShown(),
                ),
            ),
            SettingsCategory(
                R.string.settings_category_drop_in,
                listOf(
                    getRemoveStoredPaymentMethodEnabled(),
                    getSplitCardFundingSources(),
                ),
            ),
            SettingsCategory(
                R.string.settings_category_analytics,
                listOf(
                    getAnalyticsLevel(),
                ),
            ),
            SettingsCategory(
                R.string.settings_category_app,
                listOf(
                    getInstantPaymentMethodType(),
                    getUITheme(),
                ),
            ),
        )
    }

    private fun getMerchantAccount(): SettingsItem {
        return SettingsItem.Text(
            identifier = SettingsIdentifier.MERCHANT_ACCOUNT,
            titleResId = R.string.settings_title_merchant_account,
            subtitle = UIText.String(keyValueStorage.getMerchantAccount()),
        )
    }

    private fun getAmount(): SettingsItem {
        return SettingsItem.Text(
            identifier = SettingsIdentifier.AMOUNT,
            titleResId = R.string.settings_title_amount,
            subtitle = UIText.String(keyValueStorage.getAmount().value.toString()),
        )
    }

    private fun getCurrency(): SettingsItem {
        val subtitle = keyValueStorage.getAmount().currency?.let {
            UIText.String(it)
        } ?: UIText.Resource(R.string.settings_null_value_placeholder)

        return SettingsItem.Text(
            identifier = SettingsIdentifier.CURRENCY,
            titleResId = R.string.settings_title_currency,
            subtitle = subtitle,
        )
    }

    private fun getThreeDSMode(): SettingsItem {
        val threeDSMode = keyValueStorage.getThreeDSMode()
        val displayValue = requireNotNull(SettingsLists.threeDSModes[threeDSMode])

        return SettingsItem.Text(
            identifier = SettingsIdentifier.THREE_DS_MODE,
            titleResId = R.string.settings_title_threeds_mode,
            subtitle = UIText.Resource(displayValue),
        )
    }

    private fun getShopperReference(): SettingsItem {
        return SettingsItem.Text(
            identifier = SettingsIdentifier.SHOPPER_REFERENCE,
            titleResId = R.string.settings_title_shopper_reference,
            subtitle = UIText.String(keyValueStorage.getShopperReference()),
        )
    }

    private fun getCountry(): SettingsItem {
        return SettingsItem.Text(
            identifier = SettingsIdentifier.COUNTRY,
            titleResId = R.string.settings_title_country,
            subtitle = UIText.String(keyValueStorage.getCountry()),
        )
    }

    private fun getIntegrationFlow(): SettingsItem {
        val integrationFlow = keyValueStorage.getIntegrationFlow()
        val displayValue = requireNotNull(SettingsLists.integrationFlows[integrationFlow])

        return SettingsItem.Text(
            identifier = SettingsIdentifier.INTEGRATION_FLOW,
            titleResId = R.string.settings_title_integration_flow,
            subtitle = UIText.Resource(displayValue),
        )
    }

    private fun getShopperLocale(): SettingsItem {
        val subtitle = keyValueStorage.getShopperLocale()?.let {
            UIText.String(it)
        } ?: UIText.Resource(R.string.settings_null_value_placeholder)

        return SettingsItem.Text(
            identifier = SettingsIdentifier.SHOPPER_LOCALE,
            titleResId = R.string.settings_title_shopper_locale,
            subtitle = subtitle,
        )
    }

    private fun getShopperEmail(): SettingsItem {
        val subtitle = keyValueStorage.getShopperEmail()?.let {
            UIText.String(it)
        } ?: UIText.Resource(R.string.settings_null_value_placeholder)

        return SettingsItem.Text(
            identifier = SettingsIdentifier.SHOPPER_EMAIL,
            titleResId = R.string.settings_title_shopper_email,
            subtitle = subtitle,
        )
    }

    private fun getAddressMode(): SettingsItem {
        val cardAddressMode = keyValueStorage.getCardAddressMode()
        val displayValue = requireNotNull(SettingsLists.cardAddressModes[cardAddressMode])

        return SettingsItem.Text(
            identifier = SettingsIdentifier.ADDRESS_MODE,
            titleResId = R.string.settings_title_address_mode,
            subtitle = UIText.Resource(displayValue),
        )
    }

    private fun getInstallmentOptionsMode(): SettingsItem {
        val installmentOptionsMode = keyValueStorage.getInstallmentOptionsMode()
        val displayValue = requireNotNull(SettingsLists.cardInstallmentOptionsModes[installmentOptionsMode])

        return SettingsItem.Text(
            identifier = SettingsIdentifier.INSTALLMENTS_MODE,
            titleResId = R.string.settings_title_card_installment_options_mode,
            subtitle = UIText.Resource(displayValue),
        )
    }

    private fun getInstallmentAmountShown(): SettingsItem {
        return SettingsItem.Switch(
            identifier = SettingsIdentifier.SHOW_INSTALLMENT_AMOUNT,
            titleResId = R.string.settings_title_card_installment_show_amount,
            checked = keyValueStorage.isInstallmentAmountShown(),
        )
    }

    private fun getSplitCardFundingSources(): SettingsItem {
        return SettingsItem.Switch(
            identifier = SettingsIdentifier.SPLIT_CARD_FUNDING_SOURCES,
            titleResId = R.string.settings_title_split_card_funding_sources,
            checked = keyValueStorage.isSplitCardFundingSources(),
        )
    }

    private fun getRemoveStoredPaymentMethodEnabled(): SettingsItem {
        return SettingsItem.Switch(
            identifier = SettingsIdentifier.REMOVE_STORED_PAYMENT_METHOD,
            titleResId = R.string.settings_title_remove_stored_payment_method,
            checked = keyValueStorage.isRemoveStoredPaymentMethodEnabled(),
        )
    }

    private fun getInstantPaymentMethodType(): SettingsItem {
        return SettingsItem.Text(
            identifier = SettingsIdentifier.INSTANT_PAYMENT_METHOD_TYPE,
            titleResId = R.string.settings_title_instant_payment_method_type,
            subtitle = UIText.String(keyValueStorage.getInstantPaymentMethodType()),
        )
    }

    private fun getAnalyticsLevel(): SettingsItem {
        val analyticsLevel = keyValueStorage.getAnalyticsLevel()
        val displayValue = requireNotNull(SettingsLists.analyticsLevels[analyticsLevel])

        return SettingsItem.Text(
            identifier = SettingsIdentifier.ANALYTICS_LEVEL,
            titleResId = R.string.settings_title_analytics_level,
            subtitle = UIText.Resource(displayValue),
        )
    }

    private fun getUITheme(): SettingsItem {
        val theme = uiThemeRepository.theme
        val displayValue = requireNotNull(SettingsLists.uiThemes[theme])
        return SettingsItem.Text(
            identifier = SettingsIdentifier.UI_THEME,
            titleResId = R.string.settings_title_ui_theme,
            subtitle = UIText.Resource(displayValue),
        )
    }
}
