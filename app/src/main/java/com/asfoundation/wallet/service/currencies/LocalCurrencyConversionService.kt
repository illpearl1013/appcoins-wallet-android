package com.asfoundation.wallet.service.currencies

import com.asf.wallet.BuildConfig
import com.asfoundation.wallet.entity.ConversionResponseBody
import com.asfoundation.wallet.ui.iab.FiatValue
import io.reactivex.Observable
import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.math.RoundingMode

class LocalCurrencyConversionService(
    private val tokenToLocalFiatApi: TokenToLocalFiatApi,
    private val currencyConversionRatesPersistence: CurrencyConversionRatesPersistence) {

  val localCurrency: Single<FiatValue>
    get() = getAppcToLocalFiat("1.0", 18).firstOrError()

  fun getAppcToLocalFiat(value: String, scale: Int,
                         getFromCache: Boolean = false): Observable<FiatValue> {
    return if (getFromCache) {
      currencyConversionRatesPersistence.getAppcToLocalFiat(value, scale)
          .toObservable()
    } else getValueToFiat(value, "APPC", null, scale)
        .flatMap {
          currencyConversionRatesPersistence.saveRateFromAppcToFiat(value, it.amount
              .toString(), it.currency, it.symbol)
              .andThen(Observable.just(it))
              .onErrorReturn { throwable: Throwable ->
                throwable.printStackTrace()
                it
              }
        }
  }

  fun getEtherToLocalFiat(value: String, scale: Int): Observable<FiatValue> {
    return getValueToFiat(value, "ETH", null, scale)
        .flatMap {
          currencyConversionRatesPersistence.saveRateFromEthToFiat(value, it.amount
              .toString(), it.currency, it.symbol)
              .andThen(Observable.just(it))
              .onErrorReturn { throwable: Throwable ->
                throwable.printStackTrace()
                it
              }
        }
  }

  fun getFiatToAppc(currency: String, value: String, scale: Int): Observable<FiatValue> {
    return tokenToLocalFiatApi.convertFiatToAppc(currency, value)
        .map { response: ConversionResponseBody ->
          FiatValue(response.appcValue
              .setScale(scale, RoundingMode.FLOOR), response.currency, response.symbol)
        }
  }

  fun getValueToFiat(value: String, currency: String, targetCurrency: String? = null,
                     scale: Int): Observable<FiatValue> {
    val api = if (targetCurrency != null) tokenToLocalFiatApi.getValueToTargetFiat(currency, value,
        targetCurrency) else tokenToLocalFiatApi.getValueToTargetFiat(currency, value)
    return api.map { response: ConversionResponseBody ->
      FiatValue(response.appcValue
          .setScale(scale, RoundingMode.FLOOR), response.currency, response.symbol)
    }
  }

  fun getFiatToLocalFiat(currency: String, value: String, scale: Int): Observable<FiatValue> {
    return tokenToLocalFiatApi.getValueToTargetFiat(currency, value)
      .map { response: ConversionResponseBody ->
        FiatValue(response.appcValue
          .setScale(scale, RoundingMode.FLOOR), response.currency, response.symbol)
      }
  }

  interface TokenToLocalFiatApi {
    @GET("broker/8.20180518/exchanges/{currency}/convert/{value}")
    fun getValueToTargetFiat(@Path("currency") currency: String,
                             @Path("value") value: String): Observable<ConversionResponseBody>

    @GET("broker/8.20180518/exchanges/{currency}/convert/{value}")
    fun getValueToTargetFiat(@Path("currency") currency: String,
                             @Path("value") value: String,
                             @Query("to")
                            targetCurrency: String): Observable<ConversionResponseBody>

    @GET("broker/8.20180518/exchanges/{currency}/convert/{value}?to=APPC")
    fun convertFiatToAppc(@Path("currency") currency: String,
                           @Path("value") value: String): Observable<ConversionResponseBody>
  }

  companion object {
    const val CONVERSION_HOST = BuildConfig.BASE_HOST
  }
}