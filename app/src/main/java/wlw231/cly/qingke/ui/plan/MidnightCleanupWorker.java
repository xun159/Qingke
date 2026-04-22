package wlw231.cly.qingke.ui.plan;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;


public class MidnightCleanupWorker extends Worker {
    public MidnightCleanupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        PlanDatabaseHelper.getInstance(getApplicationContext()).deleteAllCompletedPlans();
        return Result.success();
    }
}