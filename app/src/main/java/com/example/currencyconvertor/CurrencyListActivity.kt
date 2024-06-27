package com.example.currencyconvertor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread


class CurrencyListActivity : AppCompatActivity() {
    private val currencyManager = CurrencyManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_currency_list)

        val recyclerView = findViewById<RecyclerView>(R.id.recycle_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        thread {
            val favorites = currencyManager.favoriteCurrencies
            val currencies = currencyManager.currencies.values.toList().sortedByDescending{
                favorites.contains(it.code)
            }

            CurrencyListAdapter.selectedCurrencySet = favorites
            recyclerView.adapter = CurrencyListAdapter(currencies)
        }

        CurrencyListAdapter.onClickListener = { _, position, value ->
            val selected = CurrencyListAdapter.selectedCurrencySet.orEmpty()
            if (selected.contains(value.code)) currencyManager.removeFavorite(value.code)
            else currencyManager.addFavorite(value.code)
            CurrencyListAdapter.selectedCurrencySet = currencyManager.favoriteCurrencies
            recyclerView.adapter?.notifyItemChanged(position)
        }


        supportActionBar?.setDisplayHomeAsUpEnabled(true);

    }
}