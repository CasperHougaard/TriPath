package com.tripath.data.model

/**
 * Biological sex used to pick sex-specific healthy reference ranges (body-fat %, BMR, etc.).
 * Intentionally minimal — this drives physiological reference tables, not identity.
 */
enum class BiologicalSex(val label: String) {
    MALE("Male"),
    FEMALE("Female")
}
