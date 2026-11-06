# Plain English

For anyone who wants to know what this project is without learning to read machine code first.
No jargon without a plain sentence next to it.

## The situation

Septerra Core shipped in 1999 as a **compiled program**. Think of a finished meal: you can taste
it, but the recipe was never published. We own the dish, not the instructions.

So there are three separate things to build, and it helps to keep them apart:

1. **The Frame** — a modern wrapper that lets the original engine run on today's computers.
   This already exists; it's the recompilation by M-HT that we build and test.
2. **The Notes** — reading the compiled program and writing down what it does in a form humans
   can read. Slow, unglamorous, and the only way to truly own the game.
3. **The Art Layer** — a way to swap the pictures without touching the program at all.

Most people assume #3 needs #2 finished. It doesn't. Pictures can be replaced while the engine
stays exactly as it is.

## Words you'll keep hearing

| word | plain meaning | comparison |
|---|---|---|
| compiled program | the game as shipped — instructions the computer understands, not people | a cooked meal |
| decompilation | reading the finished program and reconstructing the recipe | tasting backwards to write the recipe |
| recompilation | translating the original instructions so a modern machine runs them | re-printing a book in a new format |
| shim | the adapter that translates old system calls into modern ones | a power adapter for foreign sockets |
| function | one job the program does | one tool in a kitchen |
| struct | a labelled form describing one thing | a character sheet with boxes |
| state machine | the game's list of modes it switches between | a theatre's scenes and act changes |
| slot | a numbered place for a character on screen (there are 13) | parking spaces |
| `.db` archive | the suitcase holding all the game's files | a suitcase |
| record key | the tag on each item in the suitcase, a number | luggage tag |
| blob | one item in the suitcase, still packed | a packed item |
| pack | a folder of replacement items that overrides the suitcase | aftermarket parts |
| oracle | the working build we compare against | a control group |

## How the pieces fit

- The **Frame** runs the original engine.
- The **pack layer** feeds that engine different pictures.
- The **Notes** slowly replace the engine itself with readable code, one function at a time.

You can ship art packs long before the engine is rewritten. That's the point of doing them
first: visible progress, no waiting.

## The path, in three stages

**Stage 1 — packs work end to end.** Weeks, not months. Pictures go in, game shows them.
What's missing: names for the entries, so you can find the picture you want without a hex number.

**Stage 2 — better pictures.** Per-animation-frame replacement, and the ability to draw art
larger than the original. This is where user-made upscales start to be worth anything.

**Stage 3 — the readable engine.** Porting the program into maintainable source code. Years of
work, and the reason to keep the Notes going all along.

## Who does what

| you | me |
|---|---|
| taste, art direction, decisions | reading machine code |
| playing and testing | writing and building the code |
| saying "that looks wrong" | keeping the notes and tools honest |
| choosing what a pack should look like | making packs possible |

## Learning by doing, not by reading

The fastest way in is one loop you can see: change one thing, run the game, look at the result.
Every stage above is built to give you that loop. If an explanation isn't leading to something
you can see, it's the wrong explanation.
