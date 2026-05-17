# Planner and Start Page

This document describes what the app currently has and does for the Planner tab and the Dashboard start page.

## Planner tab

The Planner tab is the screen labeled `Planner` in the bottom navigation.

### What it shows

- A planner header with either `4-week overview` or `Month overview`.
- A matrix of weeks, with each row showing one full Monday to Sunday week.
- A small `Wxx` label for each row using the ISO week number.
- One cell per day with the date and activity icons.
- A weekly summary panel showing planned TSS, actual TSS, duration, and completion progress.
- An optional discipline distribution bar when there is enough data to calculate it.

### What activity data means here

- Planned activities come from the training plan.
- Completed activities come from imported workout logs.
- The screen can show either:
  - `Planned only`: only planned workouts are shown and only matching completed workouts count toward the weekly totals.
  - `Include imported`: imported workout logs are also shown, including activities that do not have a matching plan.

### How the Planner behaves

- By default, the Planner opens in a rolling 4-week view starting from the current week.
- The current week starts on Monday.
- Previous and next month navigation switches the Planner into month mode.
- `Go to current` returns to the rolling view anchored on the current week.
- If Smart Planning is disabled, planned workouts are hidden because the planner filters out training plans in that state.
- Days can show special-period highlighting such as injury or holiday periods.
- Today is visually highlighted.

### What you can do in the Planner

- Tap a day to open the day detail screen for that date.
- Tap a workout to open the workout detail screen.
- Expand a week row to reveal the `Copy to Next Week` action.
- Use the show toggle to switch between plan-only view and combined plan-plus-imported view.

### How completed and next planned activities appear

- Completed activities are represented by workout logs attached to each day.
- Planned activities are represented by training-plan entries attached to each day.
- In combined mode, the Planner can show both on the same day.
- The Planner is primarily a weekly or monthly calendar view, not a single "next activity" card. The next planned workout is understood from the next upcoming day that contains a planned activity.

## Start page

The app start page is the `Dashboard` screen. It is the navigation start destination.

### What it shows

- A greeting and the current date.
- Sync status and a sync action.
- A training status card with fitness, fatigue, form, and safe weekly load information.
- A `Today's Focus` section based on the currently selected day.
- A `This Week` section with weekly TSS progress.
- A horizontal week strip for the current week.
- A `Completed Activities` section for the selected day when workout logs exist.

### How the current week works

- The Dashboard calculates the current week as Monday through Sunday around today.
- The week strip always represents that current Monday to Sunday range.
- Each day in the strip shows whether it is today, selected, planned, or completed.
- The Dashboard does not currently display the ISO week number as text on this page; it shows the current week through the day strip and weekly totals.

### How the Dashboard behaves

- The selected day defaults to today.
- `Today's Focus` updates from the selected day.
- If the selected day has a planned workout, the focus card shows that plan.
- If the selected day has no planned workout, the page treats it as a rest day.
- If the selected day has completed logs, they are listed under `Completed Activities`.
- Tapping a day in the week strip changes the selected day and refreshes both the focus card and the completed-activities list.

### Data shown on the start page

- Weekly planned TSS is calculated from the training plans for the current week.
- Weekly actual TSS is calculated from workout logs for the current week.
- Weekly allowed TSS is calculated from performance metrics.
- The performance card uses CTL, ATL, and TSB style training-load values.

## Short summary

- `Planner` is the schedule view for planned workouts, completed imported workouts, and week-by-week load overview.
- `Dashboard` is the start page for today's focus, the current week strip, current-week totals, and completed activities for the selected day.