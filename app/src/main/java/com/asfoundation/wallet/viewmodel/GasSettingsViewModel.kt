package com.asfoundation.wallet.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.asfoundation.wallet.entity.GasSettings
import com.asfoundation.wallet.entity.NetworkInfo
import com.asfoundation.wallet.ui.GasSettingsInteractor
import java.math.BigDecimal

class GasSettingsViewModel(private val gasSettingsInteractor: GasSettingsInteractor) :
    BaseViewModel() {

  private val gasPrice = MutableLiveData<BigDecimal>()
  private val gasLimit = MutableLiveData<BigDecimal>()
  private val defaultNetwork = MutableLiveData<NetworkInfo>()

  init {
    gasPrice.value = BigDecimal.ZERO
    gasLimit.value = BigDecimal.ZERO
  }

  fun prepare() {
    disposable.add(gasSettingsInteractor.findDefaultNetwork()
        .subscribe(
            { networkInfo: NetworkInfo ->
              onDefaultNetwork(networkInfo)
            }) { throwable: Throwable? -> onError(throwable) })
  }

  fun gasPrice(): MutableLiveData<BigDecimal> = gasPrice

  fun gasLimit(): MutableLiveData<BigDecimal> = gasLimit

  fun defaultNetwork(): LiveData<NetworkInfo> = defaultNetwork

  private fun onDefaultNetwork(networkInfo: NetworkInfo) = defaultNetwork.postValue(networkInfo)

  fun networkFee(): BigDecimal {
    return gasPrice.value!!
        .multiply(gasLimit.value)
  }

  fun saveChanges(gasPrice: BigDecimal?, gasLimit: BigDecimal?) {
    if (gasPrice != null && gasLimit != null) {
      gasSettingsInteractor.saveGasPreferences(gasPrice, gasLimit)
    }
  }

  fun getSavedGasPreferences(): GasSettings = gasSettingsInteractor.getSavedGasPreferences()

  companion object {
    const val SET_GAS_SETTINGS = 1
  }
}