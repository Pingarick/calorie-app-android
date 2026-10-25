package com.calorie.app.ui.week

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.calorie.app.databinding.ActivityWeekBinding
import kotlinx.coroutines.launch

/**
 * Вся неделя одним экраном. Тап по дню возвращает в рацион с выбранным днём,
 * чтобы не листать стрелками.
 */
class WeekActivity : AppCompatActivity() {

    private lateinit var b: ActivityWeekBinding
    private lateinit var vm: WeekViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityWeekBinding.inflate(layoutInflater)
        setContentView(b.root)

        vm = ViewModelProvider(this)[WeekViewModel::class.java]
        val adapter = WeekAdapter { offset ->
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(EXTRA_OFFSET, offset)
            )
            finish()
        }
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter
        b.btnBack.setOnClickListener { finish() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.rows.collect(adapter::submit)
            }
        }
    }

    companion object {
        const val EXTRA_OFFSET = "extra_offset"
    }
}
