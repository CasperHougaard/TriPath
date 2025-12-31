# IronBrain System Documentation

## Overview

**IronBrain** is the nickname for TriPath's smart planning system. It consists of two main components:

1. **TrainingRulesEngine** - The core validation and readiness calculation engine
2. **CoachPlanGenerator** - The auto-pilot plan generation system

Together, these form the "Iron Brain" that validates training plans and provides intelligent coaching guidance.

---

## Core Architecture

### 1. TrainingRulesEngine ("Iron Brain")

**Location:** `app/src/main/java/com/tripath/domain/coach/TrainingRulesEngine.kt`

A singleton that:
- Validates daily training plans against configurable rules
- Calculates readiness scores from multiple recovery metrics
- Generates coach warnings for rule violations
- Reads configuration from `PreferencesManager`

**Key Methods:**
- `validateDailyPlan()` - Validates completed workouts
- `validateDailyPlanForGenerator()` - Validates planned workouts (polymorphic)
- `calculateReadiness()` - Calculates readiness score from recovery metrics
- `calculateSSS()` - Calculates Structural Stress Score for mechanical load

### 2. CoachPlanGenerator (Auto-Pilot)

**Location:** `app/src/main/java/com/tripath/domain/coach/CoachPlanGenerator.kt`

Generates training plans for a specified duration using:
- Phase-aware logic
- Discipline-specific TSS budgets
- Iron Brain rule validation for each placement

**Key Methods:**
- `generateSeason()` - Generates multi-month training plans
- `calculateDisciplineBudget()` - Calculates TSS budgets per discipline
- `validatePlacement()` - Validates workout placement against rules

---

## Readiness Calculation

The readiness system combines multiple recovery metrics into a single score (0-100) with color coding.

### Formula Components:

**1. TSB Score (50% weight)**
- TSB > 5 → 100 points (fresh, ready to race)
- TSB < -30 → 0 points (overreaching, high fatigue)
- Linear interpolation between -30 and 5

**2. Subjective Metrics (30% weight)**
- Average of soreness and mood (1-10 scale)
- Converts to 0-100: `(avg - 1) / 9 * 100`
- Defaults to 50 if missing

**3. Sleep Score (20% weight)**
- Uses sleep score directly (0-100 scale)
- Defaults to 50 if missing

**4. Allergy Penalty**
- Moderate: -10 points
- Severe: -30 points

### Final Score & Color:
- **>75**: GREEN (ready to train)
- **40-75**: YELLOW (caution)
- **<40**: RED (not ready)

### Implementation Details:

```kotlin
suspend fun calculateReadiness(
    tsb: Int,
    sleepScore: Int?,
    soreness: Int?,
    mood: Int?,
    allergy: AllergySeverity
): ReadinessStatus {
    // TSB Component (50%)
    val tsbScore = when {
        tsb > 5 -> 100
        tsb < -30 -> 0
        else -> {
            val range = 5 - (-30) // 35
            val position = tsb - (-30)
            ((position.toDouble() / range) * 100).toInt().coerceIn(0, 100)
        }
    }
    
    // Subjective Component (30%)
    val subjectiveScore = // ... average of soreness and mood
    
    // Sleep Component (20%)
    val sleepScoreComponent = sleepScore ?: 50
    
    // Weighted calculation
    val weightedScore = (tsbScore * 0.5 + subjectiveScore * 0.3 + sleepScoreComponent * 0.2).roundToInt()
    
    // Apply allergy penalty
    val finalScore = (weightedScore - allergyPenalty).coerceIn(0, 100)
    
    // Determine color
    val color = when {
        finalScore > 75 -> ReadinessColor.GREEN
        finalScore >= 40 -> ReadinessColor.YELLOW
        else -> ReadinessColor.RED
    }
    
    return ReadinessStatus(score = finalScore, color = color, ...)
}
```

---

## Plan Validation Rules

Five configurable rules that generate warnings when violated:

### Rule 1: Consecutive Runs (Plate Protection)

**Purpose:** Prevent back-to-back running sessions to reduce injury risk from repetitive impact.

**Configuration:** `runConsecutiveAllowed` (boolean) in `PlanningSettingsScreen`

**Logic:**
- Checks if yesterday was RUN and today is RUN
- Exception: Skip if commute exemption is enabled and today is a commute
- Warning Type: `RULE_VIOLATION` (Blocker)

### Rule 2: Strength Spacing

**Purpose:** Ensure adequate recovery between strength sessions for muscle protein synthesis.

**Configuration:** `strengthSpacingHours` (24, 48, or 72 hours)

**Logic:**
- Calculates hours since last strength session
- Blocks if spacing < configured hours
- Warning Type: `RULE_VIOLATION` (Blocker)

### Rule 3: Post-Strength Protocol (Heavy Legs)

**Purpose:** Recommend low-intensity recovery after strength sessions.

**Configuration:** Hard-coded rule (not configurable)

**Logic:**
- If yesterday was STRENGTH and today is not SWIM
- Checks if today's zone > 1
- Warning Type: `RECOVERY_ADVICE` (Non-blocker)
- Message: "Post-Strength Rule: Consider Swim or Zone 1 Spin only."

### Rule 4: Severe Allergy Protocol

**Purpose:** Restrict training intensity during severe allergy episodes.

**Configuration:** Based on `DailyWellnessLog.allergySeverity`

**Logic:**
- If allergy severity is SEVERE
- Blocks STRENGTH or any workout with zone > 1
- Warning Type: `INJURY_RISK` (Blocker)
- Message: "Severe Allergy Active. Only Zone 1 Active Recovery allowed."

### Rule 5: Mechanical Load Monitoring (SSS)

**Purpose:** Track structural stress from running to prevent overuse injuries.

**Configuration:** `mechanicalLoadMonitoring` (boolean)

**SSS Formula:**
```kotlin
fun calculateSSS(distanceKm: Double, avgZone: Int): Double {
    return distanceKm * (1.0 + (avgZone * 0.2))
}
```

**Logic:**
- Calculates 7-day rolling SSS for current week (last 7 days)
- Calculates 7-day rolling SSS for previous week (7-14 days ago)
- Warns if current week > 15% vs previous week
- Requires: 14 days of run data minimum
- Warning Type: `INJURY_RISK` (Non-blocker)

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

---

## Auto-Pilot Plan Generation

The `CoachPlanGenerator` generates training plans using phase-aware logic and Iron Brain rule validation.

### Generation Process:

#### 1. Validation Phase

Before generation, validates:
- User profile exists
- Goal date is set and in future (2 weeks to 2 years away)
- CTL is in valid range (0-150)
- Weekly availability is configured
- Ramp rate is within safe limits

#### 2. Phase-Aware TSS Calculation

**Base TSS:** `CTL * 7`

**Ramp Rate Application:**
- Progressive overload phases (Base, Build): Apply ramp rate (3-8% configurable)
- Other phases: Maintain or reduce

**Phase Multipliers:**
- Base: 1.0
- Build: 1.05
- Peak: 1.0
- Taper: 0.55
- Off-Season: 0.95
- Transition: 0.35

**Recovery Week:** 80% reduction (every 4th week, following 3:1 loading principle)

#### 3. Discipline Budget Calculation

**Strength Tax:**
- `strengthSessions * 50 TSS`
- Cardio Budget = `totalTSS - strengthCost`

**Base Split:**
- Applied from Training Balance (IRONMAN_BASE, BALANCED, etc.)
- Swim, Bike, Run percentages from balance settings

**Safety Clamp for Running:**
- Running TSS capped at 115% of recent average + 15
- Overflow redirected to bike (low injury risk discipline)

#### 4. Week Generation Strategy

**Step 1: Place User-Defined Anchors**
- Reads from `weeklySchedule` in UserProfile
- Anchor types: LONG_RUN, LONG_BIKE, STRENGTH, RUN, BIKE, SWIM
- Each anchor validated against Iron Brain rules
- Logs warnings when anchors are blocked

**Step 2: Fill Gaps**
- Uses discipline-specific budgets (after anchors)
- Priority order: Run (high priority) → Bike (volume filler) → Swim
- Each placement validated via `validatePlacement()`
- Preserves anchor workouts

#### 5. Rule Validation During Generation

- Each workout placement checked via `validatePlacement()`
- Uses `validateDailyPlanForGenerator()` (polymorphic version)
- Blocks placements that violate rules
- Logs detailed warnings when anchors are blocked

### Example Generation Flow:

```
Week 1 (Base Phase, Target: 350 TSS)
├── Calculate Budget: Run=120, Bike=150, Swim=60, Strength=100
├── Place Anchors:
│   ├── Monday: STRENGTH (60 TSS) ✓
│   ├── Wednesday: LONG_RUN (90 TSS) ✓
│   └── Saturday: LONG_BIKE (120 TSS) ✓
├── Adjusted Budget: Run=30, Bike=30, Swim=60, Strength=40
└── Fill Gaps:
    ├── Tuesday: Run (45 TSS) - BLOCKED (consecutive runs)
    ├── Thursday: Bike (40 TSS) ✓
    └── Friday: Swim (60 TSS) ✓
```

---

## Integration with UI

### CoachViewModel Integration:

**1. Readiness State Flow**
- Calculates readiness daily
- Updates when wellness/sleep data changes
- Displays on Coach Screen as color-coded card

**2. Alerts State Flow**
- Validates today's plan (currently validates completed workouts)
- Displays warnings in alerts list
- Shows blocker vs. advisory warnings

**3. Generation State**
- `isGenerating`: Loading state during generation
- `generationError`: Error messages with helpful details
- `generationSuccess`: Number of plans created

### Data Flow:

```
User creates plan → CoachViewModel → TrainingRulesEngine.validateDailyPlan()
                                 ↓
                         List<CoachWarning>
                                 ↓
                         UI displays alerts

User requests generation → CoachViewModel → CoachPlanGenerator.generateSeason()
                                         ↓
                                 Validates each placement
                                         ↓
                                 Creates TrainingPlan objects
                                         ↓
                                 Saves to database
```

---

## Master Toggle

The entire smart planning system can be enabled/disabled via:
- `PlanningSettingsScreen` → `smartPlanningEnabled`
- When **disabled**: No validation, no readiness calculation, no warnings
- When **enabled**: Full validation and readiness assessment active

---

## Configuration Settings

All rules are configurable via `PlanningSettingsScreen`:

### 1. Master Control
- `smartPlanningEnabled`: Enable/disable entire system

### 2. Injury Prevention
- `runConsecutiveAllowed`: Allow back-to-back runs
- `mechanicalLoadMonitoring`: Track structural stress score
- `rampRateLimit`: Max weekly TSS increase (3.0% to 8.0%)

### 3. Schedule Constraints
- `strengthSpacingHours`: Hours between strength sessions (24, 48, or 72)
- `allowCommuteExemption`: Exclude commutes from recovery rules

### Storage
All settings stored in DataStore via `PreferencesManager` and read by `TrainingRulesEngine` as reactive flows.

---

## Key Philosophy

**IronBrain is validation-based, not prescriptive:**

- Users create plans manually in the Planner
- System validates and provides warnings
- Users maintain full control while receiving intelligent guidance
- Prevents injury and overtraining through scientific rules

This approach gives users autonomy while providing expert-level coaching guidance.

---

## Current Limitations

1. **Validation Scope**: Only validates completed workouts (not planned workouts)
2. **Fixed Weights**: Readiness calculation uses fixed 50/30/20 distribution
3. **Fixed Thresholds**: SSS uses fixed 15% threshold (not configurable)
4. **No Suggestions**: System only validates, doesn't suggest modifications
5. **No Learning**: No historical learning from user patterns

---

## Future Enhancement Opportunities

### 1. Enhanced Readiness Calculation
- User-configurable component weights
- Expand TSB range or use non-linear scaling
- Add 7-day trend comparison
- Include training consistency factor

### 2. Additional Validation Rules
- Ramp Rate Monitoring: Validate weekly TSS increases
- Phase-Appropriate Intensity: Warn if workout intensity doesn't match phase
- Recovery Day Validation: Ensure rest days after high-intensity sessions
- Weekly Volume Limits: Set maximum weekly TSS by phase

### 3. Plan Suggestions
- Suggest workout modifications based on readiness
- Recommend rest days when readiness is low
- Propose alternative workouts when rules are violated
- Phase-appropriate workout templates

### 4. Historical Learning
- Learn from user's completion patterns
- Adapt rules based on injury history
- Track which warnings users ignore vs. follow
- Personalized rule recommendations

### 5. Integration Enhancements
- Validate planned workouts (currently validates completed)
- Project future TSB based on planned workouts
- Multi-day plan validation (not just today)
- Weekly plan overview with aggregated warnings

### 6. Mechanical Load Enhancements
- Configurable comparison window (7, 14, 21 days)
- User-configurable threshold percentage
- Individual run SSS tracking and visualization
- Cumulative SSS over training blocks

### 7. Zone Inference Improvements
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
4. **Generates Training Plans**: Auto-pilot system creates phase-aware plans respecting all rules
5. **Respects User Control**: Users create plans manually; system provides guidance, not automation

The architecture is well-structured for incremental improvements. The validation-based approach gives users full control while providing intelligent guidance to prevent injury and optimize training.

---

**Last Updated:** January 2025  
**Database Version:** 11  
**Min SDK:** 33 | **Target SDK:** 35

