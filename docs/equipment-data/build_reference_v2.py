"""Deterministic, offline v2 overlay. v1 and source records remain immutable."""
from copy import deepcopy
from decimal import Decimal
import hashlib
import json
from pathlib import Path

BASE = Path(__file__).parent
GROUPS = ('products', 'variants', 'quotes', 'rules', 'templates')

def load(name):
    return json.loads((BASE / name).read_text(encoding='utf-8'))

def build():
    original = load('normalized-equipment.reference.v1.json')
    data = deepcopy(original)
    data['datasetVersion'] = '2026-10-24-reference-02-v2'
    ledger = {r['id']: r for r in load('reference-v2-field-ledger.json')['entries']}
    products = {p['key']: p for p in data['products']}
    variants = {v['key']: v for v in data['variants']}

    def fact(source, field, value, applies, nature='PHYSICAL_FACT', status='VERIFIED'):
        row = ledger[source]
        checked = row['checkedAt']
        checked = checked[:-5] + checked[-5:-2] + ':' + checked[-2:]
        return dict(field=field, value=value, url=row['url'], checkedAt=checked,
                    appliesTo=applies, factNature=nature, verificationStatus=status,
                    evidenceRef='reference-v2-field-ledger.json#' + source,
                    responseSha256=row.get('sha256'), limitations=row['fieldVerification'].get('limitations', []))

    # Preserve the ambiguous aggregate key as history; users choose a replacement explicitly.
    legacy = variants['SET-001--deck-none-complete']
    legacy.update(active=False, historicalOnly=True)
    replacements = []
    for slug, graphic in [('circus','马戏团'),('zoo-party','动物园派对'),('pj-la','PJ洛杉矶'),('lucas-collab','lucas联名')]:
        v = deepcopy(legacy)
        v.update(key='SET-001--graphic-' + slug, label='DBH PRO套装 / ' + graphic,
                 active=True, historicalOnly=False)
        v['identity'] = dict(graphic=graphic, manufacturerSku=None, identityBasis='RECORDED_GRAPHIC_NOT_MANUFACTURER_SKU')
        v['fieldEvidence'] = [fact('dbh-catalogue','officialGraphicVerification',None,
            dict(productKey='SET-001',variantKey=v['key'],graphic=graphic),'UNVERIFIED', 'INSUFFICIENT')]
        data['variants'].append(v)
        replacements.append(v['key'])
    legacy['replacements'] = replacements
    legacy['identity'] = dict(graphic=None, manufacturerSku=None, identityBasis='LEGACY_AGGREGATE_EXPLICIT_SELECTION_REQUIRED')

    legacy_hw = variants['HW-001--purchase-variant-unresolved']
    legacy_hw.update(active=False, historicalOnly=True)
    hardware = []
    for label, inches in [('1',Decimal('1')),('1-125',Decimal('1.125')),('1-25',Decimal('1.25'))]:
        v = deepcopy(legacy_hw)
        v.update(key='HW-001--allen-' + label + 'in',label="Shorty's Allen / " + str(inches) + '英寸',active=True,historicalOnly=False)
        v['specs'].update(lengthMm=float(inches*Decimal('25.4')),headType='ALLEN',nutsIncluded=True)
        v['identity'] = dict(headType='ALLEN',lengthInch=float(inches),manufacturerSku=None,identityBasis='OFFICIAL_ALLEN_OPTION')
        v['packaging'] = dict(boltsPerPack=8,nutsPerPack=8,nutProfile='LOW_PROFILE',scope='ORIGINAL_HARDWARE_FAMILY',domesticPrice=None)
        v['fieldEvidence'] = [fact('shortys-allen','lengthMm',v['specs']['lengthMm'],dict(productKey='HW-001',variantKey=v['key'],headType='ALLEN',lengthInch=float(inches))),
            fact('shortys-allen','packaging',v['packaging'],dict(productKey='HW-001',family='Original Hardware'), 'FAMILY_FACT')]
        data['variants'].append(v);hardware.append(v['key'])
    ph = deepcopy(legacy_hw)
    ph.update(key='HW-001--phillips-length-unresolved',label="Shorty's Phillips / 长度待核对",active=True,historicalOnly=False)
    ph['specs'].update(headType='PHILLIPS',lengthMm=None,nutsIncluded=None)
    ph['identity'] = dict(headType='PHILLIPS',lengthInch=None,manufacturerSku=None,identityBasis='RECORDED_HEAD_EXACT_LENGTH_UNVERIFIED')
    ph['fieldEvidence'] = [fact('shortys-phillips','exactLengthMm',None,dict(productKey='HW-001',variantKey=ph['key'],headType='PHILLIPS'),'UNVERIFIED','INSUFFICIENT')]
    data['variants'].append(ph);hardware.append(ph['key']);legacy_hw['replacements']=hardware

    truck = variants['TRUCK-006--size-8-0-k5-standard']
    truck['specs']['axleWidthInch']=8.0
    truck['compatibilityAdvice']=dict(suggestedDeckMinInch=7.75,suggestedDeckMaxInch=8.25,factNature='MANUFACTURER_ADVICE',hardBoundary=False)
    truck['fieldEvidence']=[fact('krux-guide','axleWidthInch',8.0,dict(productKey='TRUCK-006',variantKey=truck['key'],sizeCode='8.0')),
        fact('krux-guide','suggestedDeckRangeInch',[7.75,8.25],dict(productKey='TRUCK-006',variantKey=truck['key'],sizeCode='8.0'),'MANUFACTURER_ADVICE')]
    products['TRUCK-006']['fieldEvidence']=[fact('krux-k5','pageDefaultHeightMm',55,dict(model='K5 Standard',pageDefaultAxleWidthInch=7.60,notAppliedToVariantKey=truck['key']),'PAGE_DEFAULT_ONLY'),
        fact('krux-k5','retailUnit','one truck',dict(market='OVERSEAS',url=ledger['krux-k5']['url']),'RETAILER_PACKAGING_ONLY')]
    for v in data['variants']:
        if v['productKey']=='RSR-001':
            v['packaging']=dict(padsPerSet=None,domesticPrice=None)
            v['fieldEvidence']=[fact('dooks','heightMm',v['specs']['heightMm'],dict(productKey='RSR-001',variantKey=v['key'])),
                fact('dooks','holePattern','old/new school double pattern',dict(productKey='RSR-001',family='Dooks'),'FAMILY_FACT')]
        if v['productKey']=='WHEEL-009':
            v['fieldEvidence']=[fact('dbh-catalogue','independentVerification',None,dict(productKey='WHEEL-009',variantKey=v['key']),'UNVERIFIED','INSUFFICIENT')]

    for q in data['quotes']:
        if q['key'] in ['BRG-008--cn-price','GRIP-002--cn-price']:
            q['promotion']=dict(endDate='2026-10-12',precision='DATE',timezone=None,sourceText=q['rawPrice']['price_type'],evidenceRef=q['sources'][0]['evidenceRef'])
        if q['key']=='SET-001--cn-price':
            q['variantKey']=None  # Original quote is now explicitly scoped to the recorded family, not independently priced graphics.
            q['priceAssumptions'].append('580元为原资料DBH PRO产品族通用参考，供四种已记录图案估算；不是各图案单独核价，实际价格和板宽待确认。')
        if q['sourceProductKey']=='TRUCK-006' and q['market']=='CN':
            q['priceAssumptions'].append('国内原价仍按覆盖一对桥的通用参考假设；官方海外单只销售包装仅限该来源，未转移到此国内报价。')

    # Replace only internal candidate lists, never legacy saved selections.
    replacement_map={legacy['key']:replacements,legacy_hw['key']:hardware}
    for r in data['rules']:
        for node in [r.get('scope',{}),r.get('conditions',{})]:
            if 'variantKeys' in node:node['variantKeys']=[x for old in node['variantKeys'] for x in replacement_map.get(old,[old])]
    for t in data['templates']:
        t['selectionConditions']['eligibleVariantKeys']=[v['key'] for v in data['variants'] if v['active'] and products[v['productKey']]['category']!='COMPLETE']
    for row in data['mapping']:
        if row['productKey'] in ['SET-001','HW-001']:
            old=row['variantKeys'][:]
            row['variantKeys']=old+[x for k in old for x in replacement_map.get(k,[])]
            row['basis']+=' v2保留旧聚合身份并给显式替代列表；不自动将旧键映射某个新图案/长度。'
    for group in GROUPS:
        old={r['key']:r for r in original[group]}
        for row in data[group]:
            key=row['key']
            if key not in old:row['version']=1
            elif row!=old[key]:row['version']=old[key].get('version',1)+1
    return data

def write():
    before=load('normalized-equipment.reference.v1.json');candidate=build()
    output=BASE/'normalized-equipment.reference.v2.json'
    output.write_text(json.dumps(candidate,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    changes={}
    for group in GROUPS:
        old={x['key']:x for x in before[group]};new={x['key']:x for x in candidate[group]}
        changes[group]=dict(added=[k for k in new if k not in old],updated=[k for k in old if new[k]!=old[k]],unchanged=sum(new[k]==v for k,v in old.items()),deleted=[])
    report=dict(baseDataset=before['datasetVersion'],candidateDataset=candidate['datasetVersion'],candidateSha256=hashlib.sha256(output.read_bytes()).hexdigest(),groups=changes,
        productionImportPerformed=False,originalFilesModified=False,legacyKeysAutoMapped=False)
    (BASE/'reference-v2-import-diff.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return report

if __name__=='__main__':
    print(json.dumps(write(),ensure_ascii=True))
