# SCP Sync Util

A minimalist Java Swing program (Java 17, Maven) that mirrors a local Windows
folder to a remote Ubuntu folder over SCP.

Click one button and the tool walks a directory (`Dir1`, including any
sub-directories), finds every file changed since the previous run, and copies
just those files to the matching mirror location on a remote Ubuntu host. The
local and remote folders share the same name (e.g. `...\Dir1` ->
`.../Dir1`).

## Original spec

> A java swing program. JAVA 17 with mvn (no browser but runnable on pc).
> Renders a minimalist button. On button click, go through a dir (say `Dir1`,
> which can also contain sub-dirs) and check which files have been modified
> since it previously ran. If a file is modified, scp it to a target location
> which is a mirror of `Dir1`. The current pc is a Windows pc, the remote is
> Ubuntu. The local folder path and the mirror folder path are specified in a
> json (both folders have the same name). The remote IP, username and password
> are also specified in the json file, which lives in the same dir as the
> program. The last time the program ran is stored in another json file.
> Dependencies (mvn): java swing and google Gson.

## How it works

- **`config.json`** (next to the program) holds the local folder, mirror folder,
  and connection details for the chosen route.
- **`lastrun.json`** (next to the program) stores the timestamp of the last
  successful sync. Any file whose last-modified time is newer than that is
  copied. On the very first run (no `lastrun.json`) every file is copied. All of
  this is decided **locally** — the remote is never queried for timestamps.
- The last-run marker only advances when **all** files copy successfully, so a
  failed file is retried on the next click.
- **The remote is never inspected or modified apart from the copy itself** — no
  `mkdir`, no listing, no status checks. The tool knows only the target folder
  path. Because of that, **the remote mirror folder (and any sub-folders you use)
  must already exist**. A file whose remote sub-folder is missing is reported
  `[FAILED]` and retried next run; local sub-directory structure is otherwise
  preserved in the destination path.
- **Route toggle:** the window has an *OpenSSH (scp)* / *PuTTY (pscp)* selector.
  It is seeded from `mode` in `config.json` and overrides it for that run.

### Dependencies

- **Java Swing** — bundled with the JDK.
- **Google Gson** — the only external Maven dependency (reads/writes the json).

## The SCP transport

Java has no built-in SCP and (per the spec) Gson is the only library, so the
tool shells out to the system `scp` (or PuTTY's `pscp`) to do the transfer — and
nothing else; there is no separate `ssh`/`plink` step. You pick the route with
the in-window toggle; `mode` in the config seeds its initial position (otherwise
it is inferred: OpenSSH when `remoteHost` is set, else PuTTY).

### Mode 1 — OpenSSH with a host alias (recommended)

If you already connect with `ssh <alias>` and `scp <alias>:...` (i.e. you have a
`Host` entry in `~/.ssh/config` — usually `.ssh\config` on Windows — with
key-based auth), just set **`remoteHost`** to that alias. The tool then runs the
built-in `ssh`/`scp` exactly the way you do, using your existing keys. **No
password is placed on the command line.**

```json
{
  "localFolder": "C:\\Users\\me\\Dir1",
  "mirrorFolder": "/home/ubuntu/Dir1",
  "remoteHost": "myserver"
}
```

Each modified file resolves to a single command like:

```
scp -o BatchMode=yes C:\...\file.txt myserver:/home/ubuntu/Dir1/sub/file.txt
```

Requirements: Windows 10/11 built-in OpenSSH (`where scp`), the remote mirror
folder tree already present, and the alias must authenticate **without an
interactive prompt** (key auth), because a GUI app can't type a password.
`BatchMode=yes` makes it fail fast if keys aren't set up rather than hang.

### Mode 2 — PuTTY with a password

Leave `remoteHost` empty and provide `remoteIp` + `username` + `password`. The
tool uses PuTTY's `pscp` with `-pw`. Install PuTTY and make sure `pscp` is on
your `PATH`. First connect once interactively (e.g. with `pscp`/PuTTY) so the
host key is cached — batch mode won't accept a new host key.

```json
{
  "localFolder": "C:\\Users\\me\\Dir1",
  "mirrorFolder": "/home/ubuntu/Dir1",
  "remoteIp": "192.168.1.100",
  "username": "ubuntu",
  "password": "your-password-here"
}
```

## Configuration reference

Copy `config.example.json` to `config.json` (same directory as the jar).

| Field          | Meaning                                                                 |
| -------------- | ----------------------------------------------------------------------- |
| `localFolder`  | Windows path of the folder to watch (`Dir1`).                          |
| `mirrorFolder` | Remote Ubuntu path of the mirror folder (same folder name).           |
| `remoteHost`   | SSH alias (Mode 1). When set, `remoteIp`/`username`/`password` are ignored. |
| `remoteIp`     | Remote host IP/hostname (Mode 2).                                       |
| `username`     | SSH username (Mode 2).                                                  |
| `password`     | SSH password (Mode 2 only).                                             |
| `port`         | SSH port. `0`/omitted = let scp / the alias config decide.            |
| `mode`         | Seeds the route toggle: `"openssh"` or `"putty"` (optional; otherwise inferred). |
| `scpCommand`   | SCP executable (optional; defaults to `scp` or `pscp`).               |
| `batchMode`    | Add non-interactive flags so runs fail fast (optional, default `true`). |

> `config.json` and `lastrun.json` are git-ignored because they contain
> credentials / machine-specific state.

## Build

```
mvn clean package
```

This produces a single runnable fat jar at `target/scp-util.jar`.

## Run

Put `config.json` in the same directory as the jar, then:

```
java -jar scp-util.jar
```

A small window opens with a **Sync Now** button, a **Route** toggle (OpenSSH /
PuTTY), and an activity log. Pick the route, then click the button to copy
everything modified since the last successful sync.
