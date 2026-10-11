"""Parse the free-text `hours` of each restaurant into a weekly schedule.

    parse_hours("Mon–Thu 12pm–3:30pm, 5pm–9pm; Fri–Sat 12pm–10pm; Sun closed")
    -> {"mon": [[720, 930], [1020, 1260]], ..., "sun": []}

Minutes since midnight; an end past midnight is stored as > 1440 (e.g. 2am -> 1560).
Returns None when the text can't be parsed confidently, so the app shows "hours unknown"
instead of guessing.
"""
import re

DAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]
DAY_ALIASES = {d: i for i, d in enumerate(DAYS)}
DASH = r"\s*[-–—~]\s*"
TIME = r"(\d{1,2})(?::(\d{2}))?\s*(am|pm)?"
RANGE_RE = re.compile(TIME + DASH + TIME, re.I)
DAY_RE = re.compile(r"\b(mon|tue|wed|thu|fri|sat|sun)[a-z]*\b", re.I)


def _minutes(hour, minute, meridiem):
    hour, minute = int(hour), int(minute or 0)
    if meridiem:
        meridiem = meridiem.lower()
        if hour == 12:
            hour = 0
        if meridiem == "pm":
            hour += 12
    return hour * 60 + minute


def _ranges(text):
    out = []
    for m in RANGE_RE.finditer(text):
        h1, m1, ap1, h2, m2, ap2 = m.groups()
        if not ap1 and not ap2:
            return None
        # "5:00-9:30 PM" / "12-9 PM": a missing meridiem on the start borrows the end's,
        # unless that would make the start later than the end (11-2 PM -> 11 AM).
        if not ap1:
            ap1 = ap2
            if _minutes(h1, m1, ap1) > _minutes(h2, m2, ap2):
                ap1 = "am" if ap2.lower() == "pm" else "pm"
        if not ap2:
            ap2 = ap1
        start, end = _minutes(h1, m1, ap1), _minutes(h2, m2, ap2)
        if end <= start:
            end += 24 * 60  # closes after midnight
        out.append([start, end])
    return out


def _days(spec):
    """'Mon-Thu & Sat', 'Mon-Tue, Thu-Sun', 'Daily', 'weekdays' -> set of day indexes."""
    spec = spec.lower()
    if "daily" in spec or "every day" in spec:
        return set(range(7))
    if "weekday" in spec:
        return set(range(5))
    if "weekend" in spec:
        return {5, 6}
    found = set()
    for part in re.split(r"[,&/]|\band\b", spec):
        names = [DAY_ALIASES[m.group(1).lower()] for m in DAY_RE.finditer(part)]
        if len(names) == 1:
            found.add(names[0])
        elif len(names) == 2 and re.search(DASH, part):
            a, b = names
            i = a
            while True:
                found.add(i)
                if i == b:
                    break
                i = (i + 1) % 7
        elif len(names) > 2:
            found.update(names)
    return found


def parse_hours(text):
    if not text:
        return None
    clean = re.sub(r"\(.*?\)", "", text)          # drop "(Yelp lists …)" notes
    clean = re.sub(r"^[^:]*:\s*", "", clean) if re.match(r"^[A-Za-z ]+:", clean) else clean
    schedule = {d: None for d in DAYS}
    # Split into clauses at ';' or at ', <Day>' boundaries.
    pieces = re.split(r";|,(?=\s*(?:mon|tue|wed|thu|fri|sat|sun|daily)[a-z]*\b)", clean, flags=re.I)
    # "Mon-Tue, Thu-Sun 11-9": a day list without times belongs to the next piece.
    clauses, pending = [], ""
    for piece in pieces:
        if re.search(r"\d", piece) or "closed" in piece.lower():
            clauses.append(pending + "," + piece if pending else piece)
            pending = ""
        else:
            pending = pending + "," + piece if pending else piece
    for clause in clauses:
        clause = clause.strip()
        if not clause:
            continue
        if "takeout" in clause.lower() or "delivery" in clause.lower():
            continue  # secondary service hours; dine-in clause already covers the day
        first_time = re.search(r"\d", clause)
        day_part = clause[:first_time.start()] if first_time else clause
        days = _days(day_part)
        if "closed" in clause.lower() and not first_time:
            for d in days:
                schedule[DAYS[d]] = []
            continue
        ranges = _ranges(clause)
        if not ranges:
            return None
        if not days:
            # No day words at all (e.g. "Online ordering 11:30 AM-11:15 PM"): every day.
            if day_part.strip() and DAY_RE.search(day_part) is None and len(clauses) == 1:
                days = set(range(7))
            else:
                return None
        for d in days:
            schedule[DAYS[d]] = ranges
    if all(v is None for v in schedule.values()):
        return None
    # Days the source never mentions stay None ("unknown"), not "closed".
    return schedule


if __name__ == "__main__":
    import json, pathlib, sys
    root = pathlib.Path(__file__).resolve().parents[1]
    data = json.loads((root / "src/main/resources/com/mealmenu/data/restaurants.json").read_text(encoding="utf-8"))
    for r in data:
        parsed = parse_hours(r.get("hours"))
        fmt = "UNPARSED" if parsed is None else " ".join(
            f"{d}:" + (",".join(f"{a // 60}:{a % 60:02d}-{b // 60}:{b % 60:02d}" for a, b in v) or "closed")
            for d, v in parsed.items() if v is not None) + "  unknown:" + ",".join(
            d for d, v in parsed.items() if v is None)
        print(f"{r['id'][:34]:34} {fmt}")
