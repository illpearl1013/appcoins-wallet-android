package com.asfoundation.wallet.viewmodel

import android.app.Activity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.asfoundation.wallet.entity.GasSettings
import com.asfoundation.wallet.entity.PendingTransaction
import com.asfoundation.wallet.entity.TransactionBuilder
import com.asfoundation.wallet.logging.Logger
import com.asfoundation.wallet.router.GasSettingsRouter
import com.asfoundation.wallet.ui.TransferConfirmationInteractor
import com.asfoundation.wallet.util.BalanceUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable

class TransferConfirmationViewModel internal constructor(
    private val transferConfirmationInteractor: TransferConfirmationInteractor,
    private val gasSettingsRouter: GasSettingsRouter,
    private val logger: Logger) : BaseViewModel() {

  private val transactionBuilder = MutableLiveData<TransactionBuilder>()
  private val transactionHash = MutableLiveData<PendingTransaction>()
  private var subscription: Disposable? = null

  companion object {
    private val TAG = TransferConfirmationViewModel::class.java.simpleName
  }

  fun init(transactionBuilder: TransactionBuilder) {
    subscription =
        transferConfirmationInteractor.fetchGasSettings(transactionBuilder.shouldSendToken())
            .doOnSuccess { gasSettings: GasSettings? ->
              transactionBuilder.gasSettings(gasSettings)
              this.transactionBuilder.postValue(transactionBuilder)
            }
            .subscribe({}, { throwable: Throwable -> onError(throwable) })
  }

  override fun onCleared() {
    subscription?.let {
      if (!it.isDisposed) {
        it.dispose()
      }
    }
    super.onCleared()
  }

  override fun onError(throwable: Throwable) {
    super.onError(throwable)
    logger.log(TAG, throwable.message, throwable)
  }

  fun transactionBuilder(): LiveData<TransactionBuilder> {
    return transactionBuilder
  }

  fun transactionHash(): LiveData<PendingTransaction> {
    return transactionHash
  }

  fun openGasSettings(context: Activity?) {
    val transactionBuilder = transactionBuilder.value
    transactionBuilder?.let {
      gasSettingsRouter.open(context, it.gasSettings())
    }
  }

  private fun onCreateTransaction(pendingTransaction: PendingTransaction) {
    transactionHash.postValue(pendingTransaction)
  }

  fun progressFinished() {
    progress.postValue(false)
  }

  fun send() {
    progress.postValue(true)
    disposable.add(transferConfirmationInteractor.send(transactionBuilder.value)
        .map { hash: String? -> PendingTransaction(hash, false) }
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            { pendingTransaction: PendingTransaction ->
              onCreateTransaction(pendingTransaction)
            }) { throwable: Throwable -> onError(throwable) })
  }

  fun setGasSettings(gasSettings: GasSettings?) {
    val transactionBuilder = transactionBuilder.value
    val gasPriceWei = BalanceUtils.gweiToWei(gasSettings?.gasPrice)
    val newGasSettings = GasSettings(gasPriceWei, gasSettings?.gasLimit)
    transactionBuilder?.let {
      it.gasSettings(newGasSettings)
      this.transactionBuilder.value = it // refresh view
    }
  }

  fun getGasPreferences(): GasSettings = transferConfirmationInteractor.getGasPreferences()
}