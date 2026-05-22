package wlw231.cly.qingke.ui.ai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import wlw231.cly.qingke.R;
import wlw231.cly.qingke.data.CourseDatabaseHelper;
import wlw231.cly.qingke.model.Course;
import wlw231.cly.qingke.ui.plan.PlanDatabaseHelper;
import wlw231.cly.qingke.ui.plan.PlanEntity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import android.app.DatePickerDialog;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.RadioButton;

public class AIAssistantFragment extends Fragment {

    private static final String TAG = "AIAssistantFragment";
    private static final String SERVER_HOST = "192.168.5.124";
    private static final int SERVER_PORT = 6000;

    private RecyclerView rvMessages;
    private EditText etMessage;
    private ChatAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String userId;

    private final ActivityResultLauncher<String> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    String fileName = getFileName(uri);
                    adapter.addMessage(new ChatAdapter.Message("📎 上传文件: " + fileName, true));
                    uploadFile(uri, fileName);
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userId = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
                .getString("user_id", "user_123");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai_assistant, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
    }

    private void initViews(View root) {
        rvMessages = root.findViewById(R.id.rvMessages);
        etMessage = root.findViewById(R.id.etMessage);
        MaterialButton btnSend = root.findViewById(R.id.btnSend);
        MaterialButton btnUploadFile = root.findViewById(R.id.btnUploadFile);
        MaterialButton btnClearChat = root.findViewById(R.id.btnClearChat);
        MaterialButton btnPlanSuggest = root.findViewById(R.id.btnPlanSuggest);

        adapter = new ChatAdapter();
        rvMessages.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMessages.setItemAnimator(null); // 禁用动画，减少闪烁
        rvMessages.setAdapter(adapter);

        adapter.addMessage(new ChatAdapter.Message("你好！我是 AI 助手，有什么可以帮你的？", false));

        btnSend.setOnClickListener(v -> sendMessage());
        btnUploadFile.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
        btnClearChat.setOnClickListener(v -> {
            adapter.clearMessages();
            adapter.addMessage(new ChatAdapter.Message("聊天已清空，有什么新问题？", false));
        });
        btnPlanSuggest.setOnClickListener(v -> showPlanSuggestDialog());
    }

    private void sendMessage() {
        String message = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(message)) return;

        adapter.addMessage(new ChatAdapter.Message(message, true));
        etMessage.setText("");
        scrollToBottom();

        adapter.addMessage(new ChatAdapter.Message("...", false));
        int loadingPos = adapter.getItemCount() - 1;

        executor.execute(() -> {
            String response = sendChatViaSocket(userId, message);
            mainHandler.post(() -> {
                adapter.removeMessageAt(loadingPos);
                if (response != null && !response.isEmpty()) {
                    adapter.addMessage(new ChatAdapter.Message(response, false));
                } else {
                    adapter.addMessage(new ChatAdapter.Message("网络错误，请稍后重试", false));
                }
                scrollToBottom();
            });
        });
    }

    private String sendChatViaSocket(String userId, String message) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            socket.setSoTimeout(0);

            OutputStream out = socket.getOutputStream();
            byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
            String header = "CHAT|" + userId + "|" + msgBytes.length + "\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(msgBytes);
            out.flush();

            InputStream in = socket.getInputStream();
            String respHeader = readLine(in);
            Log.d(TAG, "响应头: " + respHeader);

            if (respHeader == null || respHeader.isEmpty()) {
                Log.e(TAG, "响应头为空");
                return null;
            }

            if (respHeader.startsWith("CHUNK|")) {
                return readStreamingResponse(in, respHeader);
            } else if (respHeader.startsWith("ANSWER|")) {
                int ansLen = Integer.parseInt(respHeader.split("\\|")[1]);
                byte[] ansBytes = readExact(in, ansLen);
                return new String(ansBytes, StandardCharsets.UTF_8);
            } else {
                Log.e(TAG, "未知响应头: " + respHeader);
                return null;
            }
        } catch (SocketTimeoutException e) {
            Log.e(TAG, "Socket 超时", e);
            return "服务响应超时，请稍后重试";
        } catch (IOException e) {
            Log.e(TAG, "IO 异常", e);
            return null;
        }
    }

    private String readStreamingResponse(InputStream in, String firstChunkHeader) throws IOException {
        StringBuilder fullAnswer = new StringBuilder();
        int loadingPos = adapter.getItemCount() - 1;

        String header = firstChunkHeader;
        while (true) {
            if (header.startsWith("CHUNK|")) {
                int len = Integer.parseInt(header.split("\\|")[1]);
                byte[] data = readExact(in, len);
                String chunk = new String(data, StandardCharsets.UTF_8);
                fullAnswer.append(chunk);

                mainHandler.post(() -> {
                    if (loadingPos >= 0 && loadingPos < adapter.getItemCount()) {
                        ChatAdapter.Message msg = adapter.messages.get(loadingPos);
                        if (!msg.isUser) {
                            msg.text = fullAnswer.toString();
                            adapter.notifyItemChanged(loadingPos, "text_update");
                        }
                    }
                    scrollToBottom();
                });

                header = readLine(in);
            } else if ("CHUNK_END".equals(header)) {
                Log.d(TAG, "流式传输结束");
                break;
            } else {
                Log.w(TAG, "未知流式头部: " + header);
                break;
            }
        }
        return fullAnswer.toString();
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int ch;
        while ((ch = in.read()) != -1 && ch != '\n') {
            baos.write(ch);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private byte[] readExact(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(data, offset, length - offset);
            if (read == -1) break;
            offset += read;
        }
        return data;
    }

    private void uploadFile(Uri uri, String fileName) {
        executor.execute(() -> {
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {

                if (in == null) {
                    mainHandler.post(() -> adapter.addMessage(new ChatAdapter.Message("无法读取文件", false)));
                    return;
                }

                byte[] fileBytes = IOUtils.toByteArray(in);
                OutputStream out = socket.getOutputStream();
                String header = "FILE|" + userId + "|" + fileName + "|" + fileBytes.length + "\n";
                out.write(header.getBytes(StandardCharsets.UTF_8));
                out.write(fileBytes);
                out.flush();

                InputStream socketIn = socket.getInputStream();
                StringBuilder respBuilder = new StringBuilder();
                int ch;
                while ((ch = socketIn.read()) != -1 && ch != '\n') {
                    respBuilder.append((char) ch);
                }
                String response = respBuilder.toString();

                mainHandler.post(() -> {
                    if (response.startsWith("FILE_SUCCESS")) {
                        String[] parts = response.split("\\|");
                        String msg = "✅ 文件上传成功，知识已存入图谱";
                        if (parts.length >= 3) {
                            msg += "（实体: " + parts[1] + "，关系: " + parts[2] + "）";
                        }
                        adapter.addMessage(new ChatAdapter.Message(msg, false));
                    } else {
                        adapter.addMessage(new ChatAdapter.Message("❌ 文件处理失败: " + response, false));
                    }
                });
            } catch (IOException e) {
                Log.e(TAG, "文件上传失败", e);
                mainHandler.post(() -> adapter.addMessage(new ChatAdapter.Message("网络错误，文件上传失败", false)));
            }
        });
    }

    private String getFileName(Uri uri) {
        String result = "unknown";
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) result = cursor.getString(index);
                }
            }
        }
        if ("unknown".equals(result)) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) result = path.substring(cut + 1);
            }
        }
        return result;
    }

    // ---------- 计划建议功能 ----------

    private Calendar planStartCal = Calendar.getInstance();
    private Calendar planEndCal = Calendar.getInstance();
    private Calendar courseStartCal = Calendar.getInstance();
    private Calendar courseEndCal = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private void showPlanSuggestDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_plan_suggest, null);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();

        // 初始化默认日期为今天
        resetToToday(planStartCal, planEndCal);
        resetToToday(courseStartCal, courseEndCal);

        RadioGroup rgPlanRange = dialogView.findViewById(R.id.rgPlanRange);
        RadioGroup rgCourseRange = dialogView.findViewById(R.id.rgCourseRange);
        LinearLayout layoutPlanCustom = dialogView.findViewById(R.id.layoutPlanCustom);
        LinearLayout layoutCourseCustom = dialogView.findViewById(R.id.layoutCourseCustom);
        TextView tvPlanStartDate = dialogView.findViewById(R.id.tvPlanStartDate);
        TextView tvPlanEndDate = dialogView.findViewById(R.id.tvPlanEndDate);
        TextView tvCourseStartDate = dialogView.findViewById(R.id.tvCourseStartDate);
        TextView tvCourseEndDate = dialogView.findViewById(R.id.tvCourseEndDate);
        TextView tvPlanCount = dialogView.findViewById(R.id.tvPlanCount);
        TextView tvCourseCount = dialogView.findViewById(R.id.tvCourseCount);

        // 计划范围快捷选项监听
        rgPlanRange.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbPlanToday) {
                layoutPlanCustom.setVisibility(View.GONE);
                resetToToday(planStartCal, planEndCal);
            } else if (checkedId == R.id.rbPlanThisWeek) {
                layoutPlanCustom.setVisibility(View.GONE);
                setToThisWeek(planStartCal, planEndCal);
            } else if (checkedId == R.id.rbPlanNextWeek) {
                layoutPlanCustom.setVisibility(View.GONE);
                setToNextWeek(planStartCal, planEndCal);
            } else if (checkedId == R.id.rbPlanCustom) {
                layoutPlanCustom.setVisibility(View.VISIBLE);
                tvPlanStartDate.setText(dateFormat.format(planStartCal.getTime()));
                tvPlanEndDate.setText(dateFormat.format(planEndCal.getTime()));
            }
            updateDataSummary(tvPlanCount, tvCourseCount);
        });

        // 课程范围快捷选项监听
        rgCourseRange.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbCourseToday) {
                layoutCourseCustom.setVisibility(View.GONE);
                resetToToday(courseStartCal, courseEndCal);
            } else if (checkedId == R.id.rbCourseThisWeek) {
                layoutCourseCustom.setVisibility(View.GONE);
                setToThisWeek(courseStartCal, courseEndCal);
            } else if (checkedId == R.id.rbCourseNextWeek) {
                layoutCourseCustom.setVisibility(View.GONE);
                setToNextWeek(courseStartCal, courseEndCal);
            } else if (checkedId == R.id.rbCourseCustom) {
                layoutCourseCustom.setVisibility(View.VISIBLE);
                tvCourseStartDate.setText(dateFormat.format(courseStartCal.getTime()));
                tvCourseEndDate.setText(dateFormat.format(courseEndCal.getTime()));
            }
            updateDataSummary(tvPlanCount, tvCourseCount);
        });

        // 自定义日期选择器点击事件
        tvPlanStartDate.setOnClickListener(v -> showDatePickerFor(planStartCal, tvPlanStartDate, () ->
                updateDataSummary(tvPlanCount, tvCourseCount)));
        tvPlanEndDate.setOnClickListener(v -> showDatePickerFor(planEndCal, tvPlanEndDate, () ->
                updateDataSummary(tvPlanCount, tvCourseCount)));
        tvCourseStartDate.setOnClickListener(v -> showDatePickerFor(courseStartCal, tvCourseStartDate, () ->
                updateDataSummary(tvPlanCount, tvCourseCount)));
        tvCourseEndDate.setOnClickListener(v -> showDatePickerFor(courseEndCal, tvCourseEndDate, () ->
                updateDataSummary(tvPlanCount, tvCourseCount)));

        // 初始加载数据摘要
        updateDataSummary(tvPlanCount, tvCourseCount);

        // 按钮事件
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnSend).setOnClickListener(v -> {
            dialog.dismiss();
            executePlanSuggest();
        });

        dialog.show();
    }

    private void resetToToday(Calendar start, Calendar end) {
        start.setTimeInMillis(System.currentTimeMillis());
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        end.setTimeInMillis(start.getTimeInMillis());
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);
    }

    private void setToThisWeek(Calendar start, Calendar end) {
        start.setTimeInMillis(System.currentTimeMillis());
        start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        end.setTimeInMillis(start.getTimeInMillis());
        end.add(Calendar.DAY_OF_WEEK, 6);
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);
    }

    private void setToNextWeek(Calendar start, Calendar end) {
        setToThisWeek(start, end);
        start.add(Calendar.WEEK_OF_YEAR, 1);
        end.add(Calendar.WEEK_OF_YEAR, 1);
    }

    private void showDatePickerFor(Calendar cal, TextView tv, Runnable onDateSet) {
        new DatePickerDialog(requireContext(),
                (view, year, month, day) -> {
                    cal.set(Calendar.YEAR, year);
                    cal.set(Calendar.MONTH, month);
                    cal.set(Calendar.DAY_OF_MONTH, day);
                    tv.setText(dateFormat.format(cal.getTime()));
                    if (onDateSet != null) onDateSet.run();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDataSummary(TextView tvPlanCount, TextView tvCourseCount) {
        PlanDatabaseHelper planDb = PlanDatabaseHelper.getInstance(requireContext());
        List<PlanEntity> plans = planDb.queryPlansByTimeRangeSync(
                planStartCal.getTimeInMillis(), planEndCal.getTimeInMillis());
        tvPlanCount.setText("计划：" + plans.size() + " 条");

        CourseDatabaseHelper courseDb = new CourseDatabaseHelper(requireContext());
        List<Integer> weekdays = getWeekdaysInRange(courseStartCal, courseEndCal);
        int currentWeek = getCurrentWeek();
        List<Course> courses = courseDb.queryCoursesByWeekdaysAndWeek(weekdays, currentWeek);
        tvCourseCount.setText("课程：" + courses.size() + " 节");
    }

    private void executePlanSuggest() {
        adapter.addMessage(new ChatAdapter.Message("📋 正在获取计划建议...", true));
        scrollToBottom();

        adapter.addMessage(new ChatAdapter.Message("...", false));
        int loadingPos = adapter.getItemCount() - 1;

        executor.execute(() -> {
            // 查询数据
            PlanDatabaseHelper planDb = PlanDatabaseHelper.getInstance(requireContext());
            List<PlanEntity> plans = planDb.queryPlansByTimeRangeSync(
                    planStartCal.getTimeInMillis(), planEndCal.getTimeInMillis());

            CourseDatabaseHelper courseDb = new CourseDatabaseHelper(requireContext());
            List<Integer> weekdays = getWeekdaysInRange(courseStartCal, courseEndCal);
            int currentWeek = getCurrentWeek();
            List<Course> courses = courseDb.queryCoursesByWeekdaysAndWeek(weekdays, currentWeek);

            // 构建 JSON
            String json = buildPlanSuggestJson(plans, courses);

            // 发送请求
            String response = sendPlanSuggestViaSocket(userId, json);

            mainHandler.post(() -> {
                adapter.removeMessageAt(loadingPos);
                if (response != null && !response.isEmpty()) {
                    adapter.addMessage(new ChatAdapter.Message(response, false));
                } else {
                    adapter.addMessage(new ChatAdapter.Message("网络错误，请稍后重试", false));
                }
                scrollToBottom();
            });
        });
    }

    private String buildPlanSuggestJson(List<PlanEntity> plans, List<Course> courses) {
        SimpleDateFormat dtFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        JsonObject root = new JsonObject();
        root.addProperty("userId", userId);

        // 计划时间范围
        JsonObject planRange = new JsonObject();
        planRange.addProperty("start", dateFormat.format(planStartCal.getTime()));
        planRange.addProperty("end", dateFormat.format(planEndCal.getTime()));
        root.add("planRange", planRange);

        // 课程时间范围
        JsonObject courseRange = new JsonObject();
        courseRange.addProperty("start", dateFormat.format(courseStartCal.getTime()));
        courseRange.addProperty("end", dateFormat.format(courseEndCal.getTime()));
        root.add("courseRange", courseRange);

        // 计划数组
        JsonArray plansArray = new JsonArray();
        for (PlanEntity plan : plans) {
            JsonObject p = new JsonObject();
            p.addProperty("id", plan.id);
            p.addProperty("name", plan.name);
            p.addProperty("quadrant", plan.quadrant);
            p.addProperty("quadrantName", getQuadrantName(plan.quadrant));
            p.addProperty("startTime", dtFormat.format(new java.util.Date(plan.startTimeMillis)));
            p.addProperty("endTime", dtFormat.format(new java.util.Date(plan.endTimeMillis)));
            p.addProperty("completed", plan.isCompleted);
            plansArray.add(p);
        }
        root.add("plans", plansArray);

        // 课程数组
        JsonArray coursesArray = new JsonArray();
        String[] weekdayNames = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (Course course : courses) {
            JsonObject c = new JsonObject();
            c.addProperty("id", course.getId());
            c.addProperty("name", course.getName());
            c.addProperty("weekday", course.getWeekday());
            c.addProperty("weekdayName", weekdayNames[course.getWeekday()]);
            c.addProperty("section", course.getSection());
            c.addProperty("sectionDisplay", course.getSectionDisplay());
            c.addProperty("classroom", course.getClassroom() != null ? course.getClassroom() : "");
            c.addProperty("teacher", course.getTeacher() != null ? course.getTeacher() : "");
            c.addProperty("startWeek", course.getStartWeek());
            c.addProperty("endWeek", course.getEndWeek());
            c.addProperty("startTime", course.getStartTime() != null ? course.getStartTime() : "");
            c.addProperty("endTime", course.getEndTime() != null ? course.getEndTime() : "");
            coursesArray.add(c);
        }
        root.add("courses", coursesArray);

        return root.toString();
    }

    private String sendPlanSuggestViaSocket(String userId, String json) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            socket.setSoTimeout(0);

            OutputStream out = socket.getOutputStream();
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            String header = "PLAN_SUGGEST|" + userId + "|" + jsonBytes.length + "\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(jsonBytes);
            out.flush();

            InputStream in = socket.getInputStream();
            String respHeader = readLine(in);
            Log.d(TAG, "计划建议响应头: " + respHeader);

            if (respHeader == null || respHeader.isEmpty()) {
                Log.e(TAG, "响应头为空");
                return null;
            }

            if (respHeader.startsWith("CHUNK|")) {
                return readStreamingResponse(in, respHeader);
            } else if (respHeader.startsWith("ANSWER|")) {
                int ansLen = Integer.parseInt(respHeader.split("\\|")[1]);
                byte[] ansBytes = readExact(in, ansLen);
                return new String(ansBytes, StandardCharsets.UTF_8);
            } else {
                Log.e(TAG, "未知响应头: " + respHeader);
                return null;
            }
        } catch (SocketTimeoutException e) {
            Log.e(TAG, "Socket 超时", e);
            return "服务响应超时，请稍后重试";
        } catch (IOException e) {
            Log.e(TAG, "IO 异常", e);
            return null;
        }
    }

    private String getQuadrantName(int quadrant) {
        switch (quadrant) {
            case 1: return "重要且紧急";
            case 2: return "重要不紧急";
            case 3: return "紧急不重要";
            default: return "不重要不紧急";
        }
    }

    /**
     * 获取日期范围内包含的所有星期几（1=周一...7=周日）
     */
    private List<Integer> getWeekdaysInRange(Calendar start, Calendar end) {
        List<Integer> weekdays = new ArrayList<>();
        Calendar temp = (Calendar) start.clone();
        while (!temp.after(end)) {
            int dayOfWeek = temp.get(Calendar.DAY_OF_WEEK);
            // Calendar.SUNDAY=1, MONDAY=2...SATURDAY=7 → 转换为 1=周一...7=周日
            int converted = (dayOfWeek == Calendar.SUNDAY) ? 7 : dayOfWeek - 1;
            if (!weekdays.contains(converted)) {
                weekdays.add(converted);
            }
            temp.add(Calendar.DAY_OF_MONTH, 1);
            // 避免无限循环，如果天数超过7，所有星期几都已覆盖
            if (weekdays.size() >= 7) break;
        }
        return weekdays;
    }

    /**
     * 获取当前周次（简单实现：以学期第1周的周一日期为基准计算）
     * 此处默认返回1，实际项目中应从SharedPreferences或设置中读取
     */
    private int getCurrentWeek() {
        // TODO: 从设置中读取学期开始日期并计算当前周次
        // 暂时返回1作为默认值
        return 1;
    }

    private void scrollToBottom() {
        rvMessages.post(() -> rvMessages.smoothScrollToPosition(adapter.getItemCount() - 1));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
    }

    // ---------- 内部适配器类 ----------
    public static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

        public static class Message {
            public String text;
            public final boolean isUser;

            public Message(String text, boolean isUser) {
                this.text = text;
                this.isUser = isUser;
            }
        }

        final List<Message> messages = new ArrayList<>();

        public void addMessage(Message msg) {
            messages.add(msg);
            notifyItemInserted(messages.size() - 1);
        }

        public void removeMessageAt(int position) {
            if (position >= 0 && position < messages.size()) {
                messages.remove(position);
                notifyItemRemoved(position);
            }
        }

        public void clearMessages() {
            int size = messages.size();
            messages.clear();
            notifyItemRangeRemoved(0, size);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_simple, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Message msg = messages.get(position);
            if (msg.isUser) {
                holder.cardUser.setVisibility(View.VISIBLE);
                holder.cardAssistant.setVisibility(View.GONE);
                holder.tvUserMessage.setText(msg.text);
            } else {
                holder.cardUser.setVisibility(View.GONE);
                holder.cardAssistant.setVisibility(View.VISIBLE);
                holder.tvAssistantMessage.setText(msg.text);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (!payloads.isEmpty()) {
                Message msg = messages.get(position);
                if (!msg.isUser) {
                    holder.tvAssistantMessage.setText(msg.text);
                } else {
                    holder.tvUserMessage.setText(msg.text);
                }
            } else {
                onBindViewHolder(holder, position);
            }
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final MaterialCardView cardUser, cardAssistant;
            final TextView tvUserMessage, tvAssistantMessage;

            ViewHolder(View itemView) {
                super(itemView);
                cardUser = itemView.findViewById(R.id.cardUser);
                cardAssistant = itemView.findViewById(R.id.cardAssistant);
                tvUserMessage = itemView.findViewById(R.id.tvUserMessage);
                tvAssistantMessage = itemView.findViewById(R.id.tvAssistantMessage);
            }
        }
    }
}