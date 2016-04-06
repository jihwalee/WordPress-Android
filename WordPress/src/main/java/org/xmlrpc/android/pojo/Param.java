package org.xmlrpc.android.pojo;

import org.simpleframework.xml.Element;

@Element
public class Param {
    @Element
    public Value value;

    public Param() {}

    public Param(Value value) {
        this.value = value;
    }

    public Param(int i4) {
        this.value = new Value(i4);
    }

    public Param(String string) {
        this.value = new Value(string);
    }

    public Param(Struct struct) {
        this.value = new Value(struct);
    }
}
