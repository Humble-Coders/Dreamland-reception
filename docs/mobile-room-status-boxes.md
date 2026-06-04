# Mobile Guide — Room Status Boxes (read-only)

A short guide to recreate the reception dashboard's **room grid** in the mobile app, as an
**informative, read-only** view for admins (no cleaning/housekeeping controls).

Reference implementation: `DashboardScreen.kt → RoomGridSection / RoomCard` in this repo.

---

## 1. Data you need (Firestore)

Two flat collections, both filtered by `hotelId`:

- **`roomInstances`** — one doc per physical room:
  - `roomNumber` (string), `categoryId`, `categoryName`, `status`
    (`AVAILABLE | OCCUPIED | CLEANING | MAINTENANCE | ASSIGNED`), `needsCleaning` (bool),
    `currentStayId` (string|null).
- **`stays`** — active guest stays:
  - `roomInstanceId`, `roomNumber`, `guestName`, `guestPhone`, `expectedCheckOut`,
    `roomCategoryName`, `status` (`ACTIVE | COMPLETED`).

Queries:
```
roomInstances: where hotelId == X
stays:         where hotelId == X and status == ACTIVE
```
Use real-time listeners so tiles update live.

---

## 2. Deriving a room's display status

> **Important:** occupancy is **derived from active stays**, not from the `status` field.
> At check-in the app only links the room via `currentStayId`; it does not flip `status` to `OCCUPIED`.

For each room:
```
val stay = activeStays.firstOrNull { it.roomInstanceId == room.id }
val isOccupied = stay != null

displayStatus = when {
    isOccupied             -> OCCUPIED
    room.status == CLEANING -> CLEANING
    room.status == MAINTENANCE -> MAINTENANCE
    else                   -> AVAILABLE
}
```

---

## 3. Colour legend (match the reception app)

| Status | Colour | Hex |
|--------|--------|-----|
| Available | green | `#4CAF50` |
| Occupied | red | `#EF5350` |
| Cleaning | blue | `#2196F3` |
| Maintenance | amber | `#F39C12` |

Tile style: subtle surface background, a 1px border in the status colour (≈35% alpha), and a small
status pill (coloured dot + label).

---

## 4. Tile contents

- **Always:** `Room <number>` + a **room-type** chip
  (`room.categoryName`, falling back to `stay.roomCategoryName` for occupied rooms) + status pill.
- **Occupied only:** guest name, phone, and `Check-out · <expectedCheckOut>`.
- **No buttons.** This is informational on mobile — drop the cleaning/available toggle entirely.

---

## 5. Layout rules

- **Sort ascending by room number, numeric-aware** (so `2 < 10 < 101`):
  ```
  rooms.sortedWith(compareBy({ it.roomNumber.toIntOrNull() ?: Int.MAX_VALUE }, { it.roomNumber }))
  ```
- **Equal-sized tiles:** fixed height + equal width. On phones use a **2-column grid**
  (e.g. `LazyVerticalGrid(GridCells.Fixed(2))`); on tablets allow 3–4 columns.
- Comfortable spacing (~12–16dp gap, 12–14dp corner radius).

---

## 6. Minimal reference snippet (Compose)

```kotlin
@Composable
fun RoomTile(room: RoomInstance, stay: Stay?) {
    val occupied = stay != null
    val color = when {
        occupied -> Color(0xFFEF5350)
        room.status == "CLEANING" -> Color(0xFF2196F3)
        room.status == "MAINTENANCE" -> Color(0xFFF39C12)
        else -> Color(0xFF4CAF50)
    }
    val label = when {
        occupied -> "OCCUPIED"
        room.status == "CLEANING" -> "CLEANING"
        room.status == "MAINTENANCE" -> "MAINTENANCE"
        else -> "AVAILABLE"
    }
    val type = room.categoryName.ifBlank { stay?.roomCategoryName.orEmpty() }

    Column(
        Modifier.fillMaxWidth().height(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(surfaceColor)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Room ${room.roomNumber}", fontWeight = FontWeight.Bold); if (type.isNotBlank()) TypeChip(type) }
            StatusPill(label, color)   // coloured dot + label
        }
        Spacer(Modifier.weight(1f))
        if (occupied && stay != null) {
            Text(stay.guestName); if (stay.guestPhone.isNotBlank()) Text(stay.guestPhone)
            Text("Check-out · ${formatDate(stay.expectedCheckOut)}")
        }
    }
}
```

For non-Compose stacks (Flutter/React Native), the logic is identical — only the widget code differs:
derive status → pick colour → render an equal-sized card with the pill and (if occupied) guest info.

---

## 7. Optional

- Tapping a tile can open a read-only detail sheet (guest, dates, room type). Keep it view-only.
- A small legend row (the four colour chips) helps first-time admins.
