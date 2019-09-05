package com.asfoundation.wallet.topup

import com.appcoins.wallet.gamification.repository.ForecastBonus
import com.asfoundation.wallet.topup.TopUpData.Companion.DEFAULT_VALUE
import com.asfoundation.wallet.topup.paymentMethods.PaymentMethodData
import com.asfoundation.wallet.ui.iab.FiatValue
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction
import java.math.BigDecimal
import java.util.concurrent.TimeUnit


class TopUpFragmentPresenter(private val view: TopUpFragmentView,
                             private val activity: TopUpActivityView?,
                             private val interactor: TopUpInteractor,
                             private val viewScheduler: Scheduler,
                             private val networkScheduler: Scheduler) {

  private val disposables: CompositeDisposable = CompositeDisposable()

  companion object {
    private const val NUMERIC_REGEX = "-?\\d+(\\.\\d+)?"
  }

  fun present(appPackage: String) {
    setupUi()
    handleChangeCurrencyClick()
    handleNextClick()
    handleAmountChange(appPackage)
    handlePaymentMethodSelected()
  }

  fun stop() {
    disposables.dispose()
  }

  private fun setupUi() {
    disposables.add(Single.zip(
        interactor.getPaymentMethods().subscribeOn(networkScheduler).observeOn(viewScheduler),
        interactor.getLocalCurrency().subscribeOn(networkScheduler).observeOn(viewScheduler),
        BiFunction { paymentMethods: List<PaymentMethodData>, currency: LocalCurrency ->
          view.setupUiElements(filterPaymentMethods(paymentMethods), currency)
        }).subscribe({ }, { throwable -> throwable.printStackTrace() }))
  }

  private fun handleChangeCurrencyClick() {
    disposables.add(
        view.getChangeCurrencyClick().doOnNext {
          view.toggleSwitchCurrencyOn()
          view.rotateChangeCurrencyButton()
          view.switchCurrencyData()
          view.toggleSwitchCurrencyOff()
        }.subscribe())
  }

  private fun handleNextClick() {
    disposables.add(
        view.getNextClick()
            .filter {
              it.currency.appcValue != DEFAULT_VALUE && it.currency.fiatValue != DEFAULT_VALUE
            }.doOnNext {
              view.showLoading()
              activity?.navigateToPayment(it.paymentMethod!!, it, it.selectedCurrency, "BDS",
                  "TOPUP", it.bonusValue)
            }.subscribe())
  }

  private fun handleAmountChange(packageName: String) {
    disposables.add(view.getEditTextChanges().filter {
      isNumericOrEmpty(it)
    }.doOnNext { view.setNextButtonState(false) }.debounce(700, TimeUnit.MILLISECONDS)
        .switchMap { topUpData ->
          getConvertedValue(topUpData)
              .subscribeOn(networkScheduler)
              .map { value ->
                if (topUpData.selectedCurrency == TopUpData.FIAT_CURRENCY) {
                  topUpData.currency.appcValue =
                      if (value.amount == BigDecimal.ZERO) DEFAULT_VALUE else value.amount.toString()
                } else {
                  topUpData.currency.fiatValue =
                      if (value.amount == BigDecimal.ZERO) DEFAULT_VALUE else value.amount.toString()
                }
                return@map topUpData
              }
              .doOnError { it.printStackTrace() }
              .onErrorResumeNext(Observable.empty())
              .observeOn(viewScheduler)
              .filter { it.currency.appcValue != "--" }
              .doOnComplete {
                view.setConversionValue(topUpData)
                handleInvalidValueInput(packageName, topUpData)
              }
        }
        .subscribe())
  }

  private fun isNumericOrEmpty(data: TopUpData): Boolean {
    return if (data.selectedCurrency == TopUpData.FIAT_CURRENCY) {
      data.currency.fiatValue == DEFAULT_VALUE || data.currency.fiatValue.matches(
          NUMERIC_REGEX.toRegex())
    } else {
      data.currency.appcValue == DEFAULT_VALUE || data.currency.appcValue.matches(
          NUMERIC_REGEX.toRegex())
    }
  }

  private fun hasValidData(data: TopUpData): Boolean {
    return isValidValue(data.currency.fiatValue) &&
        isValidValue(data.currency.appcValue)
        && data.paymentMethod != null
  }

  private fun isValidValue(amount: String): Boolean {
    return amount.matches(NUMERIC_REGEX.toRegex())
        && BigDecimal(amount) > BigDecimal.ZERO
  }

  private fun filterPaymentMethods(methods: List<PaymentMethodData>): List<PaymentMethodData> {
    return methods.filter { it.id == "paypal" || it.id == "credit_card" }
  }

  private fun getConvertedValue(data: TopUpData): Observable<FiatValue> {
    return if (data.selectedCurrency == TopUpData.FIAT_CURRENCY
        && data.currency.fiatValue != DEFAULT_VALUE) {
      interactor.convertLocal(data.currency.fiatCurrencyCode,
          data.currency.fiatValue, 2)
    } else if (data.selectedCurrency == TopUpData.APPC_C_CURRENCY
        && data.currency.appcValue != DEFAULT_VALUE) {
      interactor.convertAppc(data.currency.appcValue)
    } else {
      Observable.just(FiatValue(BigDecimal.ZERO, ""))
    }
  }

  private fun handlePaymentMethodSelected() {
    disposables.add(view.getPaymentMethodClick().doOnNext { view.hideKeyboard() }.subscribe())
  }

  private fun loadBonusIntoView(appPackage: String, amount: String,
                                currency: String): Observable<ForecastBonus> {
    return interactor.convertLocal(currency, amount, 18)
        .flatMapSingle { interactor.getEarningBonus(appPackage, it.amount) }
        .subscribeOn(networkScheduler)
        .observeOn(viewScheduler)
        .doOnNext {
          if (it.status != ForecastBonus.Status.ACTIVE || it.amount <= BigDecimal.ZERO) {
            view.hideBonus()
          } else {
            view.showBonus(it.amount, it.currency)
          }
        }
  }

  private fun handleInvalidValueInput(packageName: String, topUpData: TopUpData) {
    if (topUpData.currency.fiatValue != DEFAULT_VALUE) {
      val value =
          FiatValue(BigDecimal(topUpData.currency.fiatValue), topUpData.currency.fiatCurrencyCode,
              topUpData.currency.fiatCurrencySymbol)
      disposables.add(fetchMinAndMaxValues()
          .subscribeOn(networkScheduler)
          .observeOn(viewScheduler)
          .doOnSuccess {
            showValueWarning(it.first, it.second, value)
            handleShowBonus(packageName, topUpData, it.first, it.second, value)
          }
          .subscribe())
    }
  }

  private fun fetchMinAndMaxValues(): Single<Pair<FiatValue, FiatValue>> {
    return Single.zip(interactor.getMaxTopUpValue(), interactor.getMinTopUpValue(),
        BiFunction { maxValue: FiatValue, minValue: FiatValue -> Pair(maxValue, minValue) })
  }

  private fun handleShowBonus(appPackage: String, topUpData: TopUpData, maxValue: FiatValue,
                              minValue: FiatValue, value: FiatValue) {
    if (value.amount < minValue.amount || value.amount > maxValue.amount) {
      view.hideBonus()
      view.changeMainValueColor(false)
    } else {
      view.changeMainValueColor(true)
      disposables.add(loadBonusIntoView(appPackage, topUpData.currency.fiatValue,
          topUpData.currency.fiatCurrencyCode)
          .doOnNext { view.setNextButtonState(hasValidData(topUpData)) }
          .subscribe())
    }
  }

  private fun showValueWarning(maxValue: FiatValue, minValue: FiatValue, value: FiatValue) {
    val localCurrency = " ${maxValue.currency}"
    if (value.amount > maxValue.amount) {
      view.showMaxValueWarning(maxValue.amount.toPlainString() + localCurrency)
    } else {
      if (value.amount < minValue.amount) {
        view.showMinValueWarning(minValue.amount.toPlainString() + localCurrency)
      } else {
        view.hideValueInputWarning()
      }
    }
  }
}
