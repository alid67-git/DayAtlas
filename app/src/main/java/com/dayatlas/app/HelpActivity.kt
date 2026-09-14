package com.dayatlas.app

import android.os.Bundle
import com.dayatlas.app.databinding.ActivityHelpBinding
import java.util.Locale

class HelpActivity : DayAtlasActivity() {
    private lateinit var binding: ActivityHelpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.languageGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            showHelp(helpResFor(checkedId))
        }

        // Follow the app UI locale (Settings → Language), not only the device.
        val startId = when (resources.configuration.locales[0]?.language ?: Locale.getDefault().language) {
            "de" -> R.id.langDe
            "en" -> R.id.langEn
            "zh" -> R.id.langZh
            "hi" -> R.id.langHi
            "es" -> R.id.langEs
            "fr" -> R.id.langFr
            "ar" -> R.id.langAr
            else -> R.id.langTr
        }
        binding.languageGroup.check(startId)
        showHelp(helpResFor(startId))
    }

    private fun helpResFor(checkedId: Int): Int = when (checkedId) {
        R.id.langEn -> R.raw.help_en
        R.id.langDe -> R.raw.help_de
        R.id.langZh -> R.raw.help_zh
        R.id.langHi -> R.raw.help_hi
        R.id.langEs -> R.raw.help_es
        R.id.langFr -> R.raw.help_fr
        R.id.langAr -> R.raw.help_ar
        else -> R.raw.help_tr
    }

    private fun showHelp(rawRes: Int) {
        binding.helpText.text = resources.openRawResource(rawRes).bufferedReader().use { it.readText() }
    }
}
