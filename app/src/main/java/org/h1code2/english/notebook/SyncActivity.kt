package org.h1code2.english.notebook

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import org.h1code2.english.notebook.databinding.ActivitySyncBinding
import org.h1code2.english.notebook.sync.SyncServerService

class SyncActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySyncBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(SyncServerService.PREFS, Context.MODE_PRIVATE)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnRegenToken.setOnClickListener {
            prefs.edit().putString(SyncServerService.KEY_TOKEN, newToken()).apply()
            if (SyncServerService.isRunning) {
                // 令牌变了需要重启服务以生效
                SyncServerService.toggle(this, false)
                binding.switchServer.isChecked = false
                Toast.makeText(this, R.string.sync_token_regen_restart, Toast.LENGTH_LONG).show()
            }
            refresh()
        }

        binding.switchServer.setOnCheckedChangeListener { _, checked ->
            SyncServerService.toggle(this, checked)
            binding.textStatus.text = if (checked) getString(R.string.sync_starting)
            else getString(R.string.sync_disabled)
            post { refresh() }
        }

        refresh()
    }

    private fun newToken(): String {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet.random() }.joinToString("")
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val running = SyncServerService.isRunning
        binding.switchServer.setOnCheckedChangeListener(null)
        binding.switchServer.isChecked = running
        binding.switchServer.setOnCheckedChangeListener { _, checked ->
            SyncServerService.toggle(this, checked)
            binding.textStatus.text = if (checked) getString(R.string.sync_starting)
            else getString(R.string.sync_disabled)
            post { refresh() }
        }

        val port = SyncServerService.actualPort
        val ip = SyncServerService.localIp()
        val token = prefs.getString(SyncServerService.KEY_TOKEN, "") ?: ""
        binding.textAddress.text = if (ip != null) {
            getString(R.string.sync_address_fmt, ip, port)
        } else {
            getString(R.string.sync_address_unknown, port)
        }
        binding.textToken.text = token

        binding.textStatus.text = when {
            running && ip != null -> getString(R.string.sync_running_fmt, ip, port)
            running -> getString(R.string.sync_running_nolocal, port)
            else -> getString(R.string.sync_disabled)
        }

        val log = SyncServerService.readLog(this)
        binding.textSyncLog.text = if (log.isEmpty()) getString(R.string.sync_log_empty)
        else log.joinToString("\n")
        binding.textSyncLog.isVisible = true
    }

    private fun post(block: () -> Unit) {
        binding.textStatus.postDelayed({ runOnUiThread(block) }, 600)
    }
}
