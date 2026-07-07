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
  remote IP, username and password.
- **`lastrun.json`** (next to the program) stores the timestamp of the last
  successful run. Any file whose last-modified time is newer than that is
  copied. On the very first run (no `lastrun.json`) every file is copied.
- The last-run marker only advances when **all** files copy successfully, so a
  failed file is retried on the next click.
- Sub-directory structure is preserved on the remote; missing remote folders are
  created with `mkdir -p` before each copy.

### Dependencies

- **Java Swing** — bundled with the JDK.
- **Google Gson** — the only external Maven dependency (reads/writes the json).

## The SCP transport

Java has no built-in SCP and (per the spec) Gson is the only library, so the
tool shells out to an external SCP program that accepts a password on the
command line. On Windows the standard choice is **PuTTY's `pscp` and `plink`**
(both take `-pw <password>`). Install PuTTY and make sure `pscp` and `plink`
are on your `PATH`.

The executable names are configurable in `config.json` (`scpCommand` /
`sshCommand`) if you prefer different tools (e.g. `sshpass`-wrapped `scp`).

## Configuration

Copy `config.example.json` to `config.json` (in the same directory as the jar)
and fill it in:

```json
{
  "localFolder": "C:\\Users\\me\\Dir1",
  "mirrorFolder": "/home/ubuntu/Dir1",
  "remoteIp": "192.168.1.100",
  "username": "ubuntu",
  "password": "your-password-here",
  "port": 22,
  "scpCommand": "pscp",
  "sshCommand": "plink"
}
```

| Field          | Meaning                                                        |
| -------------- | ------------------------------------------------------------- |
| `localFolder`  | Windows path of the folder to watch (`Dir1`).                 |
| `mirrorFolder` | Remote Ubuntu path of the mirror folder (same folder name).  |
| `remoteIp`     | Remote host IP or hostname.                                   |
| `username`     | SSH username on the remote host.                             |
| `password`     | SSH password on the remote host.                             |
| `port`         | SSH port (optional, defaults to `22`).                       |
| `scpCommand`   | SCP executable (optional, defaults to `pscp`).               |
| `sshCommand`   | SSH executable used for `mkdir -p` (optional, default `plink`). |

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

A small window opens with a **Sync Now** button and an activity log. Click it to
copy everything modified since the last run.
