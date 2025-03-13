package dev.vinigouveia.moviesapp

import java.util.Locale

object Utils {
    fun formatMsToString(timeInMs: Long): String {
        var secondsRemaining = timeInMs / 1000

        val hours = secondsRemaining / 3600
        secondsRemaining -= hours * 3600

        val minutes = secondsRemaining / 60
        secondsRemaining -= minutes * 60

        val seconds = secondsRemaining

        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }
}
