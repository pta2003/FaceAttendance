package com.example.faceattendance;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.example.faceattendance.adapter.AttendanceLogAdapter;
import com.example.faceattendance.model.FaceDatabase;
import com.example.faceattendance.model.AttendanceLog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttendanceHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private AttendanceLogAdapter adapter;
    private FaceDatabase db;

    // UI components
    private TextInputEditText etSearchEmployee;
    private MaterialButton btnDateFilter;
    private TextView tvTotalRecords;
    private TextView tvTodayRecords;
    private LinearLayout emptyStateLayout;
    private ProgressBar progressBar;

    // Data
    private List<AttendanceLog> allLogs;
    private List<AttendanceLog> filteredLogs;
    private String selectedDate = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_history);

        initViews();
        initDatabase();
        setupRecyclerView();
        setupListeners();
        loadData();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerViewLogs);
        etSearchEmployee = findViewById(R.id.etSearchEmployee);
        btnDateFilter = findViewById(R.id.btnDateFilter);
        tvTotalRecords = findViewById(R.id.tvTotalRecords);
        tvTodayRecords = findViewById(R.id.tvTodayRecords);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        progressBar = findViewById(R.id.progressBar);
    }

    private void initDatabase() {
        db = Room.databaseBuilder(getApplicationContext(),
                        FaceDatabase.class, "face_attendance_db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        filteredLogs = new ArrayList<>();
        adapter = new AttendanceLogAdapter(filteredLogs);
        recyclerView.setAdapter(adapter);

        // Hide keyboard when scrolling RecyclerView
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    hideKeyboard();
                }
            }
        });
    }

    private void setupListeners() {
        // Search listener
        etSearchEmployee.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterLogs();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Handle keyboard actions
        etSearchEmployee.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                        actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                        (event != null && event.getAction() == android.view.KeyEvent.ACTION_DOWN &&
                                event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {

                    // Hide keyboard
                    hideKeyboard();
                    filterLogs();
                    return true;
                }
                return false;
            }
        });

        // Date filter listener
        btnDateFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard(); // Hide keyboard when opening date picker
                showDatePicker();
            }
        });
    }

    private void loadData() {
        showLoading(true);

        // Simulate loading delay (remove this in production)
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500); // Simulate network delay
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        allLogs = db.attendanceLogDao().getAllLogs();
                        filteredLogs.clear();
                        filteredLogs.addAll(allLogs);

                        updateStats();
                        updateUI();
                        showLoading(false);
                    }
                });
            }
        }).start();
    }

    private void filterLogs() {
        String searchText = etSearchEmployee.getText().toString().toLowerCase().trim();

        filteredLogs.clear();

        for (AttendanceLog log : allLogs) {
            boolean matchesSearch = searchText.isEmpty() ||
                    log.employeeName.toLowerCase().contains(searchText) ||
                    log.employeeId.toLowerCase().contains(searchText) ||
                    log.employeeId.contains(searchText); // Tìm kiếm chính xác cho số

            boolean matchesDate = selectedDate.isEmpty() ||
                    log.timestamp.startsWith(selectedDate);

            if (matchesSearch && matchesDate) {
                filteredLogs.add(log);
            }
        }

        adapter.notifyDataSetChanged();
        updateUI();
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        selectedDate = String.format(Locale.getDefault(),
                                "%04d-%02d-%02d", year, month + 1, dayOfMonth);

                        // Update button text to show selected date
                        btnDateFilter.setText(selectedDate);

                        filterLogs();
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        // Add clear option
        datePickerDialog.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Xóa lọc",
                new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        selectedDate = "";
                        btnDateFilter.setText("Lọc ngày");
                        filterLogs();
                    }
                });

        datePickerDialog.show();
    }

    private void updateStats() {
        // Update total records
        tvTotalRecords.setText(String.valueOf(allLogs.size()));

        // Update today's records
        String today = getTodayString();
        int todayCount = 0;

        for (AttendanceLog log : allLogs) {
            if (log.timestamp.startsWith(today)) {
                todayCount++;
            }
        }

        tvTodayRecords.setText(String.valueOf(todayCount));
    }

    private void updateUI() {
        if (filteredLogs.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
        }
    }

    private void showLoading(boolean show) {
        if (show) {
            progressBar.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.GONE);
        } else {
            progressBar.setVisibility(View.GONE);
        }
    }

    private String getTodayString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }
}