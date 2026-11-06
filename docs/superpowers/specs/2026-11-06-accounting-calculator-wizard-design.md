# Accounting Calculator Wizard Design

## Intent
Replace the crowded one-screen accounting calculator UI with a guided mobile-first experience while retaining all eight calculation modes and their existing formulas.

## Approved approach
A single input question per rounded card/screen, progressing via Next/Back; final result on its own screen. Alternative considered: progressive cards on one scrolling page, rejected because the user asked for sequential screens and minimal scrolling.

## Flow
Selection grid (all eight calculators) → input 1 → input 2 → input 3 when applicable → result. Progress indicates the current input and count. Back preserves entries, changing calculator starts empty. Invalid/empty input shows a contextual error without advancing. Results expose Edit and New calculation.

## Constraints
Arabic/English localization, accessibility/readable text, responsive widths, safe paddings, no deleted calculators or formula changes, no new dependencies, native Jetpack Compose. Numeric keypad with decimal support, helpful descriptions, safe landscape scrolling.
