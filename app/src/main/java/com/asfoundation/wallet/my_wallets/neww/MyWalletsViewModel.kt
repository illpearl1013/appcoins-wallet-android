package com.asfoundation.wallet.my_wallets.neww

import com.asfoundation.wallet.base.Async
import com.asfoundation.wallet.base.BaseViewModel
import com.asfoundation.wallet.base.SideEffect
import com.asfoundation.wallet.base.ViewState
import com.asfoundation.wallet.home.usecases.ObserveDefaultWalletUseCase
import com.asfoundation.wallet.ui.balance.BalanceInteractor
import com.asfoundation.wallet.ui.balance.BalanceScreenModel
import com.asfoundation.wallet.ui.balance.BalanceVerificationModel
import com.asfoundation.wallet.ui.wallets.WalletsInteract
import com.asfoundation.wallet.ui.wallets.WalletsModel

object MyWalletsSideEffect : SideEffect

data class MyWalletsState(
    val walletsAsync: Async<WalletsModel> = Async.Uninitialized,
    val walletVerifiedAsync: Async<BalanceVerificationModel> = Async.Uninitialized,
    val balanceAsync: Async<BalanceScreenModel> = Async.Uninitialized,
    val walletCreationAsync: Async<Unit> = Async.Uninitialized
) : ViewState

class MyWalletsViewModel(
    private val balanceInteractor: BalanceInteractor,
    private val walletsInteract: WalletsInteract,
    private val observeDefaultWalletUseCase: ObserveDefaultWalletUseCase
) : BaseViewModel<MyWalletsState, MyWalletsSideEffect>(initialState()) {

  companion object {
    fun initialState(): MyWalletsState {
      return MyWalletsState()
    }
  }

  init {
    observeCurrentWallet()
  }

  private fun observeCurrentWallet() {
    observeDefaultWalletUseCase()
        .doOnNext { wallet ->
          val currentWalletModel = state.walletsAsync()
          if (currentWalletModel == null || currentWalletModel.currentWallet.walletAddress != wallet.address) {
            // Refresh data if our active wallet changed
            fetchWallets()
            fetchWalletVerified()
            fetchBalance()
          }

        }
        .scopedSubscribe { e -> e.printStackTrace() }
  }

  private fun fetchWallets() {
    walletsInteract.getWalletsModel()
        .asAsyncToState { wallet -> copy(walletsAsync = wallet) }
        .repeatableScopedSubscribe(MyWalletsState::walletsAsync.name) { e ->
          e.printStackTrace()
        }
  }

  private fun fetchWalletVerified() {
    balanceInteractor.observeCurrentWalletVerified()
        .asAsyncToState { verification -> copy(walletVerifiedAsync = verification) }
        .repeatableScopedSubscribe(MyWalletsState::walletVerifiedAsync.name) { e ->
          e.printStackTrace()
        }
  }

  private fun fetchBalance() {
    balanceInteractor.requestTokenConversion()
        .asAsyncToState { balance -> copy(balanceAsync = balance) }
        .repeatableScopedSubscribe(MyWalletsState::balanceAsync.name) { e ->
          e.printStackTrace()
        }
  }

  fun createNewWallet() {
    walletsInteract.createWallet()
        .asAsyncToState { copy(walletCreationAsync = it) }
        .repeatableScopedSubscribe(MyWalletsState::walletCreationAsync.name) { e ->
          e.printStackTrace()
        }
  }
}