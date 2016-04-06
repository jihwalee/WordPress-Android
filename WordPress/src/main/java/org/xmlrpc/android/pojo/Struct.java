package org.xmlrpc.android.pojo;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

@Element
public class Struct implements Iterable<Member> {
    @ElementList(inline = true)
    private ArrayList<Member> mMembers = new ArrayList<>();

    private Map<String, Value> mValuesMap = new Hashtable<>();

    public Struct() {}

    public void add(String name, String string) {
        final Value value = new Value(string);
        mMembers.add(new Member(name, value));
        mValuesMap.put(name, value);
    }

    public void add(String name, int i4) {
        final Value value = new Value(i4);
        mMembers.add(new Member(name, value));
        mValuesMap.put(name, value);
    }

    public void add(String name, Struct struct) {
        final Value value = new Value(struct);
        mMembers.add(new Member(name, value));
        mValuesMap.put(name, value);
    }

    public Value get(String name) {
        return mValuesMap.get(name);
    }

    @Override
    public Iterator<Member> iterator() {
        return mMembers.iterator();
    }
}