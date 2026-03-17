package com.example.geodouro_project.data.remote.model

import com.google.gson.annotations.SerializedName

data class InaturalistTaxaResponse(
    @SerializedName("results")
    val results: List<InaturalistTaxonDto> = emptyList()
)

data class InaturalistTaxonDto(
    @SerializedName("id")
    val id: Long?,
    @SerializedName("name")
    val scientificName: String?,
    @SerializedName("preferred_common_name")
    val commonName: String?,
    @SerializedName("iconic_taxon_name")
    val iconicTaxonName: String?,
    @SerializedName("wikipedia_url")
    val wikipediaUrl: String?,
    @SerializedName("default_photo")
    val defaultPhoto: InaturalistPhotoDto?
)

data class InaturalistPhotoDto(
    @SerializedName("medium_url")
    val mediumUrl: String?
)
