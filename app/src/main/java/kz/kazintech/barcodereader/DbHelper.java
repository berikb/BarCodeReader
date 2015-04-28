package kz.kazintech.barcodereader;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by berik on 26.04.15
 */
public class DbHelper extends SQLiteOpenHelper {

    public static final String TBL_EVENT = "event";

    public DbHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TBL_EVENT +
                " (ts INTEGER PRIMARY KEY, addr TEXT, code TEXT, sent_ts INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i2) {
        // NOP
    }
}
