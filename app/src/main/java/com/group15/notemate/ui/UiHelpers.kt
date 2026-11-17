package com.group15.notemate.ui

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.transition.TransitionInflater
import android.view.View
import android.view.animation.AnimationUtils
import androidx.annotation.AnimRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.snackbar.Snackbar
import com.group15.notemate.R
import com.group15.notemate.model.Dates
import com.group15.notemate.model.Deadline
import com.group15.notemate.model.SmartEntity
import com.group15.notemate.viewmodel.BaseViewModel

// MVVM: View helpers shared by the activities.

/** The text that the Scan and Handwriting screens send back to the note editor. */
const val EXTRA_TEXT = "com.group15.notemate.extra.TEXT"

private const val HOUR_MILLIS = 60 * 60 * 1000L

/**
 * With enableEdgeToEdge() the app draws behind the status and navigation bars.
 * This pads the root view so nothing is hidden behind them (or behind the keyboard).
 */
fun View.applySystemBarPadding() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        view.updatePadding(
            left = bars.left,
            top = bars.top,
            right = bars.right,
            bottom = maxOf(bars.bottom, ime.bottom)
        )
        WindowInsetsCompat.CONSUMED
    }
}

/** Opens another screen of the app (explicit intent) with the slide transition (res/transition/slide.xml). */
fun Activity.openScreen(intent: Intent) {
    startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(this).toBundle())
}

/**
 * Activity transition for a screen that opens over the note editor: it slides up from the bottom
 * (res/transition/slide_bottom.xml) instead of the slide from the side set in the theme.
 * Call it in onCreate before setContentView.
 */
fun Activity.slideInFromBottom() {
    val slide = TransitionInflater.from(this).inflateTransition(R.transition.slide_bottom)
    window.enterTransition = slide
    window.returnTransition = slide
}

/** Plays a tween animation from res/anim on this view. */
fun View.play(@AnimRes animation: Int) {
    startAnimation(AnimationUtils.loadAnimation(context, animation))
}

fun Activity.showMessage(text: String) {
    Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG).show()
}

fun Activity.showMessage(@StringRes id: Int) = showMessage(getString(id))

/** Shows each one-time message from the ViewModel in a Snackbar. */
fun AppCompatActivity.observeMessages(viewModel: BaseViewModel) {
    viewModel.message.observe(this) { event ->
        event.getContentIfNotHandled()?.let { showMessage(it) }
    }
}

/** Sends [text] back to the note editor and closes this screen. */
fun Activity.returnText(text: String) {
    setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_TEXT, text))
    finishAfterTransition()
}

/** Implicit intent: opens the calendar app with a new event for [deadline], ready to save. */
fun Activity.addToCalendar(deadline: Deadline) {
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, deadline.line)
        .putExtra(CalendarContract.Events.DESCRIPTION, getString(R.string.deadline_from_note, deadline.noteTitle))
    if (deadline.hasTime) {
        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, deadline.millis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, deadline.millis + HOUR_MILLIS)
    } else {
        val day = Dates.utcMidnight(deadline.millis)
        intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, day)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, day)
    }
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        showMessage(R.string.error_no_app)
    }
}

/** "Nov 28, 2026", with the time when the note gives one. */
fun Deadline.formatWhen(): String = if (hasTime) Dates.formatDateTime(millis) else Dates.formatDate(millis)

/** Implicit intent: opens the email app, dialer, browser or maps for [entity]. */
fun Activity.openSmartEntity(entity: SmartEntity) {
    val intent = when (entity.type) {
        SmartEntity.Type.EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${entity.text}"))
        SmartEntity.Type.PHONE -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${entity.text}"))
        SmartEntity.Type.URL -> {
            val url = if (entity.text.startsWith("http", ignoreCase = true)) entity.text else "https://${entity.text}"
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }
        SmartEntity.Type.ADDRESS -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(entity.text)}"))
    }
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        showMessage(R.string.error_no_app)
    }
}
