package com.kamron.pogoiv;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.Image;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.DisplayMetrics;

import java.nio.ByteBuffer;

/**
 * Created by v on 18.08.2016.
 */
public class ScreenScanner {

    public static final int UNKNOWN_SCREEN = -1;
    public static final int POKEMON_SCREEN = 1;
    public static final int GYM_SCREEN = 2;

    private int areaX1;
    private int areaY1;
    private int areaX2;
    private int areaY2;

    public ScreenScanner(DisplayMetrics displayMetrics) {
        areaX1 = Math.round(displayMetrics.widthPixels / 24);  // these values used to get "white" left of "power up"
        areaY1 = (int) Math.round(displayMetrics.heightPixels / 1.24271845);
        areaX2 = (int) Math.round(displayMetrics.widthPixels / 1.15942029);  // these values used to get greenish color in transfer button
        areaY2 = (int) Math.round(displayMetrics.heightPixels / 1.11062907);
    }

    /**
     *
     * @return
     */
    public int scan(Image image, int widthPixels) {
        if (image == null)
            return UNKNOWN_SCREEN;

        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * widthPixels;
        int resultScreen = UNKNOWN_SCREEN;

        // create bitmap
        image.close();
        Bitmap bmp = getBitmap(buffer, pixelStride, rowPadding);
        if (bmp.getPixel(areaX1, areaY1) == Color.rgb(250, 250, 250) && bmp.getPixel(areaX2, areaY2) == Color.rgb(28, 135, 150)) {
            resultScreen = POKEMON_SCREEN;
        } else {
            resultScreen = UNKNOWN_SCREEN;
        }
        bmp.recycle();

        return resultScreen;
    }

    @NonNull
    public Bitmap getBitmap(ByteBuffer buffer, int pixelStride, int rowPadding) {
        Bitmap bmp = Bitmap.createBitmap(rawDisplayMetrics.widthPixels + rowPadding / pixelStride, displayMetrics.heightPixels, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(buffer);
        return bmp;
    }

}
