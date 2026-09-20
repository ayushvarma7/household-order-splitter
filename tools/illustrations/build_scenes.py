import sys, pathlib
sys.path.insert(0, sys.argv[1])
from scenes import *

OUT = pathlib.Path("app/src/main/res/drawable")

def bill(w, h, lines, mark=True):
    """A white till roll with ruled lines and, optionally, a currency mark."""
    body = [path(receipt(0, 0, w, h, 5, h * 0.055), PAPER)]
    for i, frac in enumerate(lines):
        body.append(path(rect(w * 0.16, h * 0.13 + i * h * 0.115,
                              w * frac, h * 0.045, h * 0.023), INK))
    if mark:
        body.append(dollar(w * 0.5, h * 0.70, h * 0.30, INK_DARK, h * 0.045))
    return body

def scene_capture():
    parts = []
    # Behind, on the right: the till.
    parts.append(group(
        [path(rect(0, 0, 44, 58, 6), SLATE),
         path(rect(5, 6, 34, 14, 2.5), PAPER),
         path(rect(8, 10.5, 13, 2.4, 1.2), YELLOW),
         path(rect(24, 10.5, 12, 2.4, 1.2), YELLOW)]
        + [path(rect(6 + (i % 3) * 11.5, 26 + (i // 3) * 10.5, 8.5, 7.5, 2),
                [SLATE_KEY, SLATE_KEY, SLATE_KEY,
                 PINK, SLATE_KEY, YELLOW,
                 SLATE_KEY, CARD, SLATE_KEY][i]) for i in range(9)],
        rotation=-5, pivot=(22, 29), translate=(70, 40)))

    # Behind, on the left: two slips, so the bill is plainly one of several.
    parts.append(group([path(receipt(0, 0, 26, 44, 4, 2.6), PINK),
                        path(rect(5, 9, 15, 2.4, 1.2), PINK_DARK),
                        path(rect(5, 16, 11, 2.4, 1.2), PINK_DARK),
                        path(rect(5, 23, 14, 2.4, 1.2), PINK_DARK)],
                       rotation=-20, pivot=(13, 22), translate=(2, 32)))
    parts.append(group([path(receipt(0, 0, 21, 34, 4, 2.4), YELLOW),
                        path(rect(4, 7, 11, 2.2, 1.1), YEL_DARK),
                        path(rect(4, 13, 8, 2.2, 1.1), YEL_DARK)],
                       rotation=-8, pivot=(10, 17), translate=(24, 30)))

    # The bill itself, in front of both and behind the card.
    parts.append(group(bill(42, 62, [0.52, 0.34, 0.44, 0.26]),
                       rotation=4, pivot=(21, 31), translate=(40, 2)))

    # The card, across the front.
    parts.append(group([path(rect(0, 3, 60, 36, 5), CARD_DARK),
                        path(rect(0, 0, 60, 36, 5), CARD),
                        path(rect(6, 7, 11, 8.5, 2), YELLOW),
                        path(rect(6, 21, 24, 2.8, 1.4), PAPER),
                        path(rect(6, 27.5, 8, 2.6, 1.3), SLATE),
                        path(rect(17, 27.5, 8, 2.6, 1.3), SLATE),
                        path(rect(28, 27.5, 8, 2.6, 1.3), SLATE),
                        path(circle(45, 18, 6.5), PAPER),
                        path(circle(51, 18, 6.5), YELLOW, alpha="0.92")],
                       rotation=-12, pivot=(30, 18), translate=(26, 62)))

    parts.append(coin(15, 103, 10.5))
    parts.append(coin(32, 111, 7.5))
    parts.append(group([path(arrow(0, 0, 21, 7, 7), ORANGE)],
                       rotation=-22, pivot=(10, 0), translate=(3, 80)))
    parts.append(group([path(arrow(0, 0, 19, 7, 7, flip=True), ORANGE)],
                       rotation=-16, pivot=(-9, 0), translate=(117, 96)))
    return "".join(parts)

def scene_people():
    parts = []
    # One bill, a dashed line down it, and a person either side.
    parts.append(group(bill(38, 56, [0.50, 0.32, 0.42], mark=False),
                       rotation=0, pivot=(19, 28), translate=(41, 8)))
    parts += [path(rect(58.6, 12 + i * 8.2, 2.8, 4.8, 1.4), CARD) for i in range(7)]
    parts.append(person(22, 100, 1.6, PINK, "#FBDFCB"))
    parts.append(person(98, 100, 1.6, CARD, "#F3D2B8"))
    parts.append(coin(20, 56, 10.5))
    parts.append(coin(100, 56, 10.5))
    # No arrows on this one. There is no room between the bill and a face for one, and
    # nothing for them to add: the perforation already says the bill divides, and a coin
    # over each person already says who gets which half.
    return "".join(parts)

def scene_done():
    parts = []
    parts.append(group(bill(46, 68, [0.50, 0.34, 0.44, 0.28]),
                       rotation=-6, pivot=(23, 34), translate=(20, 6)))
    parts.append(group([path(circle(0, 0, 23), "#2F9268"),
                        path(circle(0, 0, 19.5), "#4FC08D"),
                        path("M-9,0.5 L-3,7 L9.5,-6 L12.2,-3 L-3,13.5 L-11.8,3.5 z", PAPER)],
                       translate=(90, 86)))
    parts.append(coin(17, 96, 10.5))
    parts.append(coin(34, 106, 7.5))
    parts.append(group([path(arrow(0, 0, 17, 6.5, 6.5), ORANGE)],
                       rotation=-12, pivot=(8, 0), translate=(46, 104)))
    return "".join(parts)

NOTE = """  An introduction illustration, generated rather than drawn by hand.

  The three scenes share a vocabulary of receipts, coins, cards, arrows and people, and
  the coordinates are arithmetic: a till roll's zigzag foot is a loop rather than eighteen
  numbers to line up by eye, and a currency mark is three cubics rather than a guess. The
  generator is scenes.py, in the commit that added these.

  Multicoloured on purpose, so nothing here may be tinted at the usage. The colours sit on
  the introduction's palette wash, which is dark by construction: paper is white, the
  accents are warm, and no colour in the set is close enough to purple to sink into the
  background on the Amethyst or Plum themes."""

for name, body in (("ic_welcome_capture", scene_capture()),
                   ("ic_welcome_people", scene_people()),
                   ("ic_welcome_done", scene_done())):
    (OUT / f"{name}.xml").write_text(vector(NOTE, body))
    print("wrote", name)
