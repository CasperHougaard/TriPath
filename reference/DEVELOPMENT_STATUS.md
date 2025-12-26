# TriPath Development Status Log

**Last Updated:** December 26, 2025  
**Database Version:** 5  
**Min SDK:** 33 | **Target SDK:** 35

---

## 🎯 TriPath: Core Mission & AI Development Base

**Project Vision:** TriPath is a high-performance training platform specifically engineered to guide an athlete from long-term off-season preparation to a peak performance at Ironman 2027. The app's "North Star" is scientific periodization, balancing high-intensity triathlon disciplines with heavy strength training.

### Core Philosophies for AI Development

1. **Data-Driven Authority:** The app is not a passive logger; it is a **Strategic Coach**. Every feature must serve the goal of optimizing the athlete's CTL (Fitness), ATL (Fatigue), and TSB (Form).
2. **Scientific Periodization:** Development follows the **3:1 loading principle** (3 weeks build, 1 week recovery). The AI should always prioritize the current Training Phase (Base, Build, Peak, Off-Season) when suggesting workouts or analyzing data.
3. **The "Hjernen" (The Brain):** The Coach Screen is the central intelligence. It overrides daily plans based on physiological markers and "Life Events" (Injury, Holiday, Recovery).
4. **Off-Season Priority:** Unlike generic apps, TriPath treats **Strength Training** as a core discipline. During off-season, the app must strictly follow muscle-building science, ensuring 48h rest between heavy sessions and prioritizing structural integrity over cardio volume.
5. **High-Density Visualization:** The athlete is data-literate. UI development must favor compact, information-dense layouts (like the 4-week Planner Matrix) that allow for quick pattern recognition across long training blocks.

### Technical Guardrails

- **Primary Metric:** TSS (Training Stress Score).
- **Integration:** Deep Health Connect sync (Garmin/Oura/etc.).
- **Stability:** Strict MVVM architecture with a "Clean Data" policy—no walking/hiking data should pollute running pace statistics.

---

## ✅ Completed Features

### 1. Data Layer (100% Complete)

| Component | Status | Details |
|-----------|--------|---------|
| Room Database | ✅ | Version 5, 3 entities with auto-migrations |
| `TrainingPlan` entity | ✅ | Planned workouts with TSS, duration, type, strength focus |
| `WorkoutLog` entity | ✅ | Synced workouts with HR, distance, speed, power, steps |
| `UserProfile` entity | ✅ | FTP, max HR, LTHR, CSS (Stored in DataStore) |
| `SpecialPeriod` entity | ✅ | Tracks injury, holiday, and recovery weeks |
| Repository Pattern | ✅ | `TrainingRepository` with Coroutines and Flows |
| Hilt DI | ✅ | Centralized dependency management |

### 2. Health Connect Integration (100% Complete)

| Feature | Status | Details |
|---------|--------|---------|
| Permission handling | ✅ | 7 permissions (exercise, HR, calories, distance, speed, power, steps) |
| Exercise sync | ✅ | Maps exercises to RUN/BIKE/SWIM/STRENGTH/OTHER |
| Metrics extraction | ✅ | HR, calories, distance, speed, power, steps per workout |
| TSS calculation | ✅ | Power-based (bike), HR-based (run/other), duration-based (swim/strength) |
| Deduplication | ✅ | Uses `connectId` to prevent duplicate imports |
| Resync history | ✅ | Re-classify existing workouts (fixes walking → OTHER) |
| Sync period config | ✅ | User-configurable 7/14/30/60 days lookback |

### 3. Training Metrics Engine (100% Complete)

| Metric | Status | Implementation |
|--------|--------|----------------|
| TSS Calculation | ✅ | `TrainingMetricsCalculator` singleton |
| Power-based TSS | ✅ | Uses FTP for cycling intensity factor |
| HR-based TSS | ✅ | hrTSS formula for running/other |
| Duration-based TSS | ✅ | Configurable defaults for swim/strength |
| Workout classification | ✅ | Walking/hiking → OTHER to avoid data pollution |

### 4. UI Screens (85% Complete)

| Screen | Status | Features |
|--------|--------|----------|
| **Dashboard** | ✅ | Weekly calendar strip, load indicator, selected day details, time-based greeting |
| **Weekly Planner** | ✅ | 7-day view, add/delete workouts, week navigation |
| **Statistics** | ✅ | Period selector, TSS trend chart, volume chart, discipline breakdown, key metrics |
| **Progress (CTL/ATL)** | ✅ | CTL/ATL/TSB trends, Form status visualization, 90-day history |
| **Coach** | ✅ | AI-driven assessment, phase timeline, manual interventions (Injury/Holiday/Recovery) |
| **Settings** | ✅ | User profile editing, Health Connect sync, backup/restore, theme toggle |
| **Workout Detail** | ✅ | Detailed metrics for planned and completed workouts, HR analysis, TSS delta |

### 5. UI Components (100% Complete)

| Component | Status | Usage |
|-----------|--------|-------|
| `WorkoutCard` | ✅ | Dashboard, WeekDayItem |
| `LoadIndicator` | ✅ | Dashboard (weekly TSS progress) |
| `StatCard` | ✅ | Dashboard (weekly stats) |
| `SectionHeader` | ✅ | All screens for organization |
| `WorkoutBadge` | ✅ | Weekly planner (sport badges) |
| `TextBadge` | ✅ | Ready for status indicators |
| `EmptyState` | ✅ | WeekDayItem rest days |
| `SummaryCard` | ✅ | Ready for history screens |
| `WeeklyCalendarStrip` | ✅ | Dashboard day selection |
| `ActivitySummaryRow` | ✅ | Dashboard today's activities |

### 6. Design System (100% Complete)

| System | Status | Details |
|--------|--------|---------|
| Sport Colors | ✅ | Swim: #00B8FF, Run: #FF6B35, Bike: #1565C0, Strength: #9C27B0 |
| Spacing System | ✅ | 4dp increments from xs(4) to xxxl(32) |
| Icon Sizes | ✅ | 16/24/32/40/48dp standardized |
| Typography | ✅ | Material 3 semantic naming |
| Dark Theme | ✅ | Default, high-contrast for outdoor use |
| Light Theme | ✅ | Available via toggle |

### 7. Backup & Restore (100% Complete)

| Feature | Status | Details |
|---------|--------|---------|
| JSON Export | ✅ | Training plans, workout logs, user profile |
| JSON Import | ✅ | With version validation, atomic transaction |
| Schema versioning | ✅ | `BACKUP_VERSION = 1` |
| Clear all data | ✅ | Full database reset capability |

### 8. Preferences (100% Complete)

| Preference | Status | Details |
|------------|--------|---------|
| Dark/Light theme | ✅ | DataStore persisted, toggle in settings |
| Sync days | ✅ | Configurable 7-60 days lookback |

---

## 🚧 In Progress / Incomplete

### 1. Advanced Analytics
**Status:** Core metrics working, adding depth  
**Priority:** MEDIUM

**Missing:**
- [ ] Running pace zones (Z1-Z5)
- [ ] Bike power zones (based on FTP)
- [ ] Swim CSS-based pace targets
- [ ] Fatigue prediction based on planned TSS

### 2. Onboarding Flow
**Status:** Not implemented  
**Priority:** LOW (app works without it)

**Missing:**
- [ ] Initial setup wizard
- [ ] Health Connect permission explanation
- [ ] Profile setup on first launch

### 3. Polish & UX
- [ ] Proper database migrations (currently auto/destructive in some cases)
- [ ] Recurring workout templates
- [ ] Notifications for key milestones

---

## 🔮 Roadmap / Suggested Next Steps

### Phase 1: Enhanced Planning & UX
1. **Recurring Workouts**
   - Weekly recurring plans
   - Copy week functionality

2. **Training Plan Templates**
   - Pre-built workout templates (e.g., "Easy Run", "Brick Workout")
   - Quick-add from templates

### Phase 2: Advanced Analytics
3. **Zone-Based Training**
   - Running pace zones visualization
   - Bike power zones (based on FTP)
   - Swim CSS-based targets in details

4. **Training Blocks / Periodization**
   - Phase-specific TSS targets
   - Automated periodization planning

### Phase 3: Polish & Onboarding
5. **Onboarding**
   - First-run experience wizard
   - Health Connect setup guide

6. **Notifications**
   - Workout reminders
   - Rest day suggestions

---

## 📁 Architecture Summary

```
com.tripath/
├── data/
│   ├── local/
│   │   ├── backup/         → BackupManager, LocalDateSerializer
│   │   ├── database/       → AppDatabase, DAOs, Entities, Converters
│   │   ├── healthconnect/  → HealthConnectManager
│   │   ├── preferences/    → PreferencesManager (DataStore)
│   │   └── repository/     → TrainingRepository interface + impl
│   └── model/              → WorkoutType, Intensity, StrengthFocus enums
├── di/                     → Hilt modules (DatabaseModule, RepositoryModule)
├── domain/                 → TrainingMetricsCalculator
├── ui/
│   ├── components/         → Reusable UI components
│   ├── dashboard/          → Dashboard screen + ViewModel
│   ├── navigation/         → NavHost setup
│   ├── planner/            → Weekly planner screen + ViewModel
│   ├── settings/           → Settings screen + ViewModel
│   ├── showcase/           → Design showcase (dev only)
│   ├── stats/              → Statistics screen + ViewModel + components
│   └── theme/              → Colors, Typography, Spacing
├── MainActivity.kt
├── TriPathApplication.kt   → @HiltAndroidApp
└── HealthConnectPrivacyPolicyActivity.kt
```

---

## 🔧 Technical Debt / Considerations

| Item | Severity | Notes |
|------|----------|-------|
| ~~Destructive migrations~~ | ✅ Resolved | All migrations are now explicitly defined and non-destructive. Migration classes exist for all version transitions (1→2, 2→3, 3→4, 4→5). |
| Input validation | 🟡 Low | TSS/duration fields in AddWorkoutBottomSheet need more robust validation. |
| Error boundaries | 🟡 Low | Consider adding error handling UI for sync failures beyond snackbars. |
| UI performance | 🟡 Low | ProfileEditor and Coach chart calculations could be optimized for large datasets. |

---

## 📈 Metrics (Code Stats)

- **Kotlin Source Files:** 55
- **UI Components:** 8 reusable, 8 screen-specific
- **Database Entities:** 3
- **ViewModels:** 4
- **Health Connect Permissions:** 7

---

## 🎯 Definition of Done for MVP

For a releasable MVP, complete:

- [x] Health Connect sync working
- [x] Weekly planning with workout types
- [x] Dashboard with load progress
- [x] Statistics with charts
- [x] Backup/restore
- [x] User profile editing
- [x] CTL/ATL progress chart
- [x] Coach assessment engine
- [x] Workout details with HR analysis
- [x] Proper database migrations (non-destructive production-ready)

---

*This document should be updated as features are completed or new requirements emerge.*

