"""Plots of a household's shopping, read from an Order Splitter backup file.

    python splitter_report.py backup.json out/

Writes out/group/*.png (the whole household) and out/people/<name>.png (one page per
person). Reads the JSON the app writes to your backup folder (Settings, Backups), so it
works on real data the same way it works on make_sample_backup.py's output.

Each person's share follows the app's own rules: an item for everyone is split evenly
among the people on that order, an assigned item by its shares, and tax, fees and tip in
proportion to what each person's items cost (the app's default). The app's last-cent
rounding is not repeated here, so a figure can differ from the app's by a cent.
"""
import json
import os
import re
import sys
from collections import defaultdict
from datetime import datetime, timezone

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.ticker as mticker
import networkx as nx
import pandas as pd

# ------------------------------------------------------------------ look

ACCENT = "#6A3FC0"            # the app's Amethyst
INK, MUTED, GRID = "#1C1B1F", "#625B71", "#E7E0EC"
STORE_COLOURS = {             # the app's store pill colours
    "WALMART": "#00539C", "AMAZON_FRESH": "#3D6B00", "RESTAURANT": "#008184",
    "COSTCO": "#CB1FC2", "TARGET": "#D5375B", "ALDI": "#B75B07", "STAR_MARKET": "#6C71A4",
}
STORE_NAMES = {"WALMART": "Walmart", "AMAZON_FRESH": "Amazon Fresh", "RESTAURANT": "Restaurant",
               "COSTCO": "Costco", "TARGET": "Target", "ALDI": "Aldi", "STAR_MARKET": "Star Market"}
CATEGORY_COLOURS = ["#6A3FC0", "#1F7A5A", "#D5375B", "#2E6FD6", "#E08A00", "#4FB0A5",
                    "#9C6B30", "#C77DD6", "#7A869A", "#B9B2C6"]

plt.rcParams.update({
    "font.family": "DejaVu Sans", "font.size": 10, "axes.titlesize": 13,
    "axes.titleweight": "bold", "axes.titlelocation": "left", "axes.edgecolor": GRID,
    "axes.labelcolor": MUTED, "xtick.color": MUTED, "ytick.color": MUTED,
    "axes.spines.top": False, "axes.spines.right": False, "axes.grid": True,
    "grid.color": GRID, "grid.linewidth": 0.8, "figure.dpi": 110, "savefig.dpi": 150,
    "savefig.bbox": "tight", "legend.frameon": False,
})
DOLLARS = mticker.FuncFormatter(lambda v, _: f"${v:,.0f}")

# Coarse aisles by keyword. The app has its own categoriser; this is a short version of it.
CATEGORIES = [
    ("Dairy and eggs", r"milk|egg|yogurt|cheese|butter|ice cream"),
    ("Meat and fish", r"chicken|beef|salmon|fish|pork|turkey|bacon"),
    ("Fruit and veg", r"banana|spinach|avocado|apple|tomato|lettuce|onion|berr|veg"),
    ("Bakery and pantry", r"bread|rice|oil|pasta|flour|cereal|oat"),
    ("Snacks and sweets", r"chip|chocolate|cookie|bar\b|bars\b|candy|pizza"),
    ("Drinks", r"beer|wine|water|coffee|soda|juice|lassi|tea"),
    ("Household", r"towel|soap|detergent|toilet|trash|clean"),
    ("Personal care", r"toothpaste|shampoo|razor|deodorant"),
    ("Eating out", r"naan|masala|pad thai|ramen|taco|burger|salad|margherita"),
]


def category(name, store):
    if store == "RESTAURANT":
        return "Eating out"
    low = name.lower()
    for label, pattern in CATEGORIES:
        if re.search(pattern, low):
            return label
    return "Other"


def short(name, n=26):
    name = name.split(",")[0].strip()
    return name if len(name) <= n else name[: n - 1] + "…"


# ------------------------------------------------------------------ read

def load(path):
    with open(path) as f:
        backup = json.load(f)
    groups = backup.get("groups") or [{
        "household": backup.get("household"), "members": backup.get("members", []),
        "orders": backup.get("orders", []), "participants": backup.get("participants", []),
        "lineItems": backup.get("lineItems", []), "assignments": backup.get("assignments", []),
        "payments": backup.get("payments", []),
    }]
    return groups[0]


def shares(group):
    """One row per (item, person) with that person's share in cents, tax and fees included."""
    members = {m["id"]: m for m in group["members"]}
    on_order = defaultdict(list)
    for p in group["participants"]:
        on_order[p["orderId"]].append(p["memberId"])
    assigned = defaultdict(list)
    for a in group["assignments"]:
        assigned[a["lineItemId"]].append((a["memberId"], a.get("shares", 1)))
    items_by_order = defaultdict(list)
    for item in group["lineItems"]:
        items_by_order[item["orderId"]].append(item)

    rows = []
    for order in group["orders"]:
        people = on_order[order["id"]]
        if not people:
            continue
        when = datetime.fromtimestamp(order["orderDate"] / 1000, tz=timezone.utc)
        split = []   # (item, member, item cents share)
        for item in items_by_order[order["id"]]:
            scope, cents = item.get("scope"), item["lineTotalCents"]
            if scope in ("EXCLUDED", "UNASSIGNED") or cents <= 0:
                continue
            weights = [(m, 1) for m in people] if scope == "COMMON" else assigned[item["id"]]
            total_w = sum(w for _, w in weights)
            for m, w in weights:
                split.append((item, m, cents * w / total_w, scope, len(weights)))
        items_total = sum(s[2] for s in split) or 1
        extras = (order.get("taxCents", 0) + order.get("deliveryFeeCents", 0)
                  + order.get("tipCents", 0) + order.get("otherFeeCents", 0)
                  - order.get("discountCents", 0))
        for item, m, cents, scope, ways in split:
            rows.append({
                "order": order["id"], "date": when, "month": when.strftime("%Y-%m"),
                "store": order.get("store", "WALMART"), "payer": order.get("payerMemberId"),
                "item": item["name"], "category": category(item["name"], order.get("store")),
                "scope": scope, "ways": ways, "member": m,
                "person": members.get(m, {}).get("name", f"#{m}"),
                "item_cents": cents, "cents": cents + extras * cents / items_total,
                "line_cents": item["lineTotalCents"],
            })
    return pd.DataFrame(rows)


# ------------------------------------------------------------------ group plots

def save(fig, path):
    fig.savefig(path)
    plt.close(fig)
    print("  ", path)


def monthly_by_category(df, out):
    pivot = (df.groupby(["month", "category"])["cents"].sum().unstack(fill_value=0) / 100)
    pivot = pivot[pivot.sum().sort_values(ascending=False).index]
    fig, ax = plt.subplots(figsize=(9, 4.8))
    bottom = None
    for i, cat in enumerate(pivot.columns):
        ax.bar(pivot.index, pivot[cat], bottom=bottom, label=cat,
               color=CATEGORY_COLOURS[i % len(CATEGORY_COLOURS)], width=0.65)
        bottom = pivot[cat] if bottom is None else bottom + pivot[cat]
    for x, total in zip(pivot.index, pivot.sum(axis=1)):
        ax.text(x, total, f"${total:,.0f}", ha="center", va="bottom", fontsize=9, color=INK)
    ax.set_title("What the household spent each month, by aisle")
    ax.yaxis.set_major_formatter(DOLLARS)
    ax.set_xticks(range(len(pivot.index)), [datetime.strptime(m, "%Y-%m").strftime("%b %Y")
                                            for m in pivot.index])
    ax.grid(axis="x", visible=False)
    ax.legend(loc="upper left", bbox_to_anchor=(1, 1))
    save(fig, f"{out}/01_monthly_by_aisle.png")


def by_store(df, out):
    per_order = df.groupby(["store", "order"])["cents"].sum().reset_index()
    stats = per_order.groupby("store").agg(total=("cents", "sum"), orders=("order", "count"),
                                           basket=("cents", "mean")).sort_values("total")
    fig, (left, right) = plt.subplots(1, 2, figsize=(10, 4.2), gridspec_kw={"wspace": 0.55})
    names = [STORE_NAMES.get(s, s) for s in stats.index]
    colours = [STORE_COLOURS.get(s, MUTED) for s in stats.index]
    left.barh(names, stats["total"] / 100, color=colours)
    for y, (t, n) in enumerate(zip(stats["total"], stats["orders"])):
        left.text(t / 100, y, f"  ${t / 100:,.0f}  ({n} orders)", va="center", fontsize=9)
    left.set_title("Total spent at each store")
    left.xaxis.set_major_formatter(DOLLARS)
    left.set_xlim(0, stats["total"].max() / 100 * 1.45)
    left.grid(axis="y", visible=False)
    b = stats.sort_values("basket")
    right.barh([STORE_NAMES.get(s, s) for s in b.index], b["basket"] / 100,
               color=[STORE_COLOURS.get(s, MUTED) for s in b.index])
    for y, v in enumerate(b["basket"]):
        right.text(v / 100, y, f"  ${v / 100:,.2f}", va="center", fontsize=9)
    right.set_title("Average order size")
    right.xaxis.set_major_formatter(DOLLARS)
    right.set_xlim(0, b["basket"].max() / 100 * 1.35)
    right.grid(axis="y", visible=False)
    save(fig, f"{out}/02_stores.png")


def calendar(df, out):
    daily = df.groupby(df["date"].dt.date)["cents"].sum() / 100
    first = min(daily.index)
    first = first - pd.Timedelta(days=first.weekday())
    last = max(daily.index)
    weeks = (last - first).days // 7 + 1
    grid = [[float("nan")] * weeks for _ in range(7)]
    for day in pd.date_range(first, last):
        d = day.date()
        grid[d.weekday()][(d - first).days // 7] = daily.get(d, 0.0)
    fig, ax = plt.subplots(figsize=(10, 2.9))
    cmap = matplotlib.colors.LinearSegmentedColormap.from_list("amethyst", ["#F3ECF8", ACCENT])
    cmap.set_bad("#F1EDF4")
    masked = pd.DataFrame(grid).replace(0, float("nan")).values
    im = ax.imshow(masked, cmap=cmap, aspect="equal", vmin=0, vmax=daily.max())
    ax.set_yticks(range(7), ["Mon", "", "Wed", "", "Fri", "", "Sun"])
    months = {}
    for w in range(weeks):
        monday = first + pd.Timedelta(weeks=w)
        if monday.day <= 7 or w == 0 and monday.day > 24:
            months.setdefault(monday.strftime("%b"), w)
    if len(months) > 1 and list(months.values())[1] - list(months.values())[0] < 3:
        months.pop(next(iter(months)))
    ax.set_xticks(list(months.values()), list(months.keys()))
    ax.tick_params(length=0)
    ax.grid(False)
    for spine in ax.spines.values():
        spine.set_visible(False)
    ax.set_title("Which days the money went out")
    busiest = daily.groupby(pd.to_datetime(daily.index).day_name()).sum().idxmax()
    cbar = fig.colorbar(im, ax=ax, fraction=0.025, pad=0.02, format=DOLLARS)
    cbar.outline.set_visible(False)
    ax.text(0, 7.2, f"{len(daily)} shopping days. {busiest}s carry the most.",
            fontsize=9, color=MUTED, va="top")
    save(fig, f"{out}/03_calendar.png")


def sharing_network(df, group, out):
    colours = {m["id"]: m["colorHex"] for m in group["members"]}
    names = {m["id"]: m["name"] for m in group["members"]}
    pairs = defaultdict(float)
    subset = df[df["scope"] == "SUBSET"]
    for (_, _item), g in subset.groupby(["order", "item"]):
        ids = sorted(g["member"].tolist())
        for i in range(len(ids)):
            for j in range(i + 1, len(ids)):
                pairs[(ids[i], ids[j])] += g["item_cents"].sum() / 100
    totals = df.groupby("member")["cents"].sum() / 100
    G = nx.Graph()
    for m in totals.index:
        G.add_node(m)
    for (a, b), w in pairs.items():
        G.add_edge(a, b, weight=w)
    pos = nx.circular_layout(G)
    fig, ax = plt.subplots(figsize=(6.6, 6.2))
    heaviest = max(pairs.values()) if pairs else 1
    for (a, b), w in sorted(pairs.items(), key=lambda kv: kv[1]):
        ax.plot(*zip(pos[a], pos[b]), color=ACCENT, alpha=0.25 + 0.75 * w / heaviest,
                linewidth=1.5 + 14 * w / heaviest, solid_capstyle="round", zorder=1)
        # A third of the way along, so lines that cross in the middle keep their labels apart.
        mx, my = pos[a] + (pos[b] - pos[a]) * 0.32
        ax.text(mx, my, f"${w:,.0f}", ha="center", va="center", fontsize=9, color=INK,
                bbox=dict(boxstyle="round,pad=0.25", fc="white", ec="none"), zorder=3)
    for m, (x, y) in pos.items():
        ax.scatter(x, y, s=900 + 2200 * totals[m] / totals.max(), color=colours.get(m, MUTED),
                   edgecolor="white", linewidth=3, zorder=4)
        ax.text(x, y, names.get(m, "?")[0], ha="center", va="center", color="white",
                fontsize=15, fontweight="bold", zorder=5)
        ax.text(x * 1.62, y * 1.42, f"{names.get(m, '?')}\n${totals[m]:,.0f}", ha="center",
                va="center", fontsize=10.5, color=INK, fontweight="bold")
    ax.set_xlim(-2.0, 2.0)
    ax.set_ylim(-1.75, 1.75)
    ax.set_aspect("equal")
    ax.axis("off")
    ax.set_title("Who buys with whom\nLine = items two people shared without the rest of the house",
                 fontsize=12)
    save(fig, f"{out}/04_who_buys_with_whom.png")


def balances(df, group, out):
    names = {m["id"]: m["name"] for m in group["members"]}
    colours = {m["id"]: m["colorHex"] for m in group["members"]}
    owed = df.groupby("member")["cents"].sum()
    paid = df.groupby(["order", "payer"])["cents"].sum().reset_index().groupby("payer")["cents"].sum()
    net = {m: paid.get(m, 0) - owed.get(m, 0) for m in names}
    for p in group.get("payments", []):
        net[p["fromMemberId"]] = net.get(p["fromMemberId"], 0) + p["amountCents"]
        net[p["toMemberId"]] = net.get(p["toMemberId"], 0) - p["amountCents"]
    order = sorted(net, key=lambda m: net[m])
    fig, ax = plt.subplots(figsize=(8, 3.8))
    vals = [net[m] / 100 for m in order]
    ax.barh([names[m] for m in order], vals, color=[colours[m] for m in order])
    for y, v in enumerate(vals):
        ax.text(v, y, f"  is owed ${v:,.2f}  " if v >= 0 else f"  owes ${-v:,.2f}  ",
                va="center", ha="left" if v >= 0 else "right", fontsize=9)
    ax.axvline(0, color=MUTED, linewidth=1)
    span = max(abs(v) for v in vals) * 1.6
    ax.set_xlim(-span, span)
    ax.xaxis.set_major_formatter(mticker.FuncFormatter(lambda v, _: f"${abs(v):,.0f}"))
    ax.grid(axis="y", visible=False)
    ax.set_title("Where everyone stands, after payments back")
    # The shortest list of transfers that levels everyone, greedy largest-first.
    debt = sorted([[m, -net[m]] for m in net if net[m] < 0], key=lambda x: -x[1])
    cred = sorted([[m, net[m]] for m in net if net[m] > 0], key=lambda x: -x[1])
    moves = []
    while debt and cred:
        amount = min(debt[0][1], cred[0][1])
        moves.append(f"{names[debt[0][0]]} pays {names[cred[0][0]]} ${amount / 100:,.2f}")
        debt[0][1] -= amount
        cred[0][1] -= amount
        if debt[0][1] < 1:
            debt.pop(0)
        if cred and cred[0][1] < 1:
            cred.pop(0)
    fig.text(0.5, -0.04, "To settle up:  " + ";  ".join(moves), fontsize=10, color=INK,
             ha="center")
    save(fig, f"{out}/05_who_owes_whom.png")


def price_tracker(df, out):
    lines = df.drop_duplicates(["order", "item"])
    counts = lines.groupby("item")["order"].count().sort_values(ascending=False)
    top = [i for i in counts.index if lines[lines["item"] == i]["store"].nunique() >= 2][:6]
    fig, axes = plt.subplots(2, 3, figsize=(11, 6), sharex=True)
    for ax, item in zip(axes.flat, top):
        rows = lines[lines["item"] == item].sort_values("date")
        ax.plot(rows["date"], rows["line_cents"] / 100, color=GRID, linewidth=1.5, zorder=1)
        for store, g in rows.groupby("store"):
            ax.scatter(g["date"], g["line_cents"] / 100, color=STORE_COLOURS.get(store, MUTED),
                       s=28, zorder=2, label=STORE_NAMES.get(store, store))
        cheapest = rows.groupby("store")["line_cents"].mean().idxmin()
        ax.set_title(f"{short(item)}\ncheapest on average at {STORE_NAMES.get(cheapest, cheapest)}",
                     fontsize=10.5, linespacing=1.4)
        ax.yaxis.set_major_formatter(mticker.FuncFormatter(lambda v, _: f"${v:,.2f}"))
        ax.xaxis.set_major_formatter(matplotlib.dates.DateFormatter("%b"))
    handles = {}
    for ax in axes.flat:
        for h, l in zip(*ax.get_legend_handles_labels()):
            handles[l] = h
    fig.legend(handles.values(), handles.keys(), loc="lower center", ncol=len(handles),
               bbox_to_anchor=(0.5, -0.03))
    fig.suptitle("What regular items cost, store by store", x=0.01, ha="left",
                 fontweight="bold", fontsize=13)
    fig.tight_layout(rect=(0, 0.04, 1, 1))
    save(fig, f"{out}/06_prices_by_store.png")


def shared_vs_personal(df, out):
    label = {"COMMON": "For everyone", "SUBSET": "Split by a few", "PERSONAL": "One person's own"}
    pivot = df.groupby(["month", "scope"])["cents"].sum().unstack(fill_value=0)
    pivot = pivot.div(pivot.sum(axis=1), axis=0) * 100
    fig, ax = plt.subplots(figsize=(8.5, 4))
    bottom = None
    for scope, colour in [("COMMON", ACCENT), ("SUBSET", "#B39DE0"), ("PERSONAL", "#E6DDF5")]:
        if scope not in pivot:
            continue
        ax.bar(pivot.index, pivot[scope], bottom=bottom, color=colour, label=label[scope], width=0.6)
        for x, (b, v) in enumerate(zip(bottom if bottom is not None else [0] * len(pivot),
                                       pivot[scope])):
            if v > 7:
                ax.text(x, b + v / 2, f"{v:.0f}%", ha="center", va="center", fontsize=9,
                        color="white" if scope == "COMMON" else INK)
        bottom = pivot[scope] if bottom is None else bottom + pivot[scope]
    ax.set_xticks(range(len(pivot.index)), [datetime.strptime(m, "%Y-%m").strftime("%b")
                                            for m in pivot.index])
    ax.yaxis.set_major_formatter(mticker.PercentFormatter())
    ax.set_ylim(0, 100)
    ax.grid(False)
    ax.legend(loc="upper left", bbox_to_anchor=(1, 1))
    ax.set_title("How much of each month was shared")
    save(fig, f"{out}/07_shared_vs_personal.png")


def people_over_time(df, group, out):
    colours = {m["name"]: m["colorHex"] for m in group["members"]}
    pivot = df.groupby(["month", "person"])["cents"].sum().unstack(fill_value=0) / 100
    fig, ax = plt.subplots(figsize=(8.5, 4))
    for person in pivot.columns:
        ax.plot(range(len(pivot)), pivot[person], marker="o", linewidth=2.4,
                color=colours.get(person, MUTED), label=person)
    gap = (pivot.values.max() - pivot.values.min()) * 0.075
    placed = []
    for person, y in sorted(pivot.iloc[-1].items(), key=lambda kv: kv[1]):
        y = max(y, placed[-1] + gap) if placed else y
        placed.append(y)
        ax.text(len(pivot) - 1 + 0.1, y, f"{person}  ${pivot[person].iloc[-1]:,.0f}",
                va="center", color=colours.get(person, MUTED), fontsize=10, fontweight="bold")
    ax.set_xticks(range(len(pivot.index)), [datetime.strptime(m, "%Y-%m").strftime("%b")
                                            for m in pivot.index])
    ax.yaxis.set_major_formatter(DOLLARS)
    ax.set_xlim(-0.2, len(pivot) - 0.1)
    ax.set_title("Each person's share, month by month")
    save(fig, f"{out}/08_each_person_by_month.png")


# ------------------------------------------------------------------ one page per person

def person_page(df, group, member, out):
    me = member["name"]
    colour = member["colorHex"]
    mine = df[df["member"] == member["id"]]
    others = {m["id"]: m for m in group["members"] if m["id"] != member["id"]}
    fig = plt.figure(figsize=(11, 8.2))
    gs = fig.add_gridspec(2, 2, hspace=0.42, wspace=0.42)

    # 1. My spending against the household average.
    ax = fig.add_subplot(gs[0, 0])
    monthly = mine.groupby("month")["cents"].sum() / 100
    avg = df.groupby(["month", "member"])["cents"].sum().groupby("month").mean() / 100
    months = sorted(set(monthly.index) | set(avg.index))
    ax.bar(range(len(months)), [monthly.get(m, 0) for m in months], color=colour, width=0.6,
           label=me)
    ax.plot(range(len(months)), [avg.get(m, 0) for m in months], color=INK, marker="o",
            linewidth=1.5, label="Household average")
    ax.set_xticks(range(len(months)), [datetime.strptime(m, "%Y-%m").strftime("%b") for m in months])
    ax.yaxis.set_major_formatter(DOLLARS)
    ax.grid(axis="x", visible=False)
    ax.legend(loc="upper center", bbox_to_anchor=(0.5, -0.1), ncol=2, fontsize=8.5)
    ax.set_title("Your share each month")

    # 2. Where my money went.
    ax = fig.add_subplot(gs[0, 1])
    cats = (mine.groupby("category")["cents"].sum() / 100).sort_values()
    ax.barh(cats.index, cats.values, color=colour, alpha=0.85)
    for y, v in enumerate(cats.values):
        ax.text(v, y, f"  ${v:,.0f}", va="center", fontsize=8.5)
    ax.set_xlim(0, cats.max() * 1.3)
    ax.xaxis.set_major_formatter(DOLLARS)
    ax.grid(axis="y", visible=False)
    ax.set_title("Where it went")

    # 3. What I buy for myself most.
    ax = fig.add_subplot(gs[1, 0])
    own = mine[mine["scope"] == "PERSONAL"].groupby("item")["cents"].agg(["sum", "count"])
    own = own.sort_values("sum").tail(7)
    ax.barh([short(i, 22) for i in own.index], own["sum"] / 100, color=colour, alpha=0.65)
    for y, (v, n) in enumerate(zip(own["sum"], own["count"])):
        ax.text(v / 100, y, f"  ${v / 100:,.0f}  ×{n}", va="center", fontsize=8.5)
    if len(own):
        ax.set_xlim(0, own["sum"].max() / 100 * 1.45)
    ax.xaxis.set_major_formatter(DOLLARS)
    ax.grid(axis="y", visible=False)
    ax.set_title("Your own items, most spent")

    # 4. Who I share with, outside whole-house items.
    ax = fig.add_subplot(gs[1, 1])
    subset = df[df["scope"] == "SUBSET"]
    my_items = subset[subset["member"] == member["id"]][["order", "item"]].drop_duplicates()
    with_me = subset.merge(my_items, on=["order", "item"])
    with_me = with_me[with_me["member"] != member["id"]]
    together = with_me.groupby("member")["item_cents"].sum() / 100
    names = [others[m]["name"] for m in others]
    vals = [together.get(m, 0) for m in others]
    ax.bar(names, vals, color=[others[m]["colorHex"] for m in others], width=0.55)
    for x, v in enumerate(vals):
        ax.text(x, v, f"${v:,.0f}", ha="center", va="bottom", fontsize=9)
    ax.yaxis.set_major_formatter(DOLLARS)
    ax.grid(axis="x", visible=False)
    ax.set_title("Who you split things with")

    total = mine["cents"].sum() / 100
    share = 100 * mine["cents"].sum() / df["cents"].sum()
    fav = mine.groupby("store")["cents"].sum().idxmax()
    fig.suptitle(f"{me}:  ${total:,.0f} in all, {share:.0f}% of the household.  "
                 f"Most of it at {STORE_NAMES.get(fav, fav)}.",
                 x=0.06, ha="left", fontsize=15, fontweight="bold", color=colour)
    save(fig, f"{out}/{me.lower().replace(' ', '_')}.png")


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    group = load(sys.argv[1])
    out = sys.argv[2]
    os.makedirs(f"{out}/group", exist_ok=True)
    os.makedirs(f"{out}/people", exist_ok=True)
    df = shares(group)
    if df.empty:
        sys.exit("No finished orders in that backup yet.")
    print(f"{group['household']['name']}: {df['order'].nunique()} orders, "
          f"${df['cents'].sum() / 100:,.2f}")
    print("group:")
    monthly_by_category(df, f"{out}/group")
    by_store(df, f"{out}/group")
    calendar(df, f"{out}/group")
    sharing_network(df, group, f"{out}/group")
    balances(df, group, f"{out}/group")
    price_tracker(df, f"{out}/group")
    shared_vs_personal(df, f"{out}/group")
    people_over_time(df, group, f"{out}/group")
    print("people:")
    for member in group["members"]:
        if not member.get("isArchived"):
            person_page(df, group, member, f"{out}/people")


if __name__ == "__main__":
    main()
