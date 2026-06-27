"use strict";

// FamCal Cloud Function: serves a read-only iCalendar (.ics) feed for a family,
// so the calendar can be subscribed to from Google Calendar or Outlook.
// The feed is protected by the family's secret feedToken (passed as a query param).

const { onRequest } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

admin.initializeApp();

exports.calendarFeed = onRequest({ cors: true }, async (req, res) => {
  const familyId = String(req.query.familyId || "");
  const token = String(req.query.token || "");
  if (!familyId || !token) {
    res.status(400).send("Missing familyId or token");
    return;
  }

  const db = admin.firestore();
  const familySnap = await db.collection("families").doc(familyId).get();
  if (!familySnap.exists || familySnap.get("feedToken") !== token) {
    res.status(403).send("Forbidden");
    return;
  }

  const family = familySnap.data();
  const eventsSnap = await db
    .collection("families")
    .doc(familyId)
    .collection("events")
    .orderBy("startAt")
    .get();

  const events = eventsSnap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
  const ics = buildIcs(family.name || "FamCal", familyId, events);

  res.set("Content-Type", "text/calendar; charset=utf-8");
  res.set("Cache-Control", "public, max-age=3600");
  res.status(200).send(ics);
});

function buildIcs(calendarName, familyId, events) {
  const lines = [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//FamCal//EN",
    "CALSCALE:GREGORIAN",
    "METHOD:PUBLISH",
    `X-WR-CALNAME:${escapeText(calendarName)}`,
  ];

  for (const event of events) {
    lines.push(...buildVevent(event, familyId));
  }

  lines.push("END:VCALENDAR");
  // iCalendar lines are CRLF-terminated.
  return lines.join("\r\n") + "\r\n";
}

function buildVevent(event, familyId) {
  const start = toDate(event.startAt);
  const end = toDate(event.endAt) || start;
  if (!start) return [];

  const lines = ["BEGIN:VEVENT", `UID:${event.id}@${familyId}.famcal`];
  lines.push(`DTSTAMP:${formatUtc(new Date())}`);

  if (event.allDay) {
    lines.push(`DTSTART;VALUE=DATE:${formatDate(start)}`);
    // For all-day events DTEND is exclusive; add a day.
    const endExclusive = new Date(end.getTime() + 24 * 60 * 60 * 1000);
    lines.push(`DTEND;VALUE=DATE:${formatDate(endExclusive)}`);
  } else {
    lines.push(`DTSTART:${formatUtc(start)}`);
    lines.push(`DTEND:${formatUtc(end)}`);
  }

  const rrule = toRrule(event.recurrence);
  if (rrule) lines.push(rrule);

  lines.push(`SUMMARY:${escapeText(event.title || "(untitled)")}`);
  if (event.location) lines.push(`LOCATION:${escapeText(event.location)}`);
  if (event.notes) lines.push(`DESCRIPTION:${escapeText(event.notes)}`);

  lines.push("END:VEVENT");
  return lines;
}

function toRrule(recurrence) {
  switch (recurrence) {
    case "DAILY":
      return "RRULE:FREQ=DAILY";
    case "WEEKLY":
      return "RRULE:FREQ=WEEKLY";
    case "MONTHLY":
      return "RRULE:FREQ=MONTHLY";
    default:
      return null;
  }
}

function toDate(value) {
  if (!value) return null;
  if (typeof value.toDate === "function") return value.toDate();
  return null;
}

function pad(n) {
  return String(n).padStart(2, "0");
}

function formatUtc(date) {
  return (
    date.getUTCFullYear() +
    pad(date.getUTCMonth() + 1) +
    pad(date.getUTCDate()) +
    "T" +
    pad(date.getUTCHours()) +
    pad(date.getUTCMinutes()) +
    pad(date.getUTCSeconds()) +
    "Z"
  );
}

function formatDate(date) {
  return (
    date.getUTCFullYear() + pad(date.getUTCMonth() + 1) + pad(date.getUTCDate())
  );
}

function escapeText(text) {
  return String(text)
    .replace(/\\/g, "\\\\")
    .replace(/;/g, "\\;")
    .replace(/,/g, "\\,")
    .replace(/\r?\n/g, "\\n");
}
