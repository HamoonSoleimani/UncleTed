package com.hamoon.uncleted.util

import android.location.Location

object PolygonUtils {

    // Evin Prison perimeter vertices: Pair(Latitude, Longitude)
    val EVIN_PRISON_PERIMETER = listOf(
        Pair(35.79211607672131, 51.38142755893173),
        Pair(35.79284579163674, 51.38666960999226),
        Pair(35.79839510355216, 51.38921260554878),
        Pair(35.79935648401846, 51.38327835491085)
    )

    /**
     * Numerically robust Ray-Casting Point-in-Polygon Algorithm.
     * Uses half-open latitude intervals to eliminate vertex double-counting
     * and horizontal edge singularities, with linear interpolation to eliminate
     * division by zero on vertical polygon edges.
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

            // Determine if the ray cast eastward from pLat intersects the latitude span of edge (i, j)
            // The half-open condition ((vLatI > pLat) != (vLatJ > pLat)) guarantees that (vLatJ - vLatI) != 0
            if ((vLatI > pLat) != (vLatJ > pLat)) {
                // Compute the longitude coordinate of the intersection along the edge
                val intersectLon = vLonI + (pLat - vLatI) * (vLonJ - vLonI) / (vLatJ - vLatI)

                // If query longitude is to the west of the intersection, the eastward ray crosses the edge
                if (pLon < intersectLon) {
                    inside = !inside
                }
            }
            j = i
        }

        return inside
    }
}