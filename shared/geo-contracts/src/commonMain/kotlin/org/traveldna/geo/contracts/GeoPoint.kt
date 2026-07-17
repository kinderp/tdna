package org.traveldna.geo.contracts

/** Immutable WGS84 coordinate shared by routing, location and map contracts. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "latitude must be finite and within [-90, 90]"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "longitude must be finite and within [-180, 180]"
        }
    }
}
