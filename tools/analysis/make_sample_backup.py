"""Writes a sample backup in the app's own format, for trying the report without real data.

    python make_sample_backup.py sample_backup.json

Four made-up housemates, July to November 2026, every store the app reads. The habits are
invented but consistent (Ben buys the beer, Chen the oat milk, Ayush and Chen split eggs),
so the plots have patterns to find. Seeded, so every run writes the same file.
"""
import json
import random
import sys
from datetime import date, datetime, timedelta, timezone

random.seed(7)

MEMBERS = [
    {"id": 1, "name": "Ayush", "colorHex": "#2E6FD6"},
    {"id": 2, "name": "Ben", "colorHex": "#B5503F"},
    {"id": 3, "name": "Chen", "colorHex": "#1F7A5A"},
    {"id": 4, "name": "Dana", "colorHex": "#8E4FB8"},
]
A, B, C, D = 1, 2, 3, 4
EVERYONE = [A, B, C, D]

# How each store prices against Walmart.
STORE_FACTOR = {"WALMART": 1.00, "COSTCO": 0.86, "ALDI": 0.82, "TARGET": 1.09,
                "STAR_MARKET": 1.21, "AMAZON_FRESH": 1.05}

# name, base cents, who it is for, how often it lands in a shop (0..1)
GROCERIES = [
    ("Great Value Whole Milk, 1 Gallon", 329, EVERYONE, 0.9),
    ("Large White Eggs, 18 Count", 389, [A, C], 0.7),
    ("Whole Wheat Bread, 20 oz", 279, EVERYONE, 0.7),
    ("Bananas, Bunch", 149, EVERYONE, 0.8),
    ("Jasmine Rice, 5 lb", 849, EVERYONE, 0.25),
    ("Extra Virgin Olive Oil, 1 L", 1199, EVERYONE, 0.15),
    ("Paper Towels, 6 Rolls", 1049, EVERYONE, 0.2),
    ("Dish Soap, 24 oz", 349, EVERYONE, 0.15),
    ("Laundry Detergent, 92 oz", 1497, EVERYONE, 0.12),
    ("Chicken Breast, 2.5 lb", 1129, [A, B], 0.55),
    ("Ground Beef 80/20, 1 lb", 549, [B, D], 0.35),
    ("Salmon Fillet, 1 lb", 1099, [C], 0.3),
    ("Coffee Beans, Medium Roast, 2 lb", 1599, [A], 0.4),
    ("Protein Bars, 12 Count", 1899, [A], 0.25),
    ("Craft Beer, 12 Pack", 1799, [B], 0.45),
    ("Tortilla Chips, Family Size", 449, [B, D], 0.5),
    ("Frozen Pizza, Pepperoni", 599, [B, D], 0.4),
    ("Oat Milk, Original, 64 oz", 449, [C], 0.7),
    ("Baby Spinach, 5 oz", 349, [C, A], 0.5),
    ("Greek Yogurt, 32 oz", 599, [C], 0.45),
    ("Dark Chocolate Bar, 3.5 oz", 299, [D], 0.5),
    ("Sparkling Water, 12 Pack", 549, [D, C], 0.45),
    ("Avocados, Bag of 5", 499, [A, C, D], 0.35),
    ("Ice Cream, Vanilla, 1.5 qt", 499, [B, D], 0.3),
    ("Toothpaste, Whitening", 399, [A], 0.12),
    ("Shampoo, 13 oz", 699, [D], 0.1),
]

DISHES = [("Margherita Pizza", 1650), ("Pad Thai", 1495), ("Chicken Tikka Masala", 1795),
          ("Burger and Fries", 1550), ("Caesar Salad", 1195), ("Ramen", 1595),
          ("Fish Tacos", 1450), ("Paneer Butter Masala", 1695)]


def millis(day):
    return int(datetime(day.year, day.month, day.day, 18, 0, tzinfo=timezone.utc).timestamp() * 1000)


def scope(who):
    return "PERSONAL" if len(who) == 1 else ("COMMON" if set(who) == set(EVERYONE) else "SUBSET")


orders, lines, assignments, participants = [], [], [], []
next_line = 1


def add_order(oid, day, store, label, rows, payer, tax=0, fee=0, tip=0, note=None):
    global next_line
    subtotal = sum(cents for _, cents, _ in rows)
    total = subtotal + tax + fee + tip
    orders.append({
        "id": oid, "householdId": 1, "label": label, "orderDate": millis(day),
        "status": "SETTLED" if day < date(2026, 11, 10) else "ASSIGNED", "store": store,
        "payerMemberId": payer, "note": note, "taxCents": tax, "deliveryFeeCents": fee,
        "tipCents": tip, "otherFeeCents": 0, "discountCents": 0,
        "statedSubtotalCents": subtotal, "statedTotalCents": total, "createdAt": millis(day),
        "draftStep": "SUMMARY", "draftItemPosition": 0,
    })
    for m in EVERYONE:
        participants.append({"orderId": oid, "memberId": m})
    for position, (name, cents, who) in enumerate(rows):
        lines.append({"id": next_line, "orderId": oid, "name": name, "rawOcrText": name,
                      "quantity": 1, "lineTotalCents": cents, "scope": scope(who),
                      "position": position, "origin": "PARSED", "parsedName": name,
                      "parsedCents": cents})
        if scope(who) != "COMMON":
            for m in who:
                assignments.append({"id": len(assignments) + 1, "lineItemId": next_line,
                                    "memberId": m, "shares": 1})
        next_line += 1


start, end = date(2026, 7, 4), date(2026, 11, 25)
oid, week = 1, 0
day = start
main_stores = ["WALMART", "COSTCO", "WALMART", "ALDI"]
while day <= end:
    store = main_stores[week % len(main_stores)]
    drift = 1 + 0.004 * week
    rows = []
    for name, base, who, often in GROCERIES:
        if random.random() < often:
            cents = round(base * STORE_FACTOR[store] * drift * random.uniform(0.95, 1.05))
            rows.append((name, cents, who))
    shop_day = day + timedelta(days=random.choice([0, 0, 1]))
    add_order(oid, shop_day, store, f"{shop_day:%b %d} {store.replace('_', ' ').title()}",
              rows, payer=EVERYONE[week % 4], tax=round(sum(r[1] for r in rows) * 0.02))
    oid += 1

    # A midweek top-up at Star Market or Target, most weeks.
    if random.random() < 0.6:
        top = random.choice(["STAR_MARKET", "TARGET"])
        picks = random.sample([g for g in GROCERIES if g[3] > 0.4], k=random.randint(2, 4))
        rows = [(n, round(b * STORE_FACTOR[top] * drift * random.uniform(0.95, 1.05)), w)
                for n, b, w, _ in picks]
        top_day = day + timedelta(days=3)
        add_order(oid, top_day, top, f"{top_day:%b %d} {top.replace('_', ' ').title()}", rows,
                  payer=random.choice(EVERYONE), tax=round(sum(r[1] for r in rows) * 0.02))
        oid += 1

    # Amazon Fresh delivery every third week, with a fee and a tip.
    if week % 3 == 1:
        picks = random.sample(GROCERIES, k=6)
        rows = [(n, round(b * STORE_FACTOR["AMAZON_FRESH"] * drift), w) for n, b, w, _ in picks]
        az_day = day + timedelta(days=5)
        add_order(oid, az_day, "AMAZON_FRESH", f"{az_day:%b %d} Amazon Fresh", rows, payer=A,
                  tax=round(sum(r[1] for r in rows) * 0.02), fee=399, tip=500)
        oid += 1

    # Dinner out once a month: a dish each, a shared starter, a tip.
    if week % 4 == 2:
        rows = [("Garlic Naan Basket", 895, EVERYONE)]
        for m in EVERYONE:
            dish, cents = random.choice(DISHES)
            rows.append((dish, cents, [m]))
        rows.append(("Pitcher of Mango Lassi", 1400, [A, C, D]))
        sub = sum(r[1] for r in rows)
        dinner = day + timedelta(days=6)
        add_order(oid, dinner, "RESTAURANT", f"{dinner:%b %d} Restaurant", rows,
                  payer=random.choice(EVERYONE), tax=round(sub * 0.07), tip=round(sub * 0.18),
                  note="Dana's birthday dinner" if dinner.month == 9 else None)
        oid += 1

    day += timedelta(days=7)
    week += 1

payments = [
    {"id": 1, "householdId": 1, "fromMemberId": B, "toMemberId": A, "amountCents": 6000,
     "paidAt": millis(date(2026, 8, 31)), "note": "August"},
    {"id": 2, "householdId": 1, "fromMemberId": D, "toMemberId": C, "amountCents": 4500,
     "paidAt": millis(date(2026, 9, 30)), "note": None},
    {"id": 3, "householdId": 1, "fromMemberId": C, "toMemberId": A, "amountCents": 3000,
     "paidAt": millis(date(2026, 10, 31)), "note": "October"},
]

members = [{**m, "householdId": 1, "sortOrder": i, "isArchived": False}
           for i, m in enumerate(MEMBERS)]
backup = {
    "version": 3, "exportedAt": millis(date(2026, 11, 26)), "databaseVersion": 9,
    "appVersion": "sample",
    "groups": [{
        "household": {"id": 1, "name": "Flat 12 (sample data)", "createdAt": millis(start)},
        "members": members, "orders": orders, "participants": participants,
        "lineItems": lines, "assignments": assignments, "images": [], "memory": [],
        "payments": payments, "rules": [], "corrections": [], "discardedRows": [],
        "memberNotes": [],
    }],
}
out = sys.argv[1] if len(sys.argv) > 1 else "sample_backup.json"
with open(out, "w") as f:
    json.dump(backup, f, indent=1)
print(f"wrote {out}: {len(orders)} orders, {len(lines)} items, {len(members)} people")
