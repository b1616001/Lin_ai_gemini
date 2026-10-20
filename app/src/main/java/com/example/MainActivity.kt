package com.example

import android.os.Bundle
import android.view.LayoutInflater
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.databinding.ActivityMainBinding
import com.example.viewBinding.BaseBindingActivity

class MainActivity : BaseBindingActivity<ActivityMainBinding>() {
    companion object {
        //416370
        @JvmField
        var mainActivity: MainActivity? = null
    }
    private lateinit var navController: NavController
    var currentFragment: String = "HomeFragment"

    override fun onCreateViewBinding(layoutInflater: LayoutInflater): ActivityMainBinding? {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivity = this
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
