/*
 * Copyright (c) 2024 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by josephj on 21/8/2024.
 */

package com.adyen.checkout.example.ui.settings

import androidx.annotation.StringRes

internal sealed class SettingsItem(
    open val identifier: SettingsIdentifier,
    @StringRes open val titleResId: Int,
) {
    class Text(
        override val identifier: SettingsIdentifier,
        @StringRes override val titleResId: Int,
        val subtitle: String,
    ) : SettingsItem(
        identifier, titleResId,
    )

    class Switch(
        override val identifier: SettingsIdentifier,
        @StringRes override val titleResId: Int,
        val checked: Boolean,
    ) : SettingsItem(
        identifier, titleResId,
    )
}
