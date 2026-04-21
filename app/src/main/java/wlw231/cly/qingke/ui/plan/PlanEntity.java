package wlw231.cly.qingke.ui.plan;

public class PlanEntity {
    public long id;
    public String name;
    public int quadrant;
    public long startTimeMillis;
    public long endTimeMillis;
    public boolean isCompleted;
    public boolean notified;   // 新增：是否已发送过提醒通知
}