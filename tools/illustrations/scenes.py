"""Generates the three introduction illustrations as VectorDrawables.

Written as a generator rather than typed out by hand because the scenes share a
vocabulary (receipts, coins, cards, arrows) and because the coordinates are arithmetic:
a zigzag receipt foot is a loop, not eighteen numbers to get right by eye.
"""

PAPER      = "#FFFFFF"
PAPER_EDGE = "#DCD6EA"
INK        = "#B4AEC6"
INK_DARK   = "#6F6889"
PINK       = "#F2939C"
PINK_DARK  = "#DE7079"
YELLOW     = "#F7CE68"
YEL_DARK   = "#E5B33F"
CARD       = "#B77FE3"
CARD_DARK  = "#9760C7"
GOLD       = "#F0B429"
GOLD_DARK  = "#CE951C"
GOLD_LIGHT = "#F8D67A"
SLATE      = "#343A5E"
SLATE_MID  = "#4A5178"
SLATE_KEY  = "#6A719C"
ORANGE     = "#F5A623"

def rect(x, y, w, h, r=0):
    if r <= 0:
        return f"M{x},{y} h{w} v{h} h{-w} z"
    return (f"M{x+r},{y} h{w-2*r} a{r},{r} 0 0 1 {r},{r} v{h-2*r} "
            f"a{r},{r} 0 0 1 {-r},{r} h{-(w-2*r)} a{r},{r} 0 0 1 {-r},{-r} "
            f"v{-(h-2*r)} a{r},{r} 0 0 1 {r},{-r} z")

def circle(cx, cy, r):
    return (f"M{cx},{cy-r} a{r},{r} 0 1 0 0.01,0 z")

def receipt(x, y, w, h, teeth=5, tooth=3.0):
    """A till roll: square top, zigzag foot."""
    step = w / teeth
    d = [f"M{x},{y}", f"h{w}", f"v{h}"]
    for i in range(teeth):
        d.append(f"l{-step/2:.2f},{-tooth}")
        d.append(f"l{-step/2:.2f},{tooth}")
    d.append("z")
    return " ".join(d)

def path(d, colour, alpha=None):
    extra = f'\n            android:fillAlpha="{alpha}"' if alpha is not None else ""
    return (f'        <path\n            android:fillColor="{colour}"{extra}\n'
            f'            android:pathData="{d}" />\n')

def group(children, rotation=0, pivot=(60, 60), translate=(0, 0), scale=None):
    attrs = [f'android:pivotX="{pivot[0]}"', f'android:pivotY="{pivot[1]}"']
    if rotation:
        attrs.append(f'android:rotation="{rotation}"')
    if translate != (0, 0):
        attrs.append(f'android:translateX="{translate[0]}"')
        attrs.append(f'android:translateY="{translate[1]}"')
    if scale:
        attrs.append(f'android:scaleX="{scale[0]}"')
        attrs.append(f'android:scaleY="{scale[1]}"')
    inner = "".join(children)
    joined = "\n              ".join(attrs)
    return f'    <group\n              {joined}>\n{inner}    </group>\n'

def vector(note, body, size=128):
    return (f"<!--\n{note}\n-->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size}dp"\n'
            f'    android:height="{size}dp"\n'
            '    android:viewportWidth="120"\n'
            '    android:viewportHeight="120">\n'
            f"{body}"
            "</vector>\n")

def coin(cx, cy, r):
    """A coin, seen face on: rim, face, and a real currency mark.

    The mark was a plain vertical bar to begin with, which at this size reads as a capital
    I stamped on a disc. Small does not mean it can be suggested rather than drawn."""
    return (path(circle(cx, cy, r), GOLD_DARK)
            + path(circle(cx, cy, r - 1.4), GOLD)
            + path(circle(cx, cy, r - 3.2), GOLD_LIGHT)
            + dollar(cx, cy, r * 1.5, GOLD_DARK, max(0.9, r * 0.14)))


def torso(cx, cy, w, h):
    """Shoulders: rounded across the top, square at the foot.

    A rounded rect with a generous radius reads as a circle at this size, and a circle
    under a circle is a snowman rather than a person."""
    r = w * 0.48
    x, y = cx - w / 2, cy - h / 2
    return (f"M{x},{y+h} V{y+r} A{r},{r} 0 0 1 {x+r},{y} "
            f"H{x+w-r} A{r},{r} 0 0 1 {x+w},{y+r} V{y+h} z")

def arrow(x, y, length, thickness, head, flip=False):
    """A blunt arrow. Drawn pointing right, mirrored by the caller's group."""
    s = -1 if flip else 1
    tip = x + s * length
    back = x
    half = thickness / 2
    return (f"M{back},{y-half} L{tip - s*head},{y-half} L{tip - s*head},{y-half-head*0.55} "
            f"L{tip},{y} L{tip - s*head},{y+half+head*0.55} L{tip - s*head},{y+half} "
            f"L{back},{y+half} z")

def stroke(d, colour, width, cap="round"):
    return (f'        <path\n            android:strokeColor="{colour}"\n'
            f'            android:strokeWidth="{width}"\n'
            f'            android:strokeLineCap="{cap}"\n'
            f'            android:strokeLineJoin="round"\n'
            f'            android:fillColor="#00000000"\n'
            f'            android:pathData="{d}" />\n')

def dollar(cx, cy, height, colour, width):
    """A real currency mark: an S drawn as three cubics, with a bar through it.

    The first attempt built this from a vertical bar and two horizontal ones, which
    renders as a double dagger rather than a dollar. Curves are the only way to get an S.
    """
    q = height / 8.0
    a = height * 0.20
    s = (f"M{cx+a:.2f},{cy-3*q:.2f} "
         f"C{cx+a:.2f},{cy-4.3*q:.2f} {cx-a:.2f},{cy-4.3*q:.2f} {cx-a:.2f},{cy-2*q:.2f} "
         f"C{cx-a:.2f},{cy+0.2*q:.2f} {cx+a:.2f},{cy-0.2*q:.2f} {cx+a:.2f},{cy+2*q:.2f} "
         f"C{cx+a:.2f},{cy+4.3*q:.2f} {cx-a:.2f},{cy+4.3*q:.2f} {cx-a:.2f},{cy+3*q:.2f}")
    bar = f"M{cx:.2f},{cy-4.9*q:.2f} L{cx:.2f},{cy+4.9*q:.2f}"
    return stroke(s, colour, width) + stroke(bar, colour, width)

def person(cx, cy, scale, shirt, skin):
    """Head and shoulders. The torso is a rounded rect with a generous top radius, which
    reads as a pair of shoulders where a semicircle reads as a blob."""
    head_r = 6.4 * scale
    body_w = 24.0 * scale
    body_h = 15.0 * scale
    return (path(circle(cx, cy - body_h / 2 - head_r * 1.05, head_r), skin)
            + path(torso(cx, cy, body_w, body_h), shirt))
