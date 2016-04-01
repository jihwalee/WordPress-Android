package org.xmlrpc.android.pojo;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.ArrayList;

@Root
public class MethodResponse {
    @ElementList(required=false)
    public ArrayList<Param> params;

    @Element(required=false)
    public Value fault;
}
