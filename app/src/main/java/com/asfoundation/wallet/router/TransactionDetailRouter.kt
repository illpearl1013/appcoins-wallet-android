package com.asfoundation.wallet.router

import android.content.Context
import android.content.Intent
import com.asfoundation.wallet.C
import com.asfoundation.wallet.transactions.Transaction
import com.asfoundation.wallet.ui.balance.TransactionDetailActivity

class TransactionDetailRouter {
  fun open(context: Context, transaction: Transaction, globalBalanceCurrency: String) {
    with(context) {
      val intent = Intent(this, TransactionDetailActivity::class.java)
          .apply {
            putExtra(C.Key.TRANSACTION, transaction)
            putExtra(C.Key.GLOBAL_BALANCE_CURRENCY, globalBalanceCurrency)
          }
      startActivity(intent)
    }
  }
}