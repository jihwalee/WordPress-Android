package org.xmlrpc.android.pojo;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

@Root
public class MethodCall {
    @Element
    public String methodName;

    @Element
    public Params params;

    public MethodCall() {}

    public MethodCall(String methodName, Params params) {
        this.methodName = methodName;
        this.params = params;
    }
}