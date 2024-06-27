package com.example.currencyconvertor

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.TextInputEditText
import com.squareup.okhttp.Callback
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), PopupMenu.OnMenuItemClickListener {
    private var currencyManager = CurrencyManager(this)

    private lateinit var baseSpinner: Spinner
    private lateinit var baseTextField: TextInputEditText
    private lateinit var quoteSpinner: Spinner
    private lateinit var quoteTextField: TextInputEditText
    private lateinit var rateView: TextView

    private val defaultBaseValue = "1.00"

    private val editListActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            currencyManager = CurrencyManager(this)
            thread {
                val currencies = getFavoriteCurrencies().values.toList()
                Handler(Looper.getMainLooper()).post { setSpinnerCurrencies(currencies) }
            }
        }

    private val onBaseItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(p0: AdapterView<*>?) = Unit
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val item = baseSpinner.getItemAtPosition(position) as Currency
            currencyManager.baseCurrency = item.code
            updateRate()
        }
    }

    private val onQuoteItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(p0: AdapterView<*>?) = Unit
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val item = quoteSpinner.getItemAtPosition(position) as Currency
            currencyManager.quoteCurrency = item.code
            updateRate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<LinearLayout>(R.id.base_currency_row).let { baseRow ->
            baseTextField = baseRow.findViewById(R.id.convert_row_textfield)
            baseSpinner = baseRow.findViewById(R.id.convert_row_spinner)

            baseTextField.apply {
                setText(defaultBaseValue)
                addTextChangedListener { updateRate() }
            }
            baseSpinner.onItemSelectedListener = onBaseItemSelectedListener
        }

        findViewById<LinearLayout>(R.id.quote_currency_row).let { quoteRow ->
            quoteTextField = quoteRow.findViewById(R.id.convert_row_textfield)
            quoteSpinner = quoteRow.findViewById(R.id.convert_row_spinner)
            quoteRow.findViewById<TextView>(R.id.convert_row_text).text =
                getString(R.string.converted_amount)

            quoteTextField.isFocusable = false
            quoteSpinner.onItemSelectedListener = onQuoteItemSelectedListener
        }

        rateView = findViewById(R.id.rate)

        thread {
            val currencies = getFavoriteCurrencies().values.toList()
            Handler(Looper.getMainLooper()).post {
                setSpinnerCurrencies(currencies)
                updateRate()
            }
        }
    }

    private fun getFavoriteCurrencies(): Map<String, Currency> =
        currencyManager.currencies.filter { currencyManager.favoriteCurrencies.contains(it.key) }

    private fun updateRate() {
        val value = baseTextField.text.toString()
        if (value.isEmpty()) return

        val base = (baseSpinner.selectedItem as Currency).code
        val quote = (quoteSpinner.selectedItem as Currency).code
        fetchExchangeRate(base, quote, value)
    }

    private fun fetchExchangeRate(fromCurrency: String, toCurrency: String, value: String) {
        val url = "https://api.freecurrencyapi.com/v1/latest?base=$fromCurrency&apikey=fca_live_zZfgNhCSmjZj5qHDzSw8eSxYtweQBGenDijZiMxr"
        val client = OkHttpClient()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "fca_live_zZfgNhCSmjZj5qHDzSw8eSxYtweQBGenDijZiMxr")
            .build()
        var rate = 0.0

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Request, e: IOException) {
                Log.e("MainActivity", "Failed to execute request: ${e.message}")
            }

            override fun onResponse(response: Response) {
                response.body().let {
                    try {
                        val jsonResponse = it.string()
                        Log.w("MainActivity", "From: ${fromCurrency}, To: ${toCurrency}")
                        Log.w("MainActivity", "Response: ${jsonResponse}")
                        val jsonObject = JSONObject(jsonResponse)
                        val rates = jsonObject.getJSONObject("data")
                        rate = rates.getDouble(toCurrency.uppercase())
                        thread {
                            val date = dateFormat.parse(dateFormat.format(Date()))
                            Handler(Looper.getMainLooper()).post {
                                if (rate == null) {
                                    quoteTextField.setText("")
                                    rateView.text = getString(R.string.no_rate_available)
                                } else {
                                    val converted = value.toDouble() * rate
                                    quoteTextField.setText(String.format("%.2f", converted))

                                    var rateViewText = String.format(
                                        "1 %s = %.2f %s",
                                        fromCurrency.uppercase(),
                                        rate,
                                        toCurrency.uppercase()
                                    )

                                    val today = dateFormat.parse(dateFormat.format(Date()))
                                    rateViewText += if (date != null && date != today) {
                                        val formattedDate = SimpleDateFormat.getDateInstance().format(date)
                                        " ($formattedDate)"
                                    } else ""

                                    rateView.text = rateViewText
                                }
                            }
                        }
                    } catch (e: JSONException) {
                        rate = 0.0
                        Log.e("MainActivity", "Error parsing JSON response: ${e.message}")
                    } finally {
                        it.close()
                    }
                }
            }
        })
    }

    private fun setSpinnerCurrencies(currencies: List<Currency>) {
        baseSpinner.adapter = SpinnerAdapter(currencies)
        quoteSpinner.adapter = SpinnerAdapter(currencies)

        val base = currencyManager.baseCurrency
        val quote = currencyManager.quoteCurrency
        if (base.isNotEmpty()) {
            val basePosition = currencies.indexOfFirst { it.code == base }
            if (basePosition != -1) baseSpinner.setSelection(basePosition)
        }
        if (quote.isNotEmpty()) {
            val quotePosition = currencies.indexOfFirst { it.code == quote }
            if (quotePosition != -1) quoteSpinner.setSelection(quotePosition)
        }
    }

    fun showPopup(view: View) {
        PopupMenu(this, view).apply {
            setOnMenuItemClickListener(this@MainActivity)
            inflate(R.menu.app_menu)
            show()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.edit_currency_list -> {
                val intent = Intent(this, CurrencyListActivity::class.java)
                editListActivityLauncher.launch(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun swapCurrency(v: View) {
        val basePosition = baseSpinner.selectedItemPosition
        val quotePosition = quoteSpinner.selectedItemPosition

        baseSpinner.setSelection(quotePosition)
        quoteSpinner.setSelection(basePosition)

        val base = baseSpinner.getItemAtPosition(basePosition) as Currency
        val quote = baseSpinner.getItemAtPosition(basePosition) as Currency
        currencyManager.baseCurrency = quote.code
        currencyManager.quoteCurrency = base.code
    }
}