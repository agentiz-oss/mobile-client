package com.example.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.app.data.initLocalCache
import com.example.app.data.initSessionStorage
import com.example.app.platform.initHaptics
import com.example.app.push.Push
import com.example.app.push.attachPushHost
import com.example.app.push.routeFromIntent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before the first composition: App() reads the stored session as it starts up.
        initSessionStorage(this)
        initLocalCache(this)
        initHaptics(this)
        // Also before the first composition: the permission launcher may only be registered while
        // the activity is being created.
        attachPushHost(this)
        // A cold start from a tapped notification arrives as extras on the launch intent. Read
        // here rather than in a screen, so the route is already parked when App() first composes.
        Push.deliverRoute(routeFromIntent(intent))
        setContent {
            App()
        }
    }

    /** The app was already running: the tap re-delivers the same payload through a new intent. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Push.deliverRoute(routeFromIntent(intent))
    }
}
