package org.xmlrpc.android.pojo;

import org.simpleframework.xml.ElementList;

import java.util.List;

public class ParamsListWrapper {
    @ElementList(name="params")
    private List<Param> params;

    public ParamsListWrapper(List<Param> params) {
        this.params = params;
    }

    public List<Param> getParams() {
        return params;
    }

    public void setParams(List<Param> params) {
        this.params = params;
    }
}