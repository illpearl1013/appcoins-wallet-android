package com.asfoundation.wallet.ui.backup.entry

import io.reactivex.Observable

interface BackupWalletFragmentView {

  fun setupUi(walletAddress: String, symbol: String, formattedAmount: String)

  fun getBackupClick(): Observable<String>

  fun hideKeyboard()

  fun showPasswordFields()

  fun hidePasswordFields()
}
