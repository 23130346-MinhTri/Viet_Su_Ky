# Game Design Document — GDD V3

## 1. High concept
VIỆT SỬ KÝ is a 2.5D browser game combining Vietnamese history learning with exploration, missions, resource management and decision scenarios. Learning is embedded in the gameplay loop.

## 2. Core loop
Explore → Learn context → Receive mission → Act → Apply knowledge → Receive explanation → Earn progress → Unlock content.

## 3. Game modes
### Campaign
Guided chapters and missions. Each mission has context, learning objectives, map/activity, gameplay objectives, knowledge check, feedback, sources and reward.
### Knowledge Challenge
Single-choice, matching, ordering, map identification, true/false with explanation, and source-based questions. Every question needs an explanation and provenance.
### Historical Journey
Explore timeline, events, people, places, cultural topics, artifacts and sources.
### Settlement Management
Simplified educational simulation using possible resources such as food, wood, materials and knowledge. Production rates and quantities are game-balancing values, not historical measurements.
### Strategic Scenario
A documented context with hypothetical player choices. Label as simulation/what-if; do not rewrite actual history.

## 4. MVP scope
Two chapters: Văn Lang and Âu Lạc.
Initial content targets, adjustable to research quality:
- 5–8 missions.
- 15–25 reviewed learning entries.
- 40–60 reviewed questions.
- One interactive campaign map.
- One basic resource loop.
- Basic progress and achievements.

## 5. Mission fields
Code, title, chapter, introduction, historical context references, learning objectives, gameplay objectives, completion conditions, feedback, reward configuration, sources and editorial notes.

MVP objective types: `DISCOVER_CONTENT`, `VISIT_LOCATION`, `ANSWER_QUESTION`, `COLLECT_RESOURCE`, `BUILD_OBJECT`, `COMPLETE_INTERACTION`.

## 6. Progression
XP represents participation, not mastery. Knowledge progress should use quiz accuracy and review evidence. Unlocks are server-validated.

## 7. 2.5D direction
Isometric/oblique camera, layered terrain and structures, readable UI, period-appropriate visual language, pointer and touch input, no unsupported anachronisms.

## 8. UX principles
Teach before testing; explain after testing; short purposeful missions; optional deeper reading; visible but non-punitive progress; clear labels for uncertainty and simulation.

## 9. Rewards
Reward reasoning, completion, exploration and review. Do not reward speed alone or sell educational advantages.

## 10. Success measures
Mission completion, quiz accuracy by topic, improvement after review, chapter completion, return usage, usability and player-reported clarity.
