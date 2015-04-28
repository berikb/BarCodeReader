package kz.kazintech.barcodereader;

/**
* Created by berik on 26.04.15.
*/
final class Record {
    public final long timestamp;
    public final String address;
    public final String code;

    public Record(long timestamp, String address, String code) {
        super();
        this.timestamp = timestamp;
        this.address = address;
        this.code = code;
    }
}
