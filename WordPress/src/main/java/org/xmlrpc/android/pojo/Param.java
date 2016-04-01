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
}
