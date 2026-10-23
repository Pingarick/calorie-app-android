package com.calorie.app.ui.prefs

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.calorie.app.databinding.ActivityKnownPrefsBinding
import kotlinx.coroutines.launch

/**
 * «Что я знаю о тебе»: прозрачность персонализации. Пользователь видит выученные
 * веса, может поправить их оценкой или удалить полностью.
 */
class KnownPrefsActivity : AppCompatActivity() {

    private lateinit var b: ActivityKnownPrefsBinding
    private lateinit var vm: KnownPrefsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKnownPrefsBinding.inflate(layoutInflater)
        setContentView(b.root)

        vm = ViewModelProvider(this)[KnownPrefsViewModel::class.java]
        val adapter = KnownPrefsAdapter(
            onLike = { vm.like(it) },
            onDislike = { vm.dislike(it) },
            onForget = { vm.forget(it) }
        )
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter
        b.btnBack.setOnClickListener { finish() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.rows.collect { rows ->
                    adapter.submit(rows)
                    b.txtEmpty.isVisible = rows.isEmpty()
                    b.recycler.isVisible = rows.isNotEmpty()
                }
            }
        }
    }
}
