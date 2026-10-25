package com.pnntrn.cctv;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import java.util.List;

/** Integer cell edges keep the video grid borderless, including asymmetric layouts. */
final class CameraGridView extends ViewGroup {
    private final int size;
    CameraGridView(Context context,int size) { super(context); this.size=CameraRules.checkedGrid(size); }
    @Override protected LayoutParams generateDefaultLayoutParams() { return new MarginLayoutParams(-1,-1); }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w,h);
        List<GridLayoutSpec.Cell> cells=GridLayoutSpec.cells(size);
        for(int i=0;i<getChildCount();i++) {
            GridLayoutSpec.Cell c=cells.get(i); int cols=GridLayoutSpec.columns(size),rows=GridLayoutSpec.rows(size);
            int cw=(c.x+c.width)*w/cols-c.x*w/cols,ch=(c.y+c.height)*h/rows-c.y*h/rows;
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(ch,MeasureSpec.EXACTLY));
        }
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        int w=right-left,h=bottom-top,cols=GridLayoutSpec.columns(size),rows=GridLayoutSpec.rows(size);
        List<GridLayoutSpec.Cell> cells=GridLayoutSpec.cells(size);
        for(int i=0;i<getChildCount();i++) { GridLayoutSpec.Cell c=cells.get(i);
            getChildAt(i).layout(c.x*w/cols,c.y*h/rows,(c.x+c.width)*w/cols,(c.y+c.height)*h/rows); }
    }
}
