package com.aqaqeer.app.data

/** No demo data or configured pharmacy is compiled into the standard flavor. */
object BuildSeed {
    const val VERSION = 1
    fun catalog(): CatalogSnapshot = CatalogSeeds.unconfigured()
    @Suppress("UNUSED_PARAMETER")
    suspend fun refreshIfNeeded(dao: CatalogDao, installation: InstallationState) = Unit
}
