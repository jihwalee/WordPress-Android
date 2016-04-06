package org.xmlrpc.android.pojo;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;

import java.util.ArrayList;

@Element
public class Value {
    public static class ValueArray {
        @ElementList()
        public ArrayList<Value> data;
    }

//    private final static SimpleDateFormat sSimpleDateFormat = new SimpleDateFormat("yyyyMMdd'T'HH:mm:ss");
//
//    public static class DateTimeConverter implements Converter<Date> {
//
//        public Date read(InputNode node) throws Exception {
//            return sSimpleDateFormat.parse(node.getValue());
//        }
//
//        public void write(OutputNode node, Date date) {
//            node.setAttribute("name", "dateTime.iso8601");
//            node.setAttribute("value", sSimpleDateFormat.format(date));
//        }
//    }

    @Element(required=false)
    public Integer i4;

    @Element(required=false)
    public String string;

    @Element(required = false)
    public Struct struct;

//    private Date mDate;

    @Element(required = false, name = "dateTime.iso8601")
//    @Commit
    public String dateTimeString;

//    @Commit
//    private void parseDate() throws ParseException {
//        if(dateTimeString != null) {
//            try {
//                mDate = sSimpleDateFormat.parse(dateTimeString);
//            } catch (ParseException e) {
//                throw e;
//                // do something
//            } finally {
//                dateTimeString = null;
//            }
//        }
//    }

    @Element(required = false)
    public ValueArray array;

    public Value() {}

    public Value(int i4) {
        this.i4 = i4;
    }

    public Value(String string) {
        this.string = string;
    }

    public Value(Struct struct) {
        this.struct = struct;
    }
}