/*
 * Copyright (c) 2023 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by josephj on 13/12/2023.
 */

package com.adyen.checkout.example.ui.googlepay

import android.content.Intent

internal data class GooglePayActivityResult(
    val resultCode: Int,
    val data: Intent?,
)
