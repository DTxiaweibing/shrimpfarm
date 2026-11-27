import os
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
from reconcile_v17 import compare  # noqa: E402


def _create_old_db(path):
    conn = sqlite3.connect(path)
    conn.execute(
        "CREATE TABLE fin_income ("
        "id INTEGER PRIMARY KEY, batch_id TEXT, weight REAL, amount REAL, "
        "net_fee REAL, actual_amount REAL)"
    )
    conn.execute(
        "INSERT INTO fin_income(id,batch_id,weight,amount,net_fee,actual_amount)"
        " VALUES (1,'B1',100.0,2000.0,30.0,1970.0)"
    )
    conn.commit()
    conn.close()


def _create_new_db(path, amount=2000.0, item_rows=1):
    conn = sqlite3.connect(path)
    conn.execute(
        "CREATE TABLE fin_income ("
        "id INTEGER PRIMARY KEY, batch_id TEXT, weight REAL, amount REAL, "
        "net_fee REAL, actual_amount REAL)"
    )
    conn.execute(
        "CREATE TABLE fin_income_item ("
        "id INTEGER PRIMARY KEY, income_id INTEGER, weight REAL, amount REAL, net_fee REAL)"
    )
    conn.execute(
        "INSERT INTO fin_income(id,batch_id,weight,amount,net_fee,actual_amount)"
        " VALUES (1,'B1',100.0,2000.0,30.0,1970.0)"
    )
    for i in range(item_rows):
        conn.execute(
            "INSERT INTO fin_income_item(id,income_id,weight,amount,net_fee)"
            " VALUES (?,?,100.0,?,30.0)",
            (i + 1, 1, amount),
        )
    conn.commit()
    conn.close()


class ReconcileV17Test(unittest.TestCase):
    def test_compare_equal_returns_empty(self):
        with tempfile.TemporaryDirectory() as td:
            old = os.path.join(td, "old.db")
            new = os.path.join(td, "new.db")
            _create_old_db(old)
            _create_new_db(new)
            self.assertEqual(compare(old, new), [])

    def test_amount_diff_reports_huokuan(self):
        with tempfile.TemporaryDirectory() as td:
            old = os.path.join(td, "old.db")
            new = os.path.join(td, "new.db")
            _create_old_db(old)
            _create_new_db(new, amount=2000.01)
            diffs = compare(old, new)
            self.assertTrue(any("货款合计" in d for d in diffs), diffs)

    def test_item_count_diff_reports_mingxi(self):
        with tempfile.TemporaryDirectory() as td:
            old = os.path.join(td, "old.db")
            new = os.path.join(td, "new.db")
            _create_old_db(old)
            _create_new_db(new, item_rows=0)
            diffs = compare(old, new)
            self.assertTrue(any("明细行数" in d for d in diffs), diffs)


if __name__ == "__main__":
    unittest.main()
