# Finding: two-level state machine, four object tables, one dead flag

Decompiled `FUN_00430500` and its caller `WinMain_` (`0x412b80`) to answer one question:
can this engine be cut into chunks? Then named what the code proved.

Answer: **yes — a two-level state machine over four parallel object tables.**

## Level 1 — `WinMain_` (546 lines of C)

```c
if (g_flag_ae960 == 0) {
    ...
    if (g_gameState == STATE_2)          { FUN_004156a0(); }
    else if (g_gameState - STATE_3 < 3)  { FUN_00430500(iVar4, iVar8); }   /* states 3,4,5 */
    FUN_004046e0();
}
```

`STATE_11`, `STATE_13`, `STATE_14` and `(int)g_gameState < 6` gate other calls, so 0–5 are one
family and 11/13/14 another.

## Level 2 — `FUN_00430500` (1,090 lines) runs the in-game cycle on `g_subState`

| phase | code | what it does |
|---|---|---|
| `SUB_8` (case 0) | walks `g_objCount0` calling `FUN_00428ec0(0)` per item, then `g_objCount1` calling `FUN_004299c0()` | **update tick**, then advances itself to `SUB_9` |
| `SUB_9` (case 1) | scans table 0 for field `+0x16c == 0`, then table 2: if slot flag `+0x18 & 1` clear and `*slot == 1` → `FUN_00434be0`, and if `+0x5c == 4` → `FUN_0042c200` | **post-update / collision-state pass** |
| `SUB_10` (case 2) | waits on `DAT_004d559c`, `WaitMessage()`, `PostQuitMessage` paths | message/wait handling |
| `SUB_12` (case 4) | compares `DAT_004af834/830` against `DAT_004aea14/a10` (a timestamp pair), key `DAT_004aef88 == 0x1b` (ESC) → `FUN_00410600(1)` | **frame-timing / skip gate** |

Transitions written in the function: `SUB_0, SUB_2, SUB_3, SUB_4(×2), SUB_5, SUB_6, SUB_7,
SUB_8(×2), SUB_9, SUB_12`.

## The object system: four count/table pairs

Each count is walked together with one base array, and the first dword of each slot holds a
pointer to the object itself (`iVar9 = *piVar12`).

| count | table base | stride | evidence |
|---|---|---|---|
| `g_objCount0` 0x4af8cc | `g_objTable0` 0x4bf040 | `piVar12 + 0x2fa` (ints) | `SUB_9` loop reads `*(int*)(iVar9 + 0x16c)` |
| `g_objCount1` 0x4af8d0 | `g_objTable1` 0x4c90a0 | 1 int | same `SUB_8` loop: `piVar12 = (&g_objTable1)[i]` |
| `g_objCount2` 0x4af8c4 | `g_objTable2` 0x4c9100 | 1 slot | reached as `piVar20[-0x2cd]` from `&DAT_004c9c34` |
| `g_objCount3` 0x4af8c8 | `g_objTable3` 0x4c13f8 | 1 slot | `piVar12 = &DAT_004c13f8; *piVar12` |

Field offsets seen on objects: `+0x18` (bitflags, bit 1 = "active/in-list", bit 8), `+0x5c`
(== 4 a state), `+0x132` (bit 0x10000), `+0x16c` (a counter that must be 0 to continue).

**All four counts and all four tables live in `.bss`, not in the file image.** So save-game
state is not in this binary's initialized data — everything is built at runtime. `g_gameState`
and `g_flag_ae960` *are* initialized (both `0`), at 0x4ae978 / 0x4ae960 in the file image.

## `g_shutdown` was a bad name — corrected to `g_flag_ae960`

The earlier "contradictory evidence" resolved cleanly once xrefs were counted:

- **19 references, 6 functions — every one a `CMP`/read. Zero writes.**
- Initial value in the file image: **0**.
- Consequence: `if (g_flag_ae960 == 0)` is always taken, `do { … } while (g_flag_ae960 != 0)`
  runs exactly once, and `if (… || g_flag_ae960 != 0) break;` never breaks on it.

It is not a shutdown flag in this build; it is never set. The name is now neutral
(`g_flag_ae960`) instead of asserting a meaning the code contradicts. Caveat: `.text` still
holds undisassembled regions, so this is "no writes found", not "provably absent" — a computed
address (`mov [0x4ae95c+4], …`) would not show up as a reference.

## Typing applied — `port/ghidra/TypeState.java` (idempotent, re-runnable)

| symbol | name | type |
|---|---|---|
| 0x4ae978 | `g_gameState` | `enum GameState` (`STATE_0…5, 11, 13, 14`) |
| 0x4af898 | `g_subState` | `enum SubState` (`SUB_0…SUB_15`) |
| 0x4ae960 | `g_flag_ae960` | `int` |
| 4 counts + 4 tables | `g_objCount0…3`, `g_objTable0…3` | unchanged |

## Negative result: retyping the switch variable does not clear the case labels

Tried `piVar9` (`int *`) → `int`: 1,089 → 1,091 lines, still **8/8** `case (int *)0xN` labels,
variable renamed throughout. Reverted. Cause: there is no named switch variable — Ghidra emits
`switch((int *)(g_subState - SUB_8))` inline, so the cast comes from its own jump-table type
inference. Only the GUI's *Retype switch variable* action, or reading `case 0` as `SUB_8`.

## Next steps

1. `FUN_00428ec0` and `FUN_004299c0` — the per-object update calls in `SUB_8`; these define
   what the four tables actually hold (the fastest route to naming them properly).
2. `FUN_004156a0` (3.4 KB, `STATE_2`) — the other half of level 1.
3. Object struct: rebuild from the field offsets above (`+0x18`, `+0x5c`, `+0x132`, `+0x16c`)
   into a real struct type, then re-decompile — this fixes more than retyping a local.

## Artifacts

`work/ghidra-out/`: `decomp_430500.c` (typed), `decomp_00430500_retyped.c`, `decomp_412b80.c`,
`before/` snapshots. Scripts `port/ghidra/`: `Decompile.java`, `TypeState.java`,
`RetypeSwitch.java`, `Xrefs.java` (+ `run_*.bat`).
