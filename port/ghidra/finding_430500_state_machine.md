# Finding: the engine is a state machine, and `0x430500` is a state handler

Decompiled `FUN_00430500` and its caller `WinMain_` (`0x412b80`) to answer one question:
can this engine be cut into chunks, or is it spaghetti?

Answer: **clean state machine. Natural seams exist.** The new globals worth naming immediately
are `DAT_004ae978` (game state) and `DAT_004ae960` (shutdown flag).

## The dispatch, from `WinMain_` (546 lines of C)

```c
if (DAT_004ae960 == 0) {                      /* not shutting down */
    ...
    if (DAT_004ae978 == 2) {
        FUN_004156a0();                       /* state 2  -> other handler */
    }
    else if (DAT_004ae978 - 3U < 3) {         /* states 3, 4, 5 */
        FUN_00430500(iVar4, iVar8);           /* <<< the function we decompiled */
    }
    FUN_004046e0();
    ...
}
```

So `WinMain_` is init + per-frame dispatch on the game-state global; `FUN_00430500` handles
**states 3/4/5**, and internally switches again on the same variable:

```c
switch ((int)DAT_004ae978) {
  case 0: ...           /* line 62/966  */
  case 1: ...           /* line 81/1000 */
  case 2: ...           /* line 136/1050 */
  case 3: ...           /* label switchD_00430544_caseD_3, line 1068 */
  case 4: ... DAT_004ae978 = 4;
}
```

State writes found inside the function: `DAT_004ae978 = 5` at lines 453, 709, 916 and
`= 4` at the tail. Reads of `DAT_004ae960`, `DAT_004af164`, `DAT_004ae838`, `DAT_004ae9e8`
gate behaviour, and `DAT_004af8cc` / `DAT_004af8d0` / `DAT_004af8c4` / `DAT_004af8c8` look
like a count + pointer array walked by index.

## Readability, measured

| | `FUN_00430500` | `WinMain_` |
|---|---|---|
| C lines | 1,089 (39 KB) | 546 (18.5 KB) |
| globals touched | 76 | — |
| distinct callees | 42 | — |
| `if` / `else` | 156 / 21 | 64 / 7 |
| `goto` / labels | 21 / 6 | 9 / 2 |
| `unaff_` / `in_stack_` noise | **0 / 0** | 0 |

Verdict on readability: **middling but honest.** No `unaff_` garbage, no stack-frame
mangling — Ghidra did well. The noise that hurts is:

- **Missing types everywhere.** The state variable decompiles as `case (int *)0x0:` and
  `(int *)(DAT_004ae978 + -5)`, because `DAT_004ae978` is inferred as a pointer. Params are
  `undefined4`, so the signature is `int * __fastcall FUN_00430500(undefined4, undefined4)`.
- **The jump table is emitted twice** — cases appear around line 62–203 *and* again at
  966–1080, because Ghidra split flow at the `switchD_` labels.
- **41 `do { … } while` loops are inlined `memcpy`/`memset`**, not real loops. Ghidra decoded
  the compiler's unrolled copies literally.
- `extraout_ECX_00` … `extraout_ECX_09` — call-clobbered register spill, cosmetic.

## What to do next, in order

1. **Type the state machine first.** Rename `DAT_004ae978` → `g_gameState`, `DAT_004ae960` →
   `g_shutdown;` define an `enum GameState { … }` and apply it. Re-decompile: the double
   switch, the `(int *)0x0` cases and much of the `+ -5` arithmetic disappear. This is the
   cheapest large win available, and it's now unblocked because we know what the variable is.
2. **Name the four loop arrays** (`DAT_004af8cc/d0/c4/c8` + `DAT_004c90a0`) as a
   count/pointer pair — they're the entity or object table.
3. Then decompile one `case` at a time and treat each as a small function. Do not try to hold
   1,089 lines in your head.
4. `FUN_004156a0` (3.4 KB, state 2) is the natural second target — it's the other half of the
   same dispatcher and will confirm the structural pattern holds.

## Artifacts

`work/ghidra-out/decomp_430500.c`, `decomp_412b80.c`, `annotation.csv`.
Script: `port/ghidra/Decompile.java` (`-postScript Decompile.java 0x430500 300`).
