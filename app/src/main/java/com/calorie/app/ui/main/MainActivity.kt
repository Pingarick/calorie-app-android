package com.calorie.app.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.calorie.app.R
import com.calorie.app.ui.profile.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        if (savedInstanceState == null) show(DiaryFragment(), TAG_DIARY)

        nav.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_profile -> {
                    show(ProfileFragment(), TAG_PROFILE)
                    true
                }
                else -> {
                    show(DiaryFragment(), TAG_DIARY)
                    true
                }
            }
        }
    }

    /**
     * Показывает вкладку, не пересоздавая уже открытую. С `replace` каждая смена
     * вкладки убивала фрагмент вместе с его ViewModel — сбрасывался выбранный день
     * и прокрутка. Здесь прячем невидимые и показываем нужный.
     */
    private fun show(fragment: Fragment, tag: String) {
        val fm = supportFragmentManager
        val existing = fm.findFragmentByTag(tag)
        val tx = fm.beginTransaction()
        fm.fragments.forEach { tx.hide(it) }
        if (existing != null) tx.show(existing) else tx.add(R.id.fragmentContainer, fragment, tag)
        tx.commitNow()
    }

    private companion object {
        const val TAG_DIARY = "tab_diary"
        const val TAG_PROFILE = "tab_profile"
    }
}
