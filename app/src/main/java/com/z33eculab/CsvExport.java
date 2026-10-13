package com.z33eculab;

/** Small Android-free CSV helper. Values are parsed display values only, never raw transport data. */
public final class CsvExport {
    private CsvExport() {}
    public static String quote(String value) { return "\""+(value==null?"":value.replace("\"","\"\""))+"\""; }
    public static String utcTimestamp(long millis) {
        java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",java.util.Locale.US);
        format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return format.format(new java.util.Date(millis));
    }
}
