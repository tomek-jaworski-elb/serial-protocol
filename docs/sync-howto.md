# Moving the database between computers — operator how-to

The certificates database (participants, courses, trainers, photos) lives on each computer the
program runs on. The computers share no network, so the data travels on a USB stick as one
file. This page says what to click and what to do when the program asks a question. The design
and the exact rules are in [`sync-spec.md`](sync-spec.md).

## The one rule

**Only one computer edits at a time.** Everybody else only looks and prints until the stick
brings them the newer data. Who is editing is agreed between people; the program does not
enforce it, it only detects when the rule was broken and asks you to decide.

A computer that is never meant to edit is started with `SYNC_MODE=READ_ONLY`. It shows a yellow
bar on every page and a warning after every save. Forms still work there, so a mistake is
possible; the next import reports it as a collision.

## Where it is

Menu **More → Sync**, or `/sync`. Every logged-in user may export, import and back up.
Two buttons need the admin login: **Restore** and **New node id**.

The page shows this computer's name and node id, its mode, when it last exported and imported,
the backup folder and the backups kept there.

## Passing the data on (export)

On the computer that has been editing:

1. Open **Sync**, press **Export sync file**.
2. The browser downloads `<computer>-<date>-<time>.sync`. Copy it to the stick.

The file holds the whole database, photos included, so one file is all a receiver needs.
Nothing changes on the exporting computer except the "last export" time.

## Taking the data in (import)

On a computer that should receive the data:

1. Open **Sync**, choose the `.sync` file under **Import**, press **Read the file**.
2. Read the **Import report**. It says who made the file and when, how many records are new,
   how many change, and lists every **collision** (see below). Nothing has been written yet.
3. Pick a resolution for every collision, then press **Apply the import**. **Discard the file** throws the upload
   away and changes nothing.
4. The summary on the Sync page says how many records were added and updated and names the
   backup that was written just before.

The program refuses a file made by this same computer (that is a backup, see Restore) and a
file made by a different program version (update the program on both computers first).

A read-only computer may export too. Its file carries nothing new unless someone edited there
by mistake; the report on the receiving side shows those edits as collisions, which is the
intended way of finding and settling them.

## Collisions

A collision is a record the program cannot settle on its own. The report names the table, the
record the way a person would (name and number, course number and type) and the fields that
differ, with both values and which computer changed each one when.

| Report says | What happened | Your choice |
|---|---|---|
| created on a read-only computer | a record was added on a computer that should not edit | keep it, or discard it |
| edited on both computers | the same record was changed here and on the other computer since the last exchange | take the file's version, or keep this computer's (whole record, no field-by-field mixing) |
| deleted on one, edited on the other | one computer deleted the record while the other edited it | keep it deleted, or keep the edited one |
| number used twice | two different participants (courses, counters) got the same number | keep the file's and renumber the local one to the next free number, or the other way round |
| course type code used twice | two different course types got the same code | keep one code and the other becomes `CODE-2` |
| dangling reference | a record in the file points to something that exists nowhere, or only as deleted | skip that record, or bring the deleted one back |

When in doubt, choose the side that was supposed to be editing. Everything you decide is
written to a report file next to the backup, so a decision can be reviewed later.

## Backups

A backup (`<computer>-<date>-<time>-<reason>.bak`) is written automatically before every
import and every restore, and on **Back up now**. The newest 20 are kept (`SYNC_BACKUP_KEEP`,
0 keeps all) in the backup folder shown on the page (`SYNC_BACKUP_DIR`; in Docker the
`server-sync-backups` volume). Any user may download a backup; a backup is the same format as
a sync file, so it can also be imported on another computer in an emergency.

## Undoing an import (restore, admin)

If an import was a mistake, log in as admin, find the `before-import` backup in the list and
press **Restore**. The whole database is replaced by the backup; everything changed since it
was written is lost, but a `before-restore` backup is taken first, so the restore itself can be
undone the same way. A restore never asks about collisions.

## Setting up a new computer

Install the program with an **empty** database and import the newest `.sync` file. Never copy
a database or a Docker volume from another computer: the copy carries that computer's node
id, and two computers with one id confuse every later import. If that already happened, an
admin presses **New node id** on the copy; its sync history starts over.

## Settings

| Environment variable | Default | Meaning |
|---|---|---|
| `SYNC_MODE` | `ACTIVE` | `READ_ONLY` for a computer that must not edit; read at start |
| `SYNC_NODE_NAME` | host name | the name shown in reports and file names |
| `SYNC_BACKUP_DIR` | `./sync-backups` | where backups go |
| `SYNC_BACKUP_KEEP` | `20` | how many backups to keep, `0` = all |

Changing any of them needs a restart.
