package com.tripath.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The eight palettes, each in a light and a dark instance.
 *
 * Values are transcribed from LiftPath's `values/lp_palette_*.xml` and
 * `values-night/lp_palette_*.xml` so the two apps read as one product. Do not "improve" a
 * value here in isolation — the neutrals in each palette are tuned against each other, and
 * warming or cooling one surface in isolation is what makes a palette look accidental.
 *
 * The three roles LiftPath has no source for are derived by a fixed rule rather than invented
 * per palette, so a ninth palette needs no new taste decisions:
 *
 *  - `intensityHigh/Moderate/Low`  ← the palette's fatigue / accent / flush hues.
 *  - `disciplineSwim`  ← `chartTime`   (the palette's blue)
 *  - `disciplineBike`  ← `chartVolume` (the palette's green)
 *  - `disciplineStrength` ← `chartFatigue` (the palette's clay — matches LiftPath exactly)
 *  - `disciplineOther` ← `neutral`
 *  - `disciplineRun`   ← `chartLoad` **when that hue is warm**; otherwise a warm value
 *    authored in the palette's own register. Cloud, Ash, Sand and Fog have cool or pink
 *    accents, so their run hue is authored: leaving it on the accent put running within a
 *    few degrees of hue of either swimming or strength, and those three appear adjacent on
 *    the weekly matrix.
 *
 * Adding a ninth palette: one light instance, one dark instance, one [TriPathPalette] entry.
 * No screen should need touching.
 */

// ==================== PAPER — warm paper, warm near-black ink, one bronze accent ====================

private val PaperLight = TriPathColors(
    canvas = Color(0xFFFAF9F7),
    canvasSunken = Color(0xFFF2F0EB),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF7F5F1),
    hairline = Color(0xFFE6E2D9),
    hairlineStrong = Color(0xFFD6D1C6),
    ink = Color(0xFF191813),
    inkSecondary = Color(0xFF6B665C),
    inkTertiary = Color(0xFF9A948A),
    inkInverse = Color(0xFFFAF9F7),
    inkInverseSecondary = Color(0xFFB0AAA0),
    accent = Color(0xFFA6753C),
    accentPressed = Color(0xFF8C5F2E),
    accentWash = Color(0xFFF5EDE1),
    positive = Color(0xFF3F6B4F),
    negative = Color(0xFFA33A2B),
    neutral = Color(0xFF8C8880),
    intensityHigh = Color(0xFFA33A2B),
    intensityModerate = Color(0xFFA6753C),
    intensityLow = Color(0xFF4F7A5C),
    disciplineSwim = Color(0xFF4A6B82),
    disciplineBike = Color(0xFF4F7A5C),
    disciplineRun = Color(0xFFA6753C),
    disciplineStrength = Color(0xFFA33A2B),
    disciplineOther = Color(0xFF8C8880),
    chartVolume = Color(0xFF4F7A5C),
    chartLoad = Color(0xFFA6753C),
    chartTime = Color(0xFF4A6B82),
    chartFatigue = Color(0xFFA33A2B),
    chartGrid = Color(0xFFEAE6DD),
    ripple = Color(0x14191813),
    rippleInverse = Color(0x22FAF9F7),
    isDark = false
)

private val PaperDark = TriPathColors(
    canvas = Color(0xFF12110F),
    canvasSunken = Color(0xFF0C0B0A),
    surface = Color(0xFF1C1A17),
    surfaceAlt = Color(0xFF241F1B),
    hairline = Color(0xFF302B25),
    hairlineStrong = Color(0xFF443D35),
    ink = Color(0xFFF5F2EC),
    inkSecondary = Color(0xFFA39C90),
    inkTertiary = Color(0xFF726B60),
    inkInverse = Color(0xFF12110F),
    inkInverseSecondary = Color(0xFF55504A),
    accent = Color(0xFFD2A164),
    accentPressed = Color(0xFFB9884C),
    accentWash = Color(0xFF2A2118),
    positive = Color(0xFF7FB08F),
    negative = Color(0xFFD98374),
    neutral = Color(0xFF8C8880),
    intensityHigh = Color(0xFFD98374),
    intensityModerate = Color(0xFFD2A164),
    intensityLow = Color(0xFF7FB08F),
    disciplineSwim = Color(0xFF8AAAC4),
    disciplineBike = Color(0xFF7FB08F),
    disciplineRun = Color(0xFFD2A164),
    disciplineStrength = Color(0xFFD98374),
    disciplineOther = Color(0xFF8C8880),
    chartVolume = Color(0xFF7FB08F),
    chartLoad = Color(0xFFD2A164),
    chartTime = Color(0xFF8AAAC4),
    chartFatigue = Color(0xFFD98374),
    chartGrid = Color(0xFF2A2620),
    ripple = Color(0x1AF5F2EC),
    rippleInverse = Color(0x2212110F),
    isDark = true
)

// ==================== CHALK — cool near-white, one clay accent ====================

private val ChalkLight = TriPathColors(
    canvas = Color(0xFFF7F7F5),
    canvasSunken = Color(0xFFEFEFEC),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF5F5F2),
    hairline = Color(0xFFE4E4E0),
    hairlineStrong = Color(0xFFD2D2CC),
    ink = Color(0xFF15161A),
    inkSecondary = Color(0xFF63666E),
    inkTertiary = Color(0xFF93969E),
    inkInverse = Color(0xFFF7F7F5),
    inkInverseSecondary = Color(0xFFA8AAB0),
    accent = Color(0xFF8C2F27),
    accentPressed = Color(0xFF74251F),
    accentWash = Color(0xFFF6E8E6),
    positive = Color(0xFF3F6B4F),
    negative = Color(0xFFA33A2B),
    neutral = Color(0xFF85888E),
    intensityHigh = Color(0xFF8C2F27),
    intensityModerate = Color(0xFF9A7328),
    intensityLow = Color(0xFF3F6B4F),
    disciplineSwim = Color(0xFF3F5E80),
    disciplineBike = Color(0xFF3F6B4F),
    disciplineRun = Color(0xFF9A7328),
    disciplineStrength = Color(0xFF8C2F27),
    disciplineOther = Color(0xFF85888E),
    chartVolume = Color(0xFF3F6B4F),
    chartLoad = Color(0xFF9A7328),
    chartTime = Color(0xFF3F5E80),
    chartFatigue = Color(0xFF8C2F27),
    chartGrid = Color(0xFFE8E8E4),
    ripple = Color(0x1415161A),
    rippleInverse = Color(0x22F7F7F5),
    isDark = false
)

private val ChalkDark = TriPathColors(
    canvas = Color(0xFF101114),
    canvasSunken = Color(0xFF0A0B0D),
    surface = Color(0xFF191B1F),
    surfaceAlt = Color(0xFF212429),
    hairline = Color(0xFF2B2E34),
    hairlineStrong = Color(0xFF3E4249),
    ink = Color(0xFFF2F3F5),
    inkSecondary = Color(0xFF9BA0A8),
    inkTertiary = Color(0xFF6B7078),
    inkInverse = Color(0xFF101114),
    inkInverseSecondary = Color(0xFF4E525A),
    accent = Color(0xFFD96A5E),
    accentPressed = Color(0xFFBF564B),
    accentWash = Color(0xFF2A1A18),
    positive = Color(0xFF7FB08F),
    negative = Color(0xFFD98374),
    neutral = Color(0xFF85888E),
    intensityHigh = Color(0xFFD96A5E),
    intensityModerate = Color(0xFFD4A857),
    intensityLow = Color(0xFF7FB08F),
    disciplineSwim = Color(0xFF7FA0C4),
    disciplineBike = Color(0xFF7FB08F),
    disciplineRun = Color(0xFFD4A857),
    disciplineStrength = Color(0xFFD96A5E),
    disciplineOther = Color(0xFF85888E),
    chartVolume = Color(0xFF7FB08F),
    chartLoad = Color(0xFFD4A857),
    chartTime = Color(0xFF7FA0C4),
    chartFatigue = Color(0xFFD96A5E),
    chartGrid = Color(0xFF24272C),
    ripple = Color(0x1AF2F3F5),
    rippleInverse = Color(0x22101114),
    isDark = true
)

// ==================== BONE — warm off-white, one green accent ====================

private val BoneLight = TriPathColors(
    canvas = Color(0xFFF8F7F3),
    canvasSunken = Color(0xFFF0EFE9),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF6F5F0),
    hairline = Color(0xFFE5E3DA),
    hairlineStrong = Color(0xFFD3D0C4),
    ink = Color(0xFF1A1C18),
    inkSecondary = Color(0xFF676B62),
    inkTertiary = Color(0xFF979B90),
    inkInverse = Color(0xFFF8F7F3),
    inkInverseSecondary = Color(0xFFACAFA5),
    accent = Color(0xFF3F6B4F),
    accentPressed = Color(0xFF33573F),
    accentWash = Color(0xFFE8EFE8),
    positive = Color(0xFF3F6B4F),
    negative = Color(0xFFA33A2B),
    neutral = Color(0xFF8A8D82),
    intensityHigh = Color(0xFFA33A2B),
    intensityModerate = Color(0xFFA6753C),
    intensityLow = Color(0xFF3F6B4F),
    disciplineSwim = Color(0xFF4A6B82),
    disciplineBike = Color(0xFF3F6B4F),
    disciplineRun = Color(0xFFA6753C),
    disciplineStrength = Color(0xFFA33A2B),
    disciplineOther = Color(0xFF8A8D82),
    chartVolume = Color(0xFF3F6B4F),
    chartLoad = Color(0xFFA6753C),
    chartTime = Color(0xFF4A6B82),
    chartFatigue = Color(0xFFA33A2B),
    chartGrid = Color(0xFFE9E7DE),
    ripple = Color(0x141A1C18),
    rippleInverse = Color(0x22F8F7F3),
    isDark = false
)

private val BoneDark = TriPathColors(
    canvas = Color(0xFF101210),
    canvasSunken = Color(0xFF0A0C0A),
    surface = Color(0xFF1A1D19),
    surfaceAlt = Color(0xFF222620),
    hairline = Color(0xFF2D312B),
    hairlineStrong = Color(0xFF414639),
    ink = Color(0xFFF2F4EF),
    inkSecondary = Color(0xFF9CA396),
    inkTertiary = Color(0xFF6C7268),
    inkInverse = Color(0xFF101210),
    inkInverseSecondary = Color(0xFF4F544B),
    accent = Color(0xFF79B08C),
    accentPressed = Color(0xFF639775),
    accentWash = Color(0xFF17251C),
    positive = Color(0xFF79B08C),
    negative = Color(0xFFD98374),
    neutral = Color(0xFF8A8D82),
    intensityHigh = Color(0xFFD98374),
    intensityModerate = Color(0xFFD2A164),
    intensityLow = Color(0xFF79B08C),
    disciplineSwim = Color(0xFF8AAAC4),
    disciplineBike = Color(0xFF79B08C),
    disciplineRun = Color(0xFFD2A164),
    disciplineStrength = Color(0xFFD98374),
    disciplineOther = Color(0xFF8A8D82),
    chartVolume = Color(0xFF79B08C),
    chartLoad = Color(0xFFD2A164),
    chartTime = Color(0xFF8AAAC4),
    chartFatigue = Color(0xFFD98374),
    chartGrid = Color(0xFF232720),
    ripple = Color(0x1AF2F4EF),
    rippleInverse = Color(0x22101210),
    isDark = true
)

// ==================== STEEL — cool grey, one hot orange accent ====================

private val SteelLight = TriPathColors(
    canvas = Color(0xFFF5F6F7),
    canvasSunken = Color(0xFFECEEF0),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF3F5F6),
    hairline = Color(0xFFE2E5E8),
    hairlineStrong = Color(0xFFCFD4D9),
    ink = Color(0xFF17191C),
    inkSecondary = Color(0xFF61666D),
    inkTertiary = Color(0xFF91979E),
    inkInverse = Color(0xFFF5F6F7),
    inkInverseSecondary = Color(0xFFA6ACB2),
    accent = Color(0xFFE8502A),
    accentPressed = Color(0xFFC74020),
    accentWash = Color(0xFFFCEAE4),
    positive = Color(0xFF2F7D5A),
    negative = Color(0xFFC0392B),
    neutral = Color(0xFF838A91),
    intensityHigh = Color(0xFFC0392B),
    intensityModerate = Color(0xFFB07A16),
    intensityLow = Color(0xFF2F7D5A),
    disciplineSwim = Color(0xFF3D6E99),
    disciplineBike = Color(0xFF2F7D5A),
    disciplineRun = Color(0xFFB07A16),
    disciplineStrength = Color(0xFFC0392B),
    disciplineOther = Color(0xFF838A91),
    chartVolume = Color(0xFF2F7D5A),
    chartLoad = Color(0xFFB07A16),
    chartTime = Color(0xFF3D6E99),
    chartFatigue = Color(0xFFC0392B),
    chartGrid = Color(0xFFE7EAEC),
    ripple = Color(0x1417191C),
    rippleInverse = Color(0x22F5F6F7),
    isDark = false
)

private val SteelDark = TriPathColors(
    canvas = Color(0xFF0E1013),
    canvasSunken = Color(0xFF090A0C),
    surface = Color(0xFF171A1E),
    surfaceAlt = Color(0xFF1F2328),
    hairline = Color(0xFF292E34),
    hairlineStrong = Color(0xFF3C424A),
    ink = Color(0xFFF1F3F5),
    inkSecondary = Color(0xFF99A0A8),
    inkTertiary = Color(0xFF697079),
    inkInverse = Color(0xFF0E1013),
    inkInverseSecondary = Color(0xFF4C525A),
    accent = Color(0xFFFF7A52),
    accentPressed = Color(0xFFE3603A),
    accentWash = Color(0xFF2B1712),
    positive = Color(0xFF6FB894),
    negative = Color(0xFFE0796A),
    neutral = Color(0xFF838A91),
    intensityHigh = Color(0xFFE0796A),
    intensityModerate = Color(0xFFD9A94A),
    intensityLow = Color(0xFF6FB894),
    disciplineSwim = Color(0xFF7DA6CC),
    disciplineBike = Color(0xFF6FB894),
    disciplineRun = Color(0xFFD9A94A),
    disciplineStrength = Color(0xFFE0796A),
    disciplineOther = Color(0xFF838A91),
    chartVolume = Color(0xFF6FB894),
    chartLoad = Color(0xFFD9A94A),
    chartTime = Color(0xFF7DA6CC),
    chartFatigue = Color(0xFFE0796A),
    chartGrid = Color(0xFF22262B),
    ripple = Color(0x1AF1F3F5),
    rippleInverse = Color(0x220E1013),
    isDark = true
)

// ==================== CLOUD — blue-tinted neutrals, one true-blue accent ====================
// Run is authored warm here: on the accent it sat 25° of hue from swimming's teal.

private val CloudLight = TriPathColors(
    canvas = Color(0xFFF4F7FB),
    canvasSunken = Color(0xFFEAF0F7),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF1F5FA),
    hairline = Color(0xFFDCE6F0),
    hairlineStrong = Color(0xFFC7D6E6),
    ink = Color(0xFF131A24),
    inkSecondary = Color(0xFF5C6B7C),
    inkTertiary = Color(0xFF8A97A6),
    inkInverse = Color(0xFFF4F7FB),
    inkInverseSecondary = Color(0xFFA9B7C6),
    accent = Color(0xFF2563EB),
    accentPressed = Color(0xFF1E4FC0),
    accentWash = Color(0xFFE6EDFB),
    positive = Color(0xFF3F6B4F),
    negative = Color(0xFFA33A2B),
    neutral = Color(0xFF8590A0),
    intensityHigh = Color(0xFFA33A2B),
    intensityModerate = Color(0xFF2563EB),
    intensityLow = Color(0xFF3F6B4F),
    disciplineSwim = Color(0xFF3D7A94),
    disciplineBike = Color(0xFF3F6B4F),
    disciplineRun = Color(0xFFB5601F),
    disciplineStrength = Color(0xFFA33A2B),
    disciplineOther = Color(0xFF8590A0),
    chartVolume = Color(0xFF3F6B4F),
    chartLoad = Color(0xFF2563EB),
    chartTime = Color(0xFF3D7A94),
    chartFatigue = Color(0xFFA33A2B),
    chartGrid = Color(0xFFE2EAF3),
    ripple = Color(0x14131A24),
    rippleInverse = Color(0x22F4F7FB),
    isDark = false
)

private val CloudDark = TriPathColors(
    canvas = Color(0xFF0D1420),
    canvasSunken = Color(0xFF080D16),
    surface = Color(0xFF161F2E),
    surfaceAlt = Color(0xFF1D2838),
    hairline = Color(0xFF263447),
    hairlineStrong = Color(0xFF38495F),
    ink = Color(0xFFEEF3FA),
    inkSecondary = Color(0xFF93A2B6),
    inkTertiary = Color(0xFF647486),
    inkInverse = Color(0xFF0D1420),
    inkInverseSecondary = Color(0xFF4B586A),
    accent = Color(0xFF6C9BFF),
    accentPressed = Color(0xFF4C7EE0),
    accentWash = Color(0xFF17233A),
    positive = Color(0xFF7FB08F),
    negative = Color(0xFFD98374),
    neutral = Color(0xFF8590A0),
    intensityHigh = Color(0xFFD98374),
    intensityModerate = Color(0xFF6C9BFF),
    intensityLow = Color(0xFF7FB08F),
    disciplineSwim = Color(0xFF6FB0C9),
    disciplineBike = Color(0xFF7FB08F),
    disciplineRun = Color(0xFFE0A458),
    disciplineStrength = Color(0xFFD98374),
    disciplineOther = Color(0xFF8590A0),
    chartVolume = Color(0xFF7FB08F),
    chartLoad = Color(0xFF6C9BFF),
    chartTime = Color(0xFF6FB0C9),
    chartFatigue = Color(0xFFD98374),
    chartGrid = Color(0xFF212C3D),
    ripple = Color(0x1AEEF3FA),
    rippleInverse = Color(0x220D1420),
    isDark = true
)

// ==================== ASH — warm grey, one muted purple accent ====================
// Run is authored warm: the purple accent read as a third cool hue beside swim and bike.

private val AshLight = TriPathColors(
    canvas = Color(0xFFF6F5F4),
    canvasSunken = Color(0xFFEDEBEA),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF3F1F0),
    hairline = Color(0xFFE2DFDD),
    hairlineStrong = Color(0xFFCFCBC8),
    ink = Color(0xFF1C1A1B),
    inkSecondary = Color(0xFF6B6669),
    inkTertiary = Color(0xFF9B9598),
    inkInverse = Color(0xFFF6F5F4),
    inkInverseSecondary = Color(0xFFAFA9AC),
    accent = Color(0xFF7B4B8C),
    accentPressed = Color(0xFF653A73),
    accentWash = Color(0xFFF0E6F1),
    positive = Color(0xFF3F6B4F),
    negative = Color(0xFFA33A2B),
    neutral = Color(0xFF8D888B),
    intensityHigh = Color(0xFFA33A2B),
    intensityModerate = Color(0xFF7B4B8C),
    intensityLow = Color(0xFF3F6B4F),
    disciplineSwim = Color(0xFF4A6B82),
    disciplineBike = Color(0xFF3F6B4F),
    disciplineRun = Color(0xFFA9662B),
    disciplineStrength = Color(0xFFA33A2B),
    disciplineOther = Color(0xFF8D888B),
    chartVolume = Color(0xFF3F6B4F),
    chartLoad = Color(0xFF7B4B8C),
    chartTime = Color(0xFF4A6B82),
    chartFatigue = Color(0xFFA33A2B),
    chartGrid = Color(0xFFE9E6E5),
    ripple = Color(0x141C1A1B),
    rippleInverse = Color(0x22F6F5F4),
    isDark = false
)

private val AshDark = TriPathColors(
    canvas = Color(0xFF141213),
    canvasSunken = Color(0xFF0D0C0C),
    surface = Color(0xFF1E1C1D),
    surfaceAlt = Color(0xFF262324),
    hairline = Color(0xFF322E30),
    hairlineStrong = Color(0xFF464143),
    ink = Color(0xFFF4F1F2),
    inkSecondary = Color(0xFFA39D9F),
    inkTertiary = Color(0xFF726D6F),
    inkInverse = Color(0xFF141213),
    inkInverseSecondary = Color(0xFF555052),
    accent = Color(0xFFB084C0),
    accentPressed = Color(0xFF9A6BAA),
    accentWash = Color(0xFF271F2A),
    positive = Color(0xFF7FB08F),
    negative = Color(0xFFD98374),
    neutral = Color(0xFF8D888B),
    intensityHigh = Color(0xFFD98374),
    intensityModerate = Color(0xFFB084C0),
    intensityLow = Color(0xFF7FB08F),
    disciplineSwim = Color(0xFF8AAAC4),
    disciplineBike = Color(0xFF7FB08F),
    disciplineRun = Color(0xFFD8A268),
    disciplineStrength = Color(0xFFD98374),
    disciplineOther = Color(0xFF8D888B),
    chartVolume = Color(0xFF7FB08F),
    chartLoad = Color(0xFFB084C0),
    chartTime = Color(0xFF8AAAC4),
    chartFatigue = Color(0xFFD98374),
    chartGrid = Color(0xFF262223),
    ripple = Color(0x1AF4F1F2),
    rippleInverse = Color(0x22141213),
    isDark = true
)

// ==================== SAND — warm sand, one teal accent ====================
// Run is authored warm: the teal accent clustered with swim's blue and bike's green.

private val SandLight = TriPathColors(
    canvas = Color(0xFFFAF6EE),
    canvasSunken = Color(0xFFF1EBDD),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF7F1E6),
    hairline = Color(0xFFE8DFCB),
    hairlineStrong = Color(0xFFD8CBAE),
    ink = Color(0xFF1D1A14),
    inkSecondary = Color(0xFF6E6656),
    inkTertiary = Color(0xFF9C9483),
    inkInverse = Color(0xFFFAF6EE),
    inkInverseSecondary = Color(0xFFB3A992),
    accent = Color(0xFF1F7A73),
    accentPressed = Color(0xFF17615C),
    accentWash = Color(0xFFE3F0EE),
    positive = Color(0xFF3F6B4F),
    negative = Color(0xFFA33A2B),
    neutral = Color(0xFF948C79),
    intensityHigh = Color(0xFFA33A2B),
    intensityModerate = Color(0xFF1F7A73),
    intensityLow = Color(0xFF3F6B4F),
    disciplineSwim = Color(0xFF4A6B82),
    disciplineBike = Color(0xFF3F6B4F),
    disciplineRun = Color(0xFFB5691C),
    disciplineStrength = Color(0xFFA33A2B),
    disciplineOther = Color(0xFF948C79),
    chartVolume = Color(0xFF3F6B4F),
    chartLoad = Color(0xFF1F7A73),
    chartTime = Color(0xFF4A6B82),
    chartFatigue = Color(0xFFA33A2B),
    chartGrid = Color(0xFFEFE8D8),
    ripple = Color(0x141D1A14),
    rippleInverse = Color(0x22FAF6EE),
    isDark = false
)

private val SandDark = TriPathColors(
    canvas = Color(0xFF14120D),
    canvasSunken = Color(0xFF0D0B08),
    surface = Color(0xFF1E1B15),
    surfaceAlt = Color(0xFF26221A),
    hairline = Color(0xFF332E23),
    hairlineStrong = Color(0xFF494232),
    ink = Color(0xFFF5F1E8),
    inkSecondary = Color(0xFFA6A08E),
    inkTertiary = Color(0xFF756F60),
    inkInverse = Color(0xFF14120D),
    inkInverseSecondary = Color(0xFF585244),
    accent = Color(0xFF4FBFB4),
    accentPressed = Color(0xFF3AA69B),
    accentWash = Color(0xFF16302C),
    positive = Color(0xFF7FB08F),
    negative = Color(0xFFD98374),
    neutral = Color(0xFF948C79),
    intensityHigh = Color(0xFFD98374),
    intensityModerate = Color(0xFF4FBFB4),
    intensityLow = Color(0xFF7FB08F),
    disciplineSwim = Color(0xFF8AAAC4),
    disciplineBike = Color(0xFF7FB08F),
    disciplineRun = Color(0xFFD9A45C),
    disciplineStrength = Color(0xFFD98374),
    disciplineOther = Color(0xFF948C79),
    chartVolume = Color(0xFF7FB08F),
    chartLoad = Color(0xFF4FBFB4),
    chartTime = Color(0xFF8AAAC4),
    chartFatigue = Color(0xFFD98374),
    chartGrid = Color(0xFF292419),
    ripple = Color(0x1AF5F1E8),
    rippleInverse = Color(0x2214120D),
    isDark = true
)

// ==================== FOG — cool mauve neutrals, one dusty pink accent ====================
// Run is authored warm: pink sat within 20° of hue of strength's salmon in dark mode.

private val FogLight = TriPathColors(
    canvas = Color(0xFFF7F5F6),
    canvasSunken = Color(0xFFEFEBED),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF4F1F3),
    hairline = Color(0xFFE4DEE1),
    hairlineStrong = Color(0xFFD2CAD0),
    ink = Color(0xFF1C1A1D),
    inkSecondary = Color(0xFF6A6569),
    inkTertiary = Color(0xFF9A9498),
    inkInverse = Color(0xFFF7F5F6),
    inkInverseSecondary = Color(0xFFAFA8AD),
    accent = Color(0xFFB0567C),
    accentPressed = Color(0xFF954565),
    accentWash = Color(0xFFF7E7ED),
    positive = Color(0xFF3F6B4F),
    negative = Color(0xFFA33A2B),
    neutral = Color(0xFF8D878B),
    intensityHigh = Color(0xFFA33A2B),
    intensityModerate = Color(0xFFB0567C),
    intensityLow = Color(0xFF3F6B4F),
    disciplineSwim = Color(0xFF4A6B82),
    disciplineBike = Color(0xFF3F6B4F),
    disciplineRun = Color(0xFFAF6A2E),
    disciplineStrength = Color(0xFFA33A2B),
    disciplineOther = Color(0xFF8D878B),
    chartVolume = Color(0xFF3F6B4F),
    chartLoad = Color(0xFFB0567C),
    chartTime = Color(0xFF4A6B82),
    chartFatigue = Color(0xFFA33A2B),
    chartGrid = Color(0xFFEAE4E7),
    ripple = Color(0x141C1A1D),
    rippleInverse = Color(0x22F7F5F6),
    isDark = false
)

private val FogDark = TriPathColors(
    canvas = Color(0xFF141215),
    canvasSunken = Color(0xFF0D0C0E),
    surface = Color(0xFF1E1B1E),
    surfaceAlt = Color(0xFF262227),
    hairline = Color(0xFF322D31),
    hairlineStrong = Color(0xFF464044),
    ink = Color(0xFFF5F2F3),
    inkSecondary = Color(0xFFA49EA1),
    inkTertiary = Color(0xFF736D70),
    inkInverse = Color(0xFF141215),
    inkInverseSecondary = Color(0xFF565053),
    accent = Color(0xFFE58BAE),
    accentPressed = Color(0xFFC96F92),
    accentWash = Color(0xFF2E1D24),
    positive = Color(0xFF7FB08F),
    negative = Color(0xFFD98374),
    neutral = Color(0xFF8D878B),
    intensityHigh = Color(0xFFD98374),
    intensityModerate = Color(0xFFE58BAE),
    intensityLow = Color(0xFF7FB08F),
    disciplineSwim = Color(0xFF8AAAC4),
    disciplineBike = Color(0xFF7FB08F),
    disciplineRun = Color(0xFFDCA46B),
    disciplineStrength = Color(0xFFD98374),
    disciplineOther = Color(0xFF8D878B),
    chartVolume = Color(0xFF7FB08F),
    chartLoad = Color(0xFFE58BAE),
    chartTime = Color(0xFF8AAAC4),
    chartFatigue = Color(0xFFD98374),
    chartGrid = Color(0xFF272227),
    ripple = Color(0x1AF5F2F3),
    rippleInverse = Color(0x22141215),
    isDark = true
)

/**
 * The palettes the user can choose between — independently for light mode and dark mode.
 *
 * [prefValue] is the stable string written to DataStore. **Never renumber or reuse one.**
 * Ordinals are deliberately not persisted, so reordering this enum cannot silently change a
 * user's saved choice.
 */
enum class TriPathPalette(
    val prefValue: String,
    val label: String,
    val subtitle: String,
    val light: TriPathColors,
    val dark: TriPathColors
) {
    PAPER("paper", "Paper", "Warm paper, bronze", PaperLight, PaperDark),
    CHALK("chalk", "Chalk", "Cool white, clay", ChalkLight, ChalkDark),
    BONE("bone", "Bone", "Warm off-white, green", BoneLight, BoneDark),
    STEEL("steel", "Steel", "Cool grey, hot orange", SteelLight, SteelDark),
    CLOUD("cloud", "Cloud", "Blue neutrals, true blue", CloudLight, CloudDark),
    ASH("ash", "Ash", "Warm grey, purple", AshLight, AshDark),
    SAND("sand", "Sand", "Warm sand, teal", SandLight, SandDark),
    FOG("fog", "Fog", "Mauve, dusty pink", FogLight, FogDark);

    /** The instance for the mode currently in effect. */
    fun colors(dark: Boolean): TriPathColors = if (dark) this.dark else light

    companion object {
        val DEFAULT = PAPER

        /**
         * Unknown or absent values fall back to [DEFAULT] rather than throwing, so a
         * hand-edited pref or one restored from a build that had a palette this one doesn't
         * can never brick app start.
         */
        fun fromPrefValue(value: String?): TriPathPalette =
            entries.firstOrNull { it.prefValue == value } ?: DEFAULT
    }
}

/** Whether the app follows the system light/dark setting or forces one. */
enum class AppearanceMode(val prefValue: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        /**
         * Dark, not System — TriPath has always defaulted to dark (`dark_theme` defaulted to
         * `true`), and changing that on upgrade would flip existing users to light.
         */
        val DEFAULT = DARK

        fun fromPrefValue(value: String?): AppearanceMode =
            entries.firstOrNull { it.prefValue == value } ?: DEFAULT
    }
}
