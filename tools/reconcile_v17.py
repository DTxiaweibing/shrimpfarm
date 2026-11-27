#!/usr/bin/env python3
"""Offline six-item reconcile gate for v16->v17 finance migration.

Compares two SQLite files: a v16 backup and an upgraded v17 database.
Six items per batch: 单头数, 明细行数, 总重量, 货款合计, 捞网费合计, 实收合计.
Amounts/weight use abs(diff) <= 0.005 tolerance; counts must be exactly equal.
"""

from __future__ import annotations

import sqlite3
import sys
from pathlib import Path

AMOUNT_TOLERANCE = 0.005
FIELDS = ("单头数", "明细行数", "总重量", "货款合计", "捞网费合计", "实收合计")


def _snapshot_old(conn):
    rows = conn.execute(
        "SELECT IFNULL(batch_id,''), COUNT(*), "
        "IFNULL(SUM(weight),0), IFNULL(SUM(amount),0), IFNULL(SUM(net_fee),0), "
        "IFNULL(SUM(actual_amount),0) FROM fin_income GROUP BY batch_id"
    ).fetchall()
    out = {}
    for batch, count, weight, amount, net_fee, actual in rows:
        count = int(count)
        out[str(batch)] = {
            "单头数": count,
            "明细行数": count,
            "总重量": float(weight),
            "货款合计": float(amount),
            "捞网费合计": float(net_fee),
            "实收合计": float(actual),
        }
    return out


def _snapshot_new(conn):
    rows = conn.execute(
        "SELECT t.batch, t.cnt, t.icnt, t.w, t.a, t.nf, "
        "IFNULL((SELECT SUM(i2.actual_amount) FROM fin_income i2 "
        "WHERE IFNULL(i2.batch_id,'') = t.batch), 0) "
        "FROM (SELECT IFNULL(i.batch_id,'') batch, COUNT(DISTINCT i.id) cnt, "
        "COUNT(it.id) icnt, IFNULL(SUM(it.weight),0) w, IFNULL(SUM(it.amount),0) a, "
        "IFNULL(SUM(it.net_fee),0) nf "
        "FROM fin_income i LEFT JOIN fin_income_item it ON it.income_id=i.id "
        "GROUP BY i.batch_id) t"
    ).fetchall()
    out = {}
    for batch, count, item_count, weight, amount, net_fee, actual in rows:
        out[str(batch)] = {
            "单头数": int(count),
            "明细行数": int(item_count),
            "总重量": float(weight),
            "货款合计": float(amount),
            "捞网费合计": float(net_fee),
            "实收合计": float(actual),
        }
    return out


def _empty_snapshot():
    return {f: 0 for f in FIELDS}


def _diff_field(batch, field, before, after):
    if field in ("单头数", "明细行数"):
        if int(before) != int(after):
            return f"批次 {batch} 的 {field} 对账不一致：修改前={before}，修改后={after}"
        return None
    if abs(float(before) - float(after)) > AMOUNT_TOLERANCE:
        return f"批次 {batch} 的 {field} 对账不一致：修改前={before}，修改后={after}"
    return None


def _connect_ro(path):
    uri = Path(path).resolve().as_uri() + "?mode=ro"
    return sqlite3.connect(uri, uri=True)


def compare(old_path, new_path):
    old_conn = _connect_ro(old_path)
    new_conn = _connect_ro(new_path)
    try:
        before = _snapshot_old(old_conn)
        after = _snapshot_new(new_conn)
    finally:
        old_conn.close()
        new_conn.close()

    diffs = []
    for batch in sorted(set(before) | set(after)):
        b = before.get(batch, _empty_snapshot())
        a = after.get(batch, _empty_snapshot())
        for field in FIELDS:
            diff = _diff_field(batch, field, b[field], a[field])
            if diff:
                diffs.append(diff)
    return diffs


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 2:
        print("Usage: python tools/reconcile_v17.py <v16.db> <v17.db>", file=sys.stderr)
        return 2
    diffs = compare(args[0], args[1])
    if diffs:
        for d in diffs:
            print(f"FAIL: {d}")
        return 1

    old_conn = _connect_ro(args[0])
    new_conn = _connect_ro(args[1])
    try:
        batches = sorted(set(_snapshot_old(old_conn)) | set(_snapshot_new(new_conn)))
    finally:
        old_conn.close()
        new_conn.close()
    for batch in batches:
        for field in FIELDS:
            print(f"PASS: 批次 {batch} {field}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
