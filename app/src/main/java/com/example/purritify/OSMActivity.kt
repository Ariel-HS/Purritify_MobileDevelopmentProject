package com.example.purritify

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

class OSMActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private var selectedMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load osmdroid configuration
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        // Create and configure map
        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(-6.914744, 107.609810))
        }

        val mapEventsReceiver = object: MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint?): Boolean {
                point?.let {
                    addOrMoveMarker(point)

                    // Return selected location
                    val intent = Intent().apply {
                        putExtra("latitude", point.latitude)
                        putExtra("longitude", point.longitude)
                    }
                    setResult(RESULT_OK, intent)
                    finish()
                }

                return true
            }

            override fun longPressHelper(point: GeoPoint): Boolean {
                return false
            }
        }

        val eventsOverlay = MapEventsOverlay(mapEventsReceiver)
        map.overlays.add(eventsOverlay)

        setContentView(map)
    }

    private fun addOrMoveMarker(point: GeoPoint) {
        if (selectedMarker == null) {
            selectedMarker = Marker(map).apply {
                position = point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Selected Location"
            }
            map.overlays.add(selectedMarker)
        } else {
            selectedMarker!!.position = point
        }

        Toast.makeText(this@OSMActivity, "Location selected: ${point.latitude}, ${point.longitude}", Toast.LENGTH_SHORT).show()
        map.invalidate()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}