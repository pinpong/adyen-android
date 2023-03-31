/*
 * Copyright (c) 2023 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by atef on 31/3/2023.
 */

package com.adyen.checkout.boleto.internal.ui.model

import com.adyen.checkout.ui.core.internal.ui.model.AddressFieldPolicy
import kotlinx.parcelize.Parcelize

@Parcelize
internal sealed class AddressFieldPolicyParams : AddressFieldPolicy {

    /**
     * Address form fields will be required.
     */
    @Parcelize
    object Required : AddressFieldPolicyParams()
}
