package com.ezbook.mobile.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ezbook.mobile.Graph
import com.ezbook.mobile.ui.auth.LoginScreen
import com.ezbook.mobile.ui.home.HomeScreen
import com.ezbook.mobile.ui.home.TAB_BILLS
import com.ezbook.mobile.ui.home.TAB_HOME
import com.ezbook.mobile.ui.settings.SettingsScreen
import com.ezbook.mobile.ui.setup.SetupScreen
import com.ezbook.mobile.ui.transactions.TransactionEditScreen

object Routes {
    const val SETUP = "setup"
    const val LOGIN = "login"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val EDIT = "edit"
    const val COPY = "copy"
    fun edit(id: String) = "$EDIT/$id"
    fun copy(id: String, withTime: Boolean) = "$COPY/$id/${if (withTime) 1 else 0}"
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // 底部 Tab 选中项提升到这里：记账保存成功后可以直接切到「账单」页
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_HOME) }

    val startDestination = remember {
        when {
            !Graph.store.hasServer -> Routes.SETUP
            // token 已过期：本地就能判断，直接回登录页，避免带着废 token 发起一串注定失败的请求
            Graph.store.isLoggedIn && Graph.store.isTokenExpired() -> {
                Graph.store.logout()
                Routes.LOGIN
            }
            !Graph.store.isLoggedIn -> Routes.LOGIN
            else -> Routes.HOME
        }
    }

    LaunchedEffect(Unit) {
        Graph.sessionExpired.collect {
            Toast.makeText(context, "登录状态已失效，请重新登录", Toast.LENGTH_SHORT).show()
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.SETUP) {
            SetupScreen(
                onContinue = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onEditServer = {
                    navController.navigate(Routes.SETUP) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenTransaction = { id -> navController.navigate(Routes.edit(id)) },
                onCopyTransaction = { id, withTime -> navController.navigate(Routes.copy(id, withTime)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                selected = selectedTab,
                onSelectTab = { selectedTab = it }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onEditServer = {
                    navController.navigate(Routes.SETUP) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("${Routes.EDIT}/{txId}") { entry ->
            val txId = entry.arguments?.getString("txId") ?: "0"
            TransactionEditScreen(
                txId = txId,
                onFinished = { navController.popBackStack() },
                onCopy = { sourceId, withTime ->
                    navController.navigate(Routes.copy(sourceId, withTime))
                },
                // 保存成功后回到账单明细，而不是首页
                onSaved = {
                    navController.popBackStack()
                    selectedTab = TAB_BILLS
                }
            )
        }

        composable("${Routes.COPY}/{txId}/{withTime}") { entry ->
            val txId = entry.arguments?.getString("txId") ?: "0"
            val withTime = entry.arguments?.getString("withTime") == "1"
            TransactionEditScreen(
                txId = txId,
                copyMode = true,
                copyWithTime = withTime,
                onFinished = { navController.popBackStack() },
                onCopy = { sourceId, copyWithTime ->
                    navController.navigate(Routes.copy(sourceId, copyWithTime))
                },
                onSaved = {
                    navController.popBackStack()
                    selectedTab = TAB_BILLS
                }
            )
        }
    }
}
