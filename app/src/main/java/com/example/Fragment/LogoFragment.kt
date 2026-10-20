package com.example.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.R
import com.example.databinding.FragmentLogoBinding
import com.example.viewBinding.BaseBindingFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LogoFragment : BaseBindingFragment<FragmentLogoBinding>() {

    private var timerJob: Job? = null

    override fun onCreateViewBinding(layoutInflater: LayoutInflater): FragmentLogoBinding? {
        return FragmentLogoBinding.inflate(layoutInflater)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Allow tapping the screen to proceed immediately without waiting
        binding?.root?.setOnClickListener {
            timerJob?.cancel()
            navigateToHome()
        }

        // 3-second automatic timer to navigate to HomeFragment
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            for (remaining in 3 downTo 1) {
                if (!isAdded) return@launch
                binding?.tvLogoTimerHint?.text = "${remaining}s..."
                delay(1000L)
            }
            if (isAdded) {
                navigateToHome()
            }
        }
    }

    private fun navigateToHome() {
        if (!isAdded) return
        try {
            findNavController().navigate(R.id.action_logoFragment_to_homeFragment)
        } catch (_: Exception) {
            // Safe guard against rapid double clicks or detachment
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerJob?.cancel()
    }
}
