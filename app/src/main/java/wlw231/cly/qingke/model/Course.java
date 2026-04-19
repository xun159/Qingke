package wlw231.cly.qingke.model;

import java.io.Serializable;

public class Course implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private int weekday;        // 1-7 周一至周日
    private int section;        // 大节 1-5
    private int subSections = 2; // 仅当 section=5 时有效（晚上小节数 2 或 3）
    private int startWeek = 1;   // 起始周 1-20
    private int endWeek = 20;    // 结束周 1-20
    private String name;         // 课程名称
    private String teacher;      // 教师
    private String classroom;    // 教室
    private String color = "#FFBB86FC"; // 颜色代码，默认淡紫色
    private String startTime;    // 上课时间 "HH:mm"
    private String endTime;      // 下课时间 "HH:mm"

    public Course() {}

    public Course(int weekday, int section, String name) {
        this.weekday = weekday;
        this.section = section;
        this.name = name;
    }

    // ---------- Getter / Setter ----------
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getWeekday() {
        return weekday;
    }

    public void setWeekday(int weekday) {
        this.weekday = weekday;
    }

    public int getSection() {
        return section;
    }

    public void setSection(int section) {
        this.section = section;
    }

    public int getSubSections() {
        return subSections;
    }

    public void setSubSections(int subSections) {
        this.subSections = subSections;
    }

    public int getStartWeek() {
        return startWeek;
    }

    public void setStartWeek(int startWeek) {
        this.startWeek = startWeek;
    }

    public int getEndWeek() {
        return endWeek;
    }

    public void setEndWeek(int endWeek) {
        this.endWeek = endWeek;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTeacher() {
        return teacher;
    }

    public void setTeacher(String teacher) {
        this.teacher = teacher;
    }

    public String getClassroom() {
        return classroom;
    }

    public void setClassroom(String classroom) {
        this.classroom = classroom;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    /**
     * 获取用于显示的节次描述，如 "1-2节"、"9-11节"
     */
    public String getSectionDisplay() {
        if (section < 5) {
            return (section * 2 - 1) + "-" + (section * 2) + "节";
        } else {
            return "9-" + (8 + subSections) + "节";
        }
    }
}