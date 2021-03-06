/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;

import org.floens.chan.Chan;
import org.floens.chan.R;

import static org.floens.chan.utils.AndroidUtils.getString;
import static org.floens.chan.utils.AndroidUtils.sp;

public class ThumbnailView extends View implements ImageLoader.ImageListener {
    private ImageLoader.ImageContainer container;
    private int fadeTime = 200;

    private boolean circular = false;

    private boolean calculate;
    private Bitmap bitmap;
    private RectF bitmapRect = new RectF();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private RectF drawRect = new RectF();
    private RectF outputRect = new RectF();

    private Matrix matrix = new Matrix();
    BitmapShader bitmapShader;
    private Paint roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private boolean error = false;
    private String errorText;
    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ThumbnailView(Context context) {
        super(context);
        init();
    }

    public ThumbnailView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThumbnailView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        textPaint.setColor(0xff000000);
        textPaint.setTextSize(sp(14));
    }

    public void setUrl(String url, int width, int height) {
        if (container != null && container.getRequestUrl().equals(url)) {
            return;
        }

        if (container != null) {
            container.cancelRequest();
            container = null;
            error = false;
            setImageBitmap(null);
        }

        if (!TextUtils.isEmpty(url)) {
            container = Chan.getVolleyImageLoader().get(url, this, width, height);
        }
    }

    public void setCircular(boolean circular) {
        this.circular = circular;
    }

    public void setFadeTime(int fadeTime) {
        this.fadeTime = fadeTime;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    @Override
    public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
        if (response.getBitmap() != null) {
            setImageBitmap(response.getBitmap());
            onImageSet(isImmediate);
        }
    }

    @Override
    public void onErrorResponse(VolleyError e) {
        error = true;

        if ((e instanceof NoConnectionError) || (e instanceof NetworkError)) {
            errorText = getString(R.string.thumbnail_load_failed_network);
        } else {
            errorText = getString(R.string.thumbnail_load_failed_server);
        }

        onImageSet(false);
    }

    @Override
    protected boolean onSetAlpha(int alpha) {
        if (error) {
            textPaint.setAlpha(alpha);
        } else if (circular) {
            roundPaint.setAlpha(alpha);
        } else {
            paint.setAlpha(alpha);
        }
        invalidate();

        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getAlpha() == 0f) {
            return;
        }

        if (error) {
            canvas.save();

            Rect bounds = new Rect();
            textPaint.getTextBounds(errorText, 0, errorText.length(), bounds);
            float x = (getWidth() - getPaddingLeft() + getPaddingRight() - bounds.width()) / 2;
            float y = getHeight() - getPaddingTop() - getPaddingBottom() - (getHeight() - bounds.height()) / 2;
            canvas.drawText(errorText, getPaddingLeft() + x, getPaddingTop() + y, textPaint);

            canvas.restore();
        } else {
            if (bitmap == null) {
                return;
            }

            int width = getWidth() - getPaddingLeft() - getPaddingRight();
            int height = getHeight() - getPaddingTop() - getPaddingBottom();

            if (calculate) {
                calculate = false;
                bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
                float scale = Math.max(
                        (float) width / (float) bitmap.getWidth(),
                        (float) height / (float) bitmap.getHeight());
                float scaledX = bitmap.getWidth() * scale;
                float scaledY = bitmap.getHeight() * scale;
                float offsetX = (scaledX - width) * 0.5f;
                float offsetY = (scaledY - height) * 0.5f;

                drawRect.set(-offsetX, -offsetY, scaledX - offsetX, scaledY - offsetY);
                drawRect.offset(getPaddingLeft(), getPaddingTop());

                outputRect.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());

                matrix.setRectToRect(bitmapRect, drawRect, Matrix.ScaleToFit.FILL);

                if (circular) {
                    bitmapShader.setLocalMatrix(matrix);
                    roundPaint.setShader(bitmapShader);
                }
            }

            canvas.save();
            canvas.clipRect(outputRect);
            if (circular) {
                canvas.drawRoundRect(outputRect, width / 2, height / 2, roundPaint);
            } else {
                canvas.drawBitmap(bitmap, matrix, paint);
            }
            canvas.restore();
        }
    }

    private void onImageSet(boolean isImmediate) {
        clearAnimation();
        if (fadeTime > 0 && !isImmediate) {
            setAlpha(0f);
            animate().alpha(1f).setDuration(fadeTime);
        } else {
            setAlpha(1f);
        }
    }

    private void setImageBitmap(Bitmap bitmap) {
        bitmapShader = null;
        roundPaint.setShader(null);

        this.bitmap = bitmap;
        if (bitmap != null) {
            calculate = true;
            if (circular) {
                bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            }
        }
        invalidate();
    }
}
