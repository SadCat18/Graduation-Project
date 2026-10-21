"""Offline D01 governance artifacts only; never alters/imports the existing catalog or original exports."""
import hashlib
import json
from collections import Counter
from pathlib import Path

BASE = Path(__file__).parent
OUT = BASE / 'public-remediation-20261021'


def load(name):
    return json.loads((BASE / name).read_text(encoding='utf-8'))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build():
    raw = load('equipment.json')
    catalog = load('normalized-equipment.reference.v1.json')
    indexes = {group: {row['key']: row for row in catalog[group]} for group in ['products', 'variants', 'quotes', 'rules', 'templates']}
    all_entities = {key: row for entries in indexes.values() for key, row in entries.items()}
    facts = []
    for key, length in [('HW-002', 25.4), ('HW-003', 31.75)]:
        index, record = next((i, r) for i, r in enumerate(raw['records']) if r['record_id'] == key)
        variant = next(v for v in catalog['variants'] if v['productKey'] == key)
        assert record['spec']['quantity_per_set'] == record['spec']['nuts_included'] == 8
        values = {'lengthMm': length, 'headType': record['spec']['head_types'][0], 'nutsIncluded': True}
        for field, value in values.items():
            assert variant['specs'][field] == value, 'Already-transcribed baseline changed: review instead of silently overriding'
            raw_field = {'lengthMm': 'lengths', 'headType': 'head_types', 'nutsIncluded': 'nuts_included'}[field]
            facts.append({'entityKey': variant['key'], 'productKey': key, 'field': 'specs.' + field,
                          'value': value, 'rawValue': record['spec'][raw_field],
                          'sourcePath': f'equipment.json#/records/{index}/spec/{raw_field}',
                          'url': record['spec_source']['url'], 'collectedDate': record['spec_source']['fetched_date'],
                          'applicableVariant': variant['key'], 'verificationStatus': 'RECORDED',
                          'basis': '1 inch = 25.4 mm; single recorded length/head type; 8 nuts recorded. No live re-verification.',
                          'disposition': 'ALREADY_TRANSCRIBED_NO_CHANGE_REQUIRED'})
        facts.append({'entityKey': variant['key'], 'productKey': key, 'field': 'recordedPackaging',
                      'value': {'bolts': 8, 'nuts': 8}, 'sourcePath': f'equipment.json#/records/{index}/spec',
                      'url': record['spec_source']['url'], 'collectedDate': record['spec_source']['fetched_date'],
                      'applicableVariant': variant['key'], 'verificationStatus': 'RECORDED',
                      'basis': 'Existing rawSpec and overseas price unit; not a domestic quote or new compatibility rule.',
                      'disposition': 'RECORDED_SUPPLEMENT_ONLY'})
    supplement = {'supplementVersion': '2026-10-21-d01-review-v1', 'candidateType': 'RECORDED_SPEC_REVIEW_ONLY',
                  'baseDatasetVersion': catalog['datasetVersion'], 'baseSha256': digest(BASE/'normalized-equipment.reference.v1.json'),
                  'autoImport': False, 'noCatalogMutationRequired': True, 'proposedCatalogChanges': [],
                  'facts': facts, 'remaining': ['NO_CN_HARDWARE_PRICE', 'NO_CN_RISER_PRICE', 'INSTALLATION_RULES_PENDING'],
                  'notice': 'This is not an import dataset. Known fields already exist; no version bump, prices, thresholds or VERIFIED promotion.'}
    ledger = []
    for gap in catalog['gaps']:
        row = all_entities.get(gap['entityKey'], {})
        product_key = row.get('productKey') or row.get('sourceProductKey') or (row.get('key') if row.get('key') in indexes['products'] else None)
        category = indexes['products'].get(product_key, {}).get('category')
        code = gap['code']
        values = {**row.get('specs', {}), **row.get('conditions', {}), **row}
        unknown = [f for f in gap['fields'] if values.get(f) is None]
        if code == 'QUOTE_NOT_ELIGIBLE':
            partition, reason = 'EXACT_PURCHASE_ONLY', 'Historical verification/packaging/shipping/tax/validity gate is not a reference-mode requirement; no current purchase guarantee.'
        elif code in ['SPEC_NOT_VERIFIED', 'USAGE_ADVICE_SOURCE_PENDING'] or category == 'COMPLETE' and code == 'UNKNOWN_SPEC_FIELDS':
            partition, reason = 'RECORDED_PENDING', 'Recorded facts/tags may support reference display; independent verification or optional complete internals remain unknown.'
        elif code == 'UNKNOWN_SPEC_FIELDS' and not unknown:
            partition, reason = 'RECORDED_PENDING', 'Fields are already transcribed in current baseline; preserve historical gap rather than invent another catalog change.'
        else:
            partition, reason = 'REFERENCE_BLOCKING', 'Missing domestic price, critical compatibility facts/rules, explicit model mismatch, or unresolved template conditions still prevent a complete assembly.'
        sources = row.get('sources', [])
        ledger.append({'gapKey': gap['key'], 'entityKey': gap['entityKey'], 'originalCode': code,
                       'originalFields': gap['fields'], 'originalNote': gap['note'], 'partition': partition,
                       'reason': reason, 'unknownFields': unknown, 'currentKnownValues': {f: values[f] for f in gap['fields'] if f in values and values[f] is not None},
                       'applicableVariantKeys': [v['key'] for v in catalog['variants'] if v['key'] == row.get('key') or v['productKey'] == product_key] or row.get('scope', {}).get('variantKeys', []),
                       'sources': sources, 'sourceAvailability': 'RECORDED_OR_POLICY_ONLY' if sources else 'MISSING',
                       'nextEvidenceNeeded': 'Source-specific domestic price or recorded physical applicability where blocking; never infer market conversion or manufacture thresholds.' if partition == 'REFERENCE_BLOCKING' else 'Retain source/status/date; independently review only when a precise claim is required.'})
    report = {'baselineCounts': {group: len(catalog[group]) for group in ['products','variants','quotes','rules','templates','gaps']},
              'partitions': dict(Counter(row['partition'] for row in ledger)), 'gaps': ledger,
              'notice': '299 historical gaps retained. Partition is governance, not proof of a complete assembly or eligibility upgrade.'}
    assert len(ledger) == len({row['gapKey'] for row in ledger}) == 299
    return supplement, report


if __name__ == '__main__':
    OUT.mkdir(exist_ok=True)
    supplement, ledger = build()
    for name, data in [('recorded-spec-supplement.candidate.json', supplement), ('gap-source-ledger.json', ledger)]:
        (OUT/name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'facts':len(supplement['facts']), 'catalogChanges':0, 'partitions':ledger['partitions']}, ensure_ascii=False))
