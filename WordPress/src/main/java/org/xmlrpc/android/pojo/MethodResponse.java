package org.xmlrpc.android.pojo;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

@Root
public class MethodResponse {
    @Element(required=false)
    public Params params;

    @Element(required=false)
    public Value fault;
}
