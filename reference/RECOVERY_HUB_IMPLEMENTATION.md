# Recovery Hub Implementation Documentation

**Feature:** Recovery Hub  
**Implementation Date:** December 2024  
**Database Version:** 11 (upgraded from 10)  
**Architecture:** MVVM with Clean Architecture

---

## Overview

The Recovery Hub is a comprehensive wellness tracking and recovery management feature that enables athletes to monitor subjective wellness metrics, track recovery tasks, and receive personalized nutrition guidance based on training load. The feature integrates seamlessly with the existing TriPath training data to provide context-aware recovery protocols.

### Key Features

1. **Daily Wellness Logging**
   - Subjective soreness tracking (1-10 scale)
   - Mood monitoring (1-10 scale)
   - Allergy severity tracking
   - Morning weight recording

2. **Nutrition Targets**
   - Dynamic protein, fat, and carbohydrate recommendations
   - Based on body weight and daily Training Stress Score (TSS)
   - Automatic recalculation when weight or training load changes

3. **Recovery Protocol Tasks**
   - Context-aware task generation
   - Daily tasks (always shown)
   - Triggered tasks based on workout type, duration, and TSS
   - Task completion tracking

4. **Coach Advice**
   - Intelligent recommendations based on wellness data
   - Allergy severity warnings
   - Biological cost awareness

---

## Architecture

The Recovery Hub follows Clean Architecture principles with clear separation of concerns:

```
UI Layer (RecoveryScreen.kt)
    ↓
ViewModel (RecoveryViewModel.kt)
    ↓
Domain Layer (RecoveryEngine.kt)
    ↓
Repository (RecoveryRepository.kt / RecoveryRepositoryImpl.kt)
    ↓
Data Layer (WellnessDao.kt, RecoveryEntities.kt)
    ↓
Room Database (AppDatabase.kt)
```

### Component Responsibilities

- **UI Layer**: Jetpack Compose screens with Material 3 components
- **ViewModel**: State management, UI logic, and user action handling
- **Domain Layer**: Business logic for nutrition calculation, task filtering, and advice generation
- **Repository**: Data access abstraction and CRUD operations
- **Data Layer**: Room database entities, DAOs, and type converters

---

## Phase 1: Data Layer

### 1.1 Enums

**File:** `app/src/main/java/com/tripath/data/model/RecoveryEnums.kt`

```kotlin
enum class AllergySeverity {
    NONE, MILD, MODERATE, SEVERE
}

enum class TaskTriggerType {
    DAILY,
    TRIGGER_STRENGTH,
    TRIGGER_LONG_DURATION,
    TRIGGER_HIGH_TSS
}
```

### 1.2 Entities

**File:** `app/src/main/java/com/tripath/data/local/database/entities/RecoveryEntities.kt`

#### DailyWellnessLog
- Primary Key: `date: LocalDate`
- Fields:
  - `sleepMinutes: Int?` - Optional sleep duration
  - `hrvRmssd: Double?` - Optional HRV RMSSD metric
  - `morningWeight: Double?` - Morning weight in kg
  - `sorenessIndex: Int?` - Subjective soreness (1-10)
  - `moodIndex: Int?` - Subjective mood (1-10)
  - `allergySeverity: AllergySeverity?` - Allergy severity level
  - `completedTaskIds: List<Long>?` - List of completed task IDs

#### WellnessTaskDefinition
- Primary Key: `id: Long` (auto-generated)
- Fields:
  - `title: String` - Task title
  - `description: String?` - Optional task description
  - `type: TaskTriggerType` - Trigger type for task inclusion
  - `triggerThreshold: Int?` - Optional threshold for trigger-based tasks

### 1.3 Type Converters

**File:** `app/src/main/java/com/tripath/data/local/database/converters/Converters.kt`

Added type converters for:
- `AllergySeverity` ↔ `String` (stored as enum name)
- `TaskTriggerType` ↔ `String` (stored as enum name)
- `List<Long>?` ↔ `String?` (JSON serialization using kotlinx.serialization)

**Key Implementation Detail:**
- `completedTaskIds` is nullable to match database schema
- `toLongList()` converter returns `null` for empty/blank JSON strings or parsing errors
- Uses `Json.encodeToString()` and `Json.decodeFromString()` for serialization

### 1.4 DAO

**File:** `app/src/main/java/com/tripath/data/local/database/dao/WellnessDao.kt`

Provides reactive Flow-based queries and suspend functions for:
- DailyWellnessLog CRUD operations
- WellnessTaskDefinition CRUD operations
- Date range queries for logs
- Flow and one-shot variants for different use cases

### 1.5 Database Migration

**Files:**
- `app/src/main/java/com/tripath/data/local/database/AppDatabase.kt`
- `app/src/main/java/com/tripath/data/local/database/migrations/Migration10To11.kt`

**Migration Strategy:**
- Database version incremented from 10 to 11
- Migration creates two new tables: `daily_wellness_logs` and `wellness_task_definitions`
- Development fallback uses `fallbackToDestructiveMigration()` for schema mismatches

**SQL Schema:**
```sql
CREATE TABLE daily_wellness_logs (
    date INTEGER NOT NULL PRIMARY KEY,
    sleepMinutes INTEGER,
    hrvRmssd REAL,
    morningWeight REAL,
    sorenessIndex INTEGER,
    moodIndex INTEGER,
    allergySeverity TEXT,
    completedTaskIds TEXT
);

CREATE TABLE wellness_task_definitions (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    type TEXT NOT NULL,
    triggerThreshold INTEGER
);
```

---

## Phase 2: Logic & Repository

### 2.1 Recovery Engine (Domain Service)

**File:** `app/src/main/java/com/tripath/domain/RecoveryEngine.kt`

Singleton object containing business logic:

#### calculateNutrition(weightKg: Double, dailyTss: Int): NutritionTargets

**Nutrition Rules:**
- **Protein**: 2g/kg (constant regardless of TSS)
- **Fat**: 1g/kg (constant regardless of TSS)
- **Carbs**: Variable based on daily TSS:
  - TSS < 50: 3g/kg
  - TSS 50-100: 5g/kg
  - TSS > 100: 7g/kg

#### getRelevantTasks(logs: List<WorkoutLog>, allTasks: List<WellnessTaskDefinition>): List<WellnessTaskDefinition>

**Task Filtering Logic:**
1. Always includes `DAILY` type tasks
2. Includes `TRIGGER_STRENGTH` tasks if any workout is `WorkoutType.STRENGTH`
3. Includes `TRIGGER_LONG_DURATION` tasks if total duration > task threshold
4. Includes `TRIGGER_HIGH_TSS` tasks if total TSS > task threshold
5. Returns distinct tasks (removes duplicates)

#### getCoachAdvice(log: DailyWellnessLog): String

**Advice Logic:**
- If `allergySeverity >= MODERATE`, provides warning about biological cost and fatigue
- Returns empty string if no advice needed
- Messages are joined with double newline separators

### 2.2 Recovery Repository

**Interface:** `app/src/main/java/com/tripath/data/local/repository/RecoveryRepository.kt`  
**Implementation:** `app/src/main/java/com/tripath/data/local/repository/RecoveryRepositoryImpl.kt`

#### Key Methods

- **Wellness Log Operations:**
  - `getWellnessLog(date: LocalDate): DailyWellnessLog?`
  - `getWellnessLogFlow(date: LocalDate): Flow<DailyWellnessLog?>`
  - `getAllWellnessLogs(): Flow<List<DailyWellnessLog>>`
  - `getWellnessLogsByDateRange(startDate, endDate): Flow<List<DailyWellnessLog>>`
  - `insertWellnessLog(log: DailyWellnessLog)`
  - `updateWellnessLog(log: DailyWellnessLog)`
  - `deleteWellnessLog(log: DailyWellnessLog)`

- **Task Operations:**
  - `getAllTasks(): Flow<List<WellnessTaskDefinition>>`
  - `getAllTasksOnce(): List<WellnessTaskDefinition>`
  - `getTaskById(id: Long): WellnessTaskDefinition?`
  - `insertTask(task: WellnessTaskDefinition): Long`
  - `insertTasks(tasks: List<WellnessTaskDefinition>)`
  - `updateTask(task: WellnessTaskDefinition)`
  - `deleteTask(task: WellnessTaskDefinition)`

- **Initialization:**
  - `initializeDefaults()`: Inserts default tasks (Creatine, Vitamins, Stretching) if table is empty

#### Implementation Details

- All operations run on `Dispatchers.IO` for database access
- Uses `WellnessDao` injected via constructor
- `insertWellnessLog()` uses `REPLACE` conflict strategy (upsert behavior)
- Default tasks are inserted with appropriate trigger types

### 2.3 Dependency Injection

**File:** `app/src/main/java/com/tripath/di/RepositoryModule.kt`

```kotlin
@Binds
@Singleton
abstract fun bindRecoveryRepository(
    recoveryRepositoryImpl: RecoveryRepositoryImpl
): RecoveryRepository
```

**File:** `app/src/main/java/com/tripath/di/DatabaseModule.kt`

```kotlin
@Provides
@Singleton
fun provideWellnessDao(database: AppDatabase): WellnessDao {
    return database.wellnessDao()
}
```

---

## Phase 3: ViewModel

**File:** `app/src/main/java/com/tripath/ui/recovery/RecoveryViewModel.kt`

### 3.1 UI State

```kotlin
data class RecoveryUiState(
    val currentLog: DailyWellnessLog? = null,
    val nutritionTargets: NutritionTargets? = null,
    val activeTasks: List<TaskItem> = emptyList(),
    val coachAdvice: String = "",
    val isLoading: Boolean = true
)

data class TaskItem(
    val task: WellnessTaskDefinition,
    val isCompleted: Boolean
)
```

### 3.2 Initialization

**On ViewModel Creation:**
1. Calls `initializeDefaults()` to ensure default tasks exist
2. Calls `loadRecoveryData()` to load today's data

**loadRecoveryData() Workflow:**
1. Loads today's wellness log (creates default if missing)
2. Loads today's workouts from TrainingRepository
3. Calculates daily TSS from workouts
4. Loads all task definitions
5. Filters relevant tasks using `RecoveryEngine.getRelevantTasks()`
6. Builds `TaskItem` list with completion status
7. Calculates nutrition targets if weight is available
8. Generates coach advice
9. Updates UI state

### 3.3 User Actions

#### updateSubjectiveMetrics(soreness: Int?, mood: Int?, allergy: AllergySeverity?)

- Updates soreness, mood, and/or allergy severity
- Persists changes via repository
- Recalculates coach advice
- Updates UI state

#### toggleTask(taskId: Long, isChecked: Boolean)

- Adds or removes task ID from `completedTaskIds` list
- Persists changes via repository
- Updates active tasks list in state
- Updates UI state

#### updateWeight(weight: Double)

- Updates morning weight
- Persists changes via repository
- Recalculates nutrition targets based on new weight and daily TSS
- Updates UI state

### 3.4 State Management

- Uses `MutableStateFlow` for reactive state updates
- Exposes `StateFlow<RecoveryUiState>` via `asStateFlow()`
- All operations run in `viewModelScope.launch`
- State updates are atomic (single copy() call per update)

---

## Phase 4: UI & Navigation

### 4.1 Recovery Screen

**File:** `app/src/main/java/com/tripath/ui/recovery/RecoveryScreen.kt`

#### Layout Structure

Scrollable Column with three main sections:

1. **Status Card**
   - Soreness slider (1-10)
   - Mood slider (1-10)
   - Allergy severity FilterChips (None, MILD, MODERATE, SEVERE)
   - Coach advice card (displayed when advice exists)

2. **Fueling Section**
   - Morning weight slider (40-150kg)
   - Nutrition targets display (Protein/Carbs/Fat in grams)
   - Shows "Set morning weight..." message if weight not set

3. **Protocol Section**
   - List of active recovery tasks
   - Checkboxes for task completion
   - Visual distinction for triggered tasks (CheckCircle icon + semi-bold text)
   - Task title and description display

#### UI Components Used

- **Material 3 Components:**
  - `Card` for section containers
  - `Slider` for numeric inputs
  - `FilterChip` for allergy selection
  - `Checkbox` for task completion
  - `Text` with Material 3 typography
  - `Icon` for triggered task indicators

- **Custom Components:**
  - `SectionHeader` for screen header

#### Design Patterns

- Follows TriPath spacing system (`Spacing.lg`, `Spacing.md`, `Spacing.sm`)
- Uses Material 3 color scheme
- Consistent typography hierarchy
- Loading state handling
- Null-safe data display

#### State Binding

- Uses `hiltViewModel()` for dependency injection
- Collects state via `collectAsStateWithLifecycle()`
- All user actions call ViewModel functions
- Reactive updates when state changes

### 4.2 Recovery History Screen

**File:** `app/src/main/java/com/tripath/ui/recovery/RecoveryHistoryScreen.kt`

#### Overview

The Recovery History screen provides comprehensive visualization of historical wellness data, enabling athletes to identify patterns and correlations between training load, recovery metrics, and performance readiness.

#### Key Features

1. **Time Range Selection**
   - Week, Month, 3 Months, 6 Months, Year views
   - Segmented button selector for easy switching

2. **Readiness vs Load Chart**
   - Dual-axis visualization showing:
     - Readiness score (calculated from soreness, mood, allergies)
     - Daily Training Stress Score (TSS)
   - Helps identify overreaching patterns
   - Interactive info dialog explaining readiness calculation

3. **Biological Cost Chart**
   - Visualizes allergy severity impact on recovery
   - Color-coded severity levels (None, Mild, Moderate, Severe)
   - Info dialog explaining biological cost concept

4. **Wellness Trends**
   - Soreness trend line
   - Mood trend line
   - Weight trend (if available)
   - Visual correlation with training load

5. **Task Completion Analysis**
   - Shows task completion rates over time
   - Identifies which recovery protocols are consistently followed

#### UI Components

- Custom Canvas-based charts for data visualization
- Material 3 SegmentedButtonRow for time range selection
- Info dialogs for educational content
- Scrollable layout with consistent spacing

#### State Management

- Uses `RecoveryViewModel.historyState` Flow
- Time range selection updates via `setTimeRange()`
- Reactive updates when wellness data changes

### 4.3 Navigation Integration

**Files:**
- `app/src/main/java/com/tripath/ui/navigation/TriPathNavigation.kt`
- `app/src/main/java/com/tripath/ui/MainScreen.kt`

#### Navigation Routes

```kotlin
sealed class Screen {
    object Recovery : Screen("recovery")
    object RecoveryHistory : Screen("recovery_history")
    // ...
}
```

#### Route Registration

```kotlin
composable(Screen.Recovery.route) {
    RecoveryScreen(navController = navController)
}
composable(Screen.RecoveryHistory.route) {
    RecoveryHistoryScreen(navController = navController)
}
```

#### Navigation Flow

- Recovery screen includes navigation to Recovery History
- Recovery History accessible via back button or direct navigation
- Uses standard Android back navigation pattern

#### Bottom Navigation Bar

Added Recovery item to `MainScreen`:
- **Icon**: `Icons.Default.Spa`
- **Label**: "Recovery"
- **Position**: Between "Coach" and "Settings"

#### Navigation Item Order

1. Dashboard
2. Planner
3. Stats
4. Coach
5. **Recovery** (new)
6. Settings

---

## Key Technical Decisions

### 1. Nullable completedTaskIds

**Decision:** Made `completedTaskIds: List<Long>?` nullable  
**Reason:** Matches database schema (TEXT column allows NULL)  
**Impact:** Type converter handles null gracefully, UI uses `orEmpty()` for safe access

### 2. RecoveryEngine as Object

**Decision:** Implemented as Kotlin `object` (singleton)  
**Reason:** Stateless domain service with pure functions  
**Impact:** No dependency injection needed, direct method calls from ViewModel

### 3. Upsert Strategy for Logs

**Decision:** Use `REPLACE` conflict strategy for `insertWellnessLog()`  
**Reason:** Simplifies update logic (single insert/update method)  
**Impact:** No need for separate insert/update calls, simplifies ViewModel code

### 4. Reactive Flow + One-Shot Queries

**Decision:** Provide both Flow and suspend variants for queries  
**Reason:** Flows for reactive UI updates, one-shot for calculations  
**Impact:** ViewModel uses one-shot for initial load, could use Flows for real-time updates in future

### 5. Default Task Initialization

**Decision:** Initialize defaults in Repository, call from ViewModel init  
**Reason:** Ensures data exists before first use  
**Impact:** One-time setup per app install, no UI dependency on initialization

---

## Database Schema

### daily_wellness_logs Table

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| date | INTEGER | NO | Primary key (LocalDate.toEpochDay()) |
| sleepMinutes | INTEGER | YES | Sleep duration in minutes |
| hrvRmssd | REAL | YES | HRV RMSSD metric |
| morningWeight | REAL | YES | Morning weight in kg |
| sorenessIndex | INTEGER | YES | Subjective soreness (1-10) |
| moodIndex | INTEGER | YES | Subjective mood (1-10) |
| allergySeverity | TEXT | YES | Allergy severity enum name |
| completedTaskIds | TEXT | YES | JSON array of completed task IDs |

### wellness_task_definitions Table

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| id | INTEGER | NO | Primary key (auto-increment) |
| title | TEXT | NO | Task title |
| description | TEXT | YES | Optional task description |
| type | TEXT | NO | Task trigger type enum name |
| triggerThreshold | INTEGER | YES | Threshold for trigger-based tasks |

---

## Default Tasks

The system initializes three default tasks:

1. **Creatine**
   - Type: `DAILY`
   - Always shown

2. **Vitamins**
   - Type: `DAILY`
   - Always shown

3. **Stretching**
   - Type: `TRIGGER_STRENGTH`
   - Shown when strength workouts are performed

*(Note: Additional tasks can be added via the repository interface)*

---

## Future Enhancements

### Potential Additions

1. ~~**Historical Data Views**~~ ✅ **Implemented**: Recovery History screen with trends and correlations
   - ~~Wellness trends over time~~
   - ~~Correlation between metrics and performance~~
   - Weekly/monthly wellness summaries (could be enhanced)

2. **Additional Metrics**
   - Sleep quality scoring (SleepLog entity exists, integration pending)
   - HRV trend analysis (HRV data stored but not yet visualized)
   - Stress level tracking

3. **Advanced Task Triggers**
   - Custom threshold configuration
   - Multi-condition triggers (e.g., STRENGTH + HIGH_TSS)
   - Recurring task schedules

4. **Integration Enhancements**
   - Auto-import weight from Health Connect
   - Auto-import HRV from wearables
   - Sync completed tasks with calendar reminders

5. **Coach Advice Expansion**
   - Fatigue detection based on soreness trends
   - Overtraining warnings
   - Recovery day recommendations

---

## Files Created/Modified

### Created Files

1. `app/src/main/java/com/tripath/data/model/RecoveryEnums.kt`
2. `app/src/main/java/com/tripath/data/local/database/entities/RecoveryEntities.kt`
3. `app/src/main/java/com/tripath/data/local/database/dao/WellnessDao.kt`
4. `app/src/main/java/com/tripath/data/local/database/migrations/Migration10To11.kt`
5. `app/src/main/java/com/tripath/domain/RecoveryEngine.kt`
6. `app/src/main/java/com/tripath/data/local/repository/RecoveryRepository.kt`
7. `app/src/main/java/com/tripath/data/local/repository/RecoveryRepositoryImpl.kt`
8. `app/src/main/java/com/tripath/ui/recovery/RecoveryViewModel.kt`
9. `app/src/main/java/com/tripath/ui/recovery/RecoveryScreen.kt`
10. `app/src/main/java/com/tripath/ui/recovery/RecoveryHistoryScreen.kt`

### Modified Files

1. `app/src/main/java/com/tripath/data/local/database/AppDatabase.kt`
   - Added entities and DAO
   - Incremented version to 11

2. `app/src/main/java/com/tripath/data/local/database/converters/Converters.kt`
   - Added type converters for new enums and List<Long>

3. `app/src/main/java/com/tripath/di/DatabaseModule.kt`
   - Added WellnessDao provider
   - Added Migration10To11
   - Added fallbackToDestructiveMigration()

4. `app/src/main/java/com/tripath/di/RepositoryModule.kt`
   - Added RecoveryRepository binding

5. `app/src/main/java/com/tripath/ui/navigation/TriPathNavigation.kt`
   - Added Recovery route
   - Added RecoveryHistory route
   - Added RecoveryScreen and RecoveryHistoryScreen composables

6. `app/src/main/java/com/tripath/ui/MainScreen.kt`
   - Added Recovery navigation item

---

## Testing Considerations

### Unit Tests

- `RecoveryEngine` calculations (nutrition, task filtering, advice)
- Repository CRUD operations
- Type converter serialization/deserialization

### Integration Tests

- Database migration (10 → 11)
- Repository with DAO
- ViewModel state updates

### UI Tests

- Screen navigation
- User interactions (sliders, checkboxes, chips)
- State updates reflected in UI

---

## Known Issues & Limitations

1. ~~**No Historical View**: Currently only shows today's data~~ ✅ **Resolved**: Recovery History screen implemented
2. **No Task Management UI**: Tasks can only be managed via repository (defaults only)
3. **No Data Export**: Wellness logs not included in backup system (yet)
4. **Weight Precision**: Slider uses 0.5kg steps (could be configurable)
5. **Sleep Integration**: Sleep data from Health Connect stored but not yet visualized in Recovery Hub

---

## Conclusion

The Recovery Hub feature successfully integrates wellness tracking into TriPath's training ecosystem. It provides athletes with actionable insights based on their training load and subjective metrics, following the app's scientific approach to periodization and recovery management.

The implementation follows Clean Architecture principles, maintains consistency with existing codebase patterns, and provides a solid foundation for future enhancements.

---

**Document Version:** 1.0  
**Last Updated:** December 2024

