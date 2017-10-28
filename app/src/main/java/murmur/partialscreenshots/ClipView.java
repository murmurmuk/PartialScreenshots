package murmur.partialscreenshots;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;



public class ClipView extends View {
    private float x1, y1, x2, y2;
    private final Paint mPaint;
    public ClipView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(Color.BLACK);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2);
    }

    public void updateRegion(float inX1, float inY1, float inX2, float inY2){
        x1 = inX1;
        x2 = inX2;
        y1 = inY1;
        y2 = inY2;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(x1, y1, x2, y2, mPaint);
    }

}

