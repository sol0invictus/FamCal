"use strict";

// FamCal Cloud Function: serves a read-only iCalendar (.ics) feed for a family,
// so the calendar can be subscribed to from Google Calendar or Outlook.
// The feed is protected by the family's secret feedToken (passed as a query param).

const { onRequest } = require("firebase-functions/v2/https");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
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

// Tells family members' devices when an event is created, updated, or deleted.
// Messages are data-only so the app handles them even in the background: it shows a
// notification (creates/updates, not to the person who made the change) and refreshes
// its local reminder alarms (all changes, including deletes).
exports.notifyOnEventWrite = onDocumentWritten(
  "families/{familyId}/events/{eventId}",
  async (event) => {
    const after = event.data && event.data.after && event.data.after.data();
    const before = event.data && event.data.before && event.data.before.data();

    const familyId = event.params.familyId;
    const db = admin.firestore();
    const familySnap = await db.collection("families").doc(familyId).get();
    if (!familySnap.exists) return;

    const memberUids = familySnap.get("memberUids") || [];
    // Who made the change; deletes carry no author, so everyone gets a silent refresh.
    const actor = after ? after.updatedBy || after.createdBy : null;

    const tokensByUid = {};
    for (const uid of memberUids) {
      const userSnap = await db.collection("users").doc(uid).get();
      const userTokens = userSnap.get("fcmTokens");
      if (Array.isArray(userTokens) && userTokens.length) tokensByUid[uid] = userTokens;
    }

    const familyName = familySnap.get("name") || "Family";
    const sends = [];
    for (const [uid, tokens] of Object.entries(tokensByUid)) {
      const data = { familyId };
      if (after && uid !== actor) {
        data.title = before ? "Event updated" : "New event";
        data.body = `${after.title || "(untitled)"} · ${familyName}`;
        // Lets the app open on the event's day when the notification is tapped.
        const start = toDate(after.startAt);
        if (start) data.startMillis = String(start.getTime());
        data.recurring = after.recurrence && after.recurrence !== "NONE" ? "1" : "0";
      } else if (after) {
        continue; // the person who made the change already has it scheduled locally
      }
      sends.push(
        admin.messaging().sendEachForMulticast({
          tokens,
          data,
          android: { priority: data.title ? "high" : "normal" },
        })
      );
    }
    await Promise.all(sends);
  }
);

// Household-task pushes (data-only, type "task"):
//  - "New task for you" when a task is (re)assigned to someone other than the editor.
//  - "Your turn: ..." when a rotation hands a repeating task to the next person.
//  - "<name> finished: ..." to everyone else when a task is marked done.
// Every member also gets a silent refresh so home-screen widgets stay current.
exports.notifyOnTaskWrite = onDocumentWritten(
  "families/{familyId}/tasks/{taskId}",
  async (event) => {
    const after = event.data && event.data.after && event.data.after.data();
    const before = event.data && event.data.before && event.data.before.data();
    const familyId = event.params.familyId;
    const taskId = event.params.taskId;
    const db = admin.firestore();
    const familySnap = await db.collection("families").doc(familyId).get();
    if (!familySnap.exists) return;
    const memberUids = familySnap.get("memberUids") || [];

    const messages = {}; // uid -> { title, body }
    if (after) {
      const actor = after.updatedBy || after.createdBy || "";
      const title = after.title || "Task";
      // Only a newer completion counts; an undo moves lastDoneAt back and must stay quiet.
      const doneNow = !!after.lastDoneAt &&
        (!before || !before.lastDoneAt || isLater(after.lastDoneAt, before.lastDoneAt));
      const assignee = after.assignedTo || "";
      const assigneeChanged = assignee && (!before || (before.assignedTo || "") !== assignee);

      if (doneNow) {
        const doer = after.lastDoneBy || actor;
        const name = await memberName(db, familyId, doer);
        for (const uid of memberUids) {
          if (uid !== doer) messages[uid] = { title: `${name} finished: ${title}`, body: "Nice work! ⭐" };
        }
        if (assigneeChanged && assignee !== doer) {
          messages[assignee] = { title: `Your turn: ${title}`, body: dueText(after.dueDate) };
        }
      } else if (assigneeChanged && assignee !== actor) {
        messages[assignee] = { title: "New task for you", body: `${title} · ${dueText(after.dueDate)}` };
      }
    }

    const sends = [];
    for (const uid of memberUids) {
      const userSnap = await db.collection("users").doc(uid).get();
      const tokens = userSnap.get("fcmTokens");
      if (!Array.isArray(tokens) || !tokens.length) continue;
      const data = { type: "task", familyId, taskId };
      const msg = messages[uid];
      if (msg) Object.assign(data, msg);
      sends.push(
        admin.messaging().sendEachForMulticast({
          tokens,
          data,
          android: { priority: msg ? "high" : "normal" },
        })
      );
    }
    await Promise.all(sends);
  }
);

async function memberName(db, familyId, uid) {
  if (!uid) return "Someone";
  const snap = await db.collection("families").doc(familyId).collection("members").doc(uid).get();
  return (snap.exists && snap.get("displayName")) || "Someone";
}

function isLater(a, b) {
  const da = toDate(a);
  const dbt = toDate(b);
  return !!da && (!dbt || da.getTime() > dbt.getTime());
}

function dueText(iso) {
  return iso ? `Due ${iso}` : "No due date";
}

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

  const rrule = toRrule(event);
  if (rrule) lines.push(rrule);

  // Exclude individually-deleted occurrences.
  if (rrule && Array.isArray(event.excludedDates) && event.excludedDates.length) {
    const time = formatUtc(start).split("T")[1]; // hhmmssZ
    for (const iso of event.excludedDates) {
      const d = String(iso).replace(/-/g, "");
      if (event.allDay) {
        lines.push(`EXDATE;VALUE=DATE:${d}`);
      } else {
        lines.push(`EXDATE:${d}T${time}`);
      }
    }
  }

  lines.push(`SUMMARY:${escapeText(event.title || "(untitled)")}`);
  if (event.location) lines.push(`LOCATION:${escapeText(event.location)}`);
  if (event.notes) lines.push(`DESCRIPTION:${escapeText(event.notes)}`);

  lines.push("END:VEVENT");
  return lines;
}

function toRrule(event) {
  const base = {
    DAILY: "FREQ=DAILY",
    WEEKLY: "FREQ=WEEKLY",
    MONTHLY: "FREQ=MONTHLY",
    YEARLY: "FREQ=YEARLY",
  }[event.recurrence];
  if (!base) return null;
  // Every N units (e.g. INTERVAL=3 with FREQ=MONTHLY = quarterly). Older events lack it.
  const interval = Number.isInteger(event.repeatInterval) ? event.repeatInterval : 1;
  const freq = interval > 1 ? `${base};INTERVAL=${interval}` : base;

  // repeatUntil is the end (23:59:59 local) of the series' last day.
  const until = toDate(event.repeatUntil);
  if (!until) return `RRULE:${freq}`;
  if (event.allDay) {
    // All-day DTSTARTs here are the UTC date of local midnight, so match that convention.
    const lastDayStart = new Date(until.getTime() - (24 * 60 * 60 - 1) * 1000);
    return `RRULE:${freq};UNTIL=${formatDate(lastDayStart)}`;
  }
  return `RRULE:${freq};UNTIL=${formatUtc(until)}`;
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
