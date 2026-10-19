"""Synthetic validator tests; no fictional products are written to the real catalog."""
import copy
import hashlib
import json
import sys
from datetime import datetime
from pathlib import Path

sys.dont_write_bytecode = True
from validate_normalized import load, validate

BASE = Path(__file__).parent
NOW = datetime.fromisoformat("2026-10-09T22:00:00+08:00")


def synthetic():
    """Invented complete-product fixture for validation, never a business acceptance sample."""
    s = {"url": "https://example.invalid/synthetic-only", "title": "SYNTHETIC, not a manufacturer source", "collectedAt": "2026-10-09T20:00:00+08:00", "verificationStatus": "VERIFIED", "evidenceRef": "synthetic-memory-only", "note": "Fictional validator positive control"}
    p = {"key": "SYNTHETIC-P", "legacyEquipmentId": None, "sourceRecordId": "SYNTHETIC-P", "category": "COMPLETE", "brand": "SYNTHETIC", "model": "not a real product", "active": True, "description": "validator only", "suitabilityTags": [], "levelTags": [], "sources": [s], "imageSource": None, "imageLicenseStatus": "UNAUTHORIZED"}
    v = {"key": "SYNTHETIC-V", "productKey": p["key"], "label": "synthetic complete", "active": True, "specs": {"deckWidthInch": None, "manufacturerComplete": True}, "rawSpec": {}, "sources": [s], "verificationStatus": "VERIFIED"}
    q = {"key": "SYNTHETIC-Q", "variantKey": v["key"], "version": 1, "currency": "CNY", "amountCents": 10010, "displayPrice": "100.10 CNY SYNTHETIC", "priceKind": "EXACT", "market": "CN", "purchaseScope": "SYNTHETIC one complete product", "packageQuantity": 1, "packageUnit": "COMPLETE", "shippingCents": 0, "taxCents": 0, "landedPriceConfirmed": True, "collectedAt": "2026-10-09T20:00:00+08:00", "validUntil": "2026-10-10T00:00:00+08:00", "verificationStatus": "VERIFIED", "active": True, "sources": [s]}
    return {"schemaVersion": 1, "datasetVersion": "synthetic-v1", "products": [p], "variants": [v], "quotes": [q], "rules": [], "templates": [], "mapping": [], "gaps": []}


def main():
    preserved = [BASE / name for name in ["equipment.json", "equipment.csv", "db-seed-skate-equipment.sql", "sources.md", "normalized-equipment.v1.json"]]
    before = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in preserved}
    real = load(BASE / "normalized-equipment.v1.json")
    original = load(BASE / "equipment.json")
    result = validate(real, as_of=NOW, original=original)
    assert result["valid"], result["errors"]
    assert result["stats"]["eligibleExactCnQuotes"] == 0
    assert not any(result["stats"]["active"].values())
    assert validate(synthetic(), as_of=NOW)["stats"]["eligibleExactCnQuotes"] == 1
    assert validate(real, as_of=NOW, original=original, previous=copy.deepcopy(real))["valid"]
    # Catch fraction parsing/rounding bugs: preserve raw text, convert exact inches.
    risers = [v["specs"]["heightMm"] for v in real["variants"] if v["productKey"] == "RSR-001"]
    assert risers == [3.175, 6.35, 12.7], risers
    assert next(q["amountCents"] for q in real["quotes"] if q["key"] == "BRG-001--cn-price") == 10010
    cases = []

    def reject(name, mutate, expected, *, base=None, previous=None, check_original=False):
        data = copy.deepcopy(base if base is not None else synthetic())
        mutate(data)
        output = validate(data, as_of=NOW, original=original if check_original else None, previous=previous)
        codes = {e["code"] for e in output["errors"]}
        assert not output["valid"] and expected in codes, (name, output)
        cases.append({"case": name, "rejectedBy": expected})

    reject("duplicate key", lambda d: d["products"].append(copy.deepcopy(d["products"][0])), "DUPLICATE_KEY")
    reject("dangling product", lambda d: d["variants"][0].update(productKey="MISSING"), "DANGLING_REFERENCE")
    reject("dangling quote variant", lambda d: d["quotes"][0].update(variantKey="MISSING"), "DANGLING_REFERENCE")
    reject("unbound active quote", lambda d: d["quotes"][0].update(variantKey=None), "ACTIVE_QUOTE_VARIANT")
    for value in [100.1, True, -1, 2**63]:
        reject("invalid cents " + repr(value), lambda d, x=value: d["quotes"][0].update(amountCents=x), "INVALID_CENTS")
    reject("range active", lambda d: d["quotes"][0].update(priceKind="RANGE", amountCents=None), "ACTIVE_QUOTE_MARKET")
    reject("from active", lambda d: d["quotes"][0].update(priceKind="FROM", amountCents=None), "ACTIVE_QUOTE_MARKET")
    reject("range exact cents", lambda d: d["quotes"][0].update(priceKind="RANGE"), "NON_EXACT_AMOUNT")
    reject("missing package", lambda d: d["quotes"][0].update(packageQuantity=None), "ACTIVE_QUOTE_PACKAGE")
    reject("zero package", lambda d: d["quotes"][0].update(packageQuantity=0), "INVALID_PACKAGE")
    reject("unknown package unit", lambda d: d["quotes"][0].update(packageUnit="SET"), "ACTIVE_QUOTE_PACKAGE")
    reject("missing expiry", lambda d: d["quotes"][0].update(validUntil=None), "ACTIVE_QUOTE_TIME")
    reject("expired", lambda d: d["quotes"][0].update(validUntil="2026-10-09T21:59:59+08:00"), "ACTIVE_QUOTE_TIME")
    reject("timezone missing", lambda d: d["quotes"][0].update(validUntil="2026-10-10T00:00:00"), "INVALID_TIME")
    reject("future collection", lambda d: d["quotes"][0].update(collectedAt="2026-10-09T23:00:00+08:00"), "ACTIVE_QUOTE_TIME")
    reject("missing source", lambda d: d["quotes"][0].update(sources=[]), "MISSING_SOURCE")
    reject("missing field evidence", lambda d: d["quotes"][0]["sources"][0].update(evidenceRef=None), "MISSING_EVIDENCE")
    reject("recorded not verified", lambda d: d["quotes"][0].update(verificationStatus="RECORDED"), "UNVERIFIED_ACTIVE")
    reject("source not verified", lambda d: d["quotes"][0]["sources"][0].update(verificationStatus="RECORDED"), "UNVERIFIED_ACTIVE")
    reject("transcription is not field evidence", lambda d: d["quotes"][0]["sources"][0].update(evidenceRef="equipment.json#/records/0/cn_price"), "TRANSCRIPTION_NOT_EVIDENCE")
    reject("unknown landed price", lambda d: d["quotes"][0].update(landedPriceConfirmed=False), "ACTIVE_QUOTE_COSTS")
    reject("unknown shipping", lambda d: d["quotes"][0].update(shippingCents=None), "ACTIVE_QUOTE_COSTS")
    reject("unknown tax", lambda d: d["quotes"][0].update(taxCents=None), "ACTIVE_QUOTE_COSTS")
    reject("overseas CNY", lambda d: d["quotes"][0].update(market="OVERSEAS"), "ACTIVE_QUOTE_MARKET")
    reject("USD CN", lambda d: d["quotes"][0].update(currency="USD"), "ACTIVE_QUOTE_MARKET")
    reject("missing manufacturer fact", lambda d: d["variants"][0]["specs"].update(manufacturerComplete=None), "COMPLETE_FACT_MISSING")
    reject("unknown hardness scale", lambda d: d["variants"][0]["specs"].update(hardnessScale="AB"), "INVALID_HARDNESS_SCALE")
    reject("same dataset version different content", lambda d: d["quotes"][0].update(amountCents=10011), "SAME_VERSION_DIFFERENT_CONTENT", previous=synthetic())
    reject("same quote version different content", lambda d: (d.update(datasetVersion="synthetic-v2"), d["quotes"][0].update(amountCents=10011)), "SAME_VERSION_DIFFERENT_CONTENT", previous=synthetic())
    newer = synthetic(); newer["quotes"][0]["version"] = 2
    reject("older quote version", lambda d: d.update(datasetVersion="synthetic-v2"), "OLDER_VERSION", previous=newer)
    reject("source mapping missing", lambda d: d["mapping"].pop(), "SOURCE_COVERAGE", base=real, check_original=True)
    reject("source hash changed", lambda d: d["mapping"][0].update(sourceRecordSha256="0"*64), "SOURCE_RECORD_CHANGED", base=real, check_original=True)
    reject("split variant mapping incomplete", lambda d: d["mapping"][0].update(variantKeys=[]), "MAPPING_VARIANT_COVERAGE", base=real)
    reject("source price changed", lambda d: d["quotes"][0].update(amountCents=1), "SOURCE_PRICE_CHANGED", base=real, check_original=True)
    def swap_quote_mappings(d):
        first = next(m for m in d["mapping"] if m["sourceRecordId"] == "DECK-001")
        second = next(m for m in d["mapping"] if m["sourceRecordId"] == "DECK-002")
        first["quoteKeys"], second["quoteKeys"] = second["quoteKeys"], first["quoteKeys"]

    reject("cross-product quote mapping swap", swap_quote_mappings, "MAPPING_QUOTE_MISMATCH", base=real)
    reject("quote mapping differs from original record", swap_quote_mappings, "MAPPING_QUOTE_COVERAGE", base=real, check_original=True)
    def misattribute_display_quote(d):
        quote = next(q for q in d["quotes"] if q["key"] == "DECK-003--cn-price")
        assert quote["variantKey"] is None
        quote["sourceProductKey"] = "DECK-001"

    reject("unbound display quote cross-product source", misattribute_display_quote, "MAPPING_QUOTE_MISMATCH", base=real)
    reject("unbound display quote original identity mismatch", misattribute_display_quote, "SOURCE_QUOTE_MISMATCH", base=real, check_original=True)
    reject("bound quote variant cross-product", lambda d: next(q for q in d["quotes"] if q["key"] == "DECK-001--overseas-price").update(variantKey=next(v["key"] for v in d["variants"] if v["productKey"] == "DECK-002")), "MAPPING_QUOTE_MISMATCH", base=real, check_original=True)
    reject("display quote source identity missing", lambda d: next(q for q in d["quotes"] if q["key"] == "DECK-003--cn-price").pop("sourceProductKey"), "SOURCE_QUOTE_MISMATCH", base=real, check_original=True)
    reject("Mid Standard mismatch", lambda d: next(q for q in d["quotes"] if q["key"]=="TRUCK-001--cn-price").update(variantKey=next(v["key"] for v in d["variants"] if v["productKey"]=="TRUCK-001")), "MODEL_MISMATCH", base=real, check_original=True)
    reject("unknown rule type", lambda d: d["rules"][0].update(type="EVAL"), "INVALID_RULE_TYPE", base=real)
    reject("script forbidden", lambda d: d["rules"][0]["conditions"].update(script="pass()"), "SCRIPT_FORBIDDEN", base=real)
    reject("rule reference missing", lambda d: d["rules"][0]["scope"].update(variantKeys=["MISSING"]), "DANGLING_REFERENCE", base=real)
    reject("template reference missing", lambda d: d["templates"][0]["selectionConditions"].update(ruleKeys=["MISSING"]), "DANGLING_REFERENCE", base=real)
    reject("template needs source conditions", lambda d: d["templates"][0].update(active=True), "TEMPLATE_SOURCE_PENDING", base=real)
    reject("draft spec cannot activate", lambda d: d["variants"][0].update(active=True), "UNVERIFIED_ACTIVE", base=real)
    usage = synthetic()
    usage["rules"] = [{"key": "SYNTHETIC-USAGE", "version": 1, "type": "USAGE_ADVICE", "severity": "ADVICE", "scope": {"productKeys": [], "variantKeys": ["SYNTHETIC-V"], "construction": None}, "conditions": {"purpose": "ACTION", "preference": "COMFORT", "variantKeys": ["SYNTHETIC-V"], "message": "synthetic sourced preference"}, "outcome": {"state": "ADVICE", "message": "synthetic only"}, "explanation": "synthetic only", "sources": copy.deepcopy(usage["products"][0]["sources"]), "verificationStatus": "VERIFIED", "active": True}]
    assert validate(usage, as_of=NOW)["valid"]
    reject("invalid usage preference", lambda d: d["rules"][0]["conditions"].update(preference="UNKNOWN"), "INVALID_PREFERENCE", base=usage)
    reject("script in usage facts", lambda d: d["rules"][0]["conditions"].update(expression="message()"), "UNKNOWN_RULE_CONDITION", base=usage)
    usage["rules"][0]["conditions"].pop("preference")
    assert validate(usage, as_of=NOW)["valid"]
    # Read-only means all input bytes, including original exports/logs, stay identical.
    assert before == {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in preserved}
    assert not (BASE / "__pycache__").exists(), "test imports must not write bytecode"
    print(json.dumps({"positiveControls": 7, "rejectedNegativeCases": len(cases), "cases": cases, "inputHashesUnchanged": True, "syntheticOnly": True}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
