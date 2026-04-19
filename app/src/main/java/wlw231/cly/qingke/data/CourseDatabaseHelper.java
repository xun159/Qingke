package wlw231.cly.qingke.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import wlw231.cly.qingke.model.Course;   // ← 必须导入

public class CourseDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "courses.db";
    private static final int DATABASE_VERSION = 3;

    public static final String TABLE_COURSES = "courses";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_WEEKDAY = "weekday";
    public static final String COLUMN_SECTION = "section";
    public static final String COLUMN_SUB_SECTIONS = "sub_sections";
    public static final String COLUMN_START_WEEK = "start_week";
    public static final String COLUMN_END_WEEK = "end_week";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_TEACHER = "teacher";
    public static final String COLUMN_CLASSROOM = "classroom";
    public static final String COLUMN_COLOR = "color";
    public static final String COLUMN_START_TIME = "start_time";
    public static final String COLUMN_END_TIME = "end_time";

    private static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_COURSES + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_WEEKDAY + " INTEGER NOT NULL, " +
                    COLUMN_SECTION + " INTEGER NOT NULL, " +
                    COLUMN_SUB_SECTIONS + " INTEGER DEFAULT 2, " +
                    COLUMN_START_WEEK + " INTEGER DEFAULT 1, " +
                    COLUMN_END_WEEK + " INTEGER DEFAULT 20, " +
                    COLUMN_NAME + " TEXT, " +
                    COLUMN_TEACHER + " TEXT, " +
                    COLUMN_CLASSROOM + " TEXT, " +
                    COLUMN_COLOR + " TEXT, " +
                    COLUMN_START_TIME + " TEXT, " +
                    COLUMN_END_TIME + " TEXT, " +
                    "UNIQUE(" + COLUMN_WEEKDAY + "," + COLUMN_SECTION + "," +
                    COLUMN_START_WEEK + "," + COLUMN_END_WEEK + ") ON CONFLICT REPLACE)";

    public CourseDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_COURSES);
        onCreate(db);
    }

    public long saveCourse(Course course) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = courseToContentValues(course);
        long id = db.insertWithOnConflict(TABLE_COURSES, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
        return id;
    }

    public int deleteCourse(int weekday, int section, int startWeek, int endWeek) {
        SQLiteDatabase db = getWritableDatabase();
        int rows = db.delete(TABLE_COURSES,
                COLUMN_WEEKDAY + "=? AND " + COLUMN_SECTION + "=? AND " +
                        COLUMN_START_WEEK + "=? AND " + COLUMN_END_WEEK + "=?",
                new String[]{String.valueOf(weekday), String.valueOf(section),
                        String.valueOf(startWeek), String.valueOf(endWeek)});
        db.close();
        return rows;
    }

    public Cursor queryCoursesByWeek(int week) {
        SQLiteDatabase db = getReadableDatabase();
        String selection = COLUMN_START_WEEK + "<=? AND " + COLUMN_END_WEEK + ">=?";
        String[] args = {String.valueOf(week), String.valueOf(week)};
        return db.query(TABLE_COURSES, null, selection, args, null, null, null);
    }

    public static Course cursorToCourse(Cursor cursor) {
        Course course = new Course();

        int idIndex = cursor.getColumnIndex(COLUMN_ID);
        if (idIndex >= 0) course.setId(cursor.getInt(idIndex));

        int weekdayIndex = cursor.getColumnIndex(COLUMN_WEEKDAY);
        if (weekdayIndex >= 0) course.setWeekday(cursor.getInt(weekdayIndex));

        int sectionIndex = cursor.getColumnIndex(COLUMN_SECTION);
        if (sectionIndex >= 0) course.setSection(cursor.getInt(sectionIndex));

        int subSectionsIndex = cursor.getColumnIndex(COLUMN_SUB_SECTIONS);
        if (subSectionsIndex >= 0) course.setSubSections(cursor.getInt(subSectionsIndex));

        int startWeekIndex = cursor.getColumnIndex(COLUMN_START_WEEK);
        if (startWeekIndex >= 0) course.setStartWeek(cursor.getInt(startWeekIndex));

        int endWeekIndex = cursor.getColumnIndex(COLUMN_END_WEEK);
        if (endWeekIndex >= 0) course.setEndWeek(cursor.getInt(endWeekIndex));

        int nameIndex = cursor.getColumnIndex(COLUMN_NAME);
        if (nameIndex >= 0) course.setName(cursor.getString(nameIndex));

        int teacherIndex = cursor.getColumnIndex(COLUMN_TEACHER);
        if (teacherIndex >= 0) course.setTeacher(cursor.getString(teacherIndex));

        int classroomIndex = cursor.getColumnIndex(COLUMN_CLASSROOM);
        if (classroomIndex >= 0) course.setClassroom(cursor.getString(classroomIndex));

        int colorIndex = cursor.getColumnIndex(COLUMN_COLOR);
        if (colorIndex >= 0) course.setColor(cursor.getString(colorIndex));

        int startTimeIndex = cursor.getColumnIndex(COLUMN_START_TIME);
        if (startTimeIndex >= 0) course.setStartTime(cursor.getString(startTimeIndex));

        int endTimeIndex = cursor.getColumnIndex(COLUMN_END_TIME);
        if (endTimeIndex >= 0) course.setEndTime(cursor.getString(endTimeIndex));

        return course;
    }

    private ContentValues courseToContentValues(Course course) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_WEEKDAY, course.getWeekday());
        values.put(COLUMN_SECTION, course.getSection());
        values.put(COLUMN_SUB_SECTIONS, course.getSubSections());
        values.put(COLUMN_START_WEEK, course.getStartWeek());
        values.put(COLUMN_END_WEEK, course.getEndWeek());
        values.put(COLUMN_NAME, course.getName());
        values.put(COLUMN_TEACHER, course.getTeacher());
        values.put(COLUMN_CLASSROOM, course.getClassroom());
        values.put(COLUMN_COLOR, course.getColor());
        values.put(COLUMN_START_TIME, course.getStartTime());
        values.put(COLUMN_END_TIME, course.getEndTime());
        return values;
    }
    public Cursor queryAllCourses() {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_COURSES, null, null, null, null, null, null);
    }
}