# UI/UX Guidelines

## Experience goals
Welcoming to learners and enthusiasts; readable; visually rich but not cluttered; desktop-first and responsive; smooth transition between reading and playing.

## Information architecture
Home, auth, dashboard, eras, era detail, timeline, history library, campaigns, game scene, quiz, progress, achievements, profile, admin.

## Player journey
Register → dashboard → chapter overview → historical introduction → campaign map → mission → activity → knowledge check → explanation/source → progress/next unlock.

## Game screen
- Top bar: chapter, resources, player menu.
- Main canvas: Phaser map.
- Side panel: mission objectives.
- Context panel: selected object/location.
- Modal layer: learning card, quiz, event, confirmation.
Use React for accessible panels and dialogs.

## Visual style
2.5D isometric/oblique; earth/wood/green-inspired palette with sufficient contrast; distinct period styling; consistent icons; Vietnamese diacritics; avoid text baked into images; avoid anachronisms.

## Accessibility
Keyboard support for core UI, visible focus, adequate contrast, text resizing, touch-friendly controls, no hover-only interaction, reduced motion where practical, text alternatives for important visual information.

## Required UI states
Loading, empty, error, unauthorized, network interruption, success and retry.

## Content presentation
Summary first, deeper detail progressively, visible sources, labels for legend/debate/interpretation/simulation, explanations after quiz submission.
