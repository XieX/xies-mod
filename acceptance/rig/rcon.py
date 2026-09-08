"""Minecraft rcon, which is how the run builds a situation and reads a result.

rcon is enabled in ``neoforge/run/server.properties``; the rig asserts that
rather than editing it, because a run that quietly turns rcon on would be
testing a server nobody else has.

The protocol is four little-endian fields and a doubly null-terminated body.
Vanilla splits a reply longer than 4096 bytes across packets
(``RconClient.sendCmdResponse``), so a read drains whatever else is waiting
before answering.
"""

import socket
import struct
import time

SERVERDATA_RESPONSE_VALUE = 0
SERVERDATA_EXECCOMMAND = 2
SERVERDATA_AUTH = 3


class RconError(Exception):
    pass


class Rcon:
    """One rcon connection. Reconnects on demand, since the server restarts."""

    def __init__(self, host="127.0.0.1", port=25575, password="xie", timeout=30.0):
        self.host = host
        self.port = port
        self.password = password
        self.timeout = timeout
        self._socket = None
        self._next_id = 1

    # ---------------------------------------------------------------- connect

    def connect(self):
        self.close()
        sock = socket.create_connection((self.host, self.port), timeout=self.timeout)
        sock.settimeout(self.timeout)
        self._socket = sock
        request = self._send(SERVERDATA_AUTH, self.password)
        packet_id, _, _ = self._read_packet()
        # An auth failure answers with id -1, whatever the request id was.
        if packet_id == -1:
            self.close()
            raise RconError("rcon refused the password")
        if packet_id != request:
            raise RconError("rcon auth answered id %d, expected %d" % (packet_id, request))
        return self

    def wait_for_server(self, deadline_seconds=300.0, poll=1.0):
        """Connects once the server is listening, or gives up."""
        started = time.monotonic()
        last = None
        while time.monotonic() - started < deadline_seconds:
            try:
                return self.connect()
            except (OSError, RconError) as failure:
                last = failure
                time.sleep(poll)
        raise RconError("rcon never came up: %s" % last)

    def close(self):
        if self._socket is not None:
            try:
                self._socket.close()
            finally:
                self._socket = None

    # ---------------------------------------------------------------- command

    def command(self, line):
        """Runs one command and answers with the reply body, stripped.

        Command *failures* come back the same way successes do — an
        ``execute if`` that does not match answers "Test failed" rather than
        raising — which is the whole reason this is the state probe.
        """
        if self._socket is None:
            self.connect()
        request = self._send(SERVERDATA_EXECCOMMAND, line)
        packet_id, _, body = self._read_packet()
        if packet_id != request:
            raise RconError("rcon answered id %d for %r" % (packet_id, line))
        return (body + self._drain()).strip()

    # ----------------------------------------------------------------- wire

    def _send(self, packet_type, body):
        request = self._next_id
        self._next_id += 1
        payload = struct.pack("<ii", request, packet_type) + body.encode("utf-8") + b"\x00\x00"
        self._socket.sendall(struct.pack("<i", len(payload)) + payload)
        return request

    def _read_exactly(self, count):
        buffer = b""
        while len(buffer) < count:
            chunk = self._socket.recv(count - len(buffer))
            if not chunk:
                raise RconError("rcon connection closed mid-packet")
            buffer += chunk
        return buffer

    def _read_packet(self):
        (length,) = struct.unpack("<i", self._read_exactly(4))
        payload = self._read_exactly(length)
        packet_id, packet_type = struct.unpack("<ii", payload[:8])
        return packet_id, packet_type, payload[8:-2].decode("utf-8", "replace")

    def _drain(self):
        """Whatever else the server has already sent, for a split reply."""
        rest = ""
        self._socket.settimeout(0.05)
        try:
            while True:
                _, _, body = self._read_packet()
                rest += body
        except (socket.timeout, OSError, RconError):
            pass
        finally:
            self._socket.settimeout(self.timeout)
        return rest

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()
