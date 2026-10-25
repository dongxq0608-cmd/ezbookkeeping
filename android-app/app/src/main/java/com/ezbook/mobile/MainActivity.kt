package com.ezbook.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.ezbook.mobile.ui.AppRoot
import com.ezbook.mobile.ui.theme.EzBookTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Graph.init(applicationContext)

        setContent {
            EzBookTheme {
                AppRoot()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 每次启动 / 回到前台都尝试续期（内部按 20 小时节流）：
        // 服务端 token 默认 30 天过期，且过期后无法再续期，只有持续续期才能保持登录
        lifecycleScope.launch { Graph.api.renewSession() }
    }
}
