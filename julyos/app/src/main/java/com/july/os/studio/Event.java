package com.july.os.studio;

import android.content.Context;
import android.graphics.Bitmap;

import com.july.os.receipts.Catalog;
import com.july.os.receipts.Receipt;
import com.july.os.receipts.Slip;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * An event (design C5 details, C2 lineup) and its receipt (F5). Layouts: A = full details and
 * lineup, B = poster with the picture on top, C = residents list (names big), Blank = lineup only.
 * Saved in prefs as JSON; Studio keeps a list of them.
 */
public final class Event {

    public static final class Slot {
        public String name = "", note = "", time = "", room = "", picture = "";
    }

    public long id = System.currentTimeMillis();
    public String name = "July Night", date = "", doors = "20:00", ends = "", venue = "", address = "", tickets = "", age = "",
            dress = "", description = "", picture = "", linkLabel = "Get tickets", url = "july.rip/events";
    public String layout = "A";
    public boolean showDetails = true, showQr = true, showPicture = true;
    public final List<Slot> slots = new ArrayList<Slot>();

    // ---- storage ----

    public static List<Event> all() {
        List<Event> out = new ArrayList<Event>();
        try {
            JSONArray a = new JSONArray(Studio.prefs().getString("studio_events", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(from(a.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return out;
    }

    public static Event get(long id) {
        for (Event e : all()) if (e.id == id) return e;
        return null;
    }

    public void save() {
        List<Event> l = all();
        boolean found = false;
        for (int i = 0; i < l.size(); i++) if (l.get(i).id == id) { l.set(i, this); found = true; }
        if (!found) l.add(0, this);
        JSONArray a = new JSONArray();
        for (Event e : l) a.put(e.json());
        Studio.prefs().edit().putString("studio_events", a.toString()).apply();
    }

    public void delete() {
        List<Event> l = all();
        JSONArray a = new JSONArray();
        for (Event e : l) if (e.id != id) a.put(e.json());
        Studio.prefs().edit().putString("studio_events", a.toString()).apply();
    }

    JSONObject json() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id).put("name", name).put("date", date).put("doors", doors).put("ends", ends).put("venue", venue)
                    .put("address", address).put("tickets", tickets).put("age", age).put("dress", dress).put("desc", description)
                    .put("pic", picture).put("ll", linkLabel).put("url", url).put("layout", layout)
                    .put("sd", showDetails).put("sq", showQr).put("sp", showPicture);
            JSONArray s = new JSONArray();
            for (Slot x : slots) s.put(new JSONObject().put("n", x.name).put("o", x.note).put("t", x.time).put("r", x.room).put("p", x.picture));
            o.put("slots", s);
        } catch (Exception ignored) {
        }
        return o;
    }

    static Event from(JSONObject o) {
        Event e = new Event();
        e.id = o.optLong("id", e.id);
        e.name = o.optString("name", e.name);
        e.date = o.optString("date");
        e.doors = o.optString("doors");
        e.ends = o.optString("ends");
        e.venue = o.optString("venue");
        e.address = o.optString("address");
        e.tickets = o.optString("tickets");
        e.age = o.optString("age");
        e.dress = o.optString("dress");
        e.description = o.optString("desc");
        e.picture = o.optString("pic");
        e.linkLabel = o.optString("ll", e.linkLabel);
        e.url = o.optString("url", e.url);
        e.layout = o.optString("layout", "A");
        e.showDetails = o.optBoolean("sd", true);
        e.showQr = o.optBoolean("sq", true);
        e.showPicture = o.optBoolean("sp", true);
        JSONArray s = o.optJSONArray("slots");
        for (int i = 0; s != null && i < s.length(); i++) {
            JSONObject x = s.optJSONObject(i);
            if (x == null) continue;
            Slot sl = new Slot();
            sl.name = x.optString("n");
            sl.note = x.optString("o");
            sl.time = x.optString("t");
            sl.room = x.optString("r");
            sl.picture = x.optString("p");
            e.slots.add(sl);
        }
        return e;
    }

    /** One line: "Sat 31 Oct · Venue · 18+ · KES 500". */
    public String meta() {
        return Slip.dot(date, venue, age, tickets);
    }

    // ---- F5 · the receipt ----

    public Bitmap draw(Context c, Studio.PaperSetup paper) {
        Slip s = paper.slip(c);
        Bitmap pic = showPicture ? Studio.load(picture) : null;
        if ("B".equals(layout)) {
            // poster: picture first, the name huge, then the lineup
            if (pic != null) s.picture(pic, 100, paper.style, 55, "C");
            s.logo(22, "C").text(name, 22, true, "C").text(Slip.dot(date, doors.length() > 0 ? "Doors " + doors : ""), 11, false, "C");
            if (showDetails) s.text(Slip.dot(venue, age, tickets), 11, false, "C");
            s.rule();
            for (Slot x : slots) s.text(Slip.dot(x.time, x.name), 13, true, "C");
        } else if ("C".equals(layout)) {
            // residents: names big, times small
            s.logo().heading(name).rule();
            for (Slot x : slots) {
                s.text(x.name, 18, true, "L");
                s.kv(x.note.length() > 0 ? x.note : x.room, x.time);
                s.gap(4);
            }
            if (showDetails) s.rule().center(meta());
        } else if ("Blank".equals(layout)) {
            s.heading(name).rule();
            for (Slot x : slots) s.kv(Slip.dot(x.time, x.name), x.room, true);
        } else {
            // A: the design's F5
            s.logo().heading(name).center(date).rule();
            if (showDetails) {
                if (doors.length() > 0) s.kv("Doors", doors);
                if (ends.length() > 0) s.kv("Ends", ends);
                if (venue.length() > 0) s.kv("Venue", venue);
                if (tickets.length() > 0) s.kv("Tickets", tickets);
                if (age.length() > 0) s.kv("Age", age);
                if (dress.length() > 0) s.kv("Dress", dress);
                s.rule();
            }
            for (Slot x : slots) s.entry(Slip.dot(x.time, x.name), "", Slip.dot(x.room, x.note), null, false);
            if (description.length() > 0) s.rule().text(description);
            if (pic != null) s.picture(pic, 100, paper.style, 55, "C");
        }
        if (showQr && url.length() > 0) s.rule().qr(url.startsWith("http") ? url : "https://" + url, "Scan to open on july.rip").center(Slip.dot(linkLabel, url));
        return s.build();
    }

    /** For Preview / Print center: this event as a receipt. */
    public Receipt receipt() {
        final Event e = this;
        return new Receipt(Catalog.EVENTS, "Event lineup", name, "calendar", true) {
            @Override
            public String detail() {
                return Slip.dot("Event lineup", e.name, "layout " + e.layout);
            }

            @Override
            public String historyTitle() {
                return "Event lineup · " + e.name;
            }

            @Override
            public String historySub() {
                return e.slots.size() + (e.slots.size() == 1 ? " slot" : " slots");
            }

            @Override
            public Bitmap make(Context c, int width, Progress p) {
                return e.draw(c, Studio.paper());
            }

            @Override
            public int defaultCopies() {
                return Studio.paper().copies;
            }
        };
    }
}
