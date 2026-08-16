#!/usr/bin/env python3
"""Independent policy invariants for the A14 brutal test suite."""
from __future__ import annotations


EXPECTED_SCHEMA_VERSION = 2

REQUIRED_COVERAGE_TARGET = 74

REQUIRED_INDEPENDENT_KILLS = 11

REQUIRED_INDEPENDENT_MUTATIONS = frozenset({
    "duplicate SMART key",
    "symbolic verified tree",
    "ambiguous resume path",
    "TASK state drift",
    "generated progress tamper",
    "remove fetch-depth",
    "wrong CI branch",
    "signing leak",
    "Windows-only path",
    "duplicate Feature ID",
    "swallow fatal",
})

KNOWN_LEGACY_LEDGER_IDS = frozenset({
    "fake CI PASS",
    "hardcoded API 37 package",
    "preference default flip",
    "process scope flip",
    "install phase flip",
    "remove Installer dispatch",
    "static Context leak",
    "eager HandlerThread",
})

FORBIDDEN_KILL_GATES = frozenset({"a14_contract"})

VALID_COVERAGE_STATUSES = frozenset({
    "ACTIVE_INDEPENDENT",
    "BLOCKED_NO_INDEPENDENT_GATE",
    "MUTATOR_STALE",
})
