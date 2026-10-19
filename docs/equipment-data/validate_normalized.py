"""Read-only v1 validation. No exports, network, database or bytecode writes."""
import argparse
import hashlib
import json
import re
from collections import Counter
from datetime import date, datetime, timezone
from decimal import Decimal
from pathlib import Path

CATEGORIES = set("DECK TRUCK WHEEL BEARING GRIP HARDWARE RISER COMPLETE".split())
STATUSES = {"RECORDED", "VERIFIED", "INSUFFICIENT"}
RULE_TYPES = set("DECK_TRUCK_FIT WHEEL_TRUCK_RISER RISER_HARDWARE_LENGTH GRIP_COVERAGE COMPLETENESS USAGE_ADVICE".split())
FIELDS = {
    "products": "key legacyEquipmentId sourceRecordId category brand model active description suitabilityTags levelTags sources imageSource imageLicenseStatus",
    "variants": "key productKey label active specs rawSpec sources verificationStatus",
    "quotes": "key variantKey version currency amountCents displayPrice priceKind market purchaseScope packageQuantity packageUnit shippingCents taxCents landedPriceConfirmed collectedAt validUntil verificationStatus active sources",
    "rules": "key version type severity scope conditions outcome explanation sources verificationStatus active",
    "templates": "key version purpose requiredCategories selectionConditions reasonCodes sources verificationStatus active",
    "mapping": "sourceRecordId sourcePath productKey variantKeys quoteKeys ruleKeys basis sourceRecordSha256",
    "gaps": "key entityKey code fields note",
}
SPEC_FIELDS = {
    "DECK": "widthInch lengthInch wheelbaseInch",
    "TRUCK": "sizeCode construction axleWidthInch heightMm deckMinInch deckMaxInch",
    "WHEEL": "diameterMm hardnessValue hardnessScale widthMm contactPatchMm shape",
    "BEARING": "standard", "GRIP": "widthInch lengthInch",
    "HARDWARE": "lengthMm headType nutsIncluded", "RISER": "heightMm",
    "COMPLETE": "deckWidthInch manufacturerComplete",
}
MAX_LONG = 2**63 - 1


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def integer(value, minimum=0):
    return type(value) is int and minimum <= value <= MAX_LONG


def reference(value):
    return value.get("pricingMode") == "REFERENCE"


def recorded_sources(value):
    values = value.get("sources")
    return isinstance(values, list) and bool(values) and all(isinstance(s, dict) and s.get("verificationStatus") in ("RECORDED", "VERIFIED") for s in values)


def timestamp(value):
    if not isinstance(value, str):
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return parsed if "T" in value and parsed.utcoffset() is not None else None
    except ValueError:
        return None


def validate(dataset, *, as_of=None, original=None, previous=None):
    """Return structural errors separately from recorded data gaps/eligibility."""
    now = as_of or datetime.now(timezone.utc)
    errors = []

    def fail(path, code, detail):
        errors.append({"path": path, "code": code, "detail": detail})

    def require(obj, fields, path):
        for field in fields.split():
            if field not in obj:
                fail(path, "MISSING_FIELD", field)

    def key_ok(value):
        return isinstance(value, str) and bool(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,199}", value))

    def sources(obj, path):
        values = obj.get("sources")
        if not isinstance(values, list) or not values:
            fail(path, "MISSING_SOURCE", "sources must be a nonempty list")
            return False
        verified = True
        for i, s in enumerate(values):
            sp = f"{path}.sources[{i}]"
            if not isinstance(s, dict):
                fail(sp, "INVALID_SOURCE", "expected object")
                verified = False
                continue
            require(s, "url title collectedAt verificationStatus evidenceRef note", sp)
            if s.get("verificationStatus") not in STATUSES:
                fail(sp, "INVALID_STATUS", "source status")
            if not s.get("title") or not s.get("evidenceRef"):
                fail(sp, "MISSING_EVIDENCE", "title/evidenceRef required even for transcription")
            url = s.get("url")
            if url is not None and (not isinstance(url, str) or not re.match(r"^https?://[^/\s]+", url)):
                fail(sp, "INVALID_SOURCE_URL", "http(s) URL or null for local document")
            if s.get("collectedAt") is not None and timestamp(s["collectedAt"]) is None:
                fail(sp, "INVALID_TIME", "timezone ISO8601 required")
            if s.get("collectedDate") is not None:
                try:
                    date.fromisoformat(s["collectedDate"])
                except (TypeError, ValueError):
                    fail(sp, "INVALID_DATE", "YYYY-MM-DD required")
            verified = verified and s.get("verificationStatus") == "VERIFIED"
            if s.get("verificationStatus") == "VERIFIED" and timestamp(s.get("collectedAt")) is None:
                fail(sp, "VERIFIED_SOURCE_TIME_MISSING", "verification needs a timestamp")
            if s.get("verificationStatus") == "VERIFIED" and isinstance(s.get("evidenceRef"), str) and s["evidenceRef"].split("#")[0] in {"equipment.json", "sources.md"}:
                fail(sp, "TRANSCRIPTION_NOT_EVIDENCE", "original record/log alone cannot prove field verification")
        return verified

    if not isinstance(dataset, dict):
        return {"valid": False, "errors": [{"path": "$", "code": "INVALID_ROOT", "detail": "expected object"}], "gaps": [], "stats": {}}
    require(dataset, "schemaVersion datasetVersion products variants quotes rules templates mapping gaps", "$")
    if type(dataset.get("schemaVersion")) is not int or dataset["schemaVersion"] != 1:
        fail("$", "SCHEMA_VERSION", "expected integer 1")
    if not key_ok(dataset.get("datasetVersion")):
        fail("$", "INVALID_VERSION", "stable datasetVersion required")
    if isinstance(dataset.get("products"), list) and len(dataset["products"]) > 1000:
        fail("products", "COUNT_LIMIT", "at most 1000 products")
    if isinstance(dataset.get("variants"), list) and len(dataset["variants"]) > 5000:
        fail("variants", "COUNT_LIMIT", "at most 5000 variants")
    indexes = {}
    lists = {}
    verified_sources = {}
    for group, required in FIELDS.items():
        values = dataset.get(group)
        if not isinstance(values, list):
            fail(group, "INVALID_COLLECTION", "expected array")
            values = []
        lists[group] = values
        indexes[group] = {}
        for i, obj in enumerate(values):
            path = f"{group}[{i}]"
            if not isinstance(obj, dict):
                fail(path, "INVALID_OBJECT", "expected object")
                continue
            require(obj, required, path)
            key = obj.get("sourceRecordId") if group == "mapping" else obj.get("key")
            if not key_ok(key):
                fail(path, "INVALID_KEY", "invalid stable key")
                continue
            if key in indexes[group]:
                fail(path, "DUPLICATE_KEY", key)
            else:
                indexes[group][key] = obj
            if group not in {"mapping", "gaps"}:
                verified_sources[(group, key)] = sources(obj, path)
                if "pricingMode" in obj and obj["pricingMode"] not in ("REFERENCE", "EXACT"):
                    fail(path, "INVALID_PRICING_MODE", "REFERENCE/EXACT or omit")
                if "priceAssumptions" in obj and (not isinstance(obj["priceAssumptions"], list) or any(not isinstance(s, str) for s in obj["priceAssumptions"])):
                    fail(path, "INVALID_ASSUMPTIONS", "string array")
                if type(obj.get("active")) is not bool:
                    fail(path, "INVALID_ACTIVE", "boolean required")
                if group != "products" and obj.get("verificationStatus") not in STATUSES:
                    fail(path, "INVALID_STATUS", "verificationStatus")
                supported = recorded_sources(obj) and obj.get("verificationStatus") in {"RECORDED", "VERIFIED"} if reference(obj) else obj.get("verificationStatus") == "VERIFIED" and verified_sources[(group, key)]
                if group != "products" and obj.get("active") is True and not supported:
                    fail(path, "UNVERIFIED_ACTIVE", "active requires VERIFIED object and field sources")
            if group in {"quotes", "rules", "templates"} and not integer(obj.get("version"), 1):
                fail(path, "INVALID_VERSION", "positive Long version required")
    products = indexes["products"]
    variants = indexes["variants"]
    for key, p in products.items():
        if p.get("category") not in CATEGORIES:
            fail(key, "INVALID_CATEGORY", "category whitelist")
        if p.get("legacyEquipmentId") is not None and not integer(p["legacyEquipmentId"], 1):
            fail(key, "INVALID_LEGACY_ID", "positive id or null")
        for f in ["suitabilityTags", "levelTags"]:
            if not isinstance(p.get(f), list) or not all(isinstance(t, str) for t in p[f]):
                fail(key, "INVALID_TAGS", f)
        if p.get("active") is True and (not recorded_sources(p) if reference(p) else not verified_sources.get(("products", key))):
            fail(key, "UNVERIFIED_ACTIVE", "product source unverified")
    for key, v in variants.items():
        p = products.get(v.get("productKey"))
        if p is None:
            fail(key, "DANGLING_REFERENCE", "productKey")
            continue
        specs = v.get("specs")
        if not isinstance(specs, dict):
            fail(key, "INVALID_SPEC", "expected object")
            continue
        required = SPEC_FIELDS.get(p.get("category"), "")
        require(specs, required, key + ".specs")
        for field, value in specs.items():
            if field.endswith(("Inch", "Mm")) or field == "hardnessValue":
                if value is not None and (type(value) not in {int, float} or not Decimal(str(value)).is_finite() or value <= 0 or value > 100000):
                    fail(key, "INVALID_DIMENSION", field)
        if specs.get("hardnessScale") not in {None, "A", "B"}:
            fail(key, "INVALID_HARDNESS_SCALE", "A/B or null")
        for field in ["nutsIncluded", "manufacturerComplete"]:
            if field in specs and specs[field] is not None and type(specs[field]) is not bool:
                fail(key, "INVALID_SPEC_BOOLEAN", field)
        if v.get("active") is True and reference(v) and not p.get("active"):
            fail(key, "ACTIVE_SPEC_INCOMPLETE", "active parent required")
        if v.get("active") is True and not reference(v):
            # Complete products may be missing internal dimensions; manufacturer fact is required.
            critical = required.split() if p.get("category") != "COMPLETE" else ["manufacturerComplete"]
            if not p.get("active") or any(specs.get(f) is None for f in critical):
                fail(key, "ACTIVE_SPEC_INCOMPLETE", "parent inactive or required facts unknown")
            if p.get("category") == "COMPLETE" and specs.get("manufacturerComplete") is not True:
                fail(key, "COMPLETE_FACT_MISSING", "manufacturer complete must be verified true")
    eligible = []
    for key, q in indexes["quotes"].items():
        vk = q.get("variantKey")
        if vk is not None and vk not in variants:
            fail(key, "DANGLING_REFERENCE", "variantKey")
        if q.get("sourceProductKey") is not None and q["sourceProductKey"] not in products:
            fail(key, "DANGLING_REFERENCE", "sourceProductKey")
        if vk in variants and q.get("sourceProductKey") not in {None, variants[vk].get("productKey")}:
            fail(key, "QUOTE_PRODUCT_MISMATCH", "quoted source product differs from variant")
        for field in ["amountCents", "shippingCents", "taxCents"]:
            if q.get(field) is not None and not integer(q[field]):
                fail(key, "INVALID_CENTS", field + " must be nonnegative integer Long")
        if q.get("packageQuantity") is not None and not integer(q["packageQuantity"], 1):
            fail(key, "INVALID_PACKAGE", "positive integer quantity")
        if type(q.get("landedPriceConfirmed")) is not bool:
            fail(key, "INVALID_LANDED_FLAG", "boolean required")
        if q.get("priceKind") not in {"EXACT", "RANGE", "FROM", "UNKNOWN"} or q.get("market") not in {"CN", "OVERSEAS", "UNKNOWN"}:
            fail(key, "INVALID_PRICE_KIND", "priceKind/market whitelist")
        if not isinstance(q.get("currency"), str) or not re.fullmatch("[A-Z]{3}", q["currency"]):
            fail(key, "INVALID_CURRENCY", "ISO currency code required")
        if q.get("priceKind") == "EXACT" and not integer(q.get("amountCents")):
            fail(key, "EXACT_AMOUNT_MISSING", "exact price needs integer cents")
        if q.get("priceKind") != "EXACT" and q.get("amountCents") is not None:
            fail(key, "NON_EXACT_AMOUNT", "range/from values are display-only, amountCents=null")
        for field in ["collectedAt", "validUntil"]:
            if q.get(field) is not None and timestamp(q[field]) is None:
                fail(key, "INVALID_TIME", field)
        collected, until = timestamp(q.get("collectedAt")), timestamp(q.get("validUntil"))
        if collected and until and until <= collected:
            fail(key, "INVALID_TIME_RANGE", "validUntil must follow collectedAt")
        if q.get("active") is True and reference(q):
            if not products.get(q.get("sourceProductKey"), {}).get("active") or vk is not None and not variants.get(vk, {}).get("active"):
                fail(key, "ACTIVE_QUOTE_VARIANT", "reference needs active product/variant")
            if q.get("priceKind") != "EXACT" or q.get("currency") != "CNY" or q.get("market") != "CN":
                fail(key, "ACTIVE_QUOTE_MARKET", "numeric single CN reference only")
            if not isinstance(q.get("priceAssumptions"), list) or not q["priceAssumptions"] or any(not isinstance(s, str) for s in q["priceAssumptions"]):
                fail(key, "REFERENCE_ASSUMPTIONS_REQUIRED", "estimate scope required")
            if key == "TRUCK-001--cn-price":
                fail(key, "MODEL_MISMATCH", "Mid price cannot apply to Standard reference variants")
        if q.get("active") is True and not reference(q):
            if vk is None or not variants.get(vk, {}).get("active"):
                fail(key, "ACTIVE_QUOTE_VARIANT", "active quote needs active concrete variant")
            if q.get("priceKind") != "EXACT" or q.get("currency") != "CNY" or q.get("market") != "CN":
                fail(key, "ACTIVE_QUOTE_MARKET", "only EXACT CNY CN participates")
            if not integer(q.get("packageQuantity"), 1) or q.get("packageUnit") not in {"PIECE", "BEARING", "WHEEL", "TRUCK", "SHEET", "BOLT_NUT_PAIR", "RISER", "COMPLETE"}:
                fail(key, "ACTIVE_QUOTE_PACKAGE", "quantity and physical unit required")
            if not q.get("purchaseScope") or q.get("landedPriceConfirmed") is not True or any(not integer(q.get(f)) for f in ["shippingCents", "taxCents"]):
                fail(key, "ACTIVE_QUOTE_COSTS", "confirmed purchase scope and shipping/tax required")
            if not collected or not until or until <= now or collected > now:
                fail(key, "ACTIVE_QUOTE_TIME", "valid timezone collection and unexpired validity required")
            if not any(e["path"] == key for e in errors) and q.get("verificationStatus") == "VERIFIED" and verified_sources.get(("quotes", key)):
                eligible.append(key)
    for key, r in indexes["rules"].items():
        if r.get("type") not in RULE_TYPES or r.get("severity") not in {"HARD", "ADVICE"}:
            fail(key, "INVALID_RULE_TYPE", "rule type/severity whitelist")
        scope, cond = r.get("scope"), r.get("conditions")
        if not isinstance(scope, dict) or not isinstance(cond, dict):
            fail(key, "INVALID_RULE_STRUCTURE", "scope/conditions must be objects")
            continue
        def has_script(value):
            if isinstance(value, dict):
                return any(str(k).lower() in {"eval", "script", "expression", "javascript", "python"} or has_script(v) for k, v in value.items())
            if isinstance(value, list):
                return any(has_script(v) for v in value)
            return False
        if has_script(cond):
            fail(key, "SCRIPT_FORBIDDEN", "rules must use structured conditions")
        for f, group in [("variantKeys", "variants"), ("productKeys", "products")]:
            refs = scope.get(f, [])
            if not isinstance(refs, list) or any(not isinstance(x, str) or x not in indexes[group] for x in refs):
                fail(key, "DANGLING_REFERENCE", "scope." + f)
        allowed = {
            "DECK_TRUCK_FIT": {"deckMinInch", "deckMaxInch", "minInclusive", "maxInclusive"},
            "WHEEL_TRUCK_RISER": {"wheelMinMm", "wheelMaxMm", "minInclusive", "maxInclusive", "riserMode", "riserMinMm"},
            "RISER_HARDWARE_LENGTH": {"riserMinMm", "riserMaxMm", "minInclusive", "maxInclusive", "hardwareMinMm", "hardwareMaxMm"},
            "GRIP_COVERAGE": {"compareWidth", "compareLength"},
            "COMPLETENESS": {"requiredQuantities", "hardwareUnit", "riserRequirement"},
            "USAGE_ADVICE": {"purpose", "preference", "variantKeys", "message"},
        }
        if r.get("type") in allowed and set(cond) - allowed[r["type"]]:
            fail(key, "UNKNOWN_RULE_CONDITION", "conditions outside the supported structure")
        if r.get("type") == "USAGE_ADVICE" and cond.get("preference") is not None and cond.get("preference") not in {"EASY_START", "COMFORT", "VALUE", "UNSURE"}:
            fail(key, "INVALID_PREFERENCE", "structured preference whitelist")
        if r.get("active") is True and reference(r):
            if r.get("severity") != ("ADVICE" if r.get("type") == "USAGE_ADVICE" else "HARD"):
                fail(key, "INVALID_RULE_SEVERITY", "hard checks cannot be advice")
            if r.get("type") != "COMPLETENESS" and not scope.get("productKeys") and not scope.get("variantKeys"):
                fail(key, "RULE_SCOPE_MISSING", "no global manufacturer rules")
        if r.get("active") is True and not reference(r):
            if r.get("severity") != ("ADVICE" if r.get("type") == "USAGE_ADVICE" else "HARD"):
                fail(key, "INVALID_RULE_SEVERITY", "hard checks cannot be advice")
            if r.get("type") != "COMPLETENESS" and not scope.get("productKeys") and not scope.get("variantKeys"):
                fail(key, "RULE_SCOPE_MISSING", "no global manufacturer rules")
            if r.get("type") in {"DECK_TRUCK_FIT", "WHEEL_TRUCK_RISER", "RISER_HARDWARE_LENGTH"}:
                scoped_products = set(scope.get("productKeys", []))
                scoped_products.update(indexes["variants"].get(v, {}).get("productKey") for v in scope.get("variantKeys", []))
                if not any(indexes["products"].get(p, {}).get("category") == "TRUCK" for p in scoped_products):
                    fail(key, "RULE_SCOPE_MISSING", "installation rule must explicitly scope a truck")
            if r.get("type") not in allowed:
                fail(key, "RULE_SCHEMA_PENDING", "other executable structures await coordinator source appendix")
            elif r["type"] == "DECK_TRUCK_FIT":
                low, high = cond.get("deckMinInch"), cond.get("deckMaxInch")
                if not scope.get("variantKeys") or not scope.get("construction") or any(type(x) not in {int, float} or not Decimal(str(x)).is_finite() or x <= 0 for x in [low, high]) or (isinstance(low, (int, float)) and isinstance(high, (int, float)) and low > high) or any(type(cond.get(f)) is not bool for f in ["minInclusive", "maxInclusive"]):
                    fail(key, "RULE_RANGE_INCOMPLETE", "explicit structure, finite bounds and endpoint inclusivity required")
            elif r["type"] == "COMPLETENESS" and (cond.get("requiredQuantities") != {"DECK": 1, "TRUCK": 2, "WHEEL": 4, "BEARING": 8, "GRIP": 1, "HARDWARE": 8} or cond.get("hardwareUnit") != "BOLT_NUT_PAIR"):
                fail(key, "COMPLETENESS_INVALID", "contract quantities and bolt/nut pairs required")
            elif r["type"] == "USAGE_ADVICE":
                refs = cond.get("variantKeys")
                if cond.get("purpose") not in {"ACTION", "CRUISE", "BALANCED"} or not isinstance(cond.get("message"), str) or not isinstance(refs, list) or not refs or any(x not in indexes["variants"] for x in refs):
                    fail(key, "RULE_RANGE_INCOMPLETE", "explicit usage facts required")
            elif r["type"] == "GRIP_COVERAGE" and (cond.get("compareWidth") is not True or cond.get("compareLength") is not True):
                fail(key, "RULE_RANGE_INCOMPLETE", "both dimensions required")
            elif r["type"] in {"WHEEL_TRUCK_RISER", "RISER_HARDWARE_LENGTH"}:
                names = ("wheelMinMm", "wheelMaxMm") if r["type"] == "WHEEL_TRUCK_RISER" else ("riserMinMm", "riserMaxMm")
                low, high = [cond.get(f) for f in names]
                zero_ok = r["type"] == "RISER_HARDWARE_LENGTH"
                if any(type(x) not in {int, float} or not Decimal(str(x)).is_finite() or (x < 0 if zero_ok else x <= 0) for x in [low, high]) or isinstance(low, (int, float)) and isinstance(high, (int, float)) and low > high or any(type(cond.get(f)) is not bool for f in ["minInclusive", "maxInclusive"]):
                    fail(key, "RULE_RANGE_INCOMPLETE", "finite ranges and explicit endpoints required")
                if r["type"] == "WHEEL_TRUCK_RISER":
                    height = cond.get("riserMinMm")
                    if cond.get("riserMode") not in {"REQUIRED", "OPTIONAL", "NONE"} or cond.get("riserMode") == "REQUIRED" and (type(height) not in {int, float} or not Decimal(str(height)).is_finite() or height <= 0):
                        fail(key, "RULE_RANGE_INCOMPLETE", "riser mode/height required")
                else:
                    lo, hi = cond.get("hardwareMinMm"), cond.get("hardwareMaxMm")
                    if any(type(x) not in {int, float} or not Decimal(str(x)).is_finite() or x <= 0 for x in [lo, hi]) or isinstance(lo, (int, float)) and isinstance(hi, (int, float)) and lo > hi:
                        fail(key, "RULE_RANGE_INCOMPLETE", "hardware bounds required")
    for key, t in indexes["templates"].items():
        if t.get("purpose") not in {"ACTION_BEGINNER", "CRUISE_BEGINNER", "BALANCED_BEGINNER"}:
            fail(key, "INVALID_TEMPLATE_PURPOSE", "purpose whitelist")
        req, cond = t.get("requiredCategories"), t.get("selectionConditions")
        if not isinstance(req, list) or any(c not in CATEGORIES for c in req) or len(req) != len(set(req)):
            fail(key, "INVALID_TEMPLATE_CATEGORIES", "unique known categories required")
        if not isinstance(cond, dict):
            fail(key, "INVALID_TEMPLATE_CONDITIONS", "expected object")
            continue
        if set(cond) - {"kind", "eligibleVariantKeys", "ruleKeys", "sourceScopePending"}:
            fail(key, "UNKNOWN_TEMPLATE_CONDITION", "unsupported executable conditions")
        for f, group in [("eligibleVariantKeys", "variants"), ("ruleKeys", "rules")]:
            refs = cond.get(f, [])
            if not isinstance(refs, list) or any(not isinstance(x, str) or x not in indexes[group] for x in refs):
                fail(key, "DANGLING_REFERENCE", f)
        if t.get("active") is True and not reference(t) and (cond.get("sourceScopePending") is not False or not cond.get("eligibleVariantKeys") or not cond.get("ruleKeys")):
            fail(key, "TEMPLATE_SOURCE_PENDING", "resolved applicable selection and rules required")
        if t.get("active") is True:
            for field, group in [("eligibleVariantKeys", "variants"), ("ruleKeys", "rules")]:
                if any(not indexes[group].get(ref, {}).get("active") for ref in cond.get(field, []) if isinstance(ref, str)):
                    fail(key, "TEMPLATE_DEPENDENCY_INACTIVE", field)
    for key, m in indexes["mapping"].items():
        if m.get("productKey") not in products or products.get(m.get("productKey"), {}).get("sourceRecordId") != key:
            fail(key, "MAPPING_PRODUCT_MISMATCH", "source/product identity")
        for f, group in [("variantKeys", "variants"), ("quoteKeys", "quotes"), ("ruleKeys", "rules")]:
            refs = m.get(f)
            if not isinstance(refs, list) or any(not isinstance(x, str) or x not in indexes[group] for x in refs):
                fail(key, "DANGLING_REFERENCE", "mapping." + f)
            elif len(refs) != len(set(refs)):
                fail(key, "DUPLICATE_REFERENCE", "mapping." + f)
            elif group == "variants" and any(variants[x].get("productKey") != m.get("productKey") for x in refs):
                fail(key, "MAPPING_PRODUCT_MISMATCH", "variant belongs to another product")
            elif group == "quotes":
                for ref in refs:
                    q = indexes["quotes"][ref]
                    if q.get("sourceProductKey") != m.get("productKey") or (
                        q.get("variantKey") is not None
                        and variants.get(q["variantKey"], {}).get("productKey") != m.get("productKey")
                    ):
                        fail(ref, "MAPPING_QUOTE_MISMATCH", "quote source/variant must belong to mapping product " + key)
        expected_variants = {vk for vk, v in variants.items() if v.get("productKey") == m.get("productKey")}
        if isinstance(m.get("variantKeys"), list) and all(isinstance(v, str) for v in m["variantKeys"]) and set(m["variantKeys"]) != expected_variants:
            fail(key, "MAPPING_VARIANT_COVERAGE", "mapping must include every split variant")
    for key, g in indexes["gaps"].items():
        entities = set().union(*(set(indexes[x]) for x in ["products", "variants", "quotes", "rules", "templates"]))
        if g.get("entityKey") not in entities | {"DATASET"}:
            fail(key, "DANGLING_REFERENCE", "gap.entityKey")
        if not isinstance(g.get("fields"), list) or not g.get("note"):
            fail(key, "INVALID_GAP", "fields and explanatory note required")
    if original is not None:
        records = original.get("records", [])
        expected = {r["record_id"] for r in records}
        if set(indexes["mapping"]) != expected or set(products) != expected:
            fail("mapping", "SOURCE_COVERAGE", "original records must have one product and mapping each")
        for i, r in enumerate(records):
            m = indexes["mapping"].get(r["record_id"], {})
            digest = hashlib.sha256(canonical(r).encode()).hexdigest()
            if m.get("sourcePath") != f"equipment.json#/records/{i}" or m.get("sourceRecordSha256") != digest:
                fail(r["record_id"], "SOURCE_RECORD_CHANGED", "original pointer/hash mismatch")
            expected_quotes = {r["record_id"] + "--" + pk.replace("_", "-") for pk in ["cn_price", "overseas_price"] if r.get(pk)}
            if isinstance(m.get("quoteKeys"), list) and all(isinstance(qk, str) for qk in m["quoteKeys"]) and set(m["quoteKeys"]) != expected_quotes:
                fail(r["record_id"], "MAPPING_QUOTE_COVERAGE", "quote mapping must match original record prices")
            for pk in ["cn_price", "overseas_price"]:
                p = r.get(pk)
                if not p:
                    continue
                q = indexes["quotes"].get(r["record_id"] + "--" + pk.replace("_", "-"), {})
                if q.get("sourceProductKey") != r["record_id"] or (
                    q.get("variantKey") is not None
                    and products.get(variants.get(q["variantKey"], {}).get("productKey"), {}).get("sourceRecordId") != r["record_id"]
                ):
                    fail(q.get("key", r["record_id"]), "SOURCE_QUOTE_MISMATCH", "quote source/variant differs from original record identity")
                a = p["amount"]
                cents = int(Decimal(str(a)) * 100) if type(a) in {int, float} else None
                if q.get("rawPrice") != p or q.get("amountCents") != cents:
                    fail(r["record_id"], "SOURCE_PRICE_CHANGED", "raw price or integer cents mismatch")
        mismatched = indexes["quotes"].get("TRUCK-001--cn-price", {})
        if mismatched.get("variantKey") is not None:
            fail("TRUCK-001--cn-price", "MODEL_MISMATCH", "Mid cannot be bound to Standard")
    if previous is not None:
        if previous.get("datasetVersion") == dataset.get("datasetVersion") and canonical(previous) != canonical(dataset):
            fail("$", "SAME_VERSION_DIFFERENT_CONTENT", "datasetVersion is immutable")
        for group in ["quotes", "rules", "templates"]:
            old = {x["key"]: x for x in previous.get(group, [])}
            for key, obj in indexes[group].items():
                if key not in old:
                    continue
                before = old[key]
                if integer(obj.get("version"), 1) and integer(before.get("version"), 1):
                    if obj["version"] < before["version"]:
                        fail(key, "OLDER_VERSION", "cannot import an older version")
                    elif obj["version"] == before["version"] and canonical(obj) != canonical(before):
                        fail(key, "SAME_VERSION_DIFFERENT_CONTENT", group)
    stats = {g: len(lists[g]) for g in FIELDS}
    stats["active"] = {g: sum(x.get("active") is True for x in indexes[g].values()) for g in ["products", "variants", "quotes", "rules", "templates"]}
    stats["eligibleExactCnQuotes"] = len(eligible) if not errors else 0
    stats["quoteKinds"] = dict(Counter(q.get("priceKind") for q in indexes["quotes"].values()))
    stats["eligibleReferenceCnQuotes"] = sum(q.get("active") is True and reference(q) and q.get("priceKind") == "EXACT" and q.get("currency") == "CNY" and q.get("market") == "CN" and integer(q.get("amountCents")) and recorded_sources(q) for q in indexes["quotes"].values()) if not errors else 0
    return {"valid": not errors, "errors": errors, "gaps": lists["gaps"], "stats": stats, "asOf": now.isoformat(), "notice": "Schema validity does not prove source authenticity, compatibility, or three complete plans."}


def load(path):
    if path.stat().st_size > 1024 * 1024:
        raise ValueError("dataset exceeds 1 MiB")
    def reject_constant(value):
        raise ValueError("non-finite JSON number: " + value)
    def pairs(items):
        result = {}
        for k, v in items:
            if k in result:
                raise ValueError("duplicate JSON field: " + k)
            result[k] = v
        return result
    return json.loads(path.read_text(encoding="utf-8-sig"), parse_constant=reject_constant, object_pairs_hook=pairs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", nargs="?", type=Path, default=Path(__file__).with_name("normalized-equipment.v1.json"))
    parser.add_argument("--original", type=Path, default=Path(__file__).with_name("equipment.json"))
    parser.add_argument("--previous", type=Path, help="read-only comparison against a prior v1 dataset")
    parser.add_argument("--as-of", help="timezone ISO8601 for reproducible expiry checks")
    parser.add_argument("--details", action="store_true", help="also print all gap entries")
    args = parser.parse_args()
    try:
        now = timestamp(args.as_of) if args.as_of else datetime.now(timezone.utc)
        if now is None:
            raise ValueError("--as-of requires timezone ISO8601")
        dataset = load(args.dataset)
        if isinstance(dataset, dict) and (len(dataset.get("products", [])) > 1000 or len(dataset.get("variants", [])) > 5000):
            raise ValueError("product/variant import count limit exceeded")
        report = validate(dataset, as_of=now, original=load(args.original), previous=load(args.previous) if args.previous else None)
        if not args.details:
            report["gaps"] = {"count": len(report["gaps"]), "detail": "see dataset.gaps or use --details"}
    except (OSError, ValueError, TypeError, KeyError, OverflowError, RecursionError) as exc:
        report = {"valid": False, "errors": [{"path": "$", "code": "INPUT_ERROR", "detail": str(exc)}]}
    print(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False))
    return 0 if report["valid"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
