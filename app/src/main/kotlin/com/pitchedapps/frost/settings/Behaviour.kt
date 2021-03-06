package com.pitchedapps.frost.settings

import ca.allanwang.kau.kpref.activity.KPrefAdapterBuilder
import com.pitchedapps.frost.R
import com.pitchedapps.frost.activities.MainActivity
import com.pitchedapps.frost.activities.SettingsActivity
import com.pitchedapps.frost.utils.Prefs

/**
 * Created by Allan Wang on 2017-06-30.
 */
fun SettingsActivity.getBehaviourPrefs(): KPrefAdapterBuilder.() -> Unit = {

    checkbox(R.string.fancy_animations, { Prefs.animate }, { Prefs.animate = it; animate = it }) {
        descRes = R.string.fancy_animations_desc
    }

    checkbox(R.string.overlay_swipe, { Prefs.overlayEnabled }, { Prefs.overlayEnabled = it; setFrostResult(MainActivity.REQUEST_REFRESH) }) {
        descRes = R.string.overlay_swipe_desc
    }

    checkbox(R.string.overlay_full_screen_swipe, { Prefs.overlayFullScreenSwipe }, { Prefs.overlayFullScreenSwipe = it }) {
        descRes = R.string.overlay_full_screen_swipe_desc
    }

    checkbox(R.string.open_links_in_default, { Prefs.linksInDefaultApp }, { Prefs.linksInDefaultApp = it }) {
        descRes = R.string.open_links_in_default_desc
    }

    checkbox(R.string.viewpager_swipe, { Prefs.viewpagerSwipe }, { Prefs.viewpagerSwipe = it }) {
        descRes = R.string.viewpager_swipe_desc
    }

    checkbox(R.string.search_bar, { Prefs.searchBar }, { Prefs.searchBar = it; setFrostResult(MainActivity.REQUEST_SEARCH) }) {
        descRes = R.string.search_bar_desc
    }

    checkbox(R.string.force_message_bottom, { Prefs.messageScrollToBottom }, { Prefs.messageScrollToBottom = it }) {
        descRes = R.string.force_message_bottom_desc
    }

    checkbox(R.string.exit_confirmation, { Prefs.exitConfirmation }, { Prefs.exitConfirmation = it }) {
        descRes = R.string.exit_confirmation_desc
    }

    checkbox(R.string.analytics, { Prefs.analytics }, { Prefs.analytics = it }) {
        descRes = R.string.analytics_desc
    }

}
