# -*- coding: utf-8 -*-
"""校验 equipment.json 的结构完整性，并生成 equipment.csv 汇总表。"""
import json
import csv
import collections
import os

BASE = os.path.dirname(os.path.abspath(__file__))

with open(os.path.join(BASE, "equipment.json"), encoding="utf-8") as f:
    d = json.load(f)

recs = d["records"]
print("JSON 有效，记录数:", len(recs))
print("分类统计:", dict(collections.Counter(r["category"] for r in recs)))
print("含国内价:", sum(1 for r in recs if r["cn_price"]),
      "| 含海外价:", sum(1 for r in recs if r["overseas_price"]))

ids = [r["record_id"] for r in recs]
assert len(ids) == len(set(ids)), "存在重复 record_id"
print("record_id 唯一性: OK")

required = ["record_id", "category", "brand", "model", "spec", "summary_zh",
            "suitability_tags", "level_tags", "cn_price", "overseas_price",
            "spec_source"]
for r in recs:
    for k in required:
        assert k in r, f"{r.get('record_id')} 缺字段 {k}"
    assert r["spec_source"]["url"], f"{r['record_id']} 缺规格来源URL"
print("必填字段检查: OK")


def fmt_price(p):
    if not p:
        return ""
    amt = p.get("amount", "")
    return f"{p.get('currency','')} {amt}"


rows = []
for r in recs:
    rows.append({
        "record_id": r["record_id"],
        "category": r["category"],
        "brand": r["brand"],
        "brand_country": r["brand_country"],
        "model": r["model"],
        "spec_summary": json.dumps(r["spec"], ensure_ascii=False),
        "suitability_tags": " ".join(r.get("suitability_tags") or []),
        "level_tags": " ".join(r.get("level_tags") or []),
        "cn_price": fmt_price(r.get("cn_price")),
        "cn_price_type": (r.get("cn_price") or {}).get("price_type", ""),
        "cn_price_source": (r.get("cn_price") or {}).get("source_url", ""),
        "overseas_price": fmt_price(r.get("overseas_price")),
        "overseas_price_type": (r.get("overseas_price") or {}).get("price_type", ""),
        "overseas_source": (r.get("overseas_price") or {}).get("source_url", ""),
        "spec_source": r["spec_source"]["url"],
        "summary_zh": r["summary_zh"],
        "notes": r.get("notes") or "",
    })

csv_path = os.path.join(BASE, "equipment.csv")
with open(csv_path, "w", newline="", encoding="utf-8-sig") as f:
    w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
    w.writeheader()
    w.writerows(rows)
print("已生成 equipment.csv，行数:", len(rows))
