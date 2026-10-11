"""Validate data/restaurants/*.json and merge them into the app resource.

Each source file is a JSON array of restaurants collected from real menu pages
(every entry keeps its `menuSource` URL and `verifiedOn` date).
Usage: python scripts/build_restaurants.py
"""
import json
import pathlib
import sys

from hours import parse_hours

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "data/restaurants"
OUT = ROOT / "src/main/resources/com/mealmenu/data/restaurants.json"
REQUIRED = ("id", "name", "cuisine", "area", "address", "lat", "lng", "menuSource")

# Coarse groups used by the app's filters (display keeps the detailed cuisine / area).
CUISINE_GROUPS = [
    ("Hot Pot", ("hot pot",)),
    ("Bakery & Cafe", ("bakery", "cafe", "dessert")),
    ("Chinese", ("chinese", "sichuan", "hunan", "shanghai", "cantonese", "dim sum", "lanzhou",
                 "malatang", "taiwanese", "dumpling")),
    ("Japanese", ("japanese", "ramen", "sushi")),
    ("Korean", ("korean",)),
    ("Indian", ("indian", "biryani")),
    ("Halal & Mediterranean", ("halal", "middle eastern", "mediterranean", "turkish", "falafel")),
    ("Mexican", ("mexican", "taco")),
    ("Pizza", ("pizza",)),
    ("American", ("american", "sandwich", "burger", "diner", "hot dog")),
]
AREA_GROUPS = {
    "Downtown New Brunswick": "New Brunswick",
    "Busch / Piscataway": "Busch & Livingston",
    "Livingston / Piscataway": "Busch & Livingston",
    "Highland Park": "Highland Park",
}


def cuisine_group(cuisine):
    lower = cuisine.lower()
    for group, keys in CUISINE_GROUPS:
        if any(k in lower for k in keys):
            return group
    return "Other"


def main():
    merged, seen, problems = [], set(), []
    for path in sorted(SRC.glob("*.json")):
        for r in json.loads(path.read_text(encoding="utf-8")):
            missing = [k for k in REQUIRED if r.get(k) in (None, "")]
            if missing:
                problems.append(f"{path.name}: {r.get('name')} missing {missing}")
                continue
            if r["id"] in seen:
                problems.append(f"{path.name}: duplicate id {r['id']}")
                continue
            if not (39.5 < r["lat"] < 41.5 and -75.5 < r["lng"] < -73.5):
                problems.append(f"{path.name}: {r['name']} coordinates outside central NJ")
                continue
            r["cuisineGroup"] = cuisine_group(r["cuisine"])
            r["areaGroup"] = AREA_GROUPS.get(r["area"], "Edison & nearby")
            r["openHours"] = parse_hours(r.get("hours"))
            r["menu"] = [s for s in (r.get("menu") or []) if s.get("items")]
            seen.add(r["id"])
            merged.append(r)
    merged.sort(key=lambda r: (r["areaGroup"], r["name"]))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(merged, ensure_ascii=False, indent=1), encoding="utf-8")
    items = sum(len(s["items"]) for r in merged for s in r["menu"])
    print(f"wrote {len(merged)} restaurants, {items} menu items -> {OUT.relative_to(ROOT)}")
    for p in problems:
        print("SKIPPED:", p, file=sys.stderr)


if __name__ == "__main__":
    main()
