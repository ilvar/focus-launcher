# Focus launcher: standing instructions for any agent in this repository

In this repository you are **`chaitany-claude`**. You have a brain: a written, three-tier memory
of this project at `.claude/brain/chaitany-claude/`. What follows is not advice. Both rules are
mandatory and apply to every session and every task, however small.

## Rule 1. Read your brain before you work

| Tier | File(s) | When you read it | Size |
| --- | --- | --- | --- |
| **1. Basics** | `.claude/brain/chaitany-claude/1-basics.md` | **Always, in full, before your first action in the project.** No exceptions. | one screen |
| **2. Overview** | `.claude/brain/chaitany-claude/2-overview/<area>.md` | Before working in that area. Read every area your task touches. | about a page each |
| **3. Details** | `.claude/brain/chaitany-claude/3-details/<topic>.md` | Only when you are about to change or debug that exact subsystem. | as long as needed |

- Tier 1 ends with an index of Tier 2; each Tier 2 file ends with pointers into Tier 3. Follow the
  pointers. Never bulk-load Tier 3: it exists so that Tier 1 and 2 can stay small.
- If the brain and the code disagree, the code is right: fix the brain as part of your work.
- If a fact you need is in no tier, find it out, then write it down (Rule 2).

## Rule 2. Update your brain when you finish

Whenever you finish a piece of work, **before your final message to the user**, update the brain.
A task is not finished until this is done.

1. **Journal.** Append a dated entry to `.claude/brain/chaitany-claude/journal.md`: what was asked,
   what you did, what you verified and how, what is still open. Absolute dates, newest at the bottom.
2. **Tiers.** Correct or extend whatever your work made stale or incomplete. Put each fact in the
   *lowest tier that needs it*:
   - Tier 1 only for what every task needs: hard rules, where things are, the state of the world in
     a line each. Keep it to one screen (about 80 lines). If it grows, demote something to Tier 2.
   - Tier 2 for how an area works and its current status.
   - Tier 3 for mechanisms, measurements, exact commands, and the reasoning behind decisions.
3. **Lessons.** A mistake that cost time, or a trap in the platform, goes into
   `3-details/mistakes-and-lessons.md` with the symptom, the cause and the fix.
4. **Decisions.** Anything the user decided or asked for goes into `2-overview/user-and-decisions.md`
   in their own intent, so nobody re-litigates it later.

Write facts, not narration: a line should still be true and useful in three months. Replace a wrong
statement rather than appending a contradiction. Record how a thing was verified, or say that it
was not.

## The brain is public. Write it that way.

By the owner's decision (2026-09-19) the brain is committed to this repository, and the
repository is public. Everything in the tiers and the journal can be read by anyone, forever.

- **Never write into the tiers or the journal:** secrets of any kind (passwords, tokens, key
  material); server addresses, login names, key file names, what else runs on the server or how
  it is configured; device serials or account details; anything observed on the owner's phone
  (which apps he has, how long he uses them, calendar contents, accounts); personal remarks about
  the owner. Describe mechanisms and decisions in general terms instead.
- **Facts like those that an agent genuinely needs** go in
  `.claude/brain/chaitany-claude/private/`, which is git-ignored. The public text may point there
  ("see `private/server.md`"); it may not repeat what is there. Never `git add -f` anything under
  `private/`.
- The pre-push audit in `2-overview/github-and-release.md` covers the brain too. Run it before
  every push.
- When unsure whether a detail is safe to publish, it is not: put it in `private/`.

## Other agents

A future agent with a different job gets its own brain beside this one, named with the same prefix:
`.claude/brain/chaitany-<role>/`, with the same three tiers, the same `private/` folder and the
same rules. Agents may read each other's brains and write only their own.
