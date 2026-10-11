"""Scrape Rutgers New Brunswick dining hall menus from Nutrislice.

Writes a compact snapshot bundled with the app so menus still show offline:
    src/main/resources/com/mealmenu/data/dining-snapshot.json

The app fetches the same API live at runtime; this snapshot is only a fallback.
Usage: python scripts/scrape_dining.py [YYYY-MM-DD]   (defaults to today)
"""
import datetime as dt
import json
import pathlib
import sys
import time
import urllib.request

API = "https://rutgers.api.nutrislice.com/menu/api"
ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/com/mealmenu/data/dining-snapshot.json"
SKIP_ICONS = {"Low Carbon Footprint", "Medium Carbon Footprint", "High Carbon Footprint"}


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "RutgersEats/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def compact_week(raw):
    """Turn a Nutrislice week payload into {date: [{station, items}]}, same shape the app uses."""
    days = {}
    for day in raw.get("days", []):
        stations = []
        current = None
        for entry in day.get("menu_items", []):
            if entry.get("is_section_title"):
                name = (entry.get("text") or "").strip() or "Other"
                current = next((s for s in stations if s["station"] == name), None)
                if current is None:
                    current = {"station": name, "items": []}
                    stations.append(current)
                continue
            food = entry.get("food")
            if not food or not food.get("name"):
                continue
            if current is None:
                current = {"station": "Menu", "items": []}
                stations.append(current)
            nutrition = food.get("rounded_nutrition_info") or {}
            serving = food.get("serving_size_info") or {}
            icons = [i.get("name") for i in (food.get("icons") or {}).get("food_icons", [])]
            item = {
                "name": food["name"].strip(),
                "cal": nutrition.get("calories"),
                "protein": nutrition.get("g_protein"),
                "carbs": nutrition.get("g_carbs"),
                "fat": nutrition.get("g_fat"),
                "serving": " ".join(x for x in (serving.get("serving_size_amount"),
                                                serving.get("serving_size_unit")) if x) or None,
                "icons": [i for i in icons if i and i not in SKIP_ICONS],
            }
            if all(existing["name"] != item["name"] for existing in current["items"]):
                current["items"].append(item)
        stations = [s for s in stations if s["items"]]
        if stations:
            days[day["date"]] = stations
    return days


def main():
    start = dt.date.fromisoformat(sys.argv[1]) if len(sys.argv) > 1 else dt.date.today()
    schools = get(f"{API}/schools/")
    halls, weeks = [], []
    for school in schools:
        geo = school.get("geolocation") or {}
        hours = {d: None if not school.get(f"{d}_enabled") else
                 f"{school[f'{d}_start'][:5]}-{school[f'{d}_end'][:5]}"
                 for d in ("mon", "tue", "wed", "thu", "fri", "sat", "sun")}
        halls.append({
            "slug": school["slug"], "name": school["name"],
            "address": school.get("address"), "phone": school.get("phone_number"),
            "lat": geo.get("latitude"), "lng": geo.get("longitude"), "hours": hours,
            "meals": [{"slug": m["slug"], "name": m["name"]}
                      for m in school.get("active_menu_types", [])],
        })
        for meal in school.get("active_menu_types", []):
            url = (f"{API}/weeks/school/{school['slug']}/menu-type/{meal['slug']}/"
                   f"{start.year}/{start.month:02d}/{start.day:02d}/")
            days = compact_week(get(url))
            weeks.append({"hall": school["slug"], "meal": meal["slug"], "days": days})
            print(f"{school['name']:28} {meal['slug']:22} {len(days)} days", flush=True)
            time.sleep(0.3)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    snapshot = {"source": f"{API}/", "generatedAt": dt.datetime.now().isoformat(timespec="seconds"),
                "halls": halls, "weeks": weeks}
    OUT.write_text(json.dumps(snapshot, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"wrote {OUT} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
