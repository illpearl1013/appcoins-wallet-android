package com.asfoundation.wallet.ui.iab

import android.net.Uri
import android.os.Bundle
import com.appcoins.wallet.bdsbilling.Billing
import com.appcoins.wallet.bdsbilling.repository.entity.Transaction
import com.appcoins.wallet.bdsbilling.repository.entity.Transaction.Status.*
import com.appcoins.wallet.billing.BillingMessagesMapper
import com.asfoundation.wallet.billing.share.ShareLinkRepository
import com.asfoundation.wallet.interact.FindDefaultWalletInteract
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import java.util.concurrent.TimeUnit

class LocalPaymentInteractor(private val remoteRepository: ShareLinkRepository,
                             private val walletInteractor: FindDefaultWalletInteract,
                             private val inAppPurchaseInteractor: InAppPurchaseInteractor,
                             private val billing: Billing,
                             private val billingMessagesMapper: BillingMessagesMapper
) {

  fun getPaymentLink(domain: String, skuId: String?,
                     originalAmount: String?, originalCurrency: String?,
                     paymentMethod: String): Single<String> {

    return walletInteractor.find()
        .flatMap {
          remoteRepository.getLink(domain, skuId, null, it.address, originalAmount,
              originalCurrency, paymentMethod)
        }
  }

  fun getTransaction(uri: Uri): Observable<Transaction> {
    return inAppPurchaseInteractor.getTransaction(uri.lastPathSegment)
        .filter {
          isEndingState(it.status)
        }
        .distinctUntilChanged()
  }

  private fun isEndingState(status: Transaction.Status): Boolean {
    return status == PENDING_USER_PAYMENT || status == COMPLETED || status == FAILED || status == CANCELED || status == INVALID_TRANSACTION
  }

  fun getCompletePurchaseBundle(isInApp: Boolean, merchantName: String, sku: String?,
                                scheduler: Scheduler,
                                orderReference: String?, hash: String?): Single<Bundle> {
    return if (isInApp && sku != null) {
      billing.getSkuPurchase(merchantName, sku, scheduler)
          .retryWhen { throwableFlowable ->
            throwableFlowable.delay(3, TimeUnit.SECONDS)
                .map { 0 }
                .timeout(3, TimeUnit.MINUTES)
          }
          .map {
            billingMessagesMapper.mapPurchase(it,
                orderReference)
          }
    } else {
      Single.just(billingMessagesMapper.successBundle(hash))
    }
  }
}
