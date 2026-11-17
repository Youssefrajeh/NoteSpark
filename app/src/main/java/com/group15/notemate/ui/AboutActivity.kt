package com.group15.notemate.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.group15.notemate.R
import com.group15.notemate.databinding.ActivityAboutBinding
import com.group15.notemate.databinding.ItemMemberBinding

/**
 * MVVM: View. What the app does, how to use it and the group members.
 * The text is fixed (res/values/strings.xml), so this screen does not need a ViewModel.
 */
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Tween animation: the logo pops when the screen opens (not again after a rotation)
        if (savedInstanceState == null) binding.imageLogo.play(R.anim.pop)

        binding.textFeatures.text = bulletList(resources.getStringArray(R.array.about_features))
        binding.textHowTo.text = bulletList(resources.getStringArray(R.array.about_how_to))
        showMembers()
    }

    private fun bulletList(items: Array<String>) = items.joinToString("\n") { getString(R.string.bullet_item, it) }

    /** Adds one row per group member: the three arrays have the names, student numbers and roles in the same order. */
    private fun showMembers() {
        val names = resources.getStringArray(R.array.member_names)
        val numbers = resources.getStringArray(R.array.member_numbers)
        val roles = resources.getStringArray(R.array.member_roles)
        for (index in names.indices) {
            val row = ItemMemberBinding.inflate(layoutInflater, binding.groupMembers, false)
            row.textName.text = names[index]
            row.textNumber.text = getString(R.string.member_number, numbers[index])
            row.textRole.text = roles[index]
            binding.groupMembers.addView(row.root)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finishAfterTransition()
        return true
    }
}
