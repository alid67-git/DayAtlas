package com.dayatlas.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import com.dayatlas.app.databinding.ActivityHelpBinding
import java.util.Locale

class HelpActivity : DayAtlasActivity() {
    private lateinit var binding: ActivityHelpBinding
    private lateinit var backCallback: OnBackPressedCallback
    private var sections: List<HelpContent.Section> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        backCallback = onBackPressedDispatcher.addCallback(this, enabled = false) { showToc() }

        binding.toolbar.setNavigationOnClickListener {
            if (binding.paneHelpSection.visibility == View.VISIBLE) showToc() else finish()
        }

        binding.languageGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            loadHelp(helpResFor(checkedId))
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
        loadHelp(helpResFor(startId))
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

    private fun loadHelp(rawRes: Int) {
        val raw = resources.openRawResource(rawRes).bufferedReader().use { it.readText() }
        val parsed = HelpContent.parse(raw)
        sections = parsed.sections
        binding.helpIntro.text = parsed.intro
        binding.helpFooter.text = parsed.footer
        renderSectionList()
        showToc()
    }

    private fun renderSectionList() {
        val container = binding.helpSectionList
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        sections.forEachIndexed { index, section ->
            val row = inflater.inflate(R.layout.item_help_section, container, false)
            row.findViewById<TextView>(R.id.helpSectionTitle).text = section.heading
            row.setOnClickListener { showSection(index) }
            container.addView(row)
        }
    }

    private fun showSection(index: Int) {
        val section = sections.getOrNull(index) ?: return
        binding.helpSectionHeading.text = section.heading
        binding.helpSectionBody.text = section.body
        binding.paneHelpSection.scrollTo(0, 0)
        binding.paneHelpToc.visibility = View.GONE
        binding.paneHelpSection.visibility = View.VISIBLE
        backCallback.isEnabled = true
    }

    private fun showToc() {
        binding.paneHelpSection.visibility = View.GONE
        binding.paneHelpToc.visibility = View.VISIBLE
        backCallback.isEnabled = false
    }
}
