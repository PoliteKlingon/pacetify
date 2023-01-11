package com.example.myapplication

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

class SRAService : Service() {
    inner class SRABinder : Binder() {
        fun getService() = this@SRAService
    }

    private val binder = SRABinder()

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_REDELIVER_INTENT
    }

    fun getNumber() = 51
}