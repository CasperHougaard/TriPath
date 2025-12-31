# Coach Logic Implementation Documentation

This document describes the TriPath smart planning system ("Iron Brain"), which validates training plans and provides readiness assessments based on user-defined rules and recovery metrics.

## Table of Contents

1. [Overview](#overview)
2. [Architecture & Components](#architecture--components)
3. [Smart Planning System](#smart-planning-system)
4. [Readiness Calculation](#readiness-calculation)
5. [Plan Validation Rules](#plan-validation-rules)
6. [Performance Metrics Integration](#performance-metrics-integration)
7. [Training Phase Integration](#training-phase-integration)
8. [Configuration & Settings](#configuration--settings)
9. [Areas for Improvement](#areas-for-improvement)

---

## Overview

The TriPath smart planning system is a validation-based training advisor that:

1. **Validates Training Plans** against user-defined rules (consecutive runs, strength spacing, mechanical load, etc.)
2. **Calculates Readiness Status** based on TSB, sleep, wellness metrics, and allergies
3. **Provides Coach Warnings** for rule violations and injury risks
4. **Integrates with Training Phases** to provide context-aware guidance

**Key Philosophy:** Users manually create training plans in the Planner. The smart planning system validates these plans and provides intelligent warnings and readiness assessments, giving users full control while preventing injury and overtraining.

---

## Architecture & Components

### Core Classes

#### 1. `TrainingRulesEngine` (Domain Layer) - "Iron Brain"
**Location:** `app/src/main/java/com/tripath/domain/coach/TrainingRulesEngine.kt`

The central intelligence of the smart planning system. A singleton that:
- Validates daily training plans against configurable rules
- Calculates readiness scores from multiple recovery metrics
- Generates coach warnings for rule violations
- Reads configuration from `PreferencesManager`

**Key Methods:**
```kotlin
suspend fun validateDailyPlan(
    yesterday: WorkoutLog?,
    todayPlan: WorkoutLog?,
    todayWellness: DailyWellnessLog,
    lastStrengthDate: LocalDate?,
    currentPhase: TrainingPhase,
    recentRuns: List<WorkoutLog>
): List<CoachWarning>

suspend fun calculateReadiness(
    tsb: Int,
    sleepHours: Double?,
    soreness: Int?,
    mood: Int?,
    allergy: AllergySeverity
): ReadinessStatus

fun calculateSSS(distanceKm: Double, avgZone: Int): Double
```

#### 2. `CoachViewModel` (UI Layer)
**Location:** `app/src/main/java/com/tripath/ui/coach/CoachViewModel.kt`

Orchestrates the smart planning system:
- Loads workout logs, wellness data, and training plans
- Calculates performance metrics (CTL/ATL/TSB)
- Calls `TrainingRulesEngine` for readiness and validation
- Manages special periods (Injury, Holiday, Recovery Week)
- Generates coach assessment messages

**Key State:**
```kotlin
data class CoachUiState(
    val currentPhase: TrainingPhase?,
    val performanceMetrics: PerformanceMetrics,
    val coachAssessment: String,
    val formStatus: FormStatus,
    // ... more fields
)

// Separate flows for smart planning features
val readinessState: StateFlow<ReadinessStatus?>
val alertsState: StateFlow<List<CoachWarning>>
val isSmartPlanningEnabled: StateFlow<Boolean>
```

#### 3. `TrainingMetricsCalculator` (Domain Layer)
**Location:** `app/src/main/java/com/tripath/domain/TrainingMetricsCalculator.kt`

Calculates performance metrics using the Banister Impulse Response model:
- **CTL (Chronic Training Load / Fitness)**: 42-day EWMA of daily TSS
- **ATL (Acute Training Load / Fatigue)**: 7-day EWMA of daily TSS
- **TSB (Training Stress Balance / Form)**: CTL - ATL

These metrics are used by `TrainingRulesEngine` for readiness calculation.

#### 4. `CoachEngine` (Domain Layer)
**Location:** `app/src/main/java/com/tripath/domain/CoachEngine.kt`

Calculates the current training phase based on goal date:
- Used by `TrainingRulesEngine` to provide phase-aware validation
- Phases: Off-Season, Base, Build, Peak, Taper, Transition

---

## Smart Planning System

### System Flow

1. **User Creates Plan** → User manually adds workouts to Planner
2. **Coach Validates** → `TrainingRulesEngine.validateDailyPlan()` checks plan against rules
3. **Readiness Calculated** → `TrainingRulesEngine.calculateReadiness()` assesses recovery status
4. **Warnings Generated** → Rule violations and risks trigger `CoachWarning` objects
5. **UI Displays** → Coach screen shows readiness card and alerts list

### Master Toggle

The entire smart planning system can be enabled/disabled via `PlanningSettingsScreen`:
- When **disabled**: No validation, no readiness calculation, no warnings
- When **enabled**: Full validation and readiness assessment active

---

## Readiness Calculation

### Overview

Readiness status provides a holistic assessment of the athlete's ability to train effectively. It combines multiple recovery metrics into a single score (0-100) with color coding.

### Calculation Formula

**Weighted Components:**
- **TSB Score (50%)**: Primary indicator of training stress balance
- **Subjective Metrics (30%)**: Average of soreness and mood (1-10 scale)
- **Sleep (20%)**: Sleep duration in hours

**Allergy Penalty:**
- Moderate: -10 points
- Severe: -30 points

### TSB Scoring

```kotlin
// TSB Component (50% weight)
val tsbScore = when {
    tsb > 5 -> 100      // Fresh, ready to race
    tsb < -30 -> 0     // Overreaching, high fatigue
    else -> {
        // Linear interpolation: -30 -> 0, 5 -> 100
        val range = 5 - (-30) // 35
        val position = tsb - (-30) // 0 to 35
        ((position.toDouble() / range) * 100).toInt().coerceIn(0, 100)
    }
}
```

### Subjective Metrics Scoring

```kotlin
// Subjective Component (30% weight)
val subjectiveScore = if (soreness != null && mood != null) {
    val avg = (soreness + mood) / 2.0
    // Scale 1-10 to 0-100: (avg - 1) / 9 * 100
    ((avg - 1) / 9.0 * 100).toInt().coerceIn(0, 100)
} else if (soreness != null) {
    ((soreness - 1) / 9.0 * 100).toInt().coerceIn(0, 100)
} else if (mood != null) {
    ((mood - 1) / 9.0 * 100).toInt().coerceIn(0, 100)
} else {
    50 // Default middle score if neither available
}
```

### Sleep Scoring

```kotlin
// Sleep Component (20% weight)
val sleepScore = if (sleepHours != null) {
    when {
        sleepHours >= 8.0 -> 100
        sleepHours <= 5.0 -> 0
        else -> {
            // Linear interpolation: 5h -> 0, 8h -> 100
            val range = 8.0 - 5.0 // 3
            val position = sleepHours - 5.0
            ((position / range) * 100).toInt().coerceIn(0, 100)
        }
    }
} else {
    50 // Default middle score if not available
}
```

### Final Score & Color Coding

```kotlin
val weightedScore = (tsbScore * 0.5 + subjectiveScore * 0.3 + sleepScore * 0.2).roundToInt()
val finalScore = (weightedScore - allergyPenalty).coerceIn(0, 100)

val color = when {
    finalScore > 75 -> ReadinessColor.GREEN   // Ready to train
    finalScore >= 40 -> ReadinessColor.YELLOW // Caution
    else -> ReadinessColor.RED                // Not ready
}
```

### Implementation

```36:120:app/src/main/java/com/tripath/domain/coach/TrainingRulesEngine.kt
    suspend fun calculateReadiness(
        tsb: Int,
        sleepHours: Double?,
        soreness: Int?,
        mood: Int?,
        allergy: AllergySeverity
    ): ReadinessStatus {
        // TSB Component (50%)
        val tsbScore = when {
            tsb > 5 -> 100
            tsb < -30 -> 0
            else -> {
                // Linear interpolation: -30 -> 0, 5 -> 100
                val range = 5 - (-30) // 35
                val position = tsb - (-30) // 0 to 35
                ((position.toDouble() / range) * 100).toInt().coerceIn(0, 100)
            }
        }

        // Subjective Component (30%) - Average of soreness and mood
        val subjectiveScore = if (soreness != null && mood != null) {
            val avg = (soreness + mood) / 2.0
            // Scale 1-10 to 0-100: (avg - 1) / 9 * 100
            ((avg - 1) / 9.0 * 100).toInt().coerceIn(0, 100)
        } else if (soreness != null) {
            ((soreness - 1) / 9.0 * 100).toInt().coerceIn(0, 100)
        } else if (mood != null) {
            ((mood - 1) / 9.0 * 100).toInt().coerceIn(0, 100)
        } else {
            50 // Default middle score if neither available
        }

        // Sleep Component (20%)
        val sleepScore = if (sleepHours != null) {
            when {
                sleepHours >= 8.0 -> 100
                sleepHours <= 5.0 -> 0
                else -> {
                    // Linear interpolation: 5h -> 0, 8h -> 100
                    val range = 8.0 - 5.0 // 3
                    val position = sleepHours - 5.0
                    ((position / range) * 100).toInt().coerceIn(0, 100)
                }
            }
        } else {
            50 // Default middle score if not available
        }

        // Calculate weighted score
        val weightedScore = (tsbScore * 0.5 + subjectiveScore * 0.3 + sleepScore * 0.2).roundToInt()

        // Apply allergy penalty
        val allergyPenalty = when (allergy) {
            AllergySeverity.MODERATE -> 10
            AllergySeverity.SEVERE -> 30
            else -> 0
        }

        val finalScore = (weightedScore - allergyPenalty).coerceIn(0, 100)

        // Determine color
        val color = when {
            finalScore > 75 -> ReadinessColor.GREEN
            finalScore >= 40 -> ReadinessColor.YELLOW
            else -> ReadinessColor.RED
        }

        // Build breakdown string
        val breakdown = buildString {
            append("TSB: $tsbScore")
            if (sleepHours != null) {
                append(", Sleep: $sleepScore")
            }
            if (soreness != null || mood != null) {
                append(", Subjective: $subjectiveScore")
            }
        }

        return ReadinessStatus(
            score = finalScore,
            color = color,
            breakdown = breakdown,
            allergyPenalty = allergyPenalty
        )
    }
```

---

## Plan Validation Rules

### Overview

The `TrainingRulesEngine` validates daily training plans against five configurable rules. Each rule can generate warnings that are displayed to the user.

### Rule 1: Consecutive Runs (Plate Protection)

**Purpose:** Prevent back-to-back running sessions to reduce injury risk from repetitive impact.

**Configuration:** `runConsecutiveAllowed` (boolean) in `PlanningSettingsScreen`

**Logic:**
```kotlin
if (!allowConsecutiveRuns && 
    yesterday?.type == WorkoutType.RUN && 
    todayPlan.type == WorkoutType.RUN) {
    
    // Exception: Skip if commute exemption is enabled and today is a commute
    if (!(allowCommuteExemption && todayPlan.isCommute)) {
        warnings.add(CoachWarning(
            type = WarningType.RULE_VIOLATION,
            title = "Consecutive Runs Blocked",
            message = "Running two days in a row is disabled in settings.",
            isBlocker = true
        ))
    }
}
```

**Warning Type:** `RULE_VIOLATION` (Blocker)

### Rule 2: Strength Spacing

**Purpose:** Ensure adequate recovery between strength sessions for muscle protein synthesis.

**Configuration:** `strengthSpacingHours` (24, 48, or 72 hours) in `PlanningSettingsScreen`

**Logic:**
```kotlin
if (todayPlan.type == WorkoutType.STRENGTH && lastStrengthDate != null) {
    val hoursSinceLastStrength = ChronoUnit.HOURS.between(
        lastStrengthDate.atStartOfDay(),
        LocalDate.now().atStartOfDay()
    )
    
    if (hoursSinceLastStrength < strengthSpacingHours) {
        warnings.add(CoachWarning(
            type = WarningType.RULE_VIOLATION,
            title = "Strength Spacing Violation",
            message = "Strength sessions must be ${strengthSpacingHours}h apart.",
            isBlocker = true
        ))
    }
}
```

**Warning Type:** `RULE_VIOLATION` (Blocker)

### Rule 3: Post-Strength Protocol (Heavy Legs)

**Purpose:** Recommend low-intensity recovery after strength sessions.

**Configuration:** Hard-coded rule (not configurable)

**Logic:**
```kotlin
if (yesterday?.type == WorkoutType.STRENGTH && todayPlan.type != WorkoutType.SWIM) {
    val todayZone = inferZoneFromWorkoutLog(todayPlan)
    if (todayZone > 1) {
        warnings.add(CoachWarning(
            type = WarningType.RECOVERY_ADVICE,
            title = "Post-Strength Protocol",
            message = "Post-Strength Rule: Consider Swim or Zone 1 Spin only.",
            isBlocker = false
        ))
    }
}
```

**Warning Type:** `RECOVERY_ADVICE` (Non-blocker)

### Rule 4: Severe Allergy Protocol

**Purpose:** Restrict training intensity during severe allergy episodes.

**Configuration:** Based on `DailyWellnessLog.allergySeverity`

**Logic:**
```kotlin
if (todayWellness.allergySeverity == AllergySeverity.SEVERE) {
    val todayZone = inferZoneFromWorkoutLog(todayPlan)
    if (todayPlan.type == WorkoutType.STRENGTH || todayZone > 1) {
        warnings.add(CoachWarning(
            type = WarningType.INJURY_RISK,
            title = "Severe Allergy Active",
            message = "Severe Allergy Active. Only Zone 1 Active Recovery allowed.",
            isBlocker = true
        ))
    }
}
```

**Warning Type:** `INJURY_RISK` (Blocker)

### Rule 5: Mechanical Load Monitoring (SSS)

**Purpose:** Track structural stress from running to prevent overuse injuries.

**Configuration:** `mechanicalLoadMonitoring` (boolean) in `PlanningSettingsScreen`

**SSS Formula:**
```kotlin
fun calculateSSS(distanceKm: Double, avgZone: Int): Double {
    return distanceKm * (1.0 + (avgZone * 0.2))
}
```

**Logic:**
```kotlin
if (monitorMechLoad) {
    // Calculate 7-day rolling SSS for current week (last 7 days)
    val currentWeekSSS = calculateWeekSSS(currentWeekRuns)
    
    // Calculate 7-day rolling SSS for previous week (7-14 days ago)
    val previousWeekSSS = calculateWeekSSS(previousWeekRuns)
    
    if (previousWeekSSS > 0 && currentWeekSSS > previousWeekSSS * 1.15) {
        warnings.add(CoachWarning(
            type = WarningType.INJURY_RISK,
            title = "Mechanical Load Increase",
            message = "Mechanical load increased >15% vs previous week. Consider reducing run volume.",
            isBlocker = false
        ))
    }
}
```

**Warning Type:** `INJURY_RISK` (Non-blocker)

### Zone Inference

The engine infers workout intensity zones from available data:

1. **HR Zone Distribution** (preferred): Weighted average from HR zone time
2. **Power Zone Distribution**: Weighted average from power zone time
3. **TSS Fallback**: Rough estimate based on TSS value
   - TSS < 30 → Zone 1
   - TSS < 60 → Zone 2
   - TSS < 90 → Zone 3
   - TSS < 120 → Zone 4
   - TSS ≥ 120 → Zone 5

### Implementation

```144:273:app/src/main/java/com/tripath/domain/coach/TrainingRulesEngine.kt
    suspend fun validateDailyPlan(
        yesterday: WorkoutLog?,
        todayPlan: WorkoutLog?,
        todayWellness: DailyWellnessLog,
        lastStrengthDate: LocalDate?,
        currentPhase: TrainingPhase,
        recentRuns: List<WorkoutLog>
    ): List<CoachWarning> {
        // Step 1: Check if Smart Planning is enabled
        val smartEnabled = preferencesManager.smartPlanningEnabledFlow.first()
        if (!smartEnabled) {
            return emptyList() // Engine is OFF
        }

        // Step 2: Load other preferences
        val allowConsecutiveRuns = preferencesManager.runConsecutiveAllowedFlow.first()
        val strengthSpacingHours = preferencesManager.strengthSpacingHoursFlow.first()
        val monitorMechLoad = preferencesManager.mechanicalLoadMonitoringFlow.first()
        val allowCommuteExemption = preferencesManager.allowCommuteExemptionFlow.first()

        val warnings = mutableListOf<CoachWarning>()

        // Early return if no plan for today
        if (todayPlan == null) {
            return warnings
        }

        // Rule 1: Run Frequency (Plate Protection)
        if (!allowConsecutiveRuns && 
            yesterday?.type == WorkoutType.RUN && 
            todayPlan.type == WorkoutType.RUN) {
            
            // Exception: Skip if commute exemption is enabled and today is a commute
            if (!(allowCommuteExemption && todayPlan.isCommute)) {
                warnings.add(
                    CoachWarning(
                        type = WarningType.RULE_VIOLATION,
                        title = "Consecutive Runs Blocked",
                        message = "Running two days in a row is disabled in settings.",
                        isBlocker = true
                    )
                )
            }
        }

        // Rule 2: Strength Spacing
        if (todayPlan.type == WorkoutType.STRENGTH && lastStrengthDate != null) {
            val hoursSinceLastStrength = ChronoUnit.HOURS.between(
                lastStrengthDate.atStartOfDay(),
                LocalDate.now().atStartOfDay()
            )
            
            if (hoursSinceLastStrength < strengthSpacingHours) {
                warnings.add(
                    CoachWarning(
                        type = WarningType.RULE_VIOLATION,
                        title = "Strength Spacing Violation",
                        message = "Strength sessions must be ${strengthSpacingHours}h apart.",
                        isBlocker = true
                    )
                )
            }
        }

        // Rule 3: Heavy Legs Protocol
        if (yesterday?.type == WorkoutType.STRENGTH && todayPlan.type != WorkoutType.SWIM) {
            val todayZone = inferZoneFromWorkoutLog(todayPlan)
            if (todayZone > 1) {
                warnings.add(
                    CoachWarning(
                        type = WarningType.RECOVERY_ADVICE,
                        title = "Post-Strength Protocol",
                        message = "Post-Strength Rule: Consider Swim or Zone 1 Spin only.",
                        isBlocker = false
                    )
                )
            }
        }

        // Rule 4: Severe Allergy Protocol
        if (todayWellness.allergySeverity == AllergySeverity.SEVERE) {
            val todayZone = inferZoneFromWorkoutLog(todayPlan)
            if (todayPlan.type == WorkoutType.STRENGTH || todayZone > 1) {
                warnings.add(
                    CoachWarning(
                        type = WarningType.INJURY_RISK,
                        title = "Severe Allergy Active",
                        message = "Severe Allergy Active. Only Zone 1 Active Recovery allowed.",
                        isBlocker = true
                    )
                )
            }
        }

        // Rule 5: Mechanical Load (SSS) Monitor
        if (monitorMechLoad) {
            val today = LocalDate.now()
            val fourteenDaysAgo = today.minusDays(14)
            
            // Filter recent runs to only RUN type from last 14 days (for comparison)
            val recentRunLogs = recentRuns.filter { log ->
                log.type == WorkoutType.RUN && 
                !log.date.isBefore(fourteenDaysAgo) && 
                !log.date.isAfter(today)
            }.sortedBy { it.date } // Sort by date for proper week comparison
            
            if (recentRunLogs.size >= 14) {
                // Calculate 7-day rolling SSS for current week (last 7 days)
                val currentWeekRuns = recentRunLogs.takeLast(7)
                val currentWeekSSS = calculateWeekSSS(currentWeekRuns)
                
                // Calculate 7-day rolling SSS for previous week (7-14 days ago)
                val previousWeekRuns = recentRunLogs.dropLast(7).takeLast(7)
                val previousWeekSSS = calculateWeekSSS(previousWeekRuns)
                
                if (previousWeekSSS > 0 && currentWeekSSS > previousWeekSSS * 1.15) {
                    warnings.add(
                        CoachWarning(
                            type = WarningType.INJURY_RISK,
                            title = "Mechanical Load Increase",
                            message = "Mechanical load increased >15% vs previous week. Consider reducing run volume.",
                            isBlocker = false
                        )
                    )
                }
            }
        }

        return warnings
    }
```

---

## Performance Metrics Integration

### TSB for Readiness

The `TrainingRulesEngine` uses TSB (Training Stress Balance) as the primary component (50% weight) in readiness calculation. TSB is calculated by `TrainingMetricsCalculator` using the Banister Impulse Response model:

- **CTL (Fitness)**: 42-day exponentially weighted moving average of daily TSS
- **ATL (Fatigue)**: 7-day exponentially weighted moving average of daily TSS
- **TSB (Form)**: CTL - ATL

**TSB Interpretation:**
- **TSB > 5**: Freshness (tapered, ready to race) → 100 readiness score
- **TSB -30 to 5**: Linear interpolation → 0-100 readiness score
- **TSB < -30**: Overreaching (high fatigue) → 0 readiness score

---

## Training Phase Integration

### Phase-Aware Validation

The `TrainingRulesEngine` receives the current training phase from `CoachEngine` but currently uses it primarily for context. Future enhancements could include:

- Phase-specific rule adjustments
- Phase-appropriate intensity recommendations
- Phase-based recovery protocols

**Current Phases:**
- Off-Season (> 6 months to goal)
- Base (> 21 weeks to goal)
- Build (9-21 weeks to goal)
- Peak (3-9 weeks to goal)
- Taper (0-3 weeks to goal)
- Transition (post-race, 0-4 weeks)

---

## Configuration & Settings

### PlanningSettingsScreen

All smart planning rules are configurable via `PlanningSettingsScreen`:

1. **Master Control**
   - `isSmartPlanningEnabled`: Enable/disable entire system

2. **Injury Prevention**
   - `runConsecutiveAllowed`: Allow back-to-back runs
   - `mechanicalLoadMonitoring`: Track structural stress score
   - `rampRateLimit`: Max weekly TSS increase (3.0% to 8.0%)

3. **Schedule Constraints**
   - `strengthSpacingHours`: Hours between strength sessions (24, 48, or 72)
   - `allowCommuteExemption`: Exclude commutes from recovery rules

### PreferencesManager

All settings are stored in DataStore via `PreferencesManager` and read by `TrainingRulesEngine` as reactive flows.

---

## Areas for Improvement

### 1. Enhanced Readiness Calculation

**Current Limitations:**
- Fixed weight distribution (50/30/20)
- Limited TSB scoring range (-30 to 5)
- No trend analysis (improving vs declining)

**Recommendations:**
- User-configurable component weights
- Expand TSB range or use non-linear scaling
- Add 7-day trend comparison (e.g., "TSB improving" vs "TSB declining")
- Include training consistency factor

### 2. Additional Validation Rules

**Potential Rules:**
- **Ramp Rate Monitoring**: Validate weekly TSS increases against `rampRateLimit`
- **Phase-Appropriate Intensity**: Warn if workout intensity doesn't match phase
- **Recovery Day Validation**: Ensure rest days after high-intensity sessions
- **Weekly Volume Limits**: Set maximum weekly TSS by phase

### 3. Plan Suggestions

**Current State:** System only validates, doesn't suggest

**Potential Enhancements:**
- Suggest workout modifications based on readiness
- Recommend rest days when readiness is low
- Propose alternative workouts when rules are violated
- Phase-appropriate workout templates

### 4. Historical Learning

**Potential Features:**
- Learn from user's completion patterns
- Adapt rules based on injury history
- Track which warnings users ignore vs. follow
- Personalized rule recommendations

### 5. Integration Enhancements

**Potential Improvements:**
- Validate planned workouts (currently validates completed)
- Project future TSB based on planned workouts
- Multi-day plan validation (not just today)
- Weekly plan overview with aggregated warnings

### 6. Mechanical Load Enhancements

**Current Limitations:**
- Only compares 7-day windows
- Requires 14 days of data minimum
- Fixed 15% threshold

**Recommendations:**
- Configurable comparison window (7, 14, 21 days)
- User-configurable threshold percentage
- Individual run SSS tracking and visualization
- Cumulative SSS over training blocks

### 7. Zone Inference Improvements

**Current Limitations:**
- Falls back to TSS-based estimation
- No power/HR data validation

**Recommendations:**
- Improve zone inference accuracy
- Validate zone data quality
- Support for custom zone definitions
- Better handling of missing data

---

## Summary

The TriPath smart planning system ("Iron Brain") is a validation-based training advisor that:

1. **Validates User-Created Plans**: Checks daily workouts against configurable rules
2. **Calculates Readiness**: Combines TSB, sleep, wellness, and allergies into a readiness score
3. **Provides Intelligent Warnings**: Generates actionable warnings for rule violations and injury risks
4. **Respects User Control**: Users create plans manually; system provides guidance, not automation

The architecture is well-structured for incremental improvements. The validation-based approach gives users full control while providing intelligent guidance to prevent injury and optimize training.
