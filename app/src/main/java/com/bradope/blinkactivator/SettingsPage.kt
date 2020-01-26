package com.bradope.blinkactivator

import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bradope.blinkactivator.blink.*
import kotlinx.android.synthetic.main.activity_maps.*
import kotlin.math.atan
import kotlin.reflect.KProperty


class SettingsPage(val activity: AppCompatActivity, val onResume: ()->Unit) {

    inner class SeekBarBinder(prop: KProperty<Int>, seeker: SeekBar, val label: TextView, val postFix: String): SeekBar.OnSeekBarChangeListener {
        init {
            seeker.progress = getSettingFetcher(prop)()
            label.text = "${seeker.progress}${postFix}"
            seeker.setOnSeekBarChangeListener(this)
        }

        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            label.text = "${progress}${postFix}"
        }
        override fun onStartTrackingTouch(seekBar: SeekBar) {}
        override fun onStopTrackingTouch(seekBar: SeekBar) {}

    }
    init {
        activity.runOnUiThread {
            activity.settings_menu.visibility = View.VISIBLE
            activity.mainScreenLayout.visibility = View.GONE
            val blinkSettings = blinkGetSettings()

            activity.closeSettings.setOnClickListener{ closeSettingsMenu() }
            syncLabelsWithSettings(blinkSettings)
        }

    }

    private fun syncLabelsWithSettings(blinkSettings: BlinkSettings) {
        activity.locationPrioritySpinner.setSelection(blinkSettings.locationPriority.ordinal)

        SeekBarBinder(blinkSettings::fastestLocationUpdateIntervalInSeconds,
            activity.fastestLocationUpdateSeekBar,
            activity.fastestLocationUpdateLabel,
            "s")
        SeekBarBinder(blinkSettings::locationUpdateIntervalInSeconds,
            activity.locationUpdateSeekBar,
            activity.locationUpdateLabel,
            "s")
        SeekBarBinder(blinkSettings::numLocationLogsOutToArm,
            activity.numLogsToArmSeek,
            activity.numLogsToArmLabel,
            "")
        SeekBarBinder(blinkSettings::minDistFromHome,
            activity.homeBoundarySeekbar,
            activity.homeBoundaryLabel,
            "m")

        SeekBarBinder(blinkSettings::renewSessionIntervalInHours,
            activity.renewSessionSeekbar,
            activity.renewSessionLabel,
            "h")

        SeekBarBinder(blinkSettings::refreshStatusIntervalInMinutes,
            activity.refreshStatusSeekbar,
            activity.refreshStatusLabel,
            "m")

        SeekBarBinder(blinkSettings::checkScheduleIntervalInMinutes,
            activity.refreshScheduleSeekbar,
            activity.refreshScheduleLabel,
            "m")
    }

    private fun valueWithoutS(text: CharSequence) = text.substring(0, text.length - 1).toInt()

    fun closeSettingsMenu() {

        val blinkSettings = blinkGetSettings()

        var settingsSyncNeeded = changeLocationSettingsIfRequired(blinkSettings)

        settingsSyncNeeded = changeHomeSettingsIfRequired(blinkSettings) || settingsSyncNeeded
        settingsSyncNeeded = changeIntervalSettingsIfRequired(blinkSettings) || settingsSyncNeeded

        if (settingsSyncNeeded) {
            writeSettingsToStorage(activity, blinkSettings)
        }

        onResume()
    }

    private fun changeLocationSettingsIfRequired(blinkSettings: BlinkSettings): Boolean {
        val selectedPriorty = LocationPriority.valueOf(activity.locationPrioritySpinner.selectedItem.toString())
        var needToUpdateLocationSettings = syncSettingIfChanged(selectedPriorty, blinkSettings::locationPriority)
        needToUpdateLocationSettings = syncSettingIfChanged(activity.fastestLocationUpdateSeekBar.progress, blinkSettings::fastestLocationUpdateIntervalInSeconds) || needToUpdateLocationSettings
        needToUpdateLocationSettings = syncSettingIfChanged(activity.locationUpdateSeekBar.progress, blinkSettings::locationUpdateIntervalInSeconds) || needToUpdateLocationSettings

        if (needToUpdateLocationSettings) {
            blinkRecreateLocationRequestClient(activity)
        }

        val settingsNeedUpdatingButNoLocationRenewNeeded = syncSettingIfChanged(activity.numLogsToArmSeek.progress, blinkSettings::numLocationLogsOutToArm)

        return needToUpdateLocationSettings || settingsNeedUpdatingButNoLocationRenewNeeded
    }

    private fun changeIntervalSettingsIfRequired(blinkSettings: BlinkSettings): Boolean {
        var changeMade = syncSettingIfChanged(activity.renewSessionSeekbar.progress, blinkSettings::renewSessionIntervalInHours)
        changeMade = syncSettingIfChanged(activity.refreshStatusSeekbar.progress, blinkSettings::refreshStatusIntervalInMinutes) || changeMade
        syncSettingIfChanged(activity.refreshScheduleSeekbar.progress, blinkSettings::checkScheduleIntervalInMinutes)
        return changeMade
    }

    private fun changeHomeSettingsIfRequired(blinkSettings: BlinkSettings) = syncSettingIfChanged(activity.homeBoundarySeekbar.progress, blinkSettings::minDistFromHome)
}