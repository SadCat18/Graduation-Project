"""Read-only assertions for D01 source transcription and exhaustive gap governance."""
import hashlib
import json
from pathlib import Path
from build_public_supplement import build, load

BASE = Path(__file__).parent
protected = [BASE/name for name in ['equipment.json','equipment.csv','db-seed-skate-equipment.sql','sources.md','normalized-equipment.v1.json','normalized-equipment.reference.v1.json']]
before = [hashlib.sha256(p.read_bytes()).hexdigest() for p in protected]
supplement, ledger = build()
assert supplement['proposedCatalogChanges'] == [] and supplement['autoImport'] is False
assert supplement['noCatalogMutationRequired'] is True
assert {row['value'] for row in supplement['facts'] if row['field'] == 'specs.lengthMm'} == {25.4, 31.75}
assert all(row['verificationStatus'] == 'RECORDED' and row['collectedDate'] == '2026-10-09' and row['url'].startswith('https://nhsskatedirect.com/') for row in supplement['facts'])
original = load('normalized-equipment.reference.v1.json')
assert {g['key'] for g in original['gaps']} == {g['gapKey'] for g in ledger['gaps']}
assert sum(ledger['partitions'].values()) == len(ledger['gaps']) == 299
assert ledger['partitions'] == {'RECORDED_PENDING':119, 'EXACT_PURCHASE_ONLY':60, 'REFERENCE_BLOCKING':120}
for row in ledger['gaps']:
    assert row['entityKey'] and row['reason'] and row['nextEvidenceNeeded']
    if row['originalCode'] in ['NO_CN_PRICE','MODEL_MISMATCH','WHEEL_TRUCK_RISER_SOURCE_PENDING','RISER_HARDWARE_LENGTH_SOURCE_PENDING','GRIP_COVERAGE_SOURCE_PENDING']:
        assert row['partition'] == 'REFERENCE_BLOCKING'
assert before == [hashlib.sha256(p.read_bytes()).hexdigest() for p in protected]
print(json.dumps({'sourceFacts':8,'gapsCovered':299,'catalogMutations':0,'partitions':ledger['partitions'],'protectedHashesUnchanged':True}))
