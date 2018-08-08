package com.appcoins.wallet.billing

import com.appcoins.wallet.billing.repository.BillingSupportedType
import com.appcoins.wallet.billing.repository.entity.Product
import com.appcoins.wallet.billing.repository.entity.Purchase
import io.reactivex.Single

internal interface Billing {

  fun isSubsSupported(): Single<BillingSupportType>

  fun isInAppSupported(): Single<BillingSupportType>

  fun getProducts(skus: List<String>, type: String): Single<List<Product>>

  fun getPurchases(type: BillingSupportedType): Single<List<Purchase>>

  fun consumePurchases(purchaseToken: String): Single<Boolean>

  enum class BillingSupportType {
    SUPPORTED, MERCHANT_NOT_FOUND, UNKNOWN_ERROR, NO_INTERNET_CONNECTION, API_ERROR
  }
}