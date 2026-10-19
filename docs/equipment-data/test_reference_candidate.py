"""Recorded reference validation and original provenance controls; never writes input files."""
import copy
import hashlib
import json
from pathlib import Path
from build_reference_candidate import build
from validate_normalized import validate, load

BASE = Path(__file__).parent
original = load(BASE/'equipment.json')
previous = load(BASE/'normalized-equipment.v1.json')
protected = [BASE/p for p in ['equipment.json','equipment.csv','db-seed-skate-equipment.sql','sources.md','normalized-equipment.v1.json']]
before = [hashlib.sha256(p.read_bytes()).hexdigest() for p in protected]
data = build()
def check(value): return validate(value, original=original, previous=previous)
result = check(data)
assert result['valid'], result['errors']
assert result['stats']['eligibleReferenceCnQuotes'] == 21
assert result['stats']['eligibleExactCnQuotes'] == 0
assert all(s['verificationStatus'] == 'RECORDED' for group in ['products','variants','quotes','rules','templates'] for row in data[group] for s in row['sources'])
assert data['mapping'] == previous['mapping']
assert all(q.get('rawPrice') == old.get('rawPrice') and q['amountCents'] == old['amountCents'] for q,old in zip(data['quotes'],previous['quotes']))
cases = []
def reject(name, change, code):
    d = copy.deepcopy(data);change(d);codes = {e['code'] for e in check(d)['errors']};assert code in codes, (name,codes);cases.append(name)
def quote(d,key): return next(q for q in d['quotes'] if q['key']==key)
reject('reference marker removed',lambda d:d['products'][0].pop('pricingMode'),'UNVERIFIED_ACTIVE')
reject('invalid mode',lambda d:d['products'][0].update(pricingMode='AUTO'),'INVALID_PRICING_MODE')
reject('price assumptions missing',lambda d:quote(d,'SET-001--cn-price').pop('priceAssumptions'),'REFERENCE_ASSUMPTIONS_REQUIRED')
reject('Mid cannot price Standard',lambda d:quote(d,'TRUCK-001--cn-price').update(active=True),'MODEL_MISMATCH')
reject('overseas not converted',lambda d:next(q for q in d['quotes'] if q['market']=='OVERSEAS').update(active=True),'ACTIVE_QUOTE_MARKET')
reject('missing cents not zero',lambda d:quote(d,'SET-001--cn-price').update(amountCents=None),'EXACT_AMOUNT_MISSING')
reject('cross product source',lambda d:quote(d,'SET-001--cn-price').update(sourceProductKey='SET-002'),'QUOTE_PRODUCT_MISMATCH')
reject('recorded price tamper',lambda d:quote(d,'SET-001--cn-price').update(amountCents=1),'SOURCE_PRICE_CHANGED')
assert before == [hashlib.sha256(p.read_bytes()).hexdigest() for p in protected]
print(json.dumps({'valid':True,'referenceQuotes':21,'exactQuotes':0,'negativeControls':cases,'protectedHashesUnchanged':True},ensure_ascii=False,indent=2))
