# Spending plots

Plots of a household's shopping, read from an Order Splitter backup. Runs on a computer,
not the phone: the app stays offline, and this reads the file it already writes.

```bash
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt

# Real data: copy a backup from the folder chosen in Settings, Backups.
.venv/bin/python splitter_report.py household-splitter-backup.json out/

# No backup to hand: make a sample one first.
.venv/bin/python make_sample_backup.py sample_backup.json
.venv/bin/python splitter_report.py sample_backup.json out/
```

`out/group/` gets eight charts of the whole household: monthly spend by aisle, spend by
store, a calendar of shopping days, who buys with whom, who owes whom, prices by store,
how much was shared, and each person month by month. `out/people/` gets one page per
person: their monthly share against the household average, where their money went,
their own most-bought items, and who they split things with.

Shares follow the app's rules (shared items evenly, assigned items by shares, tax and
fees in proportion), without its last-cent rounding, so a figure can be a cent off the app.
