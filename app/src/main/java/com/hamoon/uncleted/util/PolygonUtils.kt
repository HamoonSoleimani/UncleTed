package com.hamoon.uncleted.util

import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object PolygonUtils {

    // Pre-configured perimeter coordinates: Pair(Latitude, Longitude) for Evin Prison
    val EVIN_PRISON_PERIMETER = listOf(
        Pair(35.79211607672131, 51.38142755893173),
        Pair(35.79284579163674, 51.38666960999226),
        Pair(35.79839510355216, 51.38921260554878),
        Pair(35.79935648401846, 51.38327835491085)
    )

    data class WipeZone(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val centerLat: Double = 0.0,
        val centerLon: Double = 0.0,
        val radiusMeters: Float = 0f,
        val polygon: List<Pair<Double, Double>> = emptyList(),
        val isEnabled: Boolean = true
    )

    /**
     * Numerically robust Ray-Casting Point-in-Polygon (PIP) Algorithm.
     * Uses half-open latitude intervals [vLatI, vLatJ) to eliminate vertex double-counting
     * and horizontal collinear ray division-by-zero artifacts.
     */
    fun isLocationInZone(location: Location, polygon: List<Pair<Double, Double>>): Boolean {
        if (polygon.size < 3) return false

        var inside = false
        val pLat = location.latitude
        val pLon = location.longitude

        var j = polygon.size - 1
        for (i in polygon.indices) {
            val vLatI = polygon[i].first
            val vLonI = polygon[i].second
            val vLatJ = polygon[j].first
            val vLonJ = polygon[j].second

            // Check if horizontal ray crosses edge (vI, vJ) using half-open test
            if ((vLatI > pLat) != (vLatJ > pLat)) {
                val intersectLon = vLonI + (pLat - vLatI) * (vLonJ - vLonI) / (vLatJ - vLatI)
                if (pLon < intersectLon) {
                    inside = !inside
                }
            }
            j = i
        }

        return inside
    }

    /**
     * Universal zone boundary resolver supporting both multi-point polygon boundaries
     * and circular radius perimeters.
     */
    fun isLocationInWipeZone(location: Location, zone: WipeZone): Boolean {
        if (!zone.isEnabled) return false

        // 1. Polygon Zone Evaluation
        if (zone.polygon.size >= 3) {
            return isLocationInZone(location, zone.polygon)
        }

        // 2. Circular Geofence Radius Evaluation
        if (zone.radiusMeters > 0f) {
            val results = FloatArray(1)
            Location.distanceBetween(
                location.latitude,
                location.longitude,
                zone.centerLat,
                zone.centerLon,
                results
            )
            return results[0] <= zone.radiusMeters
        }

        return false
    }

    fun serializeZones(zones: List<WipeZone>): String {
        val array = JSONArray()
        for (z in zones) {
            val obj = JSONObject().apply {
                put("id", z.id)
                put("name", z.name)
                put("lat", z.centerLat)
                put("lon", z.centerLon)
                put("radius", z.radiusMeters.toDouble())
                put("enabled", z.isEnabled)

                val polyArray = JSONArray()
                for (pt in z.polygon) {
                    val ptObj = JSONObject().apply {
                        put("lat", pt.first)
                        put("lon", pt.second)
                    }
                    polyArray.put(ptObj)
                }
                put("polygon", polyArray)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeZones(json: String): List<WipeZone> {
        if (json.isBlank()) return emptyList()
        val zones = mutableListOf<WipeZone>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val polyList = mutableListOf<Pair<Double, Double>>()
                val polyArray = obj.optJSONArray("polygon")
                if (polyArray != null) {
                    for (j in 0 until polyArray.length()) {
                        val ptObj = polyArray.getJSONObject(j)
                        polyList.add(Pair(ptObj.getDouble("lat"), ptObj.getDouble("lon")))
                    }
                }

                zones.add(
                    WipeZone(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        centerLat = obj.optDouble("lat", 0.0),
                        centerLon = obj.optDouble("lon", 0.0),
                        radiusMeters = obj.optDouble("radius", 0.0).toFloat(),
                        polygon = polyList,
                        isEnabled = obj.optBoolean("enabled", true)
                    )
                )
            }
        } catch (_: Exception) {}
        return zones
    }
}