package com.july.os.store;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import com.july.os.JulyApp;
import com.july.os.receipts.Catalog;
import com.july.os.receipts.Receipt;
import com.july.os.receipts.Slip;
import com.july.os.studio.Studio;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The July Store (design D1-D5, receipt F6), in beta: browsing is open, buying is off. Products are
 * kept here (edited in Studio by the design team); the bag lives in memory. No payment provider is
 * connected, so nothing can be paid and the sales log stays empty until one is.
 */
public final class Store {

    /** Flip to true once M-Pesa / card payments are connected. */
    public static final boolean BUYING = false;

    private Store() {}

    static SharedPreferences prefs() {
        return JulyApp.get().prefs();
    }

    public static final class Product {
        public long id = System.nanoTime();
        public String name = "", line = "", note = "", sizes = "", picture = "";
        public int price; // KES; 0 = not set yet

        public List<String> sizeList() {
            List<String> out = new ArrayList<String>();
            for (String s : sizes.split(",")) if (s.trim().length() > 0) out.add(s.trim());
            return out;
        }

        public String priceText() {
            return price > 0 ? kes(price) : "Price soon";
        }
    }

    public static final class Item {
        public final Product product;
        public final String size;

        Item(Product p, String size) {
            product = p;
            this.size = size;
        }
    }

    public static String kes(int amount) {
        return "KES " + String.format(Locale.US, "%,d", amount);
    }

    // ---- products ----

    /** The design's six until the design team changes them. */
    public static List<Product> products() {
        List<Product> out = new ArrayList<Product>();
        String raw = prefs().getString("store_products", null);
        if (raw == null) return seed();
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Product p = new Product();
                p.id = o.optLong("id", p.id);
                p.name = o.optString("name");
                p.line = o.optString("line");
                p.note = o.optString("note");
                p.sizes = o.optString("sizes");
                p.picture = o.optString("pic");
                p.price = o.optInt("price");
                out.add(p);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static List<Product> seed() {
        String[][] s = {{"July Edit 01", "Limited print", "40 made · mixed media", ""}, {"Station tee", "Black", "", "S,M,L,XL"},
                {"Sticker pack", "Set of 6", "", ""}, {"Tote", "Canvas", "", ""}, {"Zine 03", "Printed here", "", ""},
                {"Gift card", "Any amount", "", ""}};
        List<Product> out = new ArrayList<Product>();
        long id = 1;
        for (String[] x : s) {
            Product p = new Product();
            p.id = id++;
            p.name = x[0];
            p.line = x[1];
            p.note = x[2];
            p.sizes = x[3];
            out.add(p);
        }
        return out;
    }

    public static void save(List<Product> l) {
        JSONArray a = new JSONArray();
        try {
            for (Product p : l) {
                a.put(new JSONObject().put("id", p.id).put("name", p.name).put("line", p.line).put("note", p.note)
                        .put("sizes", p.sizes).put("pic", p.picture).put("price", p.price));
            }
        } catch (Exception ignored) {
        }
        prefs().edit().putString("store_products", a.toString()).apply();
    }

    public static Product get(long id) {
        for (Product p : products()) if (p.id == id) return p;
        return null;
    }

    public static void put(Product p) {
        List<Product> l = products();
        boolean found = false;
        for (int i = 0; i < l.size(); i++) if (l.get(i).id == p.id) { l.set(i, p); found = true; }
        if (!found) l.add(p);
        save(l);
    }

    public static void remove(Product p) {
        List<Product> l = products();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id == p.id) l.remove(i);
        save(l);
    }

    // ---- the bag (D3) ----

    public static final List<Item> bag = new ArrayList<Item>();
    public static int tipPct;

    public static void add(Product p, String size) {
        bag.add(new Item(p, size));
    }

    public static int subtotal() {
        int t = 0;
        for (Item i : bag) t += i.product.price;
        return t;
    }

    public static int tip() {
        return Math.round(subtotal() * tipPct / 100f);
    }

    public static int total() {
        return subtotal() + tip();
    }

    /** Some item has no price yet: the total can't be shown. */
    public static boolean unpriced() {
        for (Item i : bag) if (i.product.price <= 0) return true;
        return false;
    }

    // ---- receipts ----

    /** F6 with the beta's honesty: nothing was paid, so it's marked as a sample. */
    public static Receipt sampleReceipt() {
        return new Receipt(Catalog.STORE, "Store receipt", "Sample · not paid", "store", true) {
            @Override
            public String detail() {
                return Slip.dot("Store receipt", "sample", bag.size() + (bag.size() == 1 ? " item" : " items"));
            }

            @Override
            public String historySub() {
                return "Sample · " + bag.size() + " items";
            }

            @Override
            public Bitmap make(Context c, int width, Progress p) {
                Slip s = new Slip(c, width).logo().heading("July Store · Beta").center("Sample · not paid", 11, true).rule()
                        .kv("Order", "Sample").kv("Date", new SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(new Date())).rule();
                if (bag.isEmpty()) s.center("The bag is empty");
                for (Item i : bag) s.kv(Slip.dot(i.product.name, i.size), i.product.priceText());
                s.rule().kv("Subtotal", unpriced() ? "Price soon" : kes(subtotal()));
                if (tipPct > 0) s.kv("Tip " + tipPct + "%", unpriced() ? "-" : kes(tip()));
                s.kv("Total", unpriced() ? "Price soon" : kes(total()), true);
                return s.kv("Paid by", "Not paid (beta)").rule().center("Thank you · july.rip/store").barcode().build();
            }
        };
    }

    /** "Print tag" (D2): the product as a price tag, picture and all. */
    public static Receipt tag(final Product p) {
        return new Receipt(Catalog.STORE, "Price tag", p.name, "store", true) {
            @Override
            public String historySub() {
                return p.priceText();
            }

            @Override
            public Bitmap make(Context c, int width, Progress pr) {
                Slip s = new Slip(c, width).frame("solid", null).logo().heading("July Store");
                Bitmap pic = Studio.load(p.picture);
                if (pic != null) s.picture(pic, 70, Studio.paper().style, 55, "C");
                s.text(p.name, 22, true, "C");
                if (p.line.length() > 0) s.text(p.line, 11, false, "C");
                if (p.note.length() > 0) s.text(p.note, 10, false, "C");
                if (p.sizes.length() > 0) s.text("Sizes " + p.sizes.replace(",", " · "), 10, false, "C");
                s.text(p.priceText(), 30, true, "C");
                return s.barcode().text("Code JS-" + String.format(Locale.ROOT, "%04d", p.id % 10000), 9, false, "C").build();
            }
        };
    }
}
