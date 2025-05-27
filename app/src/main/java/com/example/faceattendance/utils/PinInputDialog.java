package com.example.faceattendance.utils;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.TransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.faceattendance.R;

public class PinInputDialog {

    public interface PinInputListener {
        void onPinEntered(String pin);
        void onPinCancelled();
    }

    private Context context;
    private String title;
    private int pinLength;
    private PinInputListener listener;
    private AlertDialog dialog;

    // Constructor mặc định với 6 số
    public PinInputDialog(Context context) {
        this(context, "Nhập mã PIN", 6);
    }

    // Constructor với title tùy chỉnh
    public PinInputDialog(Context context, String title) {
        this(context, title, 6);
    }

    // Constructor đầy đủ
    public PinInputDialog(Context context, String title, int pinLength) {
        this.context = context;
        this.title = title;
        this.pinLength = pinLength;
    }

    public PinInputDialog setListener(PinInputListener listener) {
        this.listener = listener;
        return this;
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);

        // Layout chứa các ô nhập PIN
        LinearLayout layout = createPinLayout();
        EditText[] digits = new EditText[pinLength];

        // Tạo các ô nhập
        for (int i = 0; i < pinLength; i++) {
            EditText digit = createDigitEditText(i, digits);
            digits[i] = digit;
            layout.addView(digit);
        }

        builder.setView(layout);

        builder.setPositiveButton("Xác nhận", (dialogInterface, which) -> {
            String enteredPin = getPinFromDigits(digits);
            if (enteredPin.length() < pinLength) {
                // Hiển thị thông báo lỗi và không đóng dialog
                android.widget.Toast.makeText(context,
                        "Vui lòng nhập đủ " + pinLength + " số",
                        android.widget.Toast.LENGTH_SHORT).show();
                // Không gọi listener và không đóng dialog
                return;
            }
            if (listener != null) {
                listener.onPinEntered(enteredPin);
            }
        });

        builder.setNegativeButton("Hủy", (dialogInterface, which) -> {
            if (listener != null) {
                listener.onPinCancelled();
            }
        });

        dialog = builder.create();

        // Set flag để hiển thị bàn phím khi dialog show
        dialog.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        );

        dialog.show();

        // Override positive button để có thể kiểm soát việc đóng dialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String enteredPin = getPinFromDigits(digits);
            if (enteredPin.length() < pinLength) {
                // Hiển thị thông báo lỗi và không đóng dialog
                android.widget.Toast.makeText(context,
                        "Vui lòng nhập đủ " + pinLength + " số",
                        android.widget.Toast.LENGTH_SHORT).show();

                // Focus vào ô đầu tiên còn trống
                for (int i = 0; i < pinLength; i++) {
                    if (digits[i].getText().toString().isEmpty()) {
                        digits[i].requestFocus();
                        break;
                    }
                }
                return; // Không đóng dialog
            }

            // PIN hợp lệ, gọi listener và đóng dialog
            if (listener != null) {
                listener.onPinEntered(enteredPin);
            }
            dialog.dismiss();
        });

        // Focus vào ô đầu tiên và hiển thị bàn phím sau một chút delay
        if (digits.length > 0) {
            digits[0].requestFocus();
            // Sử dụng Handler để delay một chút, đảm bảo dialog đã render xong
            digits[0].postDelayed(() -> {
                digits[0].requestFocus();
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(digits[0], InputMethodManager.SHOW_IMPLICIT);
                }

                // Disable nút Xác nhận ban đầu vì chưa nhập gì
                updateConfirmButtonState(digits);
            }, 100);
        }
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    private LinearLayout createPinLayout() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(32, 32, 32, 32);
        layout.setGravity(Gravity.CENTER);
        return layout;
    }

    private EditText createDigitEditText(int index, EditText[] digits) {
        EditText digit = new EditText(context);

        // Layout params
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(120, 120);
        params.setMargins(12, 0, 12, 0);
        digit.setLayoutParams(params);

        // Input settings
        digit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        digit.setTransformationMethod(new AsteriskTransformationMethod());
        digit.setGravity(Gravity.CENTER);
        digit.setTextSize(18);

        // Filters
        digit.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(1),
                // Filter chỉ cho phép nhập số từ 0-9
                (source, start, end, dest, dstart, dend) -> {
                    if (source != null && source.length() > 0) {
                        char c = source.charAt(0);
                        if (!Character.isDigit(c)) {
                            return "";
                        }
                    }
                    return null;
                }
        });

        // Background
        digit.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_digit_box));

        // Text change listener
        digit.addTextChangedListener(new android.text.TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 1 && index < pinLength - 1) {
                    digits[index + 1].requestFocus();
                } else if (s.length() == 0 && index > 0) {
                    digits[index - 1].requestFocus();
                }

                // Kiểm tra nếu đã nhập đủ PIN thì enable nút Xác nhận
                updateConfirmButtonState(digits);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void afterTextChanged(Editable s) {}
        });

        // Key listener for backspace
        digit.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_DEL &&
                    event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                if (digit.getText().toString().isEmpty() && index > 0) {
                    digits[index - 1].requestFocus();
                    digits[index - 1].setText("");
                }
            }
            return false;
        });

        return digit;
    }

    private String getPinFromDigits(EditText[] digits) {
        StringBuilder pin = new StringBuilder();
        for (EditText digit : digits) {
            pin.append(digit.getText().toString());
        }
        return pin.toString();
    }

    // Phương thức để cập nhật trạng thái nút Xác nhận
    private void updateConfirmButtonState(EditText[] digits) {
        if (dialog != null) {
            String currentPin = getPinFromDigits(digits);
            boolean isComplete = currentPin.length() == pinLength;

            // Enable/disable nút Xác nhận dựa vào việc đã nhập đủ PIN chưa
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(isComplete);

            // Đặt màu giống với nút Hủy khi enable/disable
            int cancelButtonColor = dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getCurrentTextColor();
            if (isComplete) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(cancelButtonColor);
            } else {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                        ContextCompat.getColor(context, android.R.color.darker_gray));
            }
        }
    }

    // Custom TransformationMethod để hiển thị dấu * cho số
    private static class AsteriskTransformationMethod implements TransformationMethod {
        @Override
        public CharSequence getTransformation(CharSequence source, View view) {
            return new AsteriskCharSequence(source);
        }

        @Override
        public void onFocusChanged(View view, CharSequence sourceText, boolean focused,
                                   int direction, android.graphics.Rect previouslyFocusedRect) {
            // Không cần xử lý gì
        }

        private static class AsteriskCharSequence implements CharSequence {
            private CharSequence mSource;

            public AsteriskCharSequence(CharSequence source) {
                mSource = source;
            }

            @Override
            public char charAt(int index) {
                return '*';
            }

            @Override
            public int length() {
                return mSource.length();
            }

            @Override
            public CharSequence subSequence(int start, int end) {
                char[] asterisks = new char[end - start];
                for (int i = 0; i < asterisks.length; i++) {
                    asterisks[i] = '*';
                }
                return new String(asterisks);
            }
        }
    }
}