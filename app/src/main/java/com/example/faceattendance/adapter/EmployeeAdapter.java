package com.example.faceattendance.adapter;

import com.example.faceattendance.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.faceattendance.model.Employee;

import java.util.List;

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
        holder.registrationDate.setText("Ngày đăng kí: " + employee.getRegistrationDate());

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

    public static class EmployeeViewHolder extends RecyclerView.ViewHolder {
        TextView employeeName, employeeId, registrationDate;

        public EmployeeViewHolder(@NonNull View itemView) {
            super(itemView);
            employeeName = itemView.findViewById(R.id.tvEmployeeName);
            employeeId = itemView.findViewById(R.id.tvEmployeeId);
            registrationDate = itemView.findViewById(R.id.tvRegistrationDate);
        }
    }
}