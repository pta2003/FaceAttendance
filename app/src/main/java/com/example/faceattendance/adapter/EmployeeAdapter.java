package com.example.faceattendance.adapter;

import com.example.faceattendance.R;

import android.content.Context;
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

import com.example.faceattendance.model.Employee;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EmployeeAdapter extends RecyclerView.Adapter<EmployeeAdapter.EmployeeViewHolder> {

    private List<Employee> employeeList;
    private Context context;
    private OnEmployeeClickListener clickListener;

    // Interface để handle click event
    public interface OnEmployeeClickListener {
        void onEmployeeClick(String employeeId);
    }

    public EmployeeAdapter(Context context, List<Employee> employeeList, OnEmployeeClickListener clickListener) {
        this.employeeList = employeeList;
        this.context = context;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public EmployeeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_employee, parent, false);
        return new EmployeeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EmployeeViewHolder holder, int position) {
        Employee employee = employeeList.get(position);
        holder.employeeName.setText(employee.getEmployeeName());
        holder.employeeId.setText("ID: " + employee.getEmployeeId());
//        holder.registrationDate.setText("Ngày đăng kí: " + employee.getRegistrationDate());

        // Set registration date với format đẹp hơn
        String formattedDate = formatRegistrationDate(employee.getRegistrationDate());
        holder.registrationDate.setText("Đăng ký: " + formattedDate);
        // Load avatar image
        loadEmployeeAvatar(holder.employeeAvatar, employee);
        // Bắt sự kiện click với callback
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onEmployeeClick(employee.getEmployeeId());
            }
        });
    }

    @Override
    public int getItemCount() {
        return employeeList.size();
    }

    // Method để cập nhật dữ liệu
    public void updateEmployeeList(List<Employee> newEmployeeList) {
        this.employeeList = newEmployeeList;
        notifyDataSetChanged();
    }
    private String formatRegistrationDate(String dateString) {
        try {
            // Assuming the date is stored as "dd/MM/yyyy" format
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date date = inputFormat.parse(dateString);
            return outputFormat.format(date);
        } catch (Exception e) {
            // Return original string if parsing fails
            return dateString;
        }
    }
    private void loadEmployeeAvatar(ImageView avatarView, Employee employee) {
        String faceBase64 = employee.getFaceBase64();
        if (faceBase64 != null && !faceBase64.isEmpty()){
            byte[] decodedBytes = Base64.decode(faceBase64, Base64.NO_WRAP);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            avatarView.setImageBitmap(bitmap);
        }
    }

    public static class EmployeeViewHolder extends RecyclerView.ViewHolder {
        TextView employeeName, employeeId, registrationDate;
        ImageView employeeAvatar;

        public EmployeeViewHolder(@NonNull View itemView) {
            super(itemView);
            employeeName = itemView.findViewById(R.id.tvEmployeeName);
            employeeId = itemView.findViewById(R.id.tvEmployeeId);
            registrationDate = itemView.findViewById(R.id.tvRegistrationDate);
            employeeAvatar = itemView.findViewById(R.id.ivEmployeeAvatar);
        }
    }
}