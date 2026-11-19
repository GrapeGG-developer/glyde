package gpr.greentune.unblock.system

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import gpr.greentune.unblock.core.Engine
import gpr.greentune.unblock.ui.MainActivity

class ToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Engine.isActive() && Engine.needsVpnPermission(this)) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_CONNECT, true)
            )
        } else {
            Engine.toggle(this)
        }
        finish()
    }
}
