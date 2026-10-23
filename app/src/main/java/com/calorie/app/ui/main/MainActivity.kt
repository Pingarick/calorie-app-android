package com.calorie.app.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.calorie.app.R
import com.calorie.app.ui.cart.CartFragment
import com.calorie.app.ui.plan.PlanFragment
import com.calorie.app.ui.profile.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        if (savedInstanceState == null) show(PlanFragment(), TAG_PLAN)

        nav.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_plan -> {
                    show(PlanFragment(), TAG_PLAN)
                    true
                }
                R.id.nav_profile -> {
                    show(ProfileFragment(), TAG_PROFILE)
                    true
                }
                R.id.nav_cart -> {
                    show(CartFragment(), TAG_CART)
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
        const val TAG_PLAN = "tab_plan"
        const val TAG_DIARY = "tab_diary"
        const val TAG_CART = "tab_cart"
        const val TAG_PROFILE = "tab_profile"
    }
}
