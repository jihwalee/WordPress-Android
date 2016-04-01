package org.xmlrpc.android.pojo;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.ArrayList;

@Root
public class MethodCall {
    @Element
    public String methodName;

    @ElementList
    public ArrayList<Param> params;

    public MethodCall() {}

    public MethodCall(String methodName, ArrayList<Param> params) {
        this.methodName = methodName;
        this.params = params;
    }
}