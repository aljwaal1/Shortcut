package com.explapp.shortcut.automation

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ParkedCarActivity : AppCompatActivity() {
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) saveLocation() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showMenu()
    }

    private fun showMenu() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val lat = prefs.getString(KEY_LAT, null)?.toDoubleOrNull()
        val lon = prefs.getString(KEY_LON, null)?.toDoubleOrNull()
        AlertDialog.Builder(this)
            .setTitle(local("Parked car", "السيارة المركونة"))
            .setPositiveButton(local("Save current location", "حفظ الموقع الحالي")) { _, _ -> ensureLocationPermission() }
            .apply {
                if (lat != null && lon != null) {
                    setNeutralButton(local("Directions", "الاتجاهات")) { _, _ -> openDirections(lat, lon) }
                }
            }
            .setNegativeButton(local("Close", "إغلاق")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun ensureLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            saveLocation()
        } else {
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
    }

    @Suppress("MissingPermission")
    private fun saveLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            finish()
            return
        }
        val manager = getSystemService(LocationManager::class.java)
        val location = manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        if (location == null) {
            Toast.makeText(
                this,
                local("No recent location available. Turn on location and try again.", "لا يوجد موقع حديث. شغّل الموقع وحاول مرة أخرى."),
                Toast.LENGTH_LONG,
            ).show()
        } else {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_LAT, location.latitude.toString())
                .putString(KEY_LON, location.longitude.toString())
                .apply()
            Toast.makeText(this, local("Parked location saved", "تم حفظ موقع السيارة"), Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun openDirections(lat: Double, lon: Double) {
        val geo = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, geo)) }
            .onFailure {
                Toast.makeText(this, local("No maps app found", "لم يتم العثور على تطبيق خرائط"), Toast.LENGTH_LONG).show()
            }
        finish()
    }

    private fun local(en: String, ar: String): String =
        if (resources.configuration.locales[0].language == "ar") ar else en

    companion object {
        private const val PREFS = "parked_car"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
    }
}
