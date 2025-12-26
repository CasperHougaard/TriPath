# TriPath Design Philosophy & Guidelines

This document outlines the design philosophy and implementation guidelines for TriPath, a professional triathlon training and coaching platform built with Jetpack Compose.

## Design Philosophy

### Core Principles
1. **Sport-Centric Design**: Each sport (Swim, Bike, Run, Strength) has distinct, high-contrast colors for immediate recognition
2. **Card-Based Interface**: Content is organized into Material 3 cards with consistent elevation and corner radii
3. **Clean & Modern**: Embraces Material Design 3 with Compose best practices
4. **Dark-First Design**: Optimized for dark mode with high-contrast colors for outdoor/evening training visibility
5. **Data-Driven UI**: Training load, TSS, and metrics are prominently displayed
6. **Accessibility First**: All interactive elements follow Material Design accessibility guidelines

## Color System

### Sport Colors
The app uses high-contrast, visually distinct colors for each sport to enable quick recognition in calendars, charts, and cards:

```kotlin
// Sport-specific colors
Swim: Color(0xFF00B8FF)      // Electric Blue - High contrast
Run: Color(0xFFFF6B35)       // Safety Orange - High contrast
Strength: Color(0xFF9C27B0)  // Purple - Visually dominant for off-season
Bike: Color(0xFF1565C0)      // Triathlon Blue
```

### Material Theme Integration

The app uses Material 3 color scheme with triathlon-inspired palette:

**Dark Theme (Default):**
```kotlin
primary = Color(0xFF42A5F5)         // Light Blue
secondary = Color(0xFFFF6B35)       // Safety Orange
tertiary = Color(0xFF00B8FF)        // Electric Blue
background = Color(0xFF121212)      // Dark background
surface = Color(0xFF1E1E1E)         // Card surface
```

**Light Theme:**
```kotlin
primary = Color(0xFF1565C0)         // Tri Blue
secondary = Color(0xFFFF6B35)       // Safety Orange
tertiary = Color(0xFF00B8FF)        // Electric Blue
background = Color(0xFFFFFBFE)      // Light background
surface = Color(0xFFFFFBFE)         // Card surface
```

### Color Usage Guidelines

| Color Role | Usage | Examples |
|------------|-------|----------|
| `MaterialTheme.colorScheme.primary` | Primary actions, highlights | Primary buttons, progress indicators |
| `MaterialTheme.colorScheme.secondary` | Secondary actions, accents | Secondary buttons, badges |
| `MaterialTheme.colorScheme.tertiary` | Tertiary actions, alerts | Over-training indicators, warnings |
| `MaterialTheme.colorScheme.surface` | Card backgrounds | All Card components |
| `MaterialTheme.colorScheme.background` | Screen backgrounds | Scaffold backgrounds |
| `MaterialTheme.colorScheme.onSurface` | Text on surfaces | Primary text content |
| `MaterialTheme.colorScheme.onBackground` | Text on background | Screen-level text |
| Sport colors (via `WorkoutType.toColor()`) | Workout type indicators | Icons, badges, chart segments |

### Transparency Guidelines
- **70% opacity**: Secondary text (`onSurface.copy(alpha = 0.7f)`)
- **60% opacity**: Tertiary text, subtle information (`onSurface.copy(alpha = 0.6f)`)
- **50% opacity**: Disabled states
- **30% opacity**: Very subtle backgrounds

## Typography Scale

TriPath uses the Material 3 typography system with semantic naming:

### Text Style Hierarchy

| Style | Size | Weight | Usage |
|-------|------|--------|-------|
| `displayLarge` | 57sp | Normal | Hero sections |
| `displayMedium` | 45sp | Normal | Feature headlines |
| `displaySmall` | 36sp | Normal | Section headlines |
| `headlineLarge` | 32sp | Normal | Page titles |
| `headlineMedium` | 28sp | Normal | Section headers |
| `headlineSmall` | 24sp | Normal | Card headers |
| `titleLarge` | 22sp | Bold | Primary card titles |
| `titleMedium` | 16sp | Medium | Secondary titles |
| `titleSmall` | 14sp | Medium | Tertiary titles |
| `bodyLarge` | 16sp | Normal | Primary body text |
| `bodyMedium` | 14sp | Normal | Secondary body text |
| `bodySmall` | 12sp | Normal | Tertiary body text |
| `labelLarge` | 14sp | Medium | Large labels |
| `labelMedium` | 12sp | Medium | Standard labels |
| `labelSmall` | 11sp | Medium | Small labels, badges |

### Typography Best Practices
✅ Always use `MaterialTheme.typography.[style]` instead of hardcoded text styles  
✅ Use `titleLarge` for card titles that need emphasis  
✅ Use `bodyLarge` for main content text  
✅ Use `labelMedium` for field labels and metadata  
✅ Use semantic naming (title/body/label) over arbitrary sizes  

## Spacing System

### Standard Spacing Scale
Use Material Design spacing based on 4dp increments:

```kotlin
val SpacingSystem = object {
    val xs = 4.dp      // Extra small gaps, tight spacing
    val sm = 8.dp      // Small gaps, compact layouts
    val md = 12.dp     // Medium gaps, card content
    val lg = 16.dp     // Large gaps, primary content padding
    val xl = 20.dp     // Extra large gaps
    val xxl = 24.dp    // Screen-level padding
    val xxxl = 32.dp   // Major section separation
}
```

### Spacing Usage

| Spacing | Usage |
|---------|-------|
| 4.dp | Tight vertical spacing within card content |
| 8.dp | Standard gap between related elements, list item spacing |
| 12.dp | Card internal padding, moderate element spacing |
| 16.dp | Standard card padding, screen-level element spacing |
| 20.dp | Large card padding |
| 24.dp | Screen-level padding (main content areas) |
| 32.dp | Major section separation |

### Padding Guidelines
- **Screen padding**: 16dp horizontal, 16dp vertical
- **Card padding**: 16dp all sides
- **List item padding**: 16dp horizontal, 12dp vertical
- **Bottom navigation clearance**: Use Scaffold with bottom bar

## Card Design System

### Card Types

#### Standard Card (Most Common)
```kotlin
Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
) {
    // Content with 16.dp padding
}
```

**Properties:**
- Default elevation (Material 3 automatically handles elevation)
- Surface color from theme
- Standard corner radius (Material 3 default: 12.dp)

#### Clickable Card (Interactive)
```kotlin
Card(
    onClick = { /* action */ },
    modifier = modifier,
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
) {
    // Content
}
```

**Properties:**
- Use `onClick` parameter for Material ripple effect
- Includes built-in pressed state
- Accessibility handled automatically

#### Elevated Card (Emphasis)
```kotlin
Card(
    modifier = modifier,
    elevation = CardDefaults.cardElevation(
        defaultElevation = 4.dp
    ),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
) {
    // Important content
}
```

**Use for:**
- Featured content
- Active/selected states
- Modals and dialogs

#### Outlined Card (Subtle)
```kotlin
OutlinedCard(
    modifier = modifier,
    colors = CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
) {
    // Subtle content
}
```

**Use for:**
- Secondary information
- Empty states
- Placeholder content

#### Colored Card (Branded)
```kotlin
Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
) {
    // Primary action content
}
```

**Use for:**
- Primary action cards
- Hero cards
- Workout type indicators (with sport colors)

### Card Sizing
- **Min height for action cards**: 72.dp
- **Stat cards**: 120.dp minimum
- **List items**: wrap_content with min 64.dp
- **Hero cards**: 140-160.dp

## Component Patterns

### Workout Cards

Workout cards are the primary UI element for displaying training sessions:

```kotlin
Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = workoutIcon,
            contentDescription = null,
            tint = workoutType.toColor(),
            modifier = Modifier.size(32.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = workoutName,
                style = MaterialTheme.typography.titleMedium,
                color = workoutType.toColor()
            )
            Text(
                text = workoutDetails,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
```

**Key Features:**
- 32.dp sport-colored icon
- Title in sport color
- Details in secondary text color
- 16.dp card padding
- 12.dp gap between icon and content
- 4.dp gap between text elements

### Load Indicators

Display training load progress with visual feedback:

```kotlin
Card {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Weekly Load Progress",
            style = MaterialTheme.typography.titleLarge
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Planned: $planned TSS")
            Text("Actual: $actual TSS")
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = if (progress > 1f) {
                MaterialTheme.colorScheme.tertiary // Over-training warning
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    }
}
```

**Key Features:**
- Color changes to tertiary (warning) when exceeding planned load
- Planned vs. actual values clearly labeled
- 12.dp spacing between elements

### Week Day Items

Calendar-style workout planning interface:

```kotlin
Card(
    onClick = { onAddClick() },
    modifier = modifier
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Date header
        Text(
            text = dayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = date,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Workout list or empty state
        if (workouts.isEmpty()) {
            Text(
                text = "Rest day - tap to add workout",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        } else {
            workouts.forEach { workout ->
                WorkoutCard(workout = workout)
            }
        }
    }
}
```

### Stat Cards

Display key metrics and statistics:

```kotlin
Card(
    modifier = modifier.width(160.dp)
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
```

**Properties:**
- Fixed width: 160.dp
- Icon size: 24.dp
- Label: labelMedium
- Value: headlineSmall, bold

### Bottom Sheets

Use Material 3 ModalBottomSheet for forms and actions:

```kotlin
ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState()
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Add Workout",
            style = MaterialTheme.typography.headlineSmall
        )
        
        // Form fields
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
        }
    }
}
```

## Icon System

### Icon Sizes
- **16.dp**: Small inline icons, badges
- **24.dp**: Standard UI icons
- **32.dp**: Workout card icons
- **40.dp**: Large feature icons
- **48.dp**: Hero/primary action icons

### Sport Icons
Use Material Icons for consistency:
- **Run**: `Icons.AutoMirrored.Filled.DirectionsRun`
- **Bike**: `Icons.Default.PedalBike`
- **Swim**: `Icons.Default.Pool`
- **Strength**: `Icons.Default.FitnessCenter`
- **Other**: `Icons.AutoMirrored.Filled.DirectionsWalk`

### Icon Tinting
```kotlin
Icon(
    imageVector = icon,
    contentDescription = description,
    tint = workoutType.toColor() // Sport colors
)

Icon(
    imageVector = icon,
    contentDescription = description,
    tint = MaterialTheme.colorScheme.primary // Primary actions
)

Icon(
    imageVector = icon,
    contentDescription = description,
    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) // Secondary
)
```

## Layout Patterns

### Screen Structure
```kotlin
Scaffold(
    topBar = { /* Optional TopAppBar */ },
    bottomBar = { /* NavigationBar for main screens */ },
    floatingActionButton = { /* Optional FAB */ }
) { paddingValues ->
    // Content with paddingValues
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen content
    }
}
```

### List Layouts
```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    items(list) { item ->
        ItemCard(item = item)
    }
}
```

### Grid Layouts
```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    items(list) { item ->
        StatCard(item = item)
    }
}
```

## Navigation

### Bottom Navigation Bar
```kotlin
NavigationBar {
    items.forEach { screen ->
        NavigationBarItem(
            icon = { Icon(screen.icon, contentDescription = null) },
            label = { Text(screen.label) },
            selected = currentRoute == screen.route,
            onClick = { navController.navigate(screen.route) }
        )
    }
}
```

**Main Navigation Items:**
- Dashboard (home icon)
- Weekly Planner (calendar icon)
- Settings (settings icon)

### Navigation Guidelines
- Use NavigationBar for main app sections (3-5 items)
- Use TopAppBar with back button for detail screens
- Use FAB for primary creation actions
- Use bottom sheets for quick data entry

## Form Components

### Text Input Fields
```kotlin
OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text("Label") },
    modifier = Modifier.fillMaxWidth(),
    colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        focusedLabelColor = MaterialTheme.colorScheme.primary
    )
)
```

### Dropdowns/Selectors
```kotlin
ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded }
) {
    OutlinedTextField(
        value = selectedValue,
        onValueChange = {},
        readOnly = true,
        label = { Text("Select") },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        modifier = Modifier
            .fillMaxWidth()
            .menuAnchor()
    )
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option) },
                onClick = {
                    onSelect(option)
                    expanded = false
                }
            )
        }
    }
}
```

### Buttons

**Primary Action:**
```kotlin
Button(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth()
) {
    Text("Primary Action")
}
```

**Secondary Action:**
```kotlin
OutlinedButton(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth()
) {
    Text("Secondary Action")
}
```

**Text Button (Tertiary):**
```kotlin
TextButton(onClick = onClick) {
    Text("Tertiary Action")
}
```

### Chips/Badges
```kotlin
AssistChip(
    onClick = onClick,
    label = { Text("Filter") },
    leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) }
)

FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text("Swim") },
    leadingIcon = { Icon(Icons.Default.Pool, contentDescription = null) }
)
```

## Charts and Data Visualization

### Chart Colors
Use sport colors for consistency:
- Swim data: Electric Blue (#00B8FF)
- Run data: Safety Orange (#FF6B35)
- Bike data: Triathlon Blue (#1565C0)
- Strength data: Purple (#9C27B0)

### Chart Container
```kotlin
Card(
    modifier = Modifier
        .fillMaxWidth()
        .height(280.dp)
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = "Chart Title",
            style = MaterialTheme.typography.titleMedium
        )
        
        // Chart component (MPAndroidChart or custom Canvas)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Chart rendering
        }
    }
}
```

## Empty States

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
) {
    Icon(
        imageVector = emptyIcon,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "No workouts planned",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    Text(
        text = "Tap + to add your first workout",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        textAlign = TextAlign.Center
    )
}
```

## Loading States

### Circular Progress
```kotlin
Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
) {
    CircularProgressIndicator()
}
```

### Linear Progress
```kotlin
LinearProgressIndicator(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.primary
)
```

## Animation Guidelines

### Standard Transitions
```kotlin
AnimatedVisibility(
    visible = isVisible,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically()
) {
    // Content
}
```

### Crossfade
```kotlin
Crossfade(
    targetState = currentScreen,
    animationSpec = tween(300)
) { screen ->
    // Screen content
}
```

## Accessibility

### Content Descriptions
✅ Always provide contentDescription for icons  
✅ Use null only for decorative icons  
✅ Describe icon purpose, not appearance  

### Touch Targets
✅ Minimum touch target: 48.dp × 48.dp  
✅ Use Modifier.minimumInteractiveComponentSize() for small elements  

### Color Contrast
✅ Text on surface: minimum 4.5:1 contrast ratio  
✅ Sport colors are pre-tested for high contrast  
✅ Use alpha transparencies carefully to maintain readability  

## Best Practices

### Do's
✅ Use MaterialTheme for all colors, typography, and shapes  
✅ Use sport colors (WorkoutType.toColor()) for workout-related UI  
✅ Apply consistent spacing using the spacing scale  
✅ Use semantic typography styles (titleLarge, bodyMedium, etc.)  
✅ Provide proper content descriptions for accessibility  
✅ Use Scaffold for screen structure  
✅ Use remember and state hoisting for UI state  
✅ Follow Material 3 component guidelines  

### Don'ts
❌ Don't hardcode colors - always use theme or sport colors  
❌ Don't use arbitrary spacing - stick to the spacing scale  
❌ Don't create custom text styles - use typography system  
❌ Don't skip accessibility considerations  
❌ Don't nest scrollable layouts (e.g., LazyColumn in ScrollView)  
❌ Don't use overly complex nesting - keep composables simple  

## Code Style

### Modifier Order
Follow consistent ordering:
```kotlin
Modifier
    .fillMaxWidth()          // Size modifiers first
    .padding(16.dp)          // Spacing modifiers
    .background(color)       // Background/visual modifiers
    .clickable { }           // Interaction modifiers last
```

### Composable Structure
```kotlin
@Composable
fun ComponentName(
    // Required parameters
    data: DataType,
    // Optional parameters with defaults
    modifier: Modifier = Modifier,
    // Callbacks last
    onClick: () -> Unit = {}
) {
    // Implementation
}
```

### Preview Conventions
```kotlin
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ComponentPreview() {
    TriPathTheme {
        ComponentName(data = sampleData)
    }
}
```

## Implementation Checklist

When creating a new screen or component:

- [ ] Uses Scaffold for screen structure
- [ ] Applies proper paddingValues from Scaffold
- [ ] Uses MaterialTheme.colorScheme for colors
- [ ] Uses MaterialTheme.typography for text
- [ ] Applies consistent spacing (4/8/12/16/24dp)
- [ ] Cards use surface color and proper elevation
- [ ] Sport colors applied via WorkoutType.toColor()
- [ ] Icons have proper contentDescription
- [ ] Touch targets meet 48dp minimum
- [ ] Includes both light and dark theme previews
- [ ] State is properly hoisted to ViewModel
- [ ] Loading and empty states are handled
- [ ] Follows Compose best practices

---

*This document should be referenced when creating new UI components or screens to maintain design consistency across TriPath.*

