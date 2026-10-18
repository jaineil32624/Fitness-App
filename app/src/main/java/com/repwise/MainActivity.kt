package com.repwise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.repwise.ui.AppViewModel
import com.repwise.ui.RepwiseApp
import com.repwise.ui.Tab

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { RepwiseApp(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_SOUNDS, false) == true) vm.selectTab(Tab.SOUNDS)
    }

    companion object {
        const val EXTRA_OPEN_SOUNDS = "com.repwise.OPEN_SOUNDS"
    }
}
