package ru.uust.schedule

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.uust.schedule.ui.ScheduleViewModel
import ru.uust.schedule.ui.components.AppBackground
import ru.uust.schedule.ui.screens.NavBar
import ru.uust.schedule.ui.screens.HomeworkScreen
import ru.uust.schedule.ui.screens.OnboardingScreen
import ru.uust.schedule.ui.screens.ScheduleScreen
import ru.uust.schedule.ui.screens.SettingsScreen
import ru.uust.schedule.ui.screens.SubjectsScreen
import ru.uust.schedule.ui.screens.Tab
import ru.uust.schedule.ui.theme.UustTheme
import ru.uust.schedule.work.SyncScheduler

class MainActivity : ComponentActivity() {

    private val vm: ScheduleViewModel by viewModels()

    /**
     * На Android 13+ уведомления требуют разрешения во время работы.
     * Спрашиваем один раз при старте, а не в момент включения тумблера:
     * иначе диалог всплывал бы поверх настроек и сбивал прокрутку.
     */
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SyncScheduler.schedule(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()

            UustTheme(theme = settings.theme) {
                Box(Modifier.fillMaxSize()) {
                    AppBackground {
                        if (!settings.onboarded || settings.groupId == 0) {
                            OnboardingScreen(vm)
                        } else {
                            MainShell(vm)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh(silent = true)
        vm.checkForUpdates(silent = true)
        // Отдельный рубеж защиты помимо фоновой синхронизации раз в 30 минут:
        // открыл приложение — виджеты сразу перерисовались на актуальном коде.
        ru.uust.schedule.widget.WidgetUpdater.updateAll(this)
    }
}

@Composable
private fun MainShell(vm: ScheduleViewModel) {
    var tab by remember { mutableStateOf(Tab.Schedule) }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Color.Transparent,
        bottomBar = { NavBar(current = tab, onSelect = { tab = it }) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            AnimatedVisibility(
                visible = tab == Tab.Schedule,
                enter = fadeIn() + slideInVertically { it / 12 },
                exit = fadeOut() + slideOutVertically { -it / 12 },
            ) { ScheduleScreen(vm) }

            AnimatedVisibility(
                visible = tab == Tab.Homework,
                enter = fadeIn() + slideInVertically { it / 12 },
                exit = fadeOut() + slideOutVertically { -it / 12 },
            ) { HomeworkScreen(vm) }

            AnimatedVisibility(
                visible = tab == Tab.Subjects,
                enter = fadeIn() + slideInVertically { it / 12 },
                exit = fadeOut() + slideOutVertically { -it / 12 },
            ) { SubjectsScreen(vm) }

            AnimatedVisibility(
                visible = tab == Tab.Settings,
                enter = fadeIn() + slideInVertically { it / 12 },
                exit = fadeOut() + slideOutVertically { -it / 12 },
            ) { SettingsScreen(vm) }
        }
    }
}
