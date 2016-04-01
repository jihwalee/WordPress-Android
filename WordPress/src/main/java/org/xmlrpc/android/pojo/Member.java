package org.xmlrpc.android.pojo;

import org.simpleframework.xml.Element;

@Element
public class Member {
    @Element
    public String name;

    @Element
    public Value value;

    public Member() {}

    public Member(String name, Value value) {
        this.name = name;
        this.value = value;
    }
}