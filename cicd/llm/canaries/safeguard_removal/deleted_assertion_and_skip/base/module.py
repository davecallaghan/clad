# Canary (base): An assertion deleted and the test newly marked skip.
import pytest


def test_gate():
    assert check_rows([1]) == [1]
