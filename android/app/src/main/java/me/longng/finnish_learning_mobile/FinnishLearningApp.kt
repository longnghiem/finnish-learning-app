package me.longng.finnish_learning_mobile

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

/**
 * Process entry point and the **Hilt root**.
 *
 * `@HiltAndroidApp` triggers Hilt's code generation: it creates the
 * application-level `SingletonComponent` that every `@Module` with
 * `@InstallIn(SingletonComponent::class)` plugs into. Without this annotation,
 * no `@AndroidEntryPoint` / `@HiltViewModel` in the app can be injected.
 *
 */
@HiltAndroidApp
class FinnishLearningApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FinnishApp process started")
    }

    private companion object {
        const val TAG = "FinnishApp"
    }
}