package kz.kazintech.barcodereader;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by berik on 26.04.15
 */
public class EventSender implements Runnable {

    private static final int BUF_LEN = 100;

    private boolean active = true;
    private SQLiteDatabase db;
    private String url;
    private long delay;
    private Handler handler;
    private Context context;

    public EventSender(Context context, SQLiteDatabase db, String url, long delay, Handler handler) {
        this.context = context;
        this.db = db;
        this.url = url;
        this.delay = delay;
        this.handler = handler;
    }

    public void stop() {
        active = false;
    }

    @Override
    public void run() {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EventSender");
        wl.acquire();
        try {
            while (active) {
                HttpClient http = new DefaultHttpClient();

                //db.delete(DbHelper.TBL_EVENT, "sent_ts IS NOT NULL", null);

                List<Record> buf = new ArrayList<Record>(BUF_LEN);
                Cursor cursor = db.rawQuery("SELECT ts,addr,code FROM " + DbHelper.TBL_EVENT +
                        " WHERE sent_ts IS NULL", null);
                int i = 0;
                while (cursor.moveToNext() && i++ < BUF_LEN) {
                    buf.add(new Record(cursor.getLong(0), cursor.getString(1), cursor.getString(2)));
                }
                cursor.close();

                for (Record rec : buf) {
                    try {
                        send(http, rec.timestamp, rec.address, rec.code);
                    } catch (Exception ex) {
                        Message.obtain(handler, MainActivity.MESSAGE_LOG, ex.getMessage()).sendToTarget();
                        break;
                    }
                    ContentValues values = new ContentValues();
                    values.put("sent_ts", System.currentTimeMillis());
                    db.update(DbHelper.TBL_EVENT, values, "ts=" + rec.timestamp, null);
                }
                try {
                    Thread.sleep(delay * 1000);
                } catch (InterruptedException ex) {
                    return;
                }
            }
        } finally {
            wl.release();
        }
    }

    private boolean send(HttpClient http, long ts, String addr, String code) throws Exception {

        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(3);
        nameValuePairs.add(new BasicNameValuePair("addr", addr));
        nameValuePairs.add(new BasicNameValuePair("data", code));
        nameValuePairs.add(new BasicNameValuePair("timestamp", "" + ts));

        HttpPost httppost = new HttpPost(url);
        httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
        HttpResponse response = http.execute(httppost);
        if (response.getStatusLine().getStatusCode() == 200) {
            return true;
        } else {
            throw new Exception(response.getStatusLine().getReasonPhrase());
        }
    }
}
