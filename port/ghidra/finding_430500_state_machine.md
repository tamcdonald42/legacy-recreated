# Finding: the engine is a state machine, and `0x430500` is a state handler

Decompiled `FUN_00430500` and its caller `WinMain_` (`0x412b80`) to answer one question:
can this engine be cut into chunks, or is it spaghetti?

Answer: **clean state machine. Natural seams exist.**

## The dispatch, from `WinMain_` (546 lines of C)

```c
if (g_shutdown == 0) {
    ...
    if (g_gameState == STATE_2) {
        FUN_004156a0();                       /* state 2  -> other handler */
    }
    else if (g_gameState - STATE_3 < 3) {     /* states 3, 4, 5 */
        FUN_00430500(iVar4, iVar8);           /* <<< the in-game handler */
    }
    FUN_004046e0();
    ...
}
```

`WinMain_` is init + per-frame dispatch on the game-state global; `FUN_00430500` handles
**states 3/4/5** and internally switches again on the same variable (cases 0, 1, 2, 3, 4),
writing `g_gameState = STATE_5` on three paths and `= STATE_4` at the tail.

Other states seen in `WinMain_`: `STATE_11` (`0xb`) and `STATE_14` (`0xe`) gate cursor
handling, `STATE_13` (`0xd`) gates `FUN_004046e0`, and `(int)g_gameState < 6` gates a
`FUN_004068c0` call — so 0–5 are one family and 11/13/14 another.

## Typing pass applied (`port/ghidra/TypeState.java`)

| was | now |
|---|---|
| `DAT_004ae978` (inferred pointer) | `g_gameState` typed `enum GameState` |
| `DAT_004ae960` | `g_shutdown` typed `int` |

`GameState` has neutral members `STATE_0 … STATE_5, STATE_11, STATE_13, STATE_14` — numbers
only, because the meanings are not established. **Do not invent names for states** until the
handlers are read; a wrong enum name is worse than no name.

### What it fixed

`WinMain_` now reads as a state machine:

```c
if (g_gameState == STATE_1) { ... }
if (g_gameState != STATE_13) { ... }
if ((int)g_gameState < 6) { ... }
if (g_gameState == STATE_2) { FUN_004156a0(); }
else if (g_gameState - STATE_3 < 3) { FUN_00430500(iVar4, iVar8); }
```

26 changed lines in `FUN_00430500`, 64 in `WinMain_`, all of them names rather than structure.

### What it did *not* fix

`FUN_00430500` is still exactly 1,089 lines, and its cases still render as
`case (int *)0x0:` … `case (int *)0x4:`. Reason: the switch operand is the **pointer-typed
local** `piVar9`, not the global —

```c
piVar9 = (int *)(g_gameState - STATE_5);
switch (piVar9) { case (int *)0x0: ... }
```

Typing the global cannot fix that. The remaining lever is retyping the *decompiler variable*
(GUI: right-click `piVar9` → Retype → `int`, then re-decompile), which is per-decompilation
and not currently scripted. Until then read it as `g_gameState - STATE_5 == 0` → state 5.

## Open question — do not trust the name `g_shutdown`

The evidence is contradictory:

- `if (g_shutdown == 0) { <game logic + dispatch> }` → logic runs when the flag is **0**
- `if ((DAT_004af014 == 0) || (g_shutdown != 0)) break;` → breaks when it is **non-zero**
- `} while (g_shutdown != 0);` → *continues* while it is **non-zero** (line 299)

Three sites say "non-zero = stop/quit", one loop says the opposite. Either that `do/while`
is an artifact of an optimised loop, or it is a restart wrapper rather than the main loop.
The name is provisional — verify against the running recomp before building on it.

## Next steps

1. Read one `case` of `FUN_00430500` at a time; do not hold 1,089 lines in your head.
2. `FUN_004156a0` (3.4 KB, `STATE_2`) is the natural second target — the other half of the
   dispatcher, and a check that the pattern generalises.
3. Resolve the `g_shutdown` inversion, then rename it properly.
4. Name the object table: `DAT_004af8cc/d0/c4/c8` + `DAT_004c90a0` walk as count/pointer pairs.

## Artifacts

`work/ghidra-out/decomp_430500.c`, `decomp_412b80.c`, plus `before/` snapshots taken before the
typing pass. Scripts: `port/ghidra/{Decompile,TypeState}.java`.
