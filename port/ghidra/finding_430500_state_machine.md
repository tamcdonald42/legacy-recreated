# Finding: the engine is a two-level state machine

Decompiled `FUN_00430500` and its caller `WinMain_` (`0x412b80`) to answer one question:
can this engine be cut into chunks, or is it spaghetti?

Answer: **two-level state machine, clean seams.** `g_gameState` picks the mode; `g_subState`
is the phase within it.

## Level 1 — `WinMain_` (546 lines of C)

```c
if (g_shutdown == 0) {
    ...
    if (g_gameState == STATE_2)          { FUN_004156a0(); }
    else if (g_gameState - STATE_3 < 3)  { FUN_00430500(iVar4, iVar8); }   /* states 3,4,5 */
    FUN_004046e0();
}
```

Other states gating behaviour in `WinMain_`: `STATE_11`, `STATE_13`, `STATE_14`, plus
`(int)g_gameState < 6` before a `FUN_004068c0` call — so 0–5 are one family, 11/13/14 another.

## Level 2 — `FUN_00430500` (1,090 lines of C) dispatches on `g_subState`

```c
if (g_gameState == STATE_5) {
    switch ((int *)(g_subState - SUB_8)) {      /* phases 8, 9, 10, 12 */
      case 0: ...    /* = SUB_8  */
      case 1: ...    /* = SUB_9  */
      case 2: ...    /* = SUB_10 */
      case 4: ...    /* = SUB_12 */
    }
}
```

Transitions actually written inside the function: `SUB_0, SUB_2, SUB_3, SUB_4 (x2), SUB_5,
SUB_6, SUB_7, SUB_8 (x2), SUB_9, SUB_12` — a real phase machine, not a flat switch.

## Typing applied (`port/ghidra/TypeState.java`)

| was | now | type |
|---|---|---|
| `DAT_004ae978` | `g_gameState` | `enum GameState` (`STATE_0…5, 11, 13, 14`) |
| `DAT_004af898` | `g_subState` | `enum SubState` (`SUB_0…SUB_15`) |
| `DAT_004ae960` | `g_shutdown` | `int` (provisional — see below) |

Member names are numbers only. Do not invent semantics for a state until its handler is read.

## Result of the typing pass

- `WinMain_` reads as a dispatcher; `FUN_00430500`'s conditions and assignments now read as
  `g_subState == SUB_5`, `g_subState = SUB_9`.
- `FUN_00430500` line count is unchanged (1,090 vs 1,089) — the typing was about legibility,
  not shrinkage.
- **The `case (int *)0x0:` labels survive** and this was attacked directly, see next section.

## Negative result: retyping the switch variable does not fix the cases

Hypothesis was that the switch operand was the named local `piVar9` (`int *`), so retyping it
to `int` would yield `case 0:`. **Tested and it does not work.**

- `piVar9 → int`: 1,089 → 1,091 lines, still 8/8 pointer cases, and the retype renamed the
  variable throughout. Reverted to `int *`.
- Cause: there is no named switch variable. Ghidra emits the operand inline —
  `switch((int *)(g_subState - SUB_8))` — so the cast comes from the decompiler's own type
  inference for the jump-table value, not from any symbol you can retype.
- Only remaining lever is the GUI's *Retype switch variable* action (not scripted here), or
  simply reading `case 0` as `SUB_8`.

## Open question — `g_shutdown` is provisional

Contradictory evidence:

- `if (g_shutdown == 0) { <game logic + dispatch> }` → logic runs when the flag is **0**
- `if (DAT_004af014 == 0 || g_shutdown != 0) break;` → stops when it is **non-zero**
- `} while (g_shutdown != 0);` → *continues* while it is **non-zero** (line 299)

Three sites read "non-zero = quit", one loop reads the opposite. Either that loop is an
optimised-loop artifact or a restart wrapper. Verify against the running recomp before
building on the name.

## Next steps

1. Read one phase at a time — `SUB_8 → SUB_9 → SUB_10 → SUB_12` is the in-game cycle.
2. `FUN_004156a0` (3.4 KB, `STATE_2`) — the other half of level 1; confirms the pattern.
3. Name the object table: `DAT_004af8cc/d0/c4/c8` + `DAT_004c90a0` walk as count/pointer pairs.
4. Resolve the `g_shutdown` inversion.

## Artifacts

`work/ghidra-out/`: `decomp_430500.c` (post-typing), `decomp_00430500_retyped.c`,
`decomp_412b80.c`, `before/` snapshots. Scripts in `port/ghidra/`:
`Decompile.java`, `TypeState.java`, `RetypeSwitch.java` (+ matching `run_*.bat`).
