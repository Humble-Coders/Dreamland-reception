# Dreamland Firestore Schema Reference

> **Source of truth:** `src/schema.js`
> This file mirrors the schema in human-readable form.
> When you update `src/schema.js`, update this file too.
> Last updated: 2026-05-26 (full audit: fixed orders/complaints/staff fields, expanded settings, added billing collection, added groupStayId/sourceId)

---

## Architecture Principles

- All operational collections are **flat (no subcollections)** — every screen fetches one collection, filtered by `hotelId` and status.
- Every operational document contains `hotelId` for multi-hotel filtering.
- Optimized for real-time updates, Kotlin Multiplatform, and Jetpack Compose.

---

# PART 1 — Hotel Listing Platform

Collections used by the public-facing hotel discovery app.

---

## Collection: `hotels`

### Basic Info
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `name` | string | ✅ | Hotel name |
| `description` | string | — | Long description |
| `hotelType` | string | ✅ | From `hotelTypes` lookup collection; supports custom values |
| `starRating` | number | ✅ | 1–5 |
| `isActive` | boolean | — | Default: `true` |
| `isLuxury` | boolean | — | Default: `false` |
| `totalRooms` | number | — | Total room count |

### Contact
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `contactPhone` | string | ✅ | |
| `contactEmail` | string | ✅ | |
| `socialLinks` | string | — | Social media links (free-form string) |
| `website` | string | — | Full URL |

### Check-In / Check-Out
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `checkInTime` | string | ✅ | |
| `checkOutTime` | string | ✅ | |
| `earlyCheckInAllowed` | boolean | — | Default: `false` |
| `earlyCheckInPrice` | number | — | Default: `0` |
| `lateCheckOutAllowed` | boolean | — | Default: `false` |
| `lateCheckOutPrice` | number | — | Default: `0` |

### Location
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `address` | string | ✅ | Street address |
| `city` | string | ✅ | |
| `country` | string | ✅ | |
| `pincode` | string | — | ZIP / postal code |
| `latitude` | number | — | Stored separately from GeoPoint |
| `longitude` | number | — | Stored separately from GeoPoint |

### Media
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `photos` | string[] | ✅ | Min 1 item; Firebase Storage URLs |

### Transport (`modes` array)
Each item in `modes`:
| Field | Type | Notes |
|-------|------|-------|
| `category` | enum | `BUS` \| `TRAIN` \| `AIRPORT` \| `METRO` |
| `name` | string | Station / route name |
| `distance` | string | Human-readable e.g. "2.5 km" |
| `distanceInKm` | number | Numeric km |
| `detailed` | string | e.g. "15 mins by cab" |
| `cab` | boolean | Cab available |
| `auto` | boolean | Auto-rickshaw available |

### Food & Dining
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `mealPlansAvailable` | object[] | — | Each: `value` (`Breakfast`\|`FullBoard`\|`HB`\|`RO`), `price` (₹, default `0`) |

### Parking
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `parkingAvailable` | boolean | — | Default: `false` |
| `parkingType` | string | — | From `parkingTypes` lookup collection; supports custom values |
| `parkingSpots` | number | — | Total spots |

### Property Highlights (`highlights` array)
Each item:
| Field | Type | Notes |
|-------|------|-------|
| `title` | string | Highlight name |
| `category` | string | From `highlightCategories` lookup collection; supports custom values |
| `amenityType` | string | From `amenityTypes` lookup collection; supports custom values |

### Policies & Compliance
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `pdfRequired` | boolean | — | Default: `false` |
| `privacyPremium` | boolean | — | Default: `false` |
| `ageRestriction` | number | — | Min age, e.g. `18` |
| `petPolicy` | boolean | — | Default: `false` |

### Ratings *(read-only — updated by Cloud Functions)*
| Field | Type | Notes |
|-------|------|-------|
| `averageRating` | number | Overall average |
| `totalReviews` | number | Count |
| `ratingBreakdown.cleanliness` | number | 0–5 |
| `ratingBreakdown.food` | number | 0–5 |
| `ratingBreakdown.staff` | number | 0–5 |
| `ratingBreakdown.location` | number | 0–5 |
| `ratingBreakdown.value` | number | 0–5 |

### Meta
| Field | Type | Notes |
|-------|------|-------|
| `status` | enum | `ACTIVE` \| `INACTIVE` \| `DRAFT` |
| `openingDate` | timestamp | Hotel opening date |
| `closingDate` | timestamp | Hotel closing / seasonal date |
| `createdAt` | timestamp | Read-only |
| `updatedAt` | timestamp | Read-only |

---

## Collection: `config`
| Field | Type | Notes |
|-------|------|-------|
| `document` | string | Policy document URL |
| `support` | string | Support page URL |
| `privacyCenter` | string | Privacy centre URL |
| `termsAndConditions` | string | T&C URL |
| `footerPage` | string | Footer content |
| `carousel` | boolean | Show hero carousel |

---

## Subcollection: `hotels/{hotelId}/reviews`
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `hotelId` | string | ✅ | Parent hotel reference |
| `userId` | string | ✅ | Reviewer user ID |
| `rating` | number | ✅ | 1–5 overall |
| `cleanliness` | number | — | 0–5 |
| `food` | number | — | 0–5 |
| `staff` | number | — | 0–5 |
| `location` | number | — | 0–5 |
| `value` | number | — | 0–5 |
| `comment` | string | — | Text review |
| `createdAt` | timestamp | — | Read-only |

---

## Collection: `attractions`
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `hotelId` | string | ✅ | Parent hotel ID |
| `name` | string | ✅ | Attraction name |
| `category` | string | ✅ | From `attractionCategories` lookup collection; supports custom values |
| `description` | string | — | Travel info |
| `pictureable` | boolean | — | Default: `false` |
| `media` | string | — | Photo URL |

---

## Collection: `categories`
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `hotelId` | string | ✅ | Parent hotel ID |
| `name` | string | ✅ | Category label |
| `icon` | string | — | Emoji icon |
| `description` | string | — | Optional description |

---

## Collection: `travelList` *(Activities)*
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `hotelId` | string | ✅ | Parent hotel ID |
| `title` | string | ✅ | Activity name |
| `category` | string | — | Free-form string category |
| `description` | string | — | Brief description |
| `duration` | string | — | e.g. "2 hours" |
| `price` | number | — | Price in ₹; `null` if free |
| `included` | boolean | — | Default: `false` |
| `media` | string | — | Photo URL |

---

## Collection: `rooms`
Represents a **room category/type** (not a specific room number).

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `hotelId` | string | ✅ | Parent hotel ID |
| `name` | string | ✅ | Room category name |
| `description` | string | — | |
| `capacity` | number | ✅ | Guests included |
| `maxOccupancy` | number | — | |
| `price` | number | ✅ | Price per night (₹); read by reception as `pricePerNight` |
| `tax` | number | — | Tax (%); read by reception as `taxPercentage` |
| `breakfastPrice` | number | — | Breakfast price per person per night (₹); used by reception billing |
| `available` | boolean | — | Default: `true` |
| `bedType` | string | — | From `bedTypes` lookup collection; supports custom values |
| `noOfBeds` | number | — | |
| `view` | string | — | From `viewTypes` lookup collection; supports custom values |
| `roomSizeSqft` | number | — | |
| `floor` | string | — | |
| `bathroomType` | string | — | From `bathroomTypes` lookup collection; supports custom values |
| `smokingAllowed` | boolean | — | Default: `false` |
| `accessibilityFeatures` | string[] | — | Names from `roomAccessibilityFeatures` |
| `connectedRooms` | boolean | — | Default: `false` |
| `extraGuestCharge` | number | — | Extra guest charge (₹) |
| `weekendPricing.fri` | number | — | Friday price (₹) |
| `weekendPricing.sat` | number | — | Saturday price (₹) |
| `minStayNights` | number | — | Min stay in nights |
| `seasonalPricing[]` | object[] | — | Each: `label`, `from` (MM-DD), `to` (MM-DD), `price` (₹) |
| `freeCancellation` | boolean | — | Default: `false` |
| `cancellationPolicy.freeBefore` | number | — | Free before (hours) |
| `cancellationPolicy.refundPercent` | number | — | Refund percent |
| `cancellationPolicy.policyNote` | string | — | Policy note |
| `amenities` | string[] | — | Names from `roomAmenities` |
| `media` | string[] | — | Photos |
| `complimentaryBenefits` | string[] | — | Names from `roomComplimentaryBenefits` |
| `purchasableBenefits` | string[] | — | Names from `roomPurchasableBenefits` |
| `createdAt` | timestamp | — | Read-only |
| `updatedAt` | timestamp | — | Read-only |

---

## Collection: `roomInstances`
Represents a **specific physical room** (e.g. room number 101) tied to a room category.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `hotelId` | string | ✅ | Parent hotel ID |
| `categoryId` | string | ✅ | `rooms/{categoryId}` |
| `categoryName` | string | — | Denormalized from `rooms` |
| `roomNumber` | string | ✅ | Room number/identifier |
| `status` | enum | ✅ | `AVAILABLE` \| `CLEANING` \| `MAINTENANCE` |
| `currentStayId` | string | — | ID of the currently active stay; `null` when no guest is in the room |
| `createdAt` | timestamp | — | Read-only |

> **`OCCUPIED` is a display label only** — it is never written to Firestore. The reception app derives "occupied" client-side: if a room's `roomInstanceId` appears in any `stays` document with `status = ACTIVE`, that room is shown as Occupied. No `ASSIGNED` status exists either; linking a booking to a room only writes `roomInstanceId` on the booking document while the room stays `AVAILABLE` in the DB.

---

## Lookup Collections
Seeded automatically on first use. Each document has a `name` field and `createdAt` timestamp.

| Collection | Purpose | Default Seed Values |
|------------|---------|---------------------|
| `hotelTypes` | Hotel type options | `resort`, `boutique`, `hostel`, `villa`, `homestay` |
| `parkingTypes` | Parking type options | `covered`, `shared`, `guest`, `valet` |
| `highlightCategories` | Property highlight categories | `hotel`, `shared`, `outdoor`, `cultural`, `water` |
| `amenityTypes` | General amenity type labels | `basic`, `shared` |
| `attractionCategories` | Attraction categories | `religious`, `nature`, `shopping`, `food`, `heritage`, `adventure` |
| `bedTypes` | Room bed type options | `single`, `twin`, `double`, `queen`, `king`, `bunk` |
| `viewTypes` | Room view options | `pool`, `garden`, `sea`, `city`, `mountain`, `courtyard` |
| `bathroomTypes` | Bathroom type options | `attached`, `shared`, `en-suite` |
| `roomAmenities` | Room amenity options | `Air Conditioning`, `Free WiFi`, `Smart TV`, `Mini Bar`, `In-Room Safe`, `Hair Dryer`, `Bathrobe & Slippers`, `Iron & Board`, `Coffee Maker`, `Work Desk`, `Balcony`, `Private Pool`, `Jacuzzi`, `Rainfall Shower`, `Blackout Curtains`, `Room Service`, `Daily Housekeeping` |
| `roomAccessibilityFeatures` | Accessibility options | `Wheelchair Accessible`, `Elevator Access`, `Roll-in Shower`, `Grab Bars`, `Visual Alarms`, `Braille Signage`, `Wide Doorways` |
| `roomComplimentaryBenefits` | Complimentary add-ons | `Breakfast Included`, `Welcome Drink`, `Evening Snacks`, `Newspaper`, `Airport Pickup`, `Early Check-in`, `Late Check-out` |
| `roomPurchasableBenefits` | Purchasable add-ons | `Spa Package`, `Romantic Room Setup`, `Decorated Room`, `Extra Bed`, `Airport Transfer`, `Private Dining`, `Bonfire Setup` |
| `bookingSources` | Booking source options (**global — no `hotelId`**) | `APP`, `WALK_IN`, `OTA`, `Phone`, `Email` |

---

## Required Fields Per Wizard Section
| Section | Required Fields |
|---------|----------------|
| Basic Info | `name`, `hotelType`, `starRating` |
| Contact | `contactPhone`, `contactEmail` |
| Check-In/Out | `checkInTime`, `checkOutTime` |
| Location | `address`, `city`, `country` |
| Media | `photos` (min 1) |

---

---

# PART 2 — Reception Operations

Collections used by the Dreamland Reception desktop app. All flat — no subcollections.

---

## Collection: `bookings`

| Field | Type | Notes |
|-------|------|-------|
| `hotelId` | string | |
| `hotelName` | string | Denormalized for display |
| `userId` | string | Booking user (agent / walk-in) |
| `userName` | string | |
| `roomCategoryId` | string | Ref to `rooms` |
| `roomCategoryName` | string | Denormalized |
| `roomInstanceId` | string | Empty at booking creation; set only when receptionist manually assigns a room via "Assign Room" in the reception app |
| `roomNumber` | string | Empty at booking creation; set alongside `roomInstanceId` when room is manually assigned |
| `guestDetails.name` | string | |
| `guestDetails.phone` | string | |
| `checkInDate` | timestamp | |
| `checkOutDate` | timestamp | |
| `status` | enum | `CONFIRMED` \| `CANCELLED` \| `NO_SHOW` \| `COMPLETED` |
| `guests.adults` | number | |
| `guests.children` | number | |
| `totalAmount` | number | ₹ |
| `advancePaidAmount` | number | ₹ |
| `options.breakfast.included` | boolean | |
| `options.breakfast.pricePerDay` | number | ₹ |
| `options.earlyCheckIn.enabled` | boolean | |
| `options.earlyCheckIn.charge` | number | ₹ |
| `options.lateCheckOut.enabled` | boolean | |
| `options.lateCheckOut.charge` | number | ₹ |
| `notes` | string | |
| `groupBookingId` | string | Optional; links grouped bookings; empty string when not part of a group |
| `source` | string | Free-form string; references a `name` from the `bookingSources` lookup collection |
| `sourceId` | string | Document ID from `bookingSources`; empty for legacy records |
| `createdAt` | timestamp | |
| `noShowMarkedAt` | timestamp | Set when `status` transitions to `NO_SHOW`; `null` otherwise |
| `noShowRefundStatus` | string | `""` (no advance) \| `"PENDING"` \| `"REFUNDED"` \| `"FORFEITED"` \| `"PARTIAL"` |
| `noShowRefundNote` | string | Free-form note recorded by receptionist (e.g. "Refunded via UPI on 04 May") |

> **No-show rules:** Marking `NO_SHOW` is final and irreversible. The receptionist is shown a confirmation dialog displaying the grace-period deadline (hotel check-in time + 12 hours) before confirming. If the booking had an advance payment, the status is set immediately to `NO_SHOW` with `noShowRefundStatus = "PENDING"`, and the receptionist is then prompted to record the refund outcome (`REFUNDED / FORFEITED / PARTIAL`). This outcome is saved back to the same booking document via `noShowMarkedAt`, `noShowRefundStatus`, and `noShowRefundNote`.

---

## Collection: `stays`

Active and completed guest stays. Created at check-in (walk-in or from-booking). Options are synced from the booking at check-in.

| Field | Type | Notes |
|-------|------|-------|
| `hotelId` | string | |
| `bookingId` | string | Ref to `bookings`; empty string for walk-ins |
| `guestName` | string | Primary guest name (denormalized from `guests[0].name`) |
| `guestPhone` | string | Primary guest phone (denormalized from `guests[0].phone`) |
| `roomInstanceId` | string | Ref to `roomInstances` |
| `roomNumber` | string | Denormalized |
| `roomCategoryId` | string | Derived from `roomInstances/{id}.categoryId` at check-in |
| `roomCategoryName` | string | Denormalized from `roomInstances/{id}.categoryName` |
| `checkInActual` | timestamp | Actual check-in datetime |
| `expectedCheckOut` | timestamp | Expected check-out date (midnight UTC) |
| `checkOutActual` | timestamp | `null` while active; set at check-out |
| `status` | enum | `ACTIVE` \| `COMPLETED` |
| `adults` | number | |
| `children` | number | |
| `breakfast` | boolean | |
| `extraBed` | boolean | |
| `earlyCheckIn` | boolean | |
| `lateCheckOut` | boolean | |
| `earlyCheckInCharge` | number | ₹; `0` if not applicable |
| `lateCheckOutCharge` | number | ₹; `0` if not applicable |
| `advanceAmount` | number | ₹ paid at check-in |
| `totalBilled` | number | ₹ total from final bill |
| `specialRequests` | string | Free-form guest requests |
| `createdAt` | timestamp | |
| `guests` | object[] | All guests on the stay — see **GuestRecord** below |
| `groupStayId` | string | Links related stays in a group checkout; empty string when not part of a group |

### GuestRecord (nested array in `guests`)
| Field | Type | Notes |
|-------|------|-------|
| `name` | string | Guest full name |
| `phone` | string | Phone number; populated for primary guest (index 0); optional for additional guests |
| `idProofVerified` | boolean | Whether ID proof was verified at check-in |

> **Primary guest:** `guests[0]` is always the primary guest. `guestName` and `guestPhone` on the stay document are denormalized copies of `guests[0].name` and `guests[0].phone` for backwards-compatible queries.
>
> **Source:** `guests[]` is populated at check-in from the walk-in form's guest entries. For from-booking check-ins, `guests[0]` is pre-filled from the booking's `guestName`/`guestPhone`; additional guests are entered at the time of check-in.
>
> **Atomic write rule:** Stay creation and `roomInstances/{id}.currentStayId` update are performed in a single Firestore **batch write**. `OCCUPIED` is never stored in Firestore — it is derived client-side from active stays.

---

## Reception App — Availability Algorithm

The Rooms screen and Check Availability panel compute available room counts **client-side** using the following steps. No Cloud Functions are involved.

1. Query `bookings` where `hotelId = X`, `status = CONFIRMED`, `checkInDate < requestedCheckOut`, `checkOutDate > requestedCheckIn`. Group by `roomCategoryId` → committed bookings per category.
2. Query `stays` where `hotelId = X`, `status = ACTIVE`, `expectedCheckOut > requestedCheckIn`. Group by `roomCategoryId` → committed stays per category.
3. For each category, count `roomInstances` where `hotelId = X`, `categoryId = cat.id`, `status NOT IN (MAINTENANCE, CLEANING)` → usable physical rooms. (`OCCUPIED` is not a DB status; occupancy is captured by Step 2.)
4. `availableRooms = usable − (committed bookings + committed stays)`.
5. Keep only categories where `availableRooms > 0`, `capacity ≥ requestedGuests`, `status ≠ maintenance`.
6. Return remaining categories with `pricePerNight` from the `rooms` document.

---

## Collection: `orders`

Room-service, laundry, and other in-stay service orders.

| Field | Type | Notes |
|-------|------|-------|
| `hotelId` | string | |
| `stayId` | string | |
| `roomNumber` | string | Denormalized |
| `guestName` | string | Denormalized |
| `type` | enum | `ROOM_SERVICE` \| `ORDER` \| `SERVICE` |
| `items[]` | object[] | Each: `name`, `price` (₹), `quantity` |
| `totalAmount` | number | ₹ |
| `status` | enum | `NEW` \| `ASSIGNED` \| `COMPLETED` |
| `notes` | string | |
| `orderedAt` | timestamp | |
| `assignedTo` | string | Staff ID |
| `assignedToName` | string | Denormalized staff name |

---

## Collection: `complaints`

| Field | Type | Notes |
|-------|------|-------|
| `hotelId` | string | |
| `stayId` | string | |
| `guestName` | string | Denormalized |
| `roomNumber` | string | Denormalized |
| `type` | string | Ref to `complaintTypes` document ID |
| `description` | string | |
| `priority` | enum | `HIGH` \| `MEDIUM` \| `LOW` |
| `status` | enum | `NEW` \| `ASSIGNED` \| `COMPLETED` |
| `assignedTo` | string | Staff ID |
| `assignedToName` | string | Denormalized staff name |
| `reportedAt` | timestamp | |
| `resolvedAt` | timestamp | `null` until resolved |

---

## Collection: `staff`

| Field | Type | Notes |
|-------|------|-------|
| `hotelId` | string | |
| `name` | string | |
| `email` | string | |
| `phone` | string | |
| `role` | enum | `HOUSEKEEPING` \| `MAINTENANCE` \| `RECEPTION` |
| `department` | string | |
| `shift` | string | `morning` \| `afternoon` \| `night` |
| `joiningDate` | timestamp | |
| `isActive` | boolean | |
| `isAvailable` | boolean | Currently on shift and free |
| `salary` | number | ₹ |

---

## Collection: `services`

Add-on services offered by the hotel (e.g. laundry, extra beds).

| Field | Type | Notes |
|-------|------|-------|
| `hotelId` | string | |
| `name` | string | e.g. `Laundry`, `Extra Bed` |
| `price` | number | ₹ |
| `isActive` | boolean | |
| `createdAt` | timestamp | |

---

## Collection: `foodItems`

Menu items available for room-service orders.

| Field | Type | Notes |
|-------|------|-------|
| `hotelId` | string | |
| `name` | string | |
| `price` | number | ₹ |
| `category` | string | e.g. `Fast Food`, `Beverages` |
| `isAvailable` | boolean | |
| `createdAt` | timestamp | |

---

## Collection: `complaintTypes`

Lookup table for complaint categories.

| Field | Type | Notes |
|-------|------|-------|
| `hotelId` | string | |
| `name` | string | e.g. `AC Issue` |
| `description` | string | |
| `isActive` | boolean | |
| `createdAt` | timestamp | |

---

## Collection: `bills`

Detailed billing records for guest stays. One document per stay, created at check-out.

| Field | Type | Notes |
|-------|------|-------|
| `hotelId` | string | |
| `stayId` | string | Primary stay ref (single and group bills) |
| `stayIds` | string[] | All stay IDs for group bills; empty for single-stay bills |
| `guestName` | string | Denormalized |
| `roomNumber` | string | `"22"` for single or `"22, 23, 24"` for group (denormalized) |
| `roomNumbers` | string[] | Individual room numbers for group bills e.g. `["22","23","24"]`; empty for single-room bills |
| `checkInDate` | timestamp | |
| `checkOutDate` | timestamp | |
| `items` | object[] | See `BillItem` below |
| `taxEnabled` | boolean | |
| `taxPercentage` | number | e.g. `18` |
| `discountType` | enum | `FLAT` \| `PERCENT` |
| `discountValue` | number | ₹ or % |
| `subtotal` | number | Sum of all item totals |
| `taxAmount` | number | Computed from subtotal × taxPercentage |
| `discountAmount` | number | Computed discount |
| `totalAmount` | number | subtotal + taxAmount - discountAmount |
| `transactions` | object[] | See `PaymentTransaction` below |
| `totalPaid` | number | Sum of transaction amounts |
| `advancePayment` | number | Carried from check-in billing record |
| `pendingAmount` | number | totalAmount - totalPaid - advancePayment |
| `status` | enum | `PENDING` \| `PARTIAL` \| `PAID` |
| `createdAt` | timestamp | |
| `updatedAt` | timestamp | |

### BillItem (nested array)
| Field | Type | Notes |
|-------|------|-------|
| `id` | string | UUID |
| `name` | string | |
| `type` | enum | `ROOM` \| `ORDER` \| `SERVICE` \| `CUSTOM` |
| `quantity` | number | |
| `unitPrice` | number | ₹ |
| `total` | number | unitPrice × quantity |
| `refId` | string | Optional reference to source document |
| `notes` | string | |

### PaymentTransaction (nested array)
| Field | Type | Notes |
|-------|------|-------|
| `id` | string | UUID |
| `amount` | number | ₹ |
| `method` | enum | `CASH` \| `UPI` \| `CARD` |
| `status` | string | `PAID` |
| `createdAt` | timestamp | |

---

## Collection: `settings`

Per-hotel operational configuration. Document ID = `hotelId`.

| Field | Type | Notes |
|-------|------|-------|
| `hotelId` | string | |
| `hotelName` | string | Denormalized hotel name |
| `currency` | string | e.g. `INR` |
| `contactInfo` | string | Free-form contact details |
| `taxPercentage` | number | Default tax % e.g. `18` |
| `taxEnabled` | boolean | Whether tax applies by default |
| `defaultDiscountType` | string | `PERCENTAGE` \| `FLAT` |
| `defaultDiscountValue` | number | Default discount amount |
| `checkInTime` | string | e.g. `12:00` |
| `checkOutTime` | string | e.g. `11:00` |
| `autoAssignRoom` | boolean | Auto-assign room on booking confirmation |
| `breakfastEnabled` | boolean | Whether breakfast option is offered |
| `breakfastPricePerPerson` | number | ₹ per person per night |
| `extraBedEnabled` | boolean | |
| `extraBedPrice` | number | ₹ |
| `earlyCheckInEnabled` | boolean | |
| `earlyCheckInCharge` | number | ₹ |
| `lateCheckOutEnabled` | boolean | |
| `lateCheckOutCharge` | number | ₹ |

---

## Collection: `billing`

Legacy per-stay invoice records (created by `BillingRepository` / `BillingInvoice` model). Superseded by the `bills` collection for new stays but still queried for older records.

| Field | Type | Notes |
|-------|------|-------|
| `bookingId` | string | Ref to `bookings` |
| `stayId` | string | Ref to `stays` |
| `guestName` | string | Denormalized |
| `roomNumber` | string | Denormalized |
| `roomCharges` | number | ₹ |
| `serviceCharges` | number | ₹ |
| `earlyCheckInCharge` | number | ₹ |
| `lateCheckOutCharge` | number | ₹ |
| `tax` | number | ₹ computed tax |
| `discount` | number | ₹ |
| `totalAmount` | number | ₹ |
| `amountPaid` | number | ₹ |
| `paymentMethod` | string | `cash` \| `card` \| `upi` \| `bank_transfer` |
| `status` | enum | `PENDING` \| `PARTIAL` \| `PAID` |
| `issuedAt` | timestamp | |
| `paidAt` | timestamp | `null` until fully paid |

---

## Real-Time Listeners (Reception App)

| Collection | Filter | Consumer |
|------------|--------|----------|
| `orders` | `status == NEW` | Kitchen / staff dashboard |
| `complaints` | `status == NEW` | Maintenance / manager view |
| `bookings` | new documents | Reception live feed |
| `stays` | `status == ACTIVE` | Front-desk occupancy view |
