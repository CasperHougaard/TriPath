# Coach Planner Implementation Reference

This document captures the current Coach Planner logic and options as implemented in the app.

## Architecture Overview

| Layer | File | Responsibility |
|---|---|---|
| Screen | `CoachScreen.kt` | Main Coach UI: readiness, alerts, phase timeline, assessment, interventions |
| Screen | `AutoPlannerSettingsScreen.kt` | Auto-planner settings UI and plan generation controls |
| ViewModel | `CoachViewModel.kt` | Loads coach data, readiness, alerts, special periods, and triggers generation |
| ViewModel | `AutoPlannerSettingsViewModel.kt` | Reads and writes coach planning preferences |
| Domain | `CoachEngine.kt` | Calculates current training phase from goal date |
| Domain | `TrainingMetricsCalculator.kt` | Calculates CTL, ATL, TSB, safe weekly TSS, and workout TSS |
| Domain | `TrainingRulesEngine.kt` | Calculates readiness and validates plans against Iron Brain rules |
| Domain | `AutoPlannerGenerator.kt` | Generates multi-week training plans using anchors, budgets, and rule validation |
| Preferences | `PreferencesManager.kt` | Persists planner settings and user profile fields |
| Models | `TrainingPhase.kt`, `CoachModels.kt`, `AnchorType.kt`, `TrainingBalance.kt`, `UserProfile.kt` | Planner-related types and configuration |

## Main Coach Screen

`CoachScreen` shows the current coaching state and analysis.

### Sections shown

1. Readiness card
   - Only shown when Smart Planning is enabled.
   - Displays the current readiness state from `TrainingRulesEngine.calculateReadiness()`.
   - Opens a breakdown dialog on tap.

2. Coach alerts
   - Only shown when Smart Planning is enabled and warnings exist.
   - Displays warnings produced by the rules engine.

3. Phase timeline
   - Shows current date, goal date, and current phase.

4. Coach assessment
   - Displays the text message generated in `CoachViewModel.generateCoachMessage()`.

5. Performance pulse
   - Displays CTL, ATL, and TSB trend lines over the last 90 days.

6. Interventions
   - Lets the user add special periods:
     - Injury
     - Holiday
     - Recovery week

7. Active periods
   - Lists all stored special periods.
   - Allows deletion.

### Top-bar action

- Settings icon navigates to `AutoPlannerSettingsScreen`.

## Coach ViewModel Logic

`CoachViewModel` owns the main Coach screen state.

### State exposed

- `uiState: CoachUiState`
- `readinessState: StateFlow<ReadinessStatus?>`
- `alertsState: StateFlow<List<CoachWarning>>`
- `isGenerating: StateFlow<Boolean>`
- `generationError: StateFlow<String?>`
- `generationSuccess: StateFlow<Int?>`
- `isSmartPlanningEnabled: StateFlow<Boolean>`

### `CoachUiState`

Contains:

- `currentPhase`
- `activeSpecialPeriods`
- `allSpecialPeriods`
- `performanceMetrics`
- `coachAssessment`
- `performanceData`
- `goalDate`
- `isLoading`
- `formStatus`
- `userProfile`

### Startup behavior

`init` immediately calls:

- `loadCoachData()`
- `loadReadinessData()`

### `loadCoachData()`

This combines:

- `repository.getUserProfile()`
- `repository.getActiveSpecialPeriods(today)`
- `repository.getAllSpecialPeriods()`
- `repository.getAllWorkoutLogs()`

Behavior:

1. If no user profile exists:
   - loading stops
   - coach assessment becomes: "Please set up your user profile and goal date to receive coaching."

2. If profile exists:
   - reads `goalDate`
   - calculates current phase via `CoachEngine.calculatePhase(today, goalDate)`
   - calculates performance metrics using all logs up to today
   - builds 90-day chart data with daily CTL/ATL/TSB snapshots
   - generates coach assessment text
   - determines form status from TSB
   - updates the full screen state

### `loadReadinessData()`

This combines:

- all workout logs
- today's wellness log
- Smart Planning enabled flag
- today's training plans
- user profile

Behavior:

1. If Smart Planning is disabled or profile is missing:
   - readiness is cleared
   - alerts are cleared

2. If Smart Planning is enabled and profile exists:
   - calculates current CTL/ATL/TSB from workout logs up to today
   - loads wellness for today, defaulting allergy severity to `NONE`
   - loads sleep score from yesterday's sleep log
   - rounds TSB to an integer
   - calculates readiness
   - computes current phase and converts it to Iron Brain phase enum
   - finds yesterday's workout
   - finds the last strength date
   - builds recent run history for the last 14 days
   - calls `validateDailyPlan()`

Current implementation note:

- `todayPlan` is passed as `null`, so alert validation is currently based on completed history and readiness context, not a concrete planned workout for today.
- Because `validateDailyPlan()` returns early when `todayPlan == null`, the current main Coach screen does not surface rule violations for a specific planned session from this path.

### Coach assessment message priorities

`generateCoachMessage()` is marked deprecated, but it still drives the current screen.

Priority order:

1. Injury special period
   - Message: recovery mode, avoid impact, prioritize repair.

2. Recovery week special period
   - Message: reduce volume and intensity, focus on sleep and nutrition.

3. Holiday special period
   - Message: maintain activity if possible, but do not stress missed sessions.

4. Critical fatigue
   - If `TSB < -40`
   - Message warns of systemic fatigue and high injury/overtraining risk.

5. Sweet spot
   - If `TSB` is between `-30` and `-10`
   - Message says the athlete is absorbing workload efficiently.

6. Phase-specific fallback
   - OffSeason: structural integrity and 48h strength recovery language
   - Base: aerobic capacity, technique, consistency
   - Build: progressive overload and recovery discipline
   - Peak: race-pace intervals and simulation
   - Taper: maintain sharpness, reduce volume
   - Transition: rest and reset mentally

### Form status thresholds

`determineFormStatus(tsb)`:

- `TSB > 5.0` -> `FRESHNESS`
- `-30.0 <= TSB <= -10.0` -> `OPTIMAL`
- `TSB < -30.0` -> `OVERREACHING`
- all other values -> `OPTIMAL`

### Special period actions

- `addSpecialPeriod(type, startDate, endDate, notes)` inserts a `SpecialPeriod`
- `deleteSpecialPeriod(id)` removes a period

### Profile update actions available from Coach area

- `updateAvailability(...)`
- `updateDayAnchor(day, type)`
- `generateSeasonPlan(months = 3)`

## Training Phase Logic

`CoachEngine.calculatePhase(currentDate, goalDate)` drives phase selection.

### Rules

If `goalDate == null`:
- Returns `Base`

If current date is after the goal date:
- Up to 4 weeks post-race -> `Transition`
- More than 4 weeks post-race -> `Base`

If goal date is more than 6 months away:
- Returns `OffSeason`

Otherwise phase is based on weeks until goal:

- `<= 3 weeks` -> `Taper`
- `<= 9 weeks` -> `Peak`
- `<= 21 weeks` -> `Build`
- else -> `Base`

### TrainingPhase definitions

| Phase | Display Name | Focus Areas |
|---|---|---|
| `Transition` | Transition | Active Recovery, Mental Reset, Unstructured Training |
| `Taper` | Taper | Fatigue Management, Race Pace Sharpening, Logistics Planning |
| `Peak` | Peak | Race Specificity, Threshold Work, Simulation Days |
| `Build` | Build | Muscular Endurance, Tempo Work, Volume Accumulation |
| `Base` | Base | Aerobic Capacity, Technique, Consistency |
| `OffSeason` | Off-Season / Strength | Heavy Strength, Mobility, Structural Integrity |

### Coach phase mapping

`TrainingPhase.toCoachPhase()` maps the UI/domain phase to the Iron Brain enum:

- OffSeason -> `OFF_SEASON`
- Base -> `BASE`
- Build -> `BUILD`
- Peak -> `PEAK`
- Taper -> `TAPER`
- Transition -> `OFF_SEASON`

Current note:

- Transition is currently mapped to `OFF_SEASON` for Iron Brain rule evaluation.

## Performance Metrics Logic

`TrainingMetricsCalculator` centralizes TSS and performance metric calculations.

### PerformanceMetrics model

- `ctl`: Chronic Training Load / fitness
- `atl`: Acute Training Load / fatigue
- `tsb`: Training Stress Balance / form

### CTL/ATL/TSB model

The app uses the Banister impulse response model with exponentially weighted moving averages.

Constants:

- CTL time constant = `42.0`
- ATL time constant = `7.0`

Formulas applied day by day from the earliest workout to target date:

- `CTL_today = CTL_yesterday * (1 - 1/42) + TSS_today * (1/42)`
- `ATL_today = ATL_yesterday * (1 - 1/7) + TSS_today * (1/7)`
- `TSB = CTL - ATL`

Behavior details:

- Empty logs -> all metrics return `0.0`
- Only workouts on or before the target date are included
- Daily TSS is aggregated by workout date using `computedTSS`

### Safe weekly TSS

`calculateSafeWeeklyTSS(currentCtl)`:

- if CTL < 10: safe daily load = 40
- else: safe daily load = `currentCtl * 1.15`
- safe weekly TSS = `safeDailyLoad * 7`

### Workout TSS rules

Defaults:

- max HR = `185`
- FTP = `250`
- swim TSS/hour = `60`
- strength TSS/hour = `60`

By workout type:

1. Bike
   - If avg power and FTP exist, uses power-based TSS
   - Else if avg HR exists, uses HR-based TSS
   - Else fallback `durationHours * 40`

2. Run
   - If avg HR exists, uses HR-based TSS
   - Else fallback `durationHours * 50`

3. Swim
   - Uses `defaultSwimTSS` from profile, default `60/hour`

4. Strength
   - Uses `defaultStrengthHeavyTSS` from profile, default `60/hour`

5. Other
   - If avg HR exists, uses HR-based TSS
   - Else fallback `durationHours * 20`

6. Walk
   - If avg HR exists, uses HR-based TSS
   - Else fallback `durationHours * 30`

7. Hike
   - If avg HR exists, uses HR-based TSS
   - Else fallback `durationHours * 40`

HR-TSS helper:

- `durationHours * (avgHr / maxHr)^2 * 100`

## Readiness Logic

`TrainingRulesEngine.calculateReadiness()` produces `ReadinessStatus`.

### Inputs

- `tsb: Int`
- `sleepScore: Int?`
- `soreness: Int?`
- `mood: Int?`
- `allergy: AllergySeverity`

### Weighted components

1. TSB component: 50%
   - `TSB > 5` -> score `100`
   - `TSB < -30` -> score `0`
   - otherwise linearly interpolated between `-30` and `5`

2. Subjective component: 30%
   - based on soreness and mood on a 1-10 scale
   - if both present, uses their average
   - if only one present, uses that one
   - if none present, defaults to `50`

3. Sleep component: 20%
   - uses sleep score directly
   - defaults to `50` if absent

### Allergy penalty

- `NONE` -> `0`
- `MODERATE` -> `10`
- `SEVERE` -> `30`

Final score is clamped to `0..100`.

### Readiness colors

- `> 75` -> `GREEN`
- `>= 40` -> `YELLOW`
- else -> `RED`

### Readiness breakdown string

Contains readable components like:

- `TSB: X -> Y`
- `Sleep: X`
- `Subjective: X.X/10 -> Y`

## Iron Brain Rule Validation

`TrainingRulesEngine.validateDailyPlan()` validates a concrete daily workout plan.

It first checks whether Smart Planning is enabled.
If disabled:
- returns no warnings

Then loads preferences:

- allow consecutive runs
- strength spacing hours
- mechanical load monitoring
- commute exemption

### Rule 1: Consecutive runs

Triggered when:
- consecutive runs are not allowed
- yesterday was a run
- today plan is a run

Exception:
- if commute exemption is enabled and today's plan is marked as a commute

Warning generated:
- type: `RULE_VIOLATION`
- title: `Consecutive Runs Blocked`
- blocker: `true`

### Rule 2: Strength spacing

Triggered when:
- today's plan is strength
- a prior strength workout exists
- hours since last strength is less than configured spacing

Warning generated:
- type: `RULE_VIOLATION`
- title: `Strength Spacing Violation`
- blocker: `true`

Important implementation note:
- this calculation uses `ChronoUnit.HOURS` between the last strength date at start of day and `LocalDate.now().atStartOfDay()` rather than the plan date, so it is tied to the current date instead of the candidate workout date.

### Rule 3: Heavy legs protocol

Triggered when:
- yesterday was strength
- today's plan is not swim
- inferred workout zone is above 1

Warning generated:
- type: `RECOVERY_ADVICE`
- title: `Post-Strength Protocol`
- blocker: `false`

### Rule 4: Severe allergy protocol

Triggered when:
- today's wellness allergy severity is severe
- today's workout is strength or above zone 1

Warning generated:
- type: `INJURY_RISK`
- title: `Severe Allergy Active`
- blocker: `true`

### Rule 5: Mechanical load monitoring

Only evaluated if the preference is enabled.

Behavior:
- gathers run workouts from the last 14 days
- requires at least 14 run logs in the filtered set
- computes SSS for previous 7-day window and current 7-day window
- if current week SSS > previous week SSS * 1.15, creates a warning

Warning generated:
- type: `INJURY_RISK`
- title: `Mechanical Load Increase`
- blocker: `false`

### Structural Stress Score (SSS)

`calculateSSS(distanceKm, avgZone)`:

- `SSS = distanceKm * (1.0 + avgZone * 0.2)`

### Average zone inference

Used when evaluating heavy legs and mechanical load.

Priority:

1. HR zone distribution
2. Power zone distribution
3. TSS fallback:
   - `< 30` -> zone 1
   - `< 60` -> zone 2
   - `< 90` -> zone 3
   - `< 120` -> zone 4
   - else -> zone 5

## Generator-Specific Validation

`validateDailyPlanForGenerator()` is the plan-generation variant of the rules engine.

Differences from the UI validation path:

- accepts mixed history types (`WorkoutLog` and `TrainingPlan`)
- validates a generated `TrainingPlan`
- does not require wellness data
- is used to block placements during season generation

The generator treats a placement as valid only when no returned warning has `isBlocker = true`.

## Season Plan Generation

`CoachViewModel.generateSeasonPlan(months = 3)` is the entry point from UI.

### Generation flow in ViewModel

1. Sets `isGenerating = true`
2. Clears previous success and error
3. Loads profile
4. If missing profile:
   - error: `User profile not found. Please complete your profile.`
5. Loads all workout logs
6. Calculates current CTL from today's metrics
7. Computes `planStartDate` as next-or-same Monday
8. Collects recent logs from the last 14 days before today
9. Deletes all existing training plans
10. Calls `coachPlanGenerator.generateSeason(...)`
11. On success:
   - inserts generated plans
   - publishes generated count
12. On failure:
   - publishes reason and optional details
13. On exception:
   - publishes exception text
14. Finally sets `isGenerating = false`

### Validation before generation

`AutoPlannerGenerator.validateProfileForGeneration()` checks:

1. Profile exists
2. Goal date exists
3. Goal date is in the future
4. Weekly availability is present, otherwise generator logs a warning and assumes fallback availability
5. CTL is not negative
6. CTL does not exceed 150
7. Goal date is at least 2 weeks away from generation start
8. Goal date is not more than 2 years away
9. If generation end goes beyond goal date, generator logs a warning

### Fail-fast guard

If Smart Planning is disabled:
- generation aborts immediately with a failure message telling the user to enable it in Coach Settings

### Season loop

`generateSeason()`:

- defaults to 3 months
- uses `months * 4` weeks
- every 4th week is a recovery week

For each week:

1. Calculate phase from current simulated date and goal date
2. Calculate prescriptive base TSS
3. Apply phase adjustment and recovery week reduction
4. Generate the week
5. Add week plans to full result
6. Update simulated CTL using a rough approximation:
   - `currentSimulatedCtl = currentSimulatedCtl + (weeklyTSS / 7.0 - currentSimulatedCtl) * 0.1`
7. Advance to next week

If the full result is empty:
- returns failure saying no training plans were generated

## Weekly TSS Target Logic

### Prescriptive TSS

`calculatePrescriptiveTSS(currentCtl, rampRateLimit, phase, weekNumber)`:

- effective CTL floor = `20.0`
- base weekly TSS = `effectiveCtl * 7`
- ramp adjustment is only applied in Base and Build
- ramp adjustment = `baseTSS * (rampRateLimit / 100)`
- ramp adjustment is capped at `baseTSS / 2`

### Phase adjustment

`calculatePhaseAdjustedTSS(baseTSS, phase, isRecoveryWeek)` multipliers:

- Base -> `1.0`
- Build -> `1.05`
- Peak -> `1.0`
- Taper -> `0.55`
- OffSeason -> `0.95`
- Transition -> `0.35`

If recovery week:
- final adjusted TSS is multiplied by `0.8`

## Weekly Generation Logic

`generateWeek()` works in two major steps:

1. Place user-defined anchors
2. Fill remaining gaps by discipline

### Inputs used

- `weekStart`
- `targetTSS`
- `profile`
- already generated history
- recent real logs
- current phase
- recent discipline loads

### Weekly schedule source

Uses:
- `profile.weeklySchedule`
- otherwise `UserProfile.DEFAULT_WEEKLY_SCHEDULE`

Default schedule:

- Monday -> `NONE`
- Tuesday -> `STRENGTH`
- Wednesday -> `NONE`
- Thursday -> `STRENGTH`
- Friday -> `NONE`
- Saturday -> `BIKE`
- Sunday -> `LONG_RUN`

### Discipline budget calculation

`calculateDisciplineBudget(totalTargetTSS, balance, strengthSessions, recentLoads)`:

1. Strength tax
   - `strengthCost = strengthSessions * 50`
   - `cardioBudget = max(0, totalTargetTSS - strengthCost)`

2. Base split from training balance
   - swim = `% of cardioBudget`
   - bike = `% of cardioBudget`
   - run = `% of cardioBudget`

3. Run safety clamp
   - recent average run load from repository
   - `maxSafeRun = recentRunAvg * 1.15 + 15`
   - if planned run TSS exceeds max safe run:
     - run is capped
     - overflow is shifted to bike

4. Final total = swim + bike + run + strength

### Training balance presets

`TrainingBalance` presets:

- `IRONMAN_BASE = 50 bike / 30 run / 20 swim`
- `BALANCED = 34 / 33 / 33`
- `RUN_FOCUS = 30 / 50 / 20`
- `BIKE_FOCUS = 60 / 25 / 15`

## Anchor Placement Logic

`placeUserAnchors()` iterates all 7 days and schedules the configured anchor if possible.

### Supported anchor types

- `NONE`
- `RUN`
- `BIKE`
- `SWIM`
- `STRENGTH`
- `LONG_RUN`
- `LONG_BIKE`

### Anchor placement rules

For each day:

- skips if day already has a workout
- checks remaining discipline budget
- validates placement with generator rules
- creates a ghost `TrainingPlan` if valid

### Anchor defaults

1. Strength anchor
   - duration: 60 min
   - TSS: `defaultStrengthHeavyTSS` or `60`
   - focus: `FULL_BODY`
   - intensity: `HEAVY`

2. Long run anchor
   - duration from `calculateLongRunDuration()`
   - TSS = `duration * 1.2`
   - subtype = `Long Run`

3. Long bike anchor
   - duration from `calculateLongBikeDuration()`
   - TSS = `duration * 0.8`
   - subtype = `Long Bike`

4. Run anchor
   - duration: 45 min
   - TSS: 45

5. Bike anchor
   - duration: 45 min
   - TSS: 40

6. Swim anchor
   - duration: 60 min
   - TSS: `defaultSwimTSS` or `60`

### Long run duration rules

Base duration by training balance:

- Ironman base -> 150 min
- Balanced -> 105 min
- Run focus -> 75 min
- default -> 90 min

Phase adjustments:

- Taper -> `* 0.6`
- Transition -> `* 0.4`
- otherwise `* 1.0`

TSS scaling:

- target TSS > 400 -> duration `* 1.2`
- target TSS < 200 -> duration `* 0.8`

Final clamp:
- `30..240` minutes

### Long bike duration rules

Base duration by training balance:

- Ironman base -> 180 min
- Balanced -> 120 min
- Bike focus -> 240 min
- default -> 150 min

Phase adjustments:

- Taper -> `* 0.6`
- Transition -> `* 0.4`
- otherwise `* 1.0`

TSS scaling:

- target TSS > 400 -> duration `* 1.2`
- target TSS < 200 -> duration `* 0.8`

Final clamp:
- `30..360` minutes

## Gap-Fill Logic

`fillGaps()` preserves anchors, then adds fillers in order:

1. Run fillers
2. Bike fillers
3. Swim fillers

For each discipline:

- computes remaining TSS need for that discipline
- iterates through the 7 days of the week
- skips days that already have a workout
- respects `weeklyAvailability`
- validates placement with Iron Brain rules
- inserts a filler workout if valid

Filler defaults:

- Run filler: 45 min, 45 TSS
- Bike filler: 45 min, 40 TSS
- Swim filler: 60 min, `defaultSwimTSS`

Fallback availability when none is defined for filler checks:
- RUN, BIKE, SWIM are all treated as allowed

## Ghost Plan Construction

Generated plans are created with `createGhostPlan()`.

Fields set:

- random UUID id
- date
- workout type
- subtype if applicable
- duration minutes
- planned TSS
- strength focus if applicable
- intensity if applicable

## User Profile Fields Used by Coach Planner

`UserProfile` contains planner-relevant configuration.

### Core fields

- `ftpBike`
- `maxHeartRate`
- `defaultSwimTSS`
- `defaultStrengthHeavyTSS`
- `defaultStrengthLightTSS`
- `goalDate`
- `weeklyHoursGoal`
- `lthr`
- `cssSecondsper100m`
- `thresholdRunPace`
- `weeklyAvailability`
- `longTrainingDay`
- `strengthDays`
- `trainingBalance`
- `weeklySchedule`

### Defaults

- `defaultSwimTSS = 60`
- `defaultStrengthHeavyTSS = 60`
- `defaultStrengthLightTSS = 30`
- `longTrainingDay = SUNDAY`
- `strengthDays = 2`
- `trainingBalance = IRONMAN_BASE`

## Planning Settings Screen

`AutoPlannerSettingsScreen` exposes planner auto-planner configuration.

### View model state

`AutoPlannerSettingsState` contains:

- `isSmartPlanningEnabled`
- `runConsecutiveAllowed`
- `strengthSpacingHours`
- `rampRateLimit`
- `mechanicalLoadMonitoring`
- `allowCommuteExemption`

### Section 1: Master Control

#### Enable Smart Planning

- Type: switch
- Default: `true`
- Purpose: master on/off control for AI-driven planning features

## Section 2: Injury Prevention

### Allow Consecutive Runs

- Type: switch
- Default: `false`
- Meaning: disables back-to-back runs unless changed

### Monitor Mechanical Load

- Type: switch
- Default: `true`
- Meaning: enables SSS-based load monitoring

### Max Ramp Rate

- Type: slider
- Default: `5.0`
- Range: `3.0` to `8.0`
- Step behavior: 0.5 increments
- Meaning: limits weekly TSS growth used by the generator

## Section 3: Schedule Constraints

### Strength Recovery

- Type: slider
- Default: `48 hours`
- Meaning: minimum spacing between strength sessions

### Allow Commute Exemption

- Type: switch
- Default: `true`
- Meaning: allows commute runs to bypass the consecutive-run block

### Weekly schedule editing

`AutoPlannerSettingsScreen` also reads `CoachViewModel.uiState.userProfile` and uses coach components to edit scheduling-related structure.

This area is intended to support:
- weekly anchor configuration
- discipline balance
- availability and schedule constraints tied to generation

## Planning Settings ViewModel

`AutoPlannerSettingsViewModel` is a thin preferences bridge.

### It combines these flows

- `smartPlanningEnabledFlow`
- `runConsecutiveAllowedFlow`
- `strengthSpacingHoursFlow`
- `rampRateLimitFlow`
- `mechanicalLoadMonitoringFlow`
- `allowCommuteExemptionFlow`

### Setter methods

- `setSmartPlanning(enabled)`
- `setRunConsecutiveAllowed(allowed)`
- `setStrengthSpacingHours(hours)`
- `setRampRateLimit(limit)`
- `setMechanicalLoadMonitoring(enabled)`
- `setAllowCommuteExemption(allowed)`

## Preferences and Defaults

Coach planning preferences are stored in `PreferencesManager`.

### Planner preference keys and defaults

| Preference | Key | Default |
|---|---|---|
| Smart Planning Enabled | `is_smart_planning_enabled` | `true` |
| Consecutive Runs Allowed | `run_consecutive_allowed` | `false` |
| Strength Spacing Hours | `strength_spacing_hours` | `48` |
| Ramp Rate Limit | `ramp_rate_limit` | `5.0f` |
| Mechanical Load Monitoring | `mechanical_load_monitoring` | `true` |
| Allow Commute Exemption | `allow_commute_exemption` | `true` |

### User profile persistence relevant to planning

`PreferencesManager` also persists:

- weekly availability
- long training day
- strength days
- training balance
- weekly schedule
- goal date
- swim/strength TSS defaults
- heart rate and FTP thresholds

## Warning and Readiness Models

From `CoachModels.kt`.

### `ReadinessColor`

- `GREEN`
- `YELLOW`
- `RED`

### `WarningType`

- `INJURY_RISK`
- `RULE_VIOLATION`
- `RECOVERY_ADVICE`

### Iron Brain phase enum

Separate enum used by rules engine:

- `OFF_SEASON`
- `BASE`
- `BUILD`
- `PEAK`
- `TAPER`
- `RACING`

### `ReadinessStatus`

Fields:

- `score`
- `color`
- `breakdown`
- `allergyPenalty`

### `CoachWarning`

Fields:

- `type`
- `title`
- `message`
- `isBlocker`

### `StructuralStressScore`

Fields:

- `value`
- `isHighRisk`

## Current Functional Notes and Limitations

1. Coach assessment still uses deprecated hardcoded thresholds.
2. Coach screen readiness/alerts path does not yet validate a concrete planned workout for today because `todayPlan` is passed as `null`.
3. Strength spacing in `validateDailyPlan()` uses `LocalDate.now()` rather than the actual plan date.
4. Transition phase is mapped to `OFF_SEASON` for rules-engine purposes.
5. Generator assumes a four-week month when calculating `months * 4` weeks.
6. Generator deletes all existing training plans before writing a newly generated season.

## Summary of Current User-Facing Options

The Coach Planner currently gives the user these adjustable options:

1. Enable or disable Smart Planning
2. Allow or block consecutive runs
3. Set minimum hours between strength sessions
4. Set maximum weekly ramp rate
5. Enable or disable mechanical load monitoring
6. Enable or disable commute exemption
7. Configure weekly schedule anchors
8. Configure weekly availability
9. Set long training day
10. Set strength days
11. Select training balance preset or balance values
12. Add Injury, Holiday, and Recovery Week override periods
13. Generate a new season plan

## Source Files

Primary implementation sources for this document:

- `app/src/main/java/com/tripath/ui/coach/CoachScreen.kt`
- `app/src/main/java/com/tripath/ui/coach/CoachViewModel.kt`
- `app/src/main/java/com/tripath/ui/coach/AutoPlannerSettingsScreen.kt`
- `app/src/main/java/com/tripath/ui/coach/AutoPlannerSettingsViewModel.kt`
- `app/src/main/java/com/tripath/domain/CoachEngine.kt`
- `app/src/main/java/com/tripath/domain/TrainingMetricsCalculator.kt`
- `app/src/main/java/com/tripath/domain/coach/TrainingRulesEngine.kt`
- `app/src/main/java/com/tripath/domain/coach/AutoPlannerGenerator.kt`
- `app/src/main/java/com/tripath/data/local/preferences/PreferencesManager.kt`
- `app/src/main/java/com/tripath/data/model/UserProfile.kt`
- `app/src/main/java/com/tripath/data/model/TrainingBalance.kt`
- `app/src/main/java/com/tripath/data/model/AnchorType.kt`
- `app/src/main/java/com/tripath/domain/TrainingPhase.kt`
- `app/src/main/java/com/tripath/domain/coach/CoachModels.kt`
- `app/src/main/java/com/tripath/data/local/database/entities/SpecialPeriod.kt`

