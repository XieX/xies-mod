"""The acceptance run: one command that starts the server, joins a client,
asserts a fixed list of in-game behaviours and says pass or fail for each.

    python acceptance/run.py

Every *count* in this port is verified on every run, because the server parses
and validates the whole generated datapack at startup and says what it emitted.
Every *behaviour* used to be verified exactly once, by hand, and then never
again — which is what this is for. It exits non-zero if anything fails, so it
can be left to run unattended.

What it needs is a Windows desktop with a session that can hold the foreground:
the client is a real client, and a ``useOn`` is a real click at a real window.
``--no-client`` runs the subset that rcon alone can answer.
"""

import argparse
import os
import sys
import time
import traceback

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from acceptance.checks import (  # noqa: E402
    blocks, bows, cookpot, counts, crafter, cull, decay, fertilisable, fist, fruit, sapling,
    ripen, sickle, spikey, stem,
)
from acceptance.rig.arena import REFERENCE  # noqa: E402
from acceptance.rig.game import Game  # noqa: E402
from acceptance.rig.report import Report  # noqa: E402
from acceptance.rig.session import Session, SessionError  # noqa: E402

#: In descending order of what only a running game can tell you. The stem is
#: first because it is the archetype nothing has ever watched work.
CHECKS = [
    ("counts", counts),
    ("stem", stem),
    ("fruit", fruit),
    ("sapling", sapling),
    ("decay", decay),
    ("spikey", spikey),
    ("fertilisable", fertilisable),
    ("sickle", sickle),
    ("fist", fist),
    ("bows", bows),
    ("crafter", crafter),
    ("cookpot", cookpot),
    ("ripen", ripen),
    ("blocks", blocks),
    ("cull", cull),
]

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--only", default="", help="run only these checks, comma separated")
    parser.add_argument("--skip", default="", help="run everything but these")
    parser.add_argument("--list", action="store_true", help="name the checks and stop")
    parser.add_argument("--no-client", action="store_true",
                        help="server only: skips every check that needs a click")
    parser.add_argument("--clean-world", action="store_true",
                        help="delete the acceptance world first")
    parser.add_argument("--keep-running", action="store_true",
                        help="leave the server and client up afterwards, to poke at")
    parser.add_argument("--no-screenshots", action="store_true",
                        help="do not press F2 on a failure")
    parser.add_argument("--repo", default=REPO)
    options = parser.parse_args(argv)

    if options.list:
        for name, module in CHECKS:
            print("%-14s %s" % (name, (module.__doc__ or "").strip().splitlines()[0]))
        return 0

    wanted = _wanted(options)
    report = Report()
    session = Session(options.repo, start_client=not options.no_client,
                      clean_world=options.clean_world)

    try:
        session.start()
    except SessionError as failure:
        print("could not start: %s" % failure)
        session.restore()
        return 2

    try:
        game = Game(session)
        game.prepare()
        if game.window is not None:
            wrong = game.preflight(*REFERENCE)
            if wrong:
                print("could not build the arena: %s" % wrong)
                session.stop()
                return 2
        for name, module in wanted:
            _run_check(game, report, name, module, options)
    except KeyboardInterrupt:
        print("\ninterrupted")
    finally:
        if options.keep_running:
            session.restore()
            print("  ... left running; the world is %s" % session.run_dir)
        else:
            session.stop()

    return report.summarise()


def _wanted(options):
    only = {name for name in options.only.split(",") if name}
    skip = {name for name in options.skip.split(",") if name}
    unknown = (only | skip) - {name for name, _ in CHECKS}
    if unknown:
        raise SystemExit("no such check: %s" % ", ".join(sorted(unknown)))
    return [(name, module) for name, module in CHECKS
            if (not only or name in only) and name not in skip]


def _run_check(game, report, name, module, options):
    expect = report.expect(name, game, screenshots=not options.no_screenshots)
    if getattr(module, "NEEDS_CLIENT", False) and game.window is None:
        expect.skip("the whole check", "it needs a click, and there is no client")
        return
    started = time.monotonic()
    try:
        module.run(game, expect)
    except Exception:
        expect.error("the check itself", traceback.format_exc().strip().splitlines()[-1])
        print(traceback.format_exc(), file=sys.stderr)
    print("           (%s in %.0fs)" % (name, time.monotonic() - started), flush=True)


if __name__ == "__main__":
    sys.exit(main())
