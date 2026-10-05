"""write_gate library (spec Gate 2): the behaviours that killed the original write gate."""

from __future__ import annotations

import inspect

import pytest

from cicd.llm import governed
from cicd.llm.governed import (CanaryFailure, ContractInexpressible, MissingGovernedField,
                               write_gate)

CONTRACT = {"targets": {"findings": {
    "ungraded_marker": "ungraded",
    "fields": {
        "severity": {"domain": ["high", "medium", "low"], "origins": ["model_emitted"]},
        "finding_kind": {"domain": ["red_flag", "gap"], "origins": ["model_emitted"]},
    }}}}


def row(**over):
    base = {"id": 1, "severity": "high", "finding_kind": "gap"}
    base.update(over)
    return base


def test_contract_is_a_required_positional_parameter_without_default():
    """Asserting the signature, not one call: a static autofix cannot rewrite this
    (reversion, Group 1 item 4)."""
    p = inspect.signature(write_gate).parameters["contract"]
    assert p.default is inspect.Parameter.empty
    assert p.kind in (inspect.Parameter.POSITIONAL_ONLY, inspect.Parameter.POSITIONAL_OR_KEYWORD)


def test_in_domain_rows_are_accepted():
    out = write_gate([row(), row(severity="ungraded")], CONTRACT, "findings")
    assert len(out.accepted) == 2 and out.quarantined == []


def test_out_of_domain_value_is_quarantined_and_returned():
    bad = row(severity="critical")
    out = write_gate([row(), bad], CONTRACT, "findings")
    assert out.quarantined == [bad]
    assert out.accepted == [row()]


def test_field_name_mismatch_fails_loudly():
    """finding_kind configured, finding_type carried: the original silently skipped."""
    mismatched = {"id": 1, "severity": "high", "finding_type": "gap"}
    with pytest.raises(MissingGovernedField, match="finding_kind"):
        write_gate([mismatched], CONTRACT, "findings")


def test_empty_domain_refuses_to_run():
    c = {"targets": {"t": {"fields": {"severity": {"domain": []}}}}}
    with pytest.raises(ContractInexpressible, match="empty enumeration"):
        write_gate([row()], c, "t")


def test_no_governed_fields_refuses_to_run():
    with pytest.raises(ContractInexpressible):
        write_gate([row()], {"targets": {"t": {"fields": {}}}}, "t")


def test_unknown_target_refuses_to_run():
    with pytest.raises(ContractInexpressible, match="not in the contract"):
        write_gate([row()], CONTRACT, "elsewhere")


def test_planted_rows_are_never_returned():
    out = write_gate([row()], CONTRACT, "findings")
    planted = [r for r in out.accepted + out.quarantined
               if any(isinstance(v, tuple) for v in r.values())]
    assert planted == []


def test_canary_mirrors_the_callers_row(monkeypatch):
    seen = []
    real = governed._out_of_domain

    def spy(spec, r):
        seen.append(set(r))
        return real(spec, r)

    monkeypatch.setattr(governed, "_out_of_domain", spy)
    write_gate([row(extra="x")], CONTRACT, "findings")
    assert {"id", "severity", "finding_kind", "extra"} in seen


def test_canary_fails_when_every_planted_value_passes(monkeypatch):
    monkeypatch.setattr(governed, "allowed_values", lambda spec, name: _Everything())
    with pytest.raises(CanaryFailure, match="entirely"):
        write_gate([row()], CONTRACT, "findings")


def test_canary_fails_when_detection_is_partial(monkeypatch):
    real = governed.allowed_values
    monkeypatch.setattr(governed, "allowed_values",
                        lambda spec, name: _Everything() if name == "finding_kind" else real(spec, name))
    with pytest.raises(CanaryFailure, match="partially"):
        write_gate([row()], CONTRACT, "findings")


def test_planted_value_is_derived_from_the_contract_not_hardcoded():
    spec = CONTRACT["targets"]["findings"]
    v = governed.planted_value(spec, "severity")
    assert v not in governed.allowed_values(spec, "severity")
    assert tuple(governed.allowed_values(spec, "severity")) in v


class _Everything(list):
    """A domain that contains every value: what a broken membership check looks like."""

    def __contains__(self, item):
        return True

    def __getitem__(self, i):
        return "high"
