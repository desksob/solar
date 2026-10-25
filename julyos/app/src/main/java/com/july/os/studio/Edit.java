package com.july.os.studio;

import android.content.Context;
import android.graphics.Bitmap;

import com.july.os.receipts.Catalog;
import com.july.os.receipts.Receipt;
import com.july.os.receipts.Slip;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A mixed-media print (design C4 builder, F7 print): layers stacked top to bottom on the paper,
 * with one optional frame round it all. A counter layer numbers each printed copy ("No. 007") out
 * of an edition size.
 */
public final class Edit {

    public static final String TEXT = "Text", IMAGE = "Image", FRAME = "Frame", BARS = "Bars", LINE = "Line", ART = "Art",
            DATE = "Date", COUNTER = "Counter", SHAPE = "Shape", QR = "QR";
    /** The builder's toolbar, in the design's order (QR added for ticket stubs). */
    public static final String[] TYPES = {TEXT, IMAGE, FRAME, BARS, LINE, ART, DATE, COUNTER, SHAPE, QR};

    public static final class Layer {
        public String type, text = "", path = "", style = "", align = "C", kind = "";
        public int size = 50, contrast = 55, thickness = 40, number = 1, total = 40;
        public boolean bold;

        Layer(String type) {
            this.type = type;
        }

        /** What the layer list shows ("Frame · Double", "Counter 007"). */
        public String label() {
            if (TEXT.equals(type)) return text.length() > 0 ? text : "Text";
            if (FRAME.equals(type)) return "Frame · " + cap(kind);
            if (COUNTER.equals(type)) return "Counter " + String.format(Locale.ROOT, "%03d", number);
            if (IMAGE.equals(type)) return path.length() > 0 ? "Picture" : "Picture (none yet)";
            if (SHAPE.equals(type)) return "Shape · " + cap(kind);
            if (QR.equals(type)) return "QR · " + text;
            return type;
        }
    }

    static String cap(String s) {
        return s.length() == 0 ? "" : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    public long id = System.currentTimeMillis();
    public String name = "July Edit 01";
    public final List<Layer> layers = new ArrayList<Layer>();

    /** A new layer with the defaults the builder starts from. */
    public static Layer layer(String type) {
        Layer l = new Layer(type);
        if (TEXT.equals(type)) { l.text = "Your words"; l.size = 40; l.bold = true; }
        if (FRAME.equals(type)) l.kind = "double";
        if (SHAPE.equals(type)) l.kind = "circle";
        if (QR.equals(type)) l.text = "july.rip";
        if (ART.equals(type)) l.size = 40;
        if (BARS.equals(type)) l.size = 40;
        return l;
    }

    // ---- storage ----

    public static List<Edit> all() {
        List<Edit> out = new ArrayList<Edit>();
        try {
            JSONArray a = new JSONArray(Studio.prefs().getString("studio_edits", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(from(a.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return out;
    }

    public void save() {
        List<Edit> l = all();
        boolean found = false;
        for (int i = 0; i < l.size(); i++) if (l.get(i).id == id) { l.set(i, this); found = true; }
        if (!found) l.add(0, this);
        JSONArray a = new JSONArray();
        for (Edit e : l) a.put(e.json());
        Studio.prefs().edit().putString("studio_edits", a.toString()).apply();
    }

    public void delete() {
        JSONArray a = new JSONArray();
        for (Edit e : all()) if (e.id != id) a.put(e.json());
        Studio.prefs().edit().putString("studio_edits", a.toString()).apply();
    }

    JSONObject json() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id).put("name", name);
            JSONArray a = new JSONArray();
            for (Layer l : layers) {
                a.put(new JSONObject().put("type", l.type).put("text", l.text).put("path", l.path).put("style", l.style).put("align", l.align)
                        .put("kind", l.kind).put("size", l.size).put("contrast", l.contrast).put("thick", l.thickness)
                        .put("num", l.number).put("total", l.total).put("bold", l.bold));
            }
            o.put("layers", a);
        } catch (Exception ignored) {
        }
        return o;
    }

    static Edit from(JSONObject o) {
        Edit e = new Edit();
        e.id = o.optLong("id", e.id);
        e.name = o.optString("name", e.name);
        JSONArray a = o.optJSONArray("layers");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject x = a.optJSONObject(i);
            if (x == null) continue;
            Layer l = new Layer(x.optString("type", TEXT));
            l.text = x.optString("text");
            l.path = x.optString("path");
            l.style = x.optString("style");
            l.align = x.optString("align", "C");
            l.kind = x.optString("kind");
            l.size = x.optInt("size", 50);
            l.contrast = x.optInt("contrast", 55);
            l.thickness = x.optInt("thick", 40);
            l.number = x.optInt("num", 1);
            l.total = x.optInt("total", 40);
            l.bold = x.optBoolean("bold", false);
            e.layers.add(l);
        }
        return e;
    }

    // ---- drawing (F7) ----

    public Bitmap draw(Context c, Studio.PaperSetup paper) {
        Slip s = paper.slip(c);
        for (Layer l : layers) {
            if (FRAME.equals(l.type)) {
                Bitmap art = "image".equals(l.kind) ? Studio.load(l.path) : null;
                s.frame(l.kind, art);
                break;
            }
        }
        String style = paper.style;
        for (Layer l : layers) {
            float thick = 1 + l.thickness / 12f; // 1..9 design px
            if (TEXT.equals(l.type)) {
                s.text(l.text, 8 + l.size * 0.4f, l.bold, l.align);
            } else if (IMAGE.equals(l.type)) {
                Bitmap b = Studio.load(l.path);
                if (b != null) s.picture(b, Math.max(20, l.size), l.style.length() > 0 ? l.style : style, l.contrast, l.align);
            } else if (BARS.equals(l.type)) {
                s.barcode();
            } else if (LINE.equals(l.type)) {
                s.line("dashed".equals(l.kind), thick);
            } else if (ART.equals(l.type)) {
                s.logo(10 + l.size * 0.6f, l.align);
            } else if (DATE.equals(l.type)) {
                s.text(new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date()), 8 + l.size * 0.2f, false, l.align);
            } else if (COUNTER.equals(l.type)) {
                s.text("Limited · " + l.total + " made", 10, false, l.align);
                s.text("No. " + String.format(Locale.ROOT, "%03d", l.number), 8 + l.size * 0.4f, true, l.align);
            } else if (SHAPE.equals(l.type)) {
                s.shape(l.kind, 20 + l.size * 1.6f, thick, l.align);
            } else if (QR.equals(l.type)) {
                String u = l.text.startsWith("http") ? l.text : "https://" + l.text;
                s.qr(u, l.text);
            }
        }
        return s.build();
    }

    /** After a copy prints, the counter moves on (the edition's next number). */
    public void printed(int copies) {
        for (Layer l : layers) if (COUNTER.equals(l.type)) l.number = Math.min(l.total, l.number + copies);
        save();
    }

    public Receipt receipt() {
        final Edit e = this;
        return new Receipt(Catalog.EVENTS, "Mixed media", name, "ticket", true) {
            @Override
            public String detail() {
                return Slip.dot("Mixed media", e.name, e.layers.size() + " layers");
            }

            @Override
            public String historyTitle() {
                return "Edit · " + e.name;
            }

            @Override
            public String historySub() {
                for (Layer l : e.layers) if (COUNTER.equals(l.type)) return "No. " + String.format(Locale.ROOT, "%03d", l.number) + " of " + l.total;
                return e.layers.size() + " layers";
            }

            @Override
            public Bitmap make(Context c, int width, Progress p) {
                return e.draw(c, Studio.paper());
            }

            @Override
            public int defaultCopies() {
                return Studio.paper().copies;
            }

            @Override
            public void printed(Context c) {
                e.printed(1);
            }
        };
    }

    // ---- C3 · templates ----

    /** The design's templates that are mixed-media edits (the event ones build an Event instead). */
    public static Edit template(String name) {
        Edit e = new Edit();
        e.name = name;
        if ("Ticket stub".equals(name)) {
            Layer f = layer(FRAME); f.kind = "ticket"; e.layers.add(f);
            e.layers.add(layer(ART));
            Layer t = layer(TEXT); t.text = "Admit one"; t.size = 60; e.layers.add(t);
            Layer seat = layer(TEXT); seat.text = "Seat [ROW] [NO.]"; seat.size = 25; seat.bold = false; e.layers.add(seat);
            e.layers.add(layer(DATE));
            Layer c = layer(COUNTER); c.total = 100; e.layers.add(c);
            e.layers.add(layer(QR));
        } else if ("Photo hero".equals(name)) {
            Layer i = layer(IMAGE); i.size = 100; e.layers.add(i);
            Layer t = layer(TEXT); t.text = "Caption"; t.size = 30; e.layers.add(t);
            e.layers.add(layer(DATE));
        } else if ("Special guest".equals(name)) {
            Layer a = layer(TEXT); a.text = "Special guest"; a.size = 15; a.bold = false; e.layers.add(a);
            Layer b = layer(TEXT); b.text = "[GUEST NAME]"; b.size = 90; e.layers.add(b);
            Layer l = layer(LINE); e.layers.add(l);
            e.layers.add(layer(DATE));
            e.layers.add(layer(ART));
        } else if ("Sale tag".equals(name)) {
            Layer f = layer(FRAME); f.kind = "solid"; e.layers.add(f);
            Layer a = layer(TEXT); a.text = "[ITEM]"; a.size = 30; e.layers.add(a);
            Layer p = layer(TEXT); p.text = "KES [PRICE]"; p.size = 80; e.layers.add(p);
            e.layers.add(layer(BARS));
            Layer c = layer(TEXT); c.text = "Code [CODE]"; c.size = 15; c.bold = false; e.layers.add(c);
        } else if ("Festival strip".equals(name)) {
            Layer f = layer(FRAME); f.kind = "film"; e.layers.add(f);
            Layer t = layer(TEXT); t.text = "[FESTIVAL]"; t.size = 70; e.layers.add(t);
            for (int k = 1; k <= 3; k++) {
                e.layers.add(layer(IMAGE));
                Layer cap = layer(TEXT); cap.text = "Day " + k; cap.size = 25; e.layers.add(cap);
            }
        } else {
            // "New from blank" and "July Edit": the design's F7
            Layer f = layer(FRAME); e.layers.add(f);
            Layer t = layer(TEXT); t.text = name.length() > 0 ? name : "July Edit 01"; t.size = 40; e.layers.add(t);
            e.layers.add(layer(IMAGE));
            Layer cap = layer(TEXT); cap.text = "Caption"; cap.size = 20; cap.bold = false; e.layers.add(cap);
            e.layers.add(layer(COUNTER));
        }
        return e;
    }
}
