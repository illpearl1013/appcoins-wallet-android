package com.asfoundation.wallet.billing.adyen

import android.os.Bundle
import com.adyen.checkout.core.model.ModelObject
import com.appcoins.wallet.bdsbilling.Billing
import com.appcoins.wallet.bdsbilling.repository.entity.Price
import com.appcoins.wallet.billing.BillingMessagesMapper
import com.appcoins.wallet.billing.adyen.AdyenPaymentRepository
import com.appcoins.wallet.billing.adyen.PaymentInfoModel
import com.appcoins.wallet.billing.adyen.PaymentModel
import com.appcoins.wallet.billing.adyen.TransactionResponse
import com.asfoundation.wallet.billing.partners.AddressService
import com.asfoundation.wallet.interact.FindDefaultWalletInteract
import com.asfoundation.wallet.ui.iab.FiatValue
import com.asfoundation.wallet.ui.iab.InAppPurchaseInteractor
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AdyenPaymentInteractor(
    private val adyenPaymentRepository: AdyenPaymentRepository,
    private val inAppPurchaseInteractor: InAppPurchaseInteractor,
    private val billingMessagesMapper: BillingMessagesMapper,
    private val findDefaultWalletInteract: FindDefaultWalletInteract,
    private val partnerAddressService: AddressService,
    private val billing: Billing
) {

  fun loadPaymentInfo(methods: AdyenPaymentRepository.Methods, value: String,
                      currency: String): Single<PaymentInfoModel> {
    return findDefaultWalletInteract.find()
        .flatMap { adyenPaymentRepository.loadPaymentInfo(methods, value, currency, it.address) }
  }

  fun makePayment(adyenPaymentMethod: ModelObject, returnUrl: String, value: String,
                  currency: String,
                  reference: String?, paymentType: String, origin: String?, packageName: String,
                  metadata: String?, sku: String?, callbackUrl: String?,
                  transactionType: String, developerWallet: String?): Single<PaymentModel> {
    return findDefaultWalletInteract.find()
        .flatMap { wallet ->
          Single.zip(
              partnerAddressService.getStoreAddressForPackage(packageName),
              partnerAddressService.getOemAddressForPackage(packageName),
              BiFunction { storeAddress: String, oemAddress: String ->
                Pair(storeAddress, oemAddress)
              })
              .flatMap {
                adyenPaymentRepository.makePayment(adyenPaymentMethod, returnUrl, value, currency,
                    reference, paymentType, wallet.address, origin, packageName, metadata, sku,
                    callbackUrl, transactionType, developerWallet, it.first, it.second,
                    wallet.address)
              }
        }
  }

  fun makeTopUpPayment(adyenPaymentMethod: ModelObject, returnUrl: String, value: String,
                       currency: String, paymentType: String, transactionType: String,
                       packageName: String): Single<PaymentModel> {
    return findDefaultWalletInteract.find()
        .flatMap {
          adyenPaymentRepository.makePayment(adyenPaymentMethod, returnUrl, value, currency, null,
              paymentType, it.address, null, packageName, null, null, null, transactionType,
              null, null, null, null)
        }
  }

  fun submitRedirect(uid: String, details: JSONObject,
                     paymentData: String?): Single<PaymentModel> {
    return findDefaultWalletInteract.find()
        .flatMap { adyenPaymentRepository.submitRedirect(uid, it.address, details, paymentData) }
  }

  fun disablePayments(): Single<Boolean> {
    return findDefaultWalletInteract.find()
        .flatMap { adyenPaymentRepository.disablePayments(it.address) }
  }

  fun convertToFiat(amount: Double, currency: String): Single<FiatValue> {
    return inAppPurchaseInteractor.convertToFiat(amount, currency)
  }

  fun mapCancellation(): Bundle {
    return billingMessagesMapper.mapCancellation()
  }

  fun removePreSelectedPaymentMethod() {
    inAppPurchaseInteractor.removePreSelectedPaymentMethod()
  }

  fun getCompletePurchaseBundle(type: String, merchantName: String, sku: String?,
                                orderReference: String?, hash: String?,
                                scheduler: Scheduler): Single<Bundle> {
    return if (isInApp(type) && sku != null) {
      billing.getSkuPurchase(merchantName, sku, scheduler)
          .map { billingMessagesMapper.mapPurchase(it, orderReference) }
    } else {
      Single.just(billingMessagesMapper.successBundle(hash))
    }
  }

  fun convertToLocalFiat(doubleValue: Double): Single<FiatValue> {
    return inAppPurchaseInteractor.convertToLocalFiat(doubleValue)
  }

  fun getTransactionAmount(uid: String): Single<Price> {
    return inAppPurchaseInteractor.getTransactionAmount(uid)
  }

  fun getTransaction(uid: String): Observable<PaymentModel> {
    return Observable.interval(0, 5, TimeUnit.SECONDS,
        Schedulers.io())
        .timeInterval()
        .switchMap {
          adyenPaymentRepository.getTransaction(uid)
              .toObservable()
        }
        .filter { isEndingState(it.status) }
  }

  private fun isEndingState(status: TransactionResponse.Status): Boolean {
    return (status == TransactionResponse.Status.COMPLETED || status == TransactionResponse.Status.FAILED || status == TransactionResponse.Status.CANCELED || status == TransactionResponse.Status.INVALID_TRANSACTION)
  }

  private fun isInApp(type: String): Boolean {
    return type.equals("INAPP", ignoreCase = true)
  }

  companion object {

    private val TOPUP = "topup"
    private val IAB = "iab"
  }
}