package me.longng.finnish_learning_mobile

import android.app.Application
import android.util.Log

class FinnishLearningApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FinnishApp process started")
    }

    private companion object {
        const val TAG = "FinnishApp"
    }
}