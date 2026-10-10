#!/usr/bin/env python3
"""
Rebuilds app/src/main/res/font/google_sans_flex.ttf.

Google Sans Flex is OFL-1.1 but is *not* in the google/fonts repository. The only public source
is fonts.gstatic.com, which serves it split into per-script woff2 subsets. Android cannot load
woff2, and no single subset covers the app's needs, so this downloads the subsets we want,
converts them to TTF and merges them by hand.

By hand because fontTools' own merger cannot merge variable fonts: it fails on the HVAR VarStore,
and dropping HVAR to get past that makes it discard `fvar` entirely, which loses the `opsz` axis,
the whole reason we ship this font. Since every subset is cut from one source they share an
identical design space, so copying glyphs plus their `gvar` variations across is safe.

Requires: pip install fonttools brotli
"""
import re, urllib.request, copy, io, os, sys
from fontTools.ttLib import TTFont, newTable
from fontTools.ttLib.tables._c_m_a_p import CmapSubtable

UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
CSS = (
    "https://fonts.googleapis.com/css2?family=Google+Sans+Flex:"
    "GRAD,ROND,opsz,slnt,wdth,wght@0..100,0..100,6..144,-10..0,25..151,1..1000"
)

# ALL SIX axes are requested, unlike the Notes build.
#
# Asking for `opsz,wght` alone does not merely omit the others: fonts.gstatic.com serves a
# *partial instance*, with wdth/GRAD/ROND/slnt flattened to their defaults and gone from fvar.
# Notes was right to do that: the published type scale pins those axes anyway. This app is not,
# because the whole design rests on `wdth`, which reaches 25 and is narrow enough for the
# concept's numerals without a single hand-drawn glyph.
WANT = ["latin", "latin-ext"]
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "font", "google_sans_flex.ttf")


def fetch(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA})).read()


def subsets():
    css = fetch(CSS).decode()
    parts = re.split(r"/\*\s*([\w-]+)\s*\*/", css)
    out = {}
    for i in range(1, len(parts), 2):
        m = re.search(r"url\((https://[^)]+)\)", parts[i + 1])
        if m and parts[i] in WANT:
            out[parts[i]] = m.group(1)
    return out


def as_ttf(data):
    f = TTFont(io.BytesIO(data))
    f.flavor = None
    buf = io.BytesIO()
    f.save(buf)
    buf.seek(0)
    return TTFont(buf)


def main():
    urls = subsets()
    missing = [s for s in WANT if s not in urls]
    if missing:
        sys.exit(f"subsets not served: {missing}")

    base = as_ttf(fetch(urls["latin"]))
    # Without HVAR, advance widths vary from gvar's phantom points instead (correct, just less
    # compact). MVAR only varies vertical metrics. Neither can be merged across fonts.
    for t in ("HVAR", "MVAR"):
        if t in base:
            del base[t]

    bg, bhmtx, bgvar = base["glyf"], base["hmtx"], base["gvar"].variations
    full = dict(base.getBestCmap())
    order = list(base.getGlyphOrder())
    have = set(order)

    def pull(name, sglyf, shmtx, sgvar):
        if name in have:
            return
        have.add(name)
        order.append(name)
        g = sglyf[name]
        g.expand(sglyf)
        if g.isComposite():
            for c in g.components:
                pull(c.glyphName, sglyf, shmtx, sgvar)
        bg.glyphs[name] = g
        bhmtx.metrics[name] = shmtx.metrics[name]
        if name in sgvar:
            bgvar[name] = copy.deepcopy(sgvar[name])

    for s in WANT[1:]:
        f = as_ttf(fetch(urls[s]))
        sglyf, shmtx, sgvar = f["glyf"], f["hmtx"], f["gvar"].variations
        for n in f.getGlyphOrder():
            if n != ".notdef":
                pull(n, sglyf, shmtx, sgvar)
        for cp, gn in f.getBestCmap().items():
            full.setdefault(cp, gn)
        f.close()

    bg.setGlyphOrder(order)
    base.setGlyphOrder(order)
    base["maxp"].numGlyphs = len(order)

    # Format 4 tops out at U+FFFF and the math subset reaches into the astral planes, so a
    # format 12 subtable is required alongside it.
    bmp = {cp: g for cp, g in full.items() if cp <= 0xFFFF}
    s4 = CmapSubtable.newSubtable(4)
    s4.platformID, s4.platEncID, s4.language, s4.cmap = 3, 1, 0, bmp
    s12 = CmapSubtable.newSubtable(12)
    s12.platformID, s12.platEncID, s12.language, s12.cmap = 3, 10, 0, dict(full)
    s12.format, s12.reserved, s12.length, s12.nGroups = 12, 0, 0, 0
    cm = newTable("cmap")
    cm.tableVersion, cm.tables = 0, [s4, s12]
    base["cmap"] = cm

    if "post" in base and getattr(base["post"], "formatType", 0) == 2.0:
        base["post"].glyphOrder = order
        base["post"].extraNames = []
        base["post"].mapping = {}

    base.save(OUT)
    check = TTFont(OUT)
    print(f"wrote {OUT}")
    print(f"  glyphs={len(check.getGlyphOrder())} codepoints={len(check.getBestCmap())}")
    print(f"  axes={[(a.axisTag, a.minValue, a.maxValue) for a in check['fvar'].axes]}")


if __name__ == "__main__":
    main()
