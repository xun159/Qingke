package wlw231.cly.qingke.ui.plan;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlanDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "plans.db";
    private static final int DATABASE_VERSION = 2; // 升级版本号
    private static final String TABLE_PLANS = "plans";

    private static final String COL_ID = "id";
    private static final String COL_NAME = "name";
    private static final String COL_QUADRANT = "quadrant";
    private static final String COL_START_TIME = "start_time";
    private static final String COL_END_TIME = "end_time";
    private static final String COL_COMPLETED = "completed";
    private static final String COL_NOTIFIED = "notified";

    private static PlanDatabaseHelper instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<List<PlanEntity>> todayPlansLiveData = new MutableLiveData<>();

    public static synchronized PlanDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new PlanDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private PlanDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_PLANS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NAME + " TEXT NOT NULL, " +
                COL_QUADRANT + " INTEGER NOT NULL, " +
                COL_START_TIME + " INTEGER NOT NULL, " +
                COL_END_TIME + " INTEGER NOT NULL, " +
                COL_COMPLETED + " INTEGER DEFAULT 0, " +
                COL_NOTIFIED + " INTEGER DEFAULT 0)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // 版本1 -> 2：添加 notified 列
            db.execSQL("ALTER TABLE " + TABLE_PLANS + " ADD COLUMN " + COL_NOTIFIED + " INTEGER DEFAULT 0");
        }
        // 后续版本升级可继续添加
    }

    // ---------- 异步操作（用于 UI 刷新） ----------
    public void insertPlan(PlanEntity plan, Runnable onComplete) {
        executor.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COL_NAME, plan.name);
            values.put(COL_QUADRANT, plan.quadrant);
            values.put(COL_START_TIME, plan.startTimeMillis);
            values.put(COL_END_TIME, plan.endTimeMillis);
            values.put(COL_COMPLETED, plan.isCompleted ? 1 : 0);
            values.put(COL_NOTIFIED, plan.notified ? 1 : 0);
            db.insert(TABLE_PLANS, null, values);
            db.close();
            if (onComplete != null) onComplete.run();
            refreshTodayPlans();
        });
    }

    public void deletePlan(long planId, Runnable onComplete) {
        executor.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_PLANS, COL_ID + " = ?", new String[]{String.valueOf(planId)});
            db.close();
            if (onComplete != null) onComplete.run();
            refreshTodayPlans();
        });
    }

    public void updatePlan(PlanEntity plan, Runnable onComplete) {
        executor.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COL_NAME, plan.name);
            values.put(COL_QUADRANT, plan.quadrant);
            values.put(COL_START_TIME, plan.startTimeMillis);
            values.put(COL_END_TIME, plan.endTimeMillis);
            values.put(COL_COMPLETED, plan.isCompleted ? 1 : 0);
            values.put(COL_NOTIFIED, plan.notified ? 1 : 0);
            db.update(TABLE_PLANS, values, COL_ID + " = ?", new String[]{String.valueOf(plan.id)});
            db.close();
            if (onComplete != null) onComplete.run();
            refreshTodayPlans();
        });
    }

    public void refreshTodayPlans() {
        executor.execute(() -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            long dayStart = cal.getTimeInMillis();

            cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
            cal.set(java.util.Calendar.MINUTE, 59);
            cal.set(java.util.Calendar.SECOND, 59);
            cal.set(java.util.Calendar.MILLISECOND, 999);
            long dayEnd = cal.getTimeInMillis();

            List<PlanEntity> plans = queryTodayPlansSync(dayStart, dayEnd);
            todayPlansLiveData.postValue(plans);
        });
    }

    public LiveData<List<PlanEntity>> getTodayPlansLiveData() {
        return todayPlansLiveData;
    }

    // ---------- 同步方法（用于 Service 轮询） ----------
    public List<PlanEntity> getAllPlansSync() {
        List<PlanEntity> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_PLANS, null, null, null, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                PlanEntity plan = cursorToPlan(cursor);
                list.add(plan);
            }
            cursor.close();
        }
        db.close();
        return list;
    }

    public PlanEntity getPlanById(long planId) {
        SQLiteDatabase db = getReadableDatabase();
        PlanEntity plan = null;
        Cursor cursor = db.query(TABLE_PLANS, null,
                COL_ID + " = ?", new String[]{String.valueOf(planId)},
                null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                plan = cursorToPlan(cursor);
            }
            cursor.close();
        }
        db.close();
        return plan;
    }

    public void markNotified(long planId) {
        executor.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COL_NOTIFIED, 1);
            db.update(TABLE_PLANS, values, COL_ID + " = ?", new String[]{String.valueOf(planId)});
            db.close();
            // 同时刷新 LiveData，避免 UI 显示不一致
            refreshTodayPlans();
        });
    }

    // ---------- 私有辅助方法 ----------
    private List<PlanEntity> queryTodayPlansSync(long dayStart, long dayEnd) {
        List<PlanEntity> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_PLANS, null,
                COL_START_TIME + " BETWEEN ? AND ?",
                new String[]{String.valueOf(dayStart), String.valueOf(dayEnd)},
                null, null, COL_START_TIME + " ASC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                PlanEntity plan = cursorToPlan(cursor);
                list.add(plan);
            }
            cursor.close();
        }
        db.close();
        return list;
    }

    private PlanEntity cursorToPlan(Cursor cursor) {
        PlanEntity plan = new PlanEntity();
        plan.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
        plan.name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME));
        plan.quadrant = cursor.getInt(cursor.getColumnIndexOrThrow(COL_QUADRANT));
        plan.startTimeMillis = cursor.getLong(cursor.getColumnIndexOrThrow(COL_START_TIME));
        plan.endTimeMillis = cursor.getLong(cursor.getColumnIndexOrThrow(COL_END_TIME));
        plan.isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow(COL_COMPLETED)) == 1;
        plan.notified = cursor.getInt(cursor.getColumnIndexOrThrow(COL_NOTIFIED)) == 1;
        return plan;
    }
}