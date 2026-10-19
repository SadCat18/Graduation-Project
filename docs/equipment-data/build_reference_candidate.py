"""Deterministic reference candidate from the original 50 records; no network or database."""
import json
from pathlib import Path

BASE = Path(__file__).parent


def build():
    data = json.loads((BASE / 'normalized-equipment.v1.json').read_text(encoding='utf-8'))
    data['datasetVersion'] = '2026-10-10-reference-01-v1'
    products = {p['key']: p for p in data['products']}
    variants = {v['key']: v for v in data['variants']}
    for group in ('products', 'variants', 'quotes', 'rules', 'templates'):
        for row in data[group]:
            row['version'] = row.get('version', 1) + 1
            row['pricingMode'] = 'REFERENCE'
            row['active'] = True
    for q in data['quotes']:
        q['active'] = q['market'] == 'CN' and q['currency'] == 'CNY' and q['priceKind'] == 'EXACT' and q['amountCents'] is not None and q['key'] != 'TRUCK-001--cn-price'
        assumptions = ['按2026-10-09已采集资料作参考估价；不代表当前可购或含运税到手价。']
        category = products[q['sourceProductKey']]['category']
        if q['variantKey'] is None:
            assumptions.append('同型号通用展示价用于该型号规格估算；不同规格实际售价需自行查找。')
        v = variants.get(q['variantKey'])
        package = v.get('rawSpec', {}).get('recordSpec', {}).get('quantity_per_set') if v else None
        if isinstance(package, int) and package > 0:
            q['packageQuantity'] = package
            q['packageUnit'] = 'BOLT_NUT_PAIR' if category == 'HARDWARE' else category
            assumptions.append('按原记录每套%d件计价；来源为RECORDED，未独立核验零售包装。' % package)
        elif category == 'COMPLETE':
            q['packageQuantity'] = 1
            q['packageUnit'] = 'COMPLETE'
            assumptions.append('按原资料分类的一块整板估算，内部配件与包装未独立核验。')
        else:
            assumptions.append('零售包装数量未知，按一套覆盖本类别所需数量估算；实际包装需自行确认。')
        if q['key'] == 'TRUCK-001--cn-price':
            assumptions.append('原报价为139 Mid，不能用于现有Standard规格；保留禁用。')
        q['priceAssumptions'] = assumptions
    eligible = [v['key'] for v in data['variants'] if products[v['productKey']]['category'] != 'COMPLETE']
    for t in data['templates']:
        t['selectionConditions']['eligibleVariantKeys'] = eligible
        t['selectionConditions']['ruleKeys'] = [r['key'] for r in data['rules']]
        # Missing compatibility facts are explicit; the original source status is never promoted.
        t['selectionConditions']['sourceScopePending'] = True
        t['reasonCodes'] = ['REFERENCE_ESTIMATE', 'COMPATIBILITY_PENDING', 'MISSING_PRICE_NOT_ZERO']
    return data


if __name__ == '__main__':
    candidate = build()
    output = BASE / 'normalized-equipment.reference.v1.json'
    output.write_text(json.dumps(candidate, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(output)
