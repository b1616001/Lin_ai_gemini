package com.example.viewBinding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding

abstract class BaseBindingActivity<VB : ViewBinding?> : AppCompatActivity() {
    var binding: VB? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = onCreateViewBinding(getLayoutInflater())
        setContentView(binding!!.getRoot())
    }

    protected abstract fun onCreateViewBinding(layoutInflater: LayoutInflater): VB?

    override fun onResume() {
        super.onResume()
        //        fullScreenImmersive(binding.getRoot());
    }

    fun fullScreenImmersive(view: View) {
        val uiOptions = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        view.setSystemUiVisibility(uiOptions)
    }
}