"""Arranging the run, and starting the two processes it needs.

Everything this touches outside the test world is put back afterwards, because
the run has to be able to happen on a working tree somebody else is using:

* **content packs** — the run needs a known set, and a deferred directory is
  exercised by copying it into ``xiecontent/`` as a pack of its own, since the
  leading ``_`` is what skips it and the content repo should not be edited to
  run a test. Anything else already in there is moved aside and moved back.
* **``options.txt``** — ``pauseOnLostFocus`` has to be off, or the game menu
  opens the moment the driving process hands the window back and the click
  that follows is swallowed. It has to be changed while the client is *not*
  running, because a clean exit rewrites the file.
* **``server.properties``** — the run wants its own flat world so that an
  arena is a fill rather than a landscape, and rcon.
"""

import os
import shutil
import subprocess
import threading
import time

from . import winput
from .rcon import Rcon

#: The four deferred packs the baseline counts are quoted against, and where
#: each one lives inside the content repo. ``_wip/steel`` sits one level deeper
#: than the rest. They are taken from the source repo beside this checkout, not
#: from the copy the mod installs into ``xiecontent/``: the jar bundles the live
#: content only, so an underscored directory is never there to copy.
DEFERRED_PACKS = {
    "weapons": "_plox_update/weapons",
    "ultimatefist": "_plox_update/ultimatefist",
    "nether_crops": "_plox_update/nether_crops",
    "steel": "_wip/steel",
}

#: Not a pack: the live content, and the pack this run generates.
NOT_A_PACK = {"mod-content", "_generated"}

WORLD_NAME = "acceptance-world"

SERVER_PROPERTIES = {
    "level-name": WORLD_NAME,
    "level-type": "minecraft:flat",
    "generate-structures": "false",
    "spawn-protection": "0",
    "difficulty": "easy",
    "enable-rcon": "true",
    "rcon.port": "25575",
    "rcon.password": "xie",
    "online-mode": "false",
}

#: How long after joining before the first click is worth sending.
JOIN_SETTLE = 8.0

CLIENT_OPTIONS = {
    "pauseOnLostFocus": "false",
    "autoJump": "false",
}


class SessionError(Exception):
    pass


class _Tail:
    """Keeps a process's output in a file and answers "has it said this yet"."""

    def __init__(self, stream, path):
        self.path = path
        self.lines = []
        self._lock = threading.Lock()
        self._file = open(path, "w", encoding="utf-8", newline="\n")
        self._thread = threading.Thread(target=self._pump, args=(stream,), daemon=True)
        self._thread.start()

    def _pump(self, stream):
        for raw in iter(stream.readline, b""):
            line = raw.decode("utf-8", "replace").rstrip("\r\n")
            with self._lock:
                self.lines.append(line)
                self._file.write(line + "\n")
                self._file.flush()
        with self._lock:
            self._file.close()

    def find(self, needle):
        with self._lock:
            for line in self.lines:
                if needle in line:
                    return line
        return None

    def snapshot(self):
        with self._lock:
            return list(self.lines)


class Session:
    """The arranged run: packs in place, a server up, a client joined."""

    def __init__(self, repo, player="Dev", host="127.0.0.1", start_client=True,
                 clean_world=False, verbose=False):
        self.repo = os.path.abspath(repo)
        self.player = player
        self.host = host
        self.start_client = start_client
        self.clean_world = clean_world
        self.verbose = verbose

        self.run_dir = os.path.join(self.repo, "neoforge", "run")
        self.content_dir = os.path.join(self.run_dir, "xiecontent")
        self.artifacts = os.path.join(self.run_dir, "acceptance")

        self.rcon = None
        self.server = None
        self.client = None
        self.server_log = None
        self.client_log = None
        self.window = None

        self._added_packs = []
        self._stashed_packs = None
        self._restore_files = {}

    # ------------------------------------------------------------- arranging

    def arrange(self):
        os.makedirs(self.artifacts, exist_ok=True)
        self._arrange_packs()
        self._arrange_file(os.path.join(self.run_dir, "server.properties"),
                           self._patch_properties)
        self._arrange_file(os.path.join(self.run_dir, "options.txt"),
                           self._patch_options)
        if self.clean_world:
            world = os.path.join(self.run_dir, WORLD_NAME)
            if os.path.isdir(world):
                shutil.rmtree(world)

    def _arrange_packs(self):
        content_repo = os.path.join(os.path.dirname(self.repo), "mod-content")
        if not os.path.isdir(content_repo):
            raise SessionError("no content repo at %s" % content_repo)

        # The live content itself needs no arranging: the server installs the
        # bundled copy on the first launch that finds none, and never touches one
        # that is there.
        if not os.path.isdir(os.path.join(self.content_dir, "mod-content")):
            say("no mod-content in %s; the server will install the bundled copy"
                % self.content_dir)

        for pack, source in DEFERRED_PACKS.items():
            target = os.path.join(self.content_dir, pack)
            if os.path.isdir(target):
                continue
            origin = os.path.join(content_repo, *source.split("/"))
            if not os.path.isdir(origin):
                raise SessionError("deferred pack %s is not at %s" % (pack, origin))
            shutil.copytree(origin, target)
            self._added_packs.append(target)

        if self._added_packs:
            say("copied in %s"
                % ", ".join(os.path.basename(pack) for pack in self._added_packs))

        extra = [
            entry for entry in sorted(os.listdir(self.content_dir))
            if entry not in NOT_A_PACK and entry not in DEFERRED_PACKS
            and os.path.isdir(os.path.join(self.content_dir, entry))
        ]
        if extra:
            say("moved aside %s, which would move the counts" % ", ".join(extra))
            self._stashed_packs = os.path.join(self.artifacts, "stashed-packs")
            os.makedirs(self._stashed_packs, exist_ok=True)
            for entry in extra:
                shutil.move(os.path.join(self.content_dir, entry),
                            os.path.join(self._stashed_packs, entry))

    def _arrange_file(self, path, patch):
        before = ""
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8") as handle:
                before = handle.read()
        after = patch(before)
        if after != before:
            self._restore_files[path] = before
            with open(path, "w", encoding="utf-8", newline="\n") as handle:
                handle.write(after)

    @staticmethod
    def _patch_properties(text):
        return _patch_key_values(text, SERVER_PROPERTIES, "=")

    @staticmethod
    def _patch_options(text):
        return _patch_key_values(text, CLIENT_OPTIONS, ":")

    def restore(self):
        for path, before in self._restore_files.items():
            if before:
                with open(path, "w", encoding="utf-8", newline="\n") as handle:
                    handle.write(before)
            elif os.path.exists(path):
                os.remove(path)
        self._restore_files = {}

        for target in self._added_packs:
            shutil.rmtree(target, ignore_errors=True)
        self._added_packs = []

        if self._stashed_packs and os.path.isdir(self._stashed_packs):
            for entry in os.listdir(self._stashed_packs):
                shutil.move(os.path.join(self._stashed_packs, entry),
                            os.path.join(self.content_dir, entry))
            os.rmdir(self._stashed_packs)
            self._stashed_packs = None

    # -------------------------------------------------------------- starting

    def _gradle(self, *arguments):
        command = [os.path.join(self.repo, "gradlew.bat")]
        command.extend(arguments)
        command.append("--console=plain")
        return subprocess.Popen(
            command,
            cwd=self.repo,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            stdin=subprocess.DEVNULL,
        )

    def start(self, timeout=600.0):
        self.arrange()
        self._start_server(timeout)
        if self.start_client:
            self._start_client(timeout)
        return self

    def _start_server(self, timeout):
        say("starting the server (gradlew :neoforge:runServer)")
        self.server = self._gradle(":neoforge:runServer")
        self.server_log = _Tail(self.server.stdout, os.path.join(self.artifacts, "server.log"))

        started = time.monotonic()
        while time.monotonic() - started < timeout:
            if self.server.poll() is not None:
                raise SessionError("the server exited before it was up — see %s"
                                   % self.server_log.path)
            if self.server_log.find('Done (') and self.server_log.find("RCON running"):
                break
            time.sleep(1.0)
        else:
            raise SessionError("the server never finished starting")

        self.rcon = Rcon(host=self.host).wait_for_server()
        say("server up after %.0fs" % (time.monotonic() - started))

    def _start_client(self, timeout):
        say('starting the client (gradlew :neoforge:runClient "-Pxie.join=%s")' % self.host)
        self.client = self._gradle(":neoforge:runClient", "-Pxie.join=%s" % self.host)
        self.client_log = _Tail(self.client.stdout, os.path.join(self.artifacts, "client.log"))

        started = time.monotonic()
        while time.monotonic() - started < timeout:
            if self.client.poll() is not None:
                raise SessionError("the client exited before it joined — see %s"
                                   % self.client_log.path)
            if self.player in self.rcon.command("list"):
                break
            time.sleep(1.0)
        else:
            raise SessionError("%s never joined the server" % self.player)

        hwnd, pid = winput.find_game_window(attempts=30)
        if hwnd is None:
            raise SessionError("the client joined but its window could not be found")
        self.window = hwnd
        self.client_pid = pid
        # Joining is not being ready: the client is still being sent its
        # chunks, and until it has one it cannot raycast against it.
        time.sleep(JOIN_SETTLE)
        say("%s joined after %.0fs" % (self.player, time.monotonic() - started))

    # -------------------------------------------------------------- stopping

    def stop(self):
        try:
            self._stop_client()
            self._stop_server()
        finally:
            self.restore()

    def _stop_client(self):
        if self.client is None:
            return
        say("stopping the client")
        pid = getattr(self, "client_pid", None)
        if pid:
            # The game is forked by the Gradle daemon rather than by gradlew, so
            # the window's own process is the one to end.
            subprocess.run(["taskkill", "/F", "/PID", str(pid)],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            self.client.wait(timeout=60)
        except subprocess.TimeoutExpired:
            subprocess.run(["taskkill", "/F", "/T", "/PID", str(self.client.pid)],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self.client = None

    def _stop_server(self):
        if self.server is None:
            return
        say("stopping the server")
        try:
            if self.rcon is not None:
                self.rcon.command("stop")
                self.rcon.close()
        except Exception:
            pass
        try:
            self.server.wait(timeout=120)
        except subprocess.TimeoutExpired:
            subprocess.run(["taskkill", "/F", "/T", "/PID", str(self.server.pid)],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self.server = None

    def __enter__(self):
        return self.start()

    def __exit__(self, *_):
        self.stop()


def _patch_key_values(text, wanted, separator):
    """Sets keys in a ``key<sep>value`` file, keeping every other line as it is."""
    lines = text.splitlines()
    seen = set()
    for index, line in enumerate(lines):
        if line.startswith("#") or separator not in line:
            continue
        key = line.split(separator, 1)[0]
        if key in wanted:
            lines[index] = "%s%s%s" % (key, separator, wanted[key])
            seen.add(key)
    for key, value in wanted.items():
        if key not in seen:
            lines.append("%s%s%s" % (key, separator, value))
    return "\n".join(lines) + "\n"


def say(message):
    print("  ... %s" % message, flush=True)
