"""Pass and fail, one line each, and a summary at the end."""

import time

PASS = "PASS"
FAIL = "FAIL"
ERROR = "ERROR"
SKIP = "SKIP"


class Result:
    def __init__(self, check, label, outcome, detail=""):
        self.check = check
        self.label = label
        self.outcome = outcome
        self.detail = detail

    def __str__(self):
        line = "[ %-5s ] %s: %s" % (self.outcome, self.check, self.label)
        # Plain ASCII: a Windows console is cp1252 and turns anything else into
        # a question mark in the middle of the thing you are reading.
        return line + ("  - %s" % self.detail if self.detail else "")


class Expect:
    """What a check reports through. One instance per check."""

    def __init__(self, check, results, game=None, screenshots=True):
        self.check = check
        self.results = results
        self.game = game
        self.screenshots = screenshots
        self._shot_taken = False

    def that(self, label, condition, detail=""):
        outcome = PASS if condition else FAIL
        self._record(Result(self.check, label, outcome, detail))
        return bool(condition)

    def equal(self, label, actual, expected, detail=""):
        matched = actual == expected
        said = "got %r, expected %r" % (actual, expected)
        return self.that(label, matched, said if not matched else (detail or said))

    def close_to(self, label, actual, expected, tolerance, detail=""):
        near = abs(actual - expected) <= tolerance
        said = "%.3f, expected %.3f +/- %.3f" % (actual, expected, tolerance)
        return self.that(label, near, detail or said)

    def skip(self, label, why):
        self._record(Result(self.check, label, SKIP, why))
        return False

    def error(self, label, detail):
        self._record(Result(self.check, label, ERROR, detail))
        return False

    def note(self, message):
        print("           %s" % message, flush=True)

    def _record(self, result):
        if result.outcome in (FAIL, ERROR) and self.screenshots and self.game and not self._shot_taken:
            self._shot_taken = True
            shot = self.game.screenshot()
            if shot:
                result.detail = (result.detail + "  " if result.detail else "") + "[%s]" % shot
        self.results.append(result)
        print(result, flush=True)


class Report:
    def __init__(self):
        self.results = []
        self.started = time.monotonic()

    def expect(self, check, game=None, screenshots=True):
        return Expect(check, self.results, game, screenshots)

    def counted(self, outcome):
        return sum(1 for result in self.results if result.outcome == outcome)

    @property
    def failed(self):
        return self.counted(FAIL) + self.counted(ERROR)

    def summarise(self):
        print()
        print("=" * 72)
        if self.failed:
            print("Failures:")
            for result in self.results:
                if result.outcome in (FAIL, ERROR):
                    print("  %s" % result)
        print("%d assertions in %.0fs: %d passed, %d failed, %d errored, %d skipped"
              % (len(self.results), time.monotonic() - self.started, self.counted(PASS),
                 self.counted(FAIL), self.counted(ERROR), self.counted(SKIP)))
        print("=" * 72)
        return 0 if self.failed == 0 else 1
