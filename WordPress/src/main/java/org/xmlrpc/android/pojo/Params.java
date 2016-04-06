package org.xmlrpc.android.pojo;

import org.simpleframework.xml.ElementList;

import java.util.ArrayList;

public class Params {
    @ElementList(inline = true)
    private ArrayList<Param> mParams = new ArrayList<>();

    public Params() {}

    public Params add(String string) {
        mParams.add(new Param(string));
        return this;
    }

    public Params add(int i4) {
        mParams.add(new Param(i4));
        return this;
    }

    public Params add(Struct struct) {
        mParams.add(new Param(struct));
        return this;
    }
}