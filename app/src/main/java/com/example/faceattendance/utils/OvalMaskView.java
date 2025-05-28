package com.example.faceattendance.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class OvalMaskView extends View {
    private Paint maskPaint;
    private Paint clearPaint;
    private Path ovalPath;
    private RectF ovalRect;

    // Kích thước mặc định cho oval
    private float ovalWidth = 300f;
    private float ovalHeight = 400f;

    public OvalMaskView(Context context) {
        super(context);
        init();
    }

    public OvalMaskView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OvalMaskView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Paint cho phần nền xám đậm
        maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setColor(Color.argb(240, 120, 120, 120)); // Xám đậm với độ đục cao
        maskPaint.setStyle(Paint.Style.FILL);

        // Paint để tạo lỗ trong suốt
        clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        ovalPath = new Path();
        ovalRect = new RectF();

        // Convert dp to pixels
        float density = getContext().getResources().getDisplayMetrics().density;
        ovalWidth = ovalWidth * density;
        ovalHeight = ovalHeight * density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Vẽ nền xám che toàn bộ màn hình
        canvas.drawRect(0, 0, width, height, maskPaint);

        // Tính toán vị trí trung tâm cho oval
        float centerX = width / 2f;
        float centerY = height / 2f;

        // Thiết lập kích thước oval
        ovalRect.set(
                centerX - ovalWidth / 2f,
                centerY - ovalHeight / 2f,
                centerX + ovalWidth / 2f,
                centerY + ovalHeight / 2f
        );

        // Tạo đường oval và vẽ lỗ trong suốt
        ovalPath.reset();
        ovalPath.addOval(ovalRect, Path.Direction.CW);
        canvas.drawPath(ovalPath, clearPaint);
    }

    // Phương thức để cập nhật kích thước oval nếu cần
    public void setOvalSize(float width, float height) {
        float density = getContext().getResources().getDisplayMetrics().density;
        this.ovalWidth = width * density;
        this.ovalHeight = height * density;
        invalidate(); // Vẽ lại view
    }

    // Phương thức để điều chỉnh màu và độ tối của mask
    public void setMaskColor(int alpha, int red, int green, int blue) {
        maskPaint.setColor(Color.argb(alpha, red, green, blue));
        invalidate();
    }
}