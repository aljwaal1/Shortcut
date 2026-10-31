# Shortcut Navigation and Cards Design — 2026-10-31

## Intent
Make every Shortcut feature easy to find on a phone, with consistent colorful oval cards and adaptive Arabic/English text. No function, preset, template, saved shortcut, or destination may be removed.

## Navigation
Four visible primary areas: Home (favorites, recent, shortcuts, snapshot), Tools (all tool catalog items, search, categories), Automation (create and manage saved routines and templates), More (history, permissions, settings). Retain existing deep links and activities, migrate only entry points. Distinguish actual catalog destinations from repeated quick links.

## Cards
Shared rounded (24–30dp) surfaces; color by existing VisualIdentity for sections; contrast-aware foreground; text may wrap with dynamic font scaling, no clipping; clear tap targets; support RTL and dark mode. Preserve card action handlers.

## Approaches
A. Leave separate activities, rename and recolor cards: least risk but navigation stays confusing.
B. Four clear top-level groups, shared visual components, keep existing activity destinations (chosen): improves discoverability while reducing regression risk.
C. Full navigation architecture rewrite: unnecessary risk to saved functionality.

## Constraints and acceptance
Only branch feature/android-v1-phase1; do not touch stable/v0.7.4 or main.
All ToolCatalog ids, templates, saved items, workflows, and external intents remain reachable.
No feature removed or renamed at persistence/API layer.
Verify compact widths, long Arabic/English text, large font scales, dark mode, navigation, and back behavior.
