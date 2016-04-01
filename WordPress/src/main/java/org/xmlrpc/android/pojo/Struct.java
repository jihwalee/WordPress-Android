package org.xmlrpc.android.pojo;

public class Struct {
    private Member member;

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    @Override
    public String toString() {
        return "ClassPojo [member = " + member + "]";
    }
}