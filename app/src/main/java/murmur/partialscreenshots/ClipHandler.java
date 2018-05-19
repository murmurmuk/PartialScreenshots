package murmur.partialscreenshots;

import android.view.MotionEvent;
import android.view.View;

public class ClipHandler {
    private final BubbleService service;
    private float x1;
    private float y1;
    private float rawX1, rawY1, rawX2, rawY2;
    private int[] clipBox = new int[4];//left, right, width, height

    ClipHandler(BubbleService service) {
        this.service = service;
    }

    private void updateClipBox(float l, float t, float r, float b) {
        clipBox[0] = (int) Math.ceil(l);
        clipBox[1] = (int) Math.ceil(t);
        clipBox[2] = (int) Math.ceil(r - l);
        clipBox[3] = (int) Math.ceil(b - t);
    }

    private void updateCustomView(ClipView view, float x1, float y1, float x2, float y2) {
        if (x1 > x2 && y1 > y2) {
            view.updateRegion(x2, y2, x1, y1);
            updateClipBox(rawX2, rawY2, rawX1, rawY1);
        } else if(y1 > y2) {
            view.updateRegion(x1, y2, x2, y1);
            updateClipBox(rawX1, rawY2, rawX2, rawY1);
        } else if(x1 > x2) {
            view.updateRegion(x2, y1, x1, y2);
            updateClipBox(rawX2, rawY1, rawX1, rawY2);
        } else {
            view.updateRegion(x1, y1, x2, y2);
            updateClipBox(rawX1, rawY1, rawX2, rawY2);
        }
    }

    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                x1  = event.getX();
                rawX1 = rawX2 = event.getRawX();
                y1  = event.getY();
                rawY1 = rawY2 = event.getRawY();
                break;
            case MotionEvent.ACTION_MOVE:
                float x2, y2;
                x2 = event.getX();
                rawX2 = event.getRawX();
                y2 = event.getY();
                rawY2 = event.getRawY();
                updateCustomView((ClipView) view, x1, y1, x2, y2);
                break;
            case MotionEvent.ACTION_UP:
                view.performClick();
                service.finishClipMode(clipBox);
                break;
        }
        return true;
    }
}
