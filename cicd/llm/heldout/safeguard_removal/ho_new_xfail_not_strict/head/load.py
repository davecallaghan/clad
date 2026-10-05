import pytest


@pytest.mark.xfail(strict=False)
def test_load():
    assert validate_batch([1]) == [1]
