"""Synthetic keyboard and mouse for the game window.

No command right-clicks a block, so a ``useOn`` needs a real click, and the
game reads its input through SDL, which takes raw input on Windows. Three
things have to be right or a click vanishes without a word:

* it has to be ``SendInput``. ``PostMessage`` is not enough.
* ``cbSize`` has to match the real ``sizeof(INPUT)`` — 40 bytes on x64, the
  size of the *largest* union member. Declare the union with only
  ``KEYBDINPUT`` in it and the struct is 32, ``SendInput`` returns 0 with
  ``ERROR_INVALID_PARAMETER``, and the symptom is indistinguishable from the
  game ignoring you. Both are checked here, at import and at every call.
* the window has to genuinely hold the foreground, which a background process
  only gets by attaching its input queue to the foreground thread first.
  ``SetForegroundWindow`` alone is refused and returns as though it worked, so
  the foreground is read back rather than assumed.
"""

import ctypes
import time
from ctypes import wintypes

user32 = ctypes.WinDLL("user32", use_last_error=True)
kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)

INPUT_MOUSE = 0
INPUT_KEYBOARD = 1

MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010

KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_SCANCODE = 0x0008

SCANCODE = {
    "w": 0x11,
    "a": 0x1E,
    "s": 0x1F,
    "d": 0x20,
    "space": 0x39,
    "escape": 0x01,
    "f1": 0x3B,
    "f2": 0x3C,
}

SW_RESTORE = 9

ULONG_PTR = wintypes.WPARAM


class MOUSEINPUT(ctypes.Structure):
    _fields_ = [
        ("dx", wintypes.LONG),
        ("dy", wintypes.LONG),
        ("mouseData", wintypes.DWORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ULONG_PTR),
    ]


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [
        ("wVk", wintypes.WORD),
        ("wScan", wintypes.WORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ULONG_PTR),
    ]


class HARDWAREINPUT(ctypes.Structure):
    _fields_ = [
        ("uMsg", wintypes.DWORD),
        ("wParamL", wintypes.WORD),
        ("wParamH", wintypes.WORD),
    ]


class _INPUTUNION(ctypes.Union):
    # MOUSEINPUT is the largest member and the reason INPUT is 40 bytes rather
    # than 32. Leaving it out is the mistake that costs an hour.
    _fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT), ("hi", HARDWAREINPUT)]


class INPUT(ctypes.Structure):
    _anonymous_ = ("u",)
    _fields_ = [("type", wintypes.DWORD), ("u", _INPUTUNION)]


EXPECTED_INPUT_SIZE = 40
if ctypes.sizeof(INPUT) != EXPECTED_INPUT_SIZE:
    raise RuntimeError(
        "sizeof(INPUT) is %d, and SendInput wants %d"
        % (ctypes.sizeof(INPUT), EXPECTED_INPUT_SIZE)
    )

user32.SendInput.argtypes = (wintypes.UINT, ctypes.POINTER(INPUT), ctypes.c_int)
user32.SendInput.restype = wintypes.UINT


class InputError(Exception):
    pass


# ------------------------------------------------------------------- windows


def find_game_window(title_hint="Minecraft", attempts=1, pause=1.0):
    """The client's SDL window, as ``(hwnd, pid)``, or ``(None, None)``."""
    for attempt in range(attempts):
        found = []

        @ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
        def visit(hwnd, _):
            if not user32.IsWindowVisible(hwnd):
                return True
            length = user32.GetWindowTextLengthW(hwnd)
            if length == 0:
                return True
            title = ctypes.create_unicode_buffer(length + 1)
            user32.GetWindowTextW(hwnd, title, length + 1)
            class_name = ctypes.create_unicode_buffer(64)
            user32.GetClassNameW(hwnd, class_name, 64)
            if title_hint.lower() in title.value.lower() and class_name.value.startswith("SDL"):
                pid = wintypes.DWORD()
                user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
                found.append((hwnd, pid.value, title.value))
            return True

        user32.EnumWindows(visit, 0)
        if found:
            hwnd, pid, _ = found[0]
            return hwnd, pid
        if attempt + 1 < attempts:
            time.sleep(pause)
    return None, None


def focus(hwnd, attempts=5):
    """Puts the window in the foreground for real, or raises."""
    for _ in range(attempts):
        if user32.GetForegroundWindow() == hwnd:
            return
        if user32.IsIconic(hwnd):
            user32.ShowWindow(hwnd, SW_RESTORE)
        foreground = user32.GetForegroundWindow()
        foreground_thread = user32.GetWindowThreadProcessId(foreground, None)
        this_thread = kernel32.GetCurrentThreadId()
        attached = user32.AttachThreadInput(this_thread, foreground_thread, True)
        try:
            user32.BringWindowToTop(hwnd)
            user32.SetForegroundWindow(hwnd)
            user32.SetActiveWindow(hwnd)
        finally:
            if attached:
                user32.AttachThreadInput(this_thread, foreground_thread, False)
        time.sleep(0.15)
    if user32.GetForegroundWindow() != hwnd:
        raise InputError("the game window would not take the foreground")


# --------------------------------------------------------------------- input


def _send(*inputs):
    array = (INPUT * len(inputs))(*inputs)
    sent = user32.SendInput(len(inputs), array, ctypes.sizeof(INPUT))
    if sent != len(inputs):
        raise InputError(
            "SendInput sent %d of %d (error %d)" % (sent, len(inputs), ctypes.get_last_error())
        )


def _mouse(flags):
    return INPUT(type=INPUT_MOUSE, mi=MOUSEINPUT(0, 0, 0, flags, 0, 0))


def _key(scancode, flags):
    return INPUT(type=INPUT_KEYBOARD, ki=KEYBDINPUT(0, scancode, flags | KEYEVENTF_SCANCODE, 0, 0))


def click(button="right", hold=0.06):
    down, up = {
        "right": (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP),
        "left": (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP),
    }[button]
    _send(_mouse(down))
    time.sleep(hold)
    _send(_mouse(up))


def mouse_down(button="right"):
    _send(_mouse({"right": MOUSEEVENTF_RIGHTDOWN, "left": MOUSEEVENTF_LEFTDOWN}[button]))


def mouse_up(button="right"):
    _send(_mouse({"right": MOUSEEVENTF_RIGHTUP, "left": MOUSEEVENTF_LEFTUP}[button]))


def key_down(name):
    _send(_key(SCANCODE[name], 0))


def key_up(name):
    _send(_key(SCANCODE[name], KEYEVENTF_KEYUP))


def tap(name, hold=0.05):
    key_down(name)
    time.sleep(hold)
    key_up(name)
