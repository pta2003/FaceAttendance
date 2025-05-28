package com.example.faceattendance.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.faceattendance.R;
import com.example.faceattendance.model.AttendanceLog;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttendanceLogAdapter extends RecyclerView.Adapter<AttendanceLogAdapter.LogViewHolder> {

    private List<AttendanceLog> logList;

    public AttendanceLogAdapter(List<AttendanceLog> logList) {
        this.logList = logList;
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        AttendanceLog log = logList.get(position);
        holder.tvName.setText(log.employeeName);
        holder.tvId.setText("ID: " + log.employeeId);

        // Format timestamp for better display
        String formattedTime = formatTimestamp(log.timestamp);
        holder.tvTimestamp.setText(formattedTime);

        // Hiển thị chỉ giờ và phút
        String timeOnly = extractTimeOnly(log.timestamp);
        holder.tvTimeOnly.setText(timeOnly);

        // Decode Base64 image
        if (log.faceBase64 != null && !log.faceBase64.isEmpty()) {
            try {
                byte[] decodedBytes = Base64.decode(log.faceBase64, Base64.NO_WRAP);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                holder.ivFace.setImageBitmap(bitmap);
            } catch (Exception e) {
                // Handle Base64 decode error
                holder.ivFace.setImageResource(R.drawable.ic_person_placeholder);
            }
        } else {
            holder.ivFace.setImageResource(R.drawable.ic_person_placeholder);
        }
    }

    @Override
    public int getItemCount() {
        return logList.size();
    }

    // Method to update data and notify adapter
    public void updateData(List<AttendanceLog> newLogs) {
        this.logList.clear();
        this.logList.addAll(newLogs);
        notifyDataSetChanged();
    }

    // Method to add new log at the beginning
    public void addLog(AttendanceLog log) {
        logList.add(0, log);
        notifyItemInserted(0);
    }

    // Method to clear all data
    public void clearData() {
        logList.clear();
        notifyDataSetChanged();
    }

    private String extractTimeOnly(String timestamp) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date date = inputFormat.parse(timestamp);
            return timeFormat.format(date);
        } catch (ParseException e) {
            return "--:--"; // hoặc trả về "--:--" nếu muốn hiển thị rõ ràng lỗi
        }
    }


    // Helper method to format timestamp
    private String formatTimestamp(String timestamp) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date date = inputFormat.parse(timestamp);
            return outputFormat.format(date);
        } catch (ParseException e) {
            return timestamp; // Return original if parsing fails
        }
    }

    public static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvId, tvTimestamp,tvTimeOnly;
        ImageView ivFace;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvLogName);
            tvId = itemView.findViewById(R.id.tvLogId);
            tvTimestamp = itemView.findViewById(R.id.tvLogTimestamp);
            ivFace = itemView.findViewById(R.id.ivLogFace);
            tvTimeOnly = itemView.findViewById(R.id.tvTimeOnly);
        }
    }
}