package com.july.os.station;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.july.os.store.Store;
import com.july.os.studio.Studio;

import java.io.File;
import java.util.List;

/** Section D, the July Store (beta: browsing is open, buying is off) and Studio's item editor. */
final class StorePages {

    private StorePages() {}

    static View picture(Context c, Store.Product p, float w, float h) {
        FrameLayout f = new FrameLayout(c);
        f.setBackgroundColor(St.hair);
        Bitmap b = Studio.load(p.picture);
        if (b != null) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(b);
            f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            f.addView(new St.Icon(c, St.STORE, St.muted).stroke(1.4f), new FrameLayout.LayoutParams(St.px(Math.min(w, h) / 3), St.px(Math.min(w, h) / 3), Gravity.CENTER));
        }
        return f;
    }

    static View bagButton(final StationActivity a) {
        return St.button(a, "Bag · " + Store.bag.size(), St.STORE, Store.bag.size() > 0, 40, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.push(new Checkout());
            }
        });
    }

    // ---- D1 · Store home ----

    static final class Home extends Page {
        @Override String title() { return "July Store"; }
        @Override String sub() { return "Beta"; }
        @Override int nav() { return NAV_STORE; }

        @Override
        View build() {
            final StationActivity c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout head = St.row(c);
            head.addView(HomePages.big(c, "July Store"));
            head.addView(St.pill(c, "Beta", 10, true, true), St.left(St.lp(-2, -2), 12));
            head.addView(St.space(c), new LinearLayout.LayoutParams(0, 1, 1f));
            head.addView(St.button(c, "Sales log", St.LIBRARY, false, 40, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new SalesLog());
                }
            }), St.lp(-2, 40));
            head.addView(bagButton(c), St.left(St.lp(-2, 40), 10));
            page.addView(head);
            LinearLayout banner = St.row(c);
            banner.setBackground(St.box(0, St.line, 1));
            banner.setPadding(St.px(14), 0, St.px(14), 0);
            banner.addView(St.pill(c, "Beta", 10, true, true));
            banner.addView(St.caps(c, "The store opens soon. Browsing is open, buying is off.", 11, false, 0.06f, St.ink), St.left(new LinearLayout.LayoutParams(0, -2, 1f), 12));
            page.addView(banner, St.top(St.lp(-1, 44), 12));
            ScrollView sv = new ScrollView(c);
            LinearLayout grid = St.col(c);
            sv.addView(grid);
            List<Store.Product> ps = Store.products();
            LinearLayout row = null;
            for (int i = 0; i < ps.size(); i++) {
                if (i % 3 == 0) {
                    row = St.row(c);
                    grid.addView(row, St.top(St.lp(-1, -2), i == 0 ? 0 : 12));
                }
                row.addView(card(ps.get(i)), St.left(new LinearLayout.LayoutParams(0, -2, 1f), i % 3 == 0 ? 0 : 12));
            }
            if (row != null) for (int k = ps.size() % 3; k > 0 && k < 3; k++) row.addView(St.space(c), St.left(new LinearLayout.LayoutParams(0, 1, 1f), 12));
            if (ps.isEmpty()) grid.addView(SystemPages.note(c, "No items yet. The design team adds them in Studio → Store items."), St.top(St.lp(-1, -2), 12));
            page.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 12));
            return page;
        }

        private View card(final Store.Product p) {
            Context c = a;
            LinearLayout k = St.row(c);
            k.setGravity(Gravity.CENTER_VERTICAL);
            k.setBackground(St.box(0, St.line, 1));
            k.setPadding(St.px(10), St.px(10), St.px(10), St.px(10));
            k.addView(picture(c, p, 96, 96), St.lp(96, 96));
            LinearLayout tx = St.col(c);
            tx.addView(St.caps(c, p.name, 13, true, 0f, St.ink));
            tx.addView(St.caps(c, p.line, 10, false, 0f, St.muted), St.top(St.lp(-2, -2), 2));
            tx.addView(St.caps(c, p.priceText(), 12, true, 0f, St.ink), St.top(St.lp(-2, -2), 10));
            k.addView(tx, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 12));
            St.clickable(k, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new Product(p.id));
                }
            });
            return k;
        }
    }

    // ---- D2 · Product ----

    static final class Product extends Page {
        private final long id;
        private String size;

        Product(long id) {
            this.id = id;
        }

        Store.Product p() {
            Store.Product p = Store.get(id);
            return p != null ? p : new Store.Product();
        }

        @Override String title() { return p().name; }
        @Override String sub() { return "Store · beta"; }
        @Override int nav() { return NAV_STORE; }

        @Override
        View build() {
            final StationActivity c = a;
            final Store.Product p = p();
            List<String> sizes = p.sizeList();
            if (size == null && !sizes.isEmpty()) size = sizes.get(Math.min(1, sizes.size() - 1));
            LinearLayout page = HomePages.padded(c);
            LinearLayout main = St.row(c);
            main.setGravity(Gravity.TOP);
            main.addView(picture(c, p, 330, 340), St.lp(330, 340));
            LinearLayout right = St.col(c);
            right.addView(HomePages.label(c, "Beta · preview"));
            right.addView(HomePages.big(c, p.name), St.top(St.lp(-1, -2), 6));
            right.addView(St.caps(c, com.july.os.receipts.Slip.dot(p.line, p.note), 12, false, 0.06f, St.muted), St.top(St.lp(-1, -2), 6));
            right.addView(St.caps(c, p.priceText(), 20, true, 0f, St.ink), St.top(St.lp(-1, -2), 16));
            if (!sizes.isEmpty()) {
                right.addView(StudioPages.choice(c, sizes.toArray(new String[0]), size, new PrintPages.Chosen() {
                    @Override
                    public void chose(String n) {
                        size = n;
                        a.rebuild();
                    }
                }), St.top(St.lp(-2, -2), 14));
            }
            right.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout b = St.row(c);
            b.addView(St.button(c, "Add to bag", St.PLUS, true, 52, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Store.add(p, size != null ? size : "");
                    a.toast("Added to the bag · " + Store.bag.size() + (Store.bag.size() == 1 ? " item" : " items"));
                    a.rebuild();
                }
            }), St.lp(-2, 52));
            b.addView(St.button(c, "Print tag", St.PRINTER, false, 52, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.preview(Store.tag(p));
                }
            }), St.left(St.lp(-2, 52), 10));
            b.addView(bagButton(c), St.left(St.lp(-2, 52), 10));
            right.addView(b);
            main.addView(right, St.left(new LinearLayout.LayoutParams(0, St.px(340), 1f), 26));
            page.addView(main);
            return page;
        }
    }

    // ---- D3 · Checkout (payments are off in the beta) ----

    static final class Checkout extends Page {
        @Override String title() { return "Checkout"; }

        @Override
        String sub() {
            int n = Store.bag.size();
            return n + (n == 1 ? " item" : " items");
        }

        @Override int nav() { return NAV_STORE; }

        @Override
        View build() {
            final StationActivity c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout main = St.row(c);
            main.setGravity(Gravity.TOP);
            LinearLayout left = St.col(c);
            left.addView(HomePages.big(c, "Checkout"));
            ScrollView sv = new ScrollView(c);
            LinearLayout list = St.col(c);
            sv.addView(list);
            for (int i = 0; i < Store.bag.size(); i++) {
                final int idx = i;
                Store.Item it = Store.bag.get(i);
                LinearLayout r = St.row(c);
                r.addView(picture(c, it.product, 40, 40), St.lp(40, 40));
                LinearLayout tx = St.col(c);
                tx.addView(St.caps(c, it.product.name, 14, true, 0f, St.ink));
                tx.addView(St.caps(c, it.size.length() > 0 ? "Size " + it.size : it.product.line, 10, false, 0f, St.muted), St.top(St.lp(-2, -2), 2));
                r.addView(tx, St.left(new LinearLayout.LayoutParams(0, -2, 1f), 16));
                r.addView(St.caps(c, it.product.priceText(), 12, false, 0f, St.ink), St.left(St.lp(-2, -2), 12));
                r.addView(St.iconButton(c, St.CLOSE, false, 32, 14, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Store.bag.remove(idx);
                        a.rebuild();
                    }
                }), St.left(St.lp(32, 32), 12));
                list.addView(MusicPages.ruled(c, r, 60));
            }
            if (Store.bag.isEmpty()) list.addView(SystemPages.note(c, "The bag is empty"), St.top(St.lp(-1, -2), 16));
            left.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 6));
            left.addView(St.caps(c, "Add a tip", 10, false, 0.14f, St.muted), St.top(St.lp(-1, -2), 10));
            String tip = Store.tipPct == 0 ? "None" : Store.tipPct == 5 ? "5%" : Store.tipPct == 10 ? "10%" : "Custom";
            left.addView(StudioPages.choice(c, new String[]{"None", "5%", "10%", "Custom"}, tip, new PrintPages.Chosen() {
                @Override
                public void chose(String n) {
                    if ("Custom".equals(n)) {
                        a.push(new PrintPages.TextPage("Tip percent", "", new PrintPages.TextPage.Done() {
                            @Override
                            public void text(String t) {
                                try {
                                    Store.tipPct = Math.max(0, Math.min(50, Integer.parseInt(t.replaceAll("[^0-9]", ""))));
                                } catch (Exception ignored) {
                                }
                            }
                        }));
                        return;
                    }
                    Store.tipPct = "None".equals(n) ? 0 : Integer.parseInt(n.replace("%", ""));
                    a.rebuild();
                }
            }), St.top(St.lp(-2, -2), 6));
            LinearLayout b = St.row(c);
            b.addView(St.button(c, "Back to store", St.BACK, false, 48, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.go(new Home());
                }
            }), St.lp(-2, 48));
            b.addView(St.button(c, "Print sample receipt", St.PRINTER, false, 48, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.preview(Store.sampleReceipt());
                }
            }), St.left(St.lp(-2, 48), 10));
            left.addView(b, St.top(St.lp(-1, -2), 12));
            main.addView(left, new LinearLayout.LayoutParams(0, -1, 1f));

            LinearLayout right = St.col(c);
            right.addView(St.caps(c, "Pay with", 10, false, 0.14f, St.muted));
            right.addView(payRow(c, St.SPEAKER, "M-Pesa · STK push"), St.top(St.lp(-1, 56), 6));
            right.addView(payRow(c, St.TICKET, "Card tap"), St.top(St.lp(-1, 56), 8));
            right.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout tot = St.row(c);
            tot.addView(St.caps(c, "Total incl. tip", 12, false, 0f, St.ink), new LinearLayout.LayoutParams(0, -2, 1f));
            tot.addView(St.caps(c, Store.bag.isEmpty() ? Store.kes(0) : Store.unpriced() ? "Price soon" : Store.kes(Store.total()), 20, true, 0f, St.ink));
            right.addView(MusicPages.ruled(c, tot, 60));
            LinearLayout pay = St.button(c, "Pay · opens after the beta", St.LOCK, true, 54, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.toast("Buying is off while the store is in beta");
                }
            });
            pay.setAlpha(0.4f);
            right.addView(pay, St.top(St.lp(-1, 54), 10));
            main.addView(right, St.left(St.lp(330, -1), 22));
            page.addView(main, new LinearLayout.LayoutParams(-1, -1));
            return page;
        }

        private View payRow(Context c, int icon, String name) {
            LinearLayout r = St.row(c);
            r.setBackground(St.box(0, St.line, 1));
            r.setPadding(St.px(16), 0, St.px(16), 0);
            r.addView(new St.Icon(c, icon, St.muted), St.lp(18, 18));
            r.addView(St.caps(c, name, 12, true, 0.1f, St.muted), St.left(new LinearLayout.LayoutParams(0, -2, 1f), 10));
            r.addView(St.pill(c, "Off in beta", 9, false, false));
            return r;
        }
    }

    // ---- D5 · Sales log ----

    static final class SalesLog extends Page {
        @Override String title() { return "July Store"; }
        @Override String sub() { return "Admin · Beta"; }
        @Override int nav() { return NAV_STORE; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout head = St.row(c);
            head.addView(HomePages.big(c, "Sales log"), new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout re = St.button(c, "Reprint last receipt", St.PRINTER, false, 44, null);
            re.setAlpha(0.4f);
            head.addView(re, St.lp(-2, 44));
            page.addView(head);
            page.addView(SystemPages.note(c, "No sales yet: buying is off while the store is in beta. Sales show here, newest first, once payments are connected."), St.top(St.lp(-1, -2), 16));
            return page;
        }
    }

    // ---- Studio → Store items (design team) ----

    static final class Items extends StudioPages.StudioPage {
        @Override String title() { return "Studio · Store"; }
        @Override String sub() { return "Store items"; }

        @Override
        View build() {
            Context c = a;
            LinearLayout page = HomePages.padded(c);
            LinearLayout head = St.row(c);
            head.addView(HomePages.big(c, "Store items"), new LinearLayout.LayoutParams(0, -2, 1f));
            head.addView(St.button(c, "Add item", St.PLUS, true, 40, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Store.Product p = new Store.Product();
                    p.name = "New item";
                    Store.put(p);
                    a.push(new ItemEdit(p.id));
                }
            }), St.lp(-2, 40));
            page.addView(head);
            ScrollView sv = new ScrollView(c);
            LinearLayout list = St.col(c);
            sv.addView(list);
            for (final Store.Product p : Store.products()) {
                list.addView(SystemPages.row(a, St.STORE, p.name, p.priceText(), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        a.push(new ItemEdit(p.id));
                    }
                }));
            }
            page.addView(sv, St.top(new LinearLayout.LayoutParams(-1, 0, 1f), 10));
            return page;
        }
    }

    static final class ItemEdit extends StudioPages.StudioPage {
        private final long id;

        ItemEdit(long id) {
            this.id = id;
        }

        @Override String title() { return "Studio · Store"; }
        @Override String sub() { return "Edit item"; }

        interface Set {
            void set(Store.Product p, String v);
        }

        private View f(String label, String value, final Set s) {
            return StudioPages.field(a, label, value, new PrintPages.TextPage.Done() {
                @Override
                public void text(String t) {
                    Store.Product p = Store.get(id);
                    if (p == null) return;
                    s.set(p, t.trim());
                    Store.put(p);
                }
            });
        }

        @Override
        View build() {
            final StationActivity c = a;
            LinearLayout page = HomePages.padded(c);
            final Store.Product p = Store.get(id);
            if (p == null) return page;
            LinearLayout main = St.row(c);
            main.setGravity(Gravity.TOP);
            LinearLayout left = St.col(c);
            left.addView(HomePages.big(c, p.name));
            left.addView(f("Name", p.name, new Set() { public void set(Store.Product x, String v) { x.name = v; } }), St.top(St.lp(-1, -2), 10));
            LinearLayout r = St.row(c);
            r.setGravity(Gravity.TOP);
            r.addView(f("Line (colour, set of...)", p.line, new Set() { public void set(Store.Product x, String v) { x.line = v; } }), new LinearLayout.LayoutParams(0, -2, 1f));
            r.addView(f("Price in KES (numbers only)", p.price > 0 ? String.valueOf(p.price) : "", new Set() {
                public void set(Store.Product x, String v) {
                    try {
                        x.price = Integer.parseInt(v.replaceAll("[^0-9]", ""));
                    } catch (Exception e) {
                        x.price = 0;
                    }
                }
            }), St.left(new LinearLayout.LayoutParams(0, -2, 1f), 10));
            left.addView(r, St.top(St.lp(-1, -2), 8));
            left.addView(f("Note (edition, material...)", p.note, new Set() { public void set(Store.Product x, String v) { x.note = v; } }), St.top(St.lp(-1, -2), 8));
            left.addView(f("Sizes, with commas (S,M,L)", p.sizes, new Set() { public void set(Store.Product x, String v) { x.sizes = v; } }), St.top(St.lp(-1, -2), 8));
            left.addView(St.space(c), new LinearLayout.LayoutParams(-1, 0, 1f));
            left.addView(St.button(c, "Remove item", St.TRASH, false, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Store.remove(p);
                    a.back();
                }
            }), St.lp(-2, 44));
            main.addView(left, new LinearLayout.LayoutParams(0, -1, 1f));
            LinearLayout right = St.col(c);
            right.addView(St.caps(c, "Picture", 10, false, 0.14f, St.muted));
            View pic = picture(c, p, 220, 220);
            St.clickable(pic, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.push(new StudioPages.Images(Studio.PICTURES, new StudioPages.Images.Pick() {
                        @Override
                        public void picked(File f) {
                            Store.Product x = Store.get(id);
                            if (x == null) return;
                            x.picture = f.getPath();
                            Store.put(x);
                        }
                    }));
                }
            });
            right.addView(pic, St.top(St.lp(220, 220), 6));
            right.addView(SystemPages.note(c, "Tap the picture to change it."), St.top(St.lp(-1, -2), 6));
            main.addView(right, St.left(St.lp(220, -1), 22));
            page.addView(main, new LinearLayout.LayoutParams(-1, -1));
            return page;
        }
    }
}
