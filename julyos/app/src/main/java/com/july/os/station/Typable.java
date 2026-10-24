package com.july.os.station;

/** A page that takes typing from a USB keyboard (Search, text entry). */
interface Typable {
    void type(String s);

    void delete();

    void done();
}
