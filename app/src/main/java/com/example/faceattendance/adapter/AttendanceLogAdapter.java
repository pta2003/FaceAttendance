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

import java.util.List;

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
        holder.tvTimestamp.setText(log.timestamp);

        // Decode Base64 image
        if (log.faceBase64 != null && !log.faceBase64.isEmpty()) {
            byte[] decodedBytes = Base64.decode(log.faceBase64, Base64.NO_WRAP);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            holder.ivFace.setImageBitmap(bitmap);
        }
    }

    @Override
    public int getItemCount() {
        return logList.size();
    }

    public static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvId, tvTimestamp;
        ImageView ivFace;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvLogName);
            tvId = itemView.findViewById(R.id.tvLogId);
            tvTimestamp = itemView.findViewById(R.id.tvLogTimestamp);
            ivFace = itemView.findViewById(R.id.ivLogFace);
        }
    }
}
