#!/usr/bin/env python3
"""Builds `app/src/main/res/font/noto_sans_arabic_digits.ttf`, ten glyphs and nothing else.

## Why a second font at all

The clock's Eastern Arabic numeral option needs U+0660–0669 (ARABIC-INDIC DIGIT ZERO..NINE), and
**Google Sans Flex does not have them.** That is not a subsetting mistake in `build_gsflex.py`: the
family genuinely does not cover Arabic. `fonts.googleapis.com` serves Google Sans Flex in exactly
these subsets:

    canadian-aboriginal, cherokee, latin, latin-ext, math, nushu, symbols, syriac, tifinagh,
    vietnamese

The bundled binary's 517 glyphs contain neither U+0660–0669 nor the Persian forms at U+06F0–06F9.
Checked, not assumed.

## Why Noto Sans Arabic, and why variable

Noto is Google's own companion family for the scripts Google Sans does not cover, and it is OFL
like everything else this suite ships. The part that matters is that it is **variable on `wght`
100–900**. A static Arabic face would sit at one thickness while the Latin numerals around it move
with the app's weight, and the dial would look like two fonts. This one tracks.

Subsetted to the ten digits it is about 8 KB, against roughly 300 KB for the whole Arabic subset.

Requires: pip install fonttools brotli
Usage:    python3 tools/build_noto_digits.py
"""

import pathlib
import re
import urllib.request

from fontTools import subset
from fontTools.ttLib import TTFont

CSS = "https://fonts.googleapis.com/css2?family=Noto+Sans+Arabic:wght@100..900"
# gstatic serves a different file per script; the digits live in the `arabic` one.
WANTED_SUBSET = "arabic"
DIGITS = list(range(0x0660, 0x066A))

OUT = pathlib.Path(__file__).parent.parent / "app/src/main/res/font/noto_sans_arabic_digits.ttf"

# Without a browser UA, gstatic answers with a static TTF and no `fvar` at all, the same trap
# `build_gsflex.py` documents. The weight axis is the whole reason for choosing this font.
UA = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)


def fetch(url: str) -> bytes:
    return urllib.request.urlopen(
        urllib.request.Request(url, headers={"User-Agent": UA})
    ).read()


def main() -> None:
    css = fetch(CSS).decode()
    blocks = re.split(r"/\* ([a-z-]+) \*/", css)
    url = None
    for i in range(1, len(blocks), 2):
        if blocks[i] == WANTED_SUBSET:
            url = re.search(r"src:\s*url\((https://[^)]+)\)", blocks[i + 1]).group(1)
            break
    if url is None:
        raise SystemExit(f"no {WANTED_SUBSET!r} subset in the CSS, so Google changed the family")

    tmp = pathlib.Path("/tmp/noto-sans-arabic.woff2")
    tmp.write_bytes(fetch(url))

    font = TTFont(tmp)
    if "fvar" not in font:
        raise SystemExit("got a static instance: the weight axis is missing, check the UA")

    options = subset.Options(layout_features=["*"], name_IDs=["*"], notdef_outline=True)
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=DIGITS)
    subsetter.subset(font)
    font.flavor = None  # woff2 in, plain TTF out; Android cannot load woff2
    OUT.parent.mkdir(parents=True, exist_ok=True)
    font.save(OUT)

    check = TTFont(OUT)
    have = [i for i in range(10) if 0x0660 + i in check.getBestCmap()]
    axes = [(a.axisTag, a.minValue, a.maxValue) for a in check["fvar"].axes]
    if have != list(range(10)):
        raise SystemExit(f"missing digits: {set(range(10)) - set(have)}")
    print(f"→ {OUT.name}  {OUT.stat().st_size} bytes, digits {have}, axes {axes}")


if __name__ == "__main__":
    main()
