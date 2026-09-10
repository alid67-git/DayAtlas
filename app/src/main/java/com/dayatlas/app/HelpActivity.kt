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

        binding.languageGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            showHelp(
                when (checkedId) {
                    R.id.langEn -> R.raw.help_en
                    R.id.langDe -> R.raw.help_de
                    else -> R.raw.help_tr
                },
            )
        }

        val startId = when (Locale.getDefault().language) {
            "de" -> R.id.langDe
            "en" -> R.id.langEn
            else -> R.id.langTr
        }
        binding.languageGroup.check(startId)
    }

    private fun showHelp(rawRes: Int) {
        binding.helpText.text = resources.openRawResource(rawRes).bufferedReader().use { it.readText() }
    }
}
