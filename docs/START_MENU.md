# Chat Start Menu and Challenge Clock (26.3 alpha.5)

- On joining a world, server sends clickable styled messages **not panel backgrounds**.
- Not started: [게임 시작] -> /beddefense start and [규칙 보기] -> /beddefense rules.
- Already started: [진행 상태] -> /beddefense status and [규칙 보기].
- /beddefense or /beddefense menu re-opens on demand.
- Persistent world state is stored in the Overworld SavedData (single world-level challenge).
- Time before explicit start is not counted for the challenge stopwatch; join/rejoin does not restart it. A single active challenge has no user-accessible reset in this version.
- HUD uses challenge-relative day from the Overworld clock and elapsed game ticks from the first successful start.
- Bed prototype registration is gated behind challenge start; this does not implement actual respawn rules.
- If world time is set with commands, day may jump but stopwatch is unaffected because it relies on elapsed game ticks.
- Warning displayed near vanilla sleep time after start is a UI-only demonstration; no mobs will spawn or attack.
- Game still plays vanilla before and after starting: the UI command does not activate the planned hardcore/death/raid mechanics yet.
