package org.xmlrpc.android.pojo;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementArray;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.convert.Convert;
import org.simpleframework.xml.convert.Converter;
import org.simpleframework.xml.core.Commit;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

@Element
public class Value {
    public static class ValueArray {
        @ElementList()
        private ArrayList<Value> data;
    }

    private final static SimpleDateFormat sSimpleDateFormat = new SimpleDateFormat("yyyyMMdd'T'HH:mm:ss");

    public static class DateTimeConverter implements Converter<Date> {

        public Date read(InputNode node) throws Exception {
            return sSimpleDateFormat.parse(node.getValue());
        }

        public void write(OutputNode node, Date date) {
            node.setAttribute("name", "dateTime.iso8601");
            node.setAttribute("value", sSimpleDateFormat.format(date));
        }
    }

    @Element(required=false)
    public Integer i4;

    @Element(required=false)
    public String string;

    @ElementList(required = false)
    private ArrayList<Member> struct;

    private Date mDate;

    @Element(required = false, name = "dateTime.iso8601")
    @Commit
    public String dateTimeString;

    @Commit
    private void parseDate() throws ParseException {
        if(dateTimeString != null) {
            try {
                mDate = sSimpleDateFormat.parse(dateTimeString);
            } catch (ParseException e) {
                throw e;
                // do something
            } finally {
                dateTimeString = null;
            }
        }
    }

    @Element(required = false)
    private ValueArray array;

    public Value() {}

    public Value(int i4) {
        this.i4 = i4;
    }

    public Value(String string) {
        this.string = string;
    }

    public Value(ArrayList<Member> struct) {
        this.struct = struct;
    }
}