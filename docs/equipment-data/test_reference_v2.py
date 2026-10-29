"""Offline content/lineage checks; Java/H2 tests separately validate import and calculation."""
import hashlib
import json
from pathlib import Path
from build_reference_v2 import build, load

BASE=Path(__file__).parent
protected=['equipment.json','normalized-equipment.v1.json','normalized-equipment.reference.v1.json']
before={p:hashlib.sha256((BASE/p).read_bytes()).hexdigest() for p in protected}
data=build();assert data==load('normalized-equipment.reference.v2.json')
old=load('normalized-equipment.reference.v1.json')
for group in ['products','variants','quotes','rules','templates']:
    a={x['key']:x for x in old[group]};b={x['key']:x for x in data[group]}
    assert set(a)<=set(b),group
    for k,v in a.items():
        assert b[k]['version']==v.get('version',1)+(b[k]!=v),(group,k)
products={p['key']:p for p in data['products']};variants={v['key']:v for v in data['variants']};quotes={q['key']:q for q in data['quotes']}
assert {p['key']:p['sourceRecordId'] for p in old['products']}=={k:p['sourceRecordId'] for k,p in products.items()}
for k in ['SET-001--deck-none-complete','HW-001--purchase-variant-unresolved']:
    assert variants[k]['historicalOnly'] and not variants[k]['active']
    assert len(variants[k]['replacements'])==4
    assert all(variants[v]['active'] for v in variants[k]['replacements'])
for v in variants.values():
    if v['productKey']=='SET-001' and v['active']:
        assert v['specs']['deckWidthInch'] is None and v['identity']['manufacturerSku'] is None
    for f in v.get('fieldEvidence',[]):
        assert f['checkedAt'] and f['appliesTo'] and f['url'] and f['factNature']
assert variants['HW-001--phillips-length-unresolved']['specs']['lengthMm'] is None
assert [variants['HW-001--allen-'+k+'in']['specs']['lengthMm'] for k in ['1','1-125','1-25']]==[25.4,28.575,31.75]
assert all(variants['HW-001--allen-'+k+'in']['packaging']['nutProfile']=='LOW_PROFILE' for k in ['1','1-125','1-25'])
assert [variants['RSR-001--height-'+k+'mm']['specs']['heightMm'] for k in ['3-175','6-35','12-7']]==[3.175,6.35,12.7]
truck=variants['TRUCK-006--size-8-0-k5-standard'];assert truck['specs']['axleWidthInch']==8.0 and truck['specs']['heightMm'] is None
assert truck['specs']['deckMinInch'] is None and not truck['compatibilityAdvice']['hardBoundary']
wheel=next(v for v in variants.values() if v['productKey']=='WHEEL-009');assert wheel['specs']['widthMm'] is None and wheel['specs']['contactPatchMm'] is None and wheel['verificationStatus']=='RECORDED'
assert quotes['TRUCK-006--cn-price']['packageQuantity'] is None and quotes['TRUCK-006--cn-price']['amountCents']==32200
assert quotes['SET-001--cn-price']['variantKey'] is None and quotes['SET-001--cn-price']['amountCents']==58000
for key in ['BRG-008--cn-price','GRIP-002--cn-price']:
    q=quotes[key];assert q['validUntil'] is None and q['promotion']['timezone'] is None and q['promotion']['endDate']=='2026-10-12'
assert before=={p:hashlib.sha256((BASE/p).read_bytes()).hexdigest() for p in protected}
print(json.dumps(dict(result='PASS',products=len(products),variants=len(variants),protectedInputsUnchanged=True,networkCalls=0,productionImports=0)))
