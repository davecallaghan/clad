"""openFDA: current label content and structured metadata.

openFDA supplies the *current* version of each label with structured fields
(effective_time, version, set_id) and the label body split into named sections.
It does not supply superseded versions -- see dailymed.py. The division of labour
is deliberate: structured current metadata here, history and superseded text there.
"""
from common import fda

# Label sections the case families draw on.
SECTIONS = ("indications_and_usage", "purpose", "dosage_and_administration",
            "pediatric_use", "geriatric_use", "use_in_specific_populations",
            "contraindications", "warnings", "warnings_and_cautions")


def by_substance(substance, limit=50):
    """Current labels for one active substance."""
    q = f'openfda.substance_name:"{substance}"'
    try:
        d = fda("label", search=q, limit=limit)
    except Exception:
        return []
    return [_flatten(r) for r in d.get("results", [])]


def _flatten(r):
    of = r.get("openfda", {})
    rec = {
        "set_id": r.get("set_id"),
        "spl_id": r.get("id"),
        "version": r.get("version"),
        "effective_time": r.get("effective_time"),
        "manufacturer": (of.get("manufacturer_name") or [None])[0],
        "brand_name": (of.get("brand_name") or [None])[0],
        "generic_name": (of.get("generic_name") or [None])[0],
        "product_type": (of.get("product_type") or [None])[0],
        "route": (of.get("route") or [None])[0],
        "substance": (of.get("substance_name") or [None])[0],
    }
    for s in SECTIONS:
        v = r.get(s)
        rec[s] = " ".join(v) if isinstance(v, list) else v
    return rec


def has_paediatric_distinction(rec):
    """True when the label draws an explicit age-based dosing or use distinction.

    This is the ground truth for the population family: the label itself states
    that the adult instruction does not govern a paediatric case.
    """
    hay = " ".join(str(rec.get(k) or "") for k in
                   ("dosage_and_administration", "pediatric_use",
                    "use_in_specific_populations", "warnings")).lower()
    return any(t in hay for t in ("children", "pediatric", "paediatric",
                                  "years of age", "under 12", "under 2"))
