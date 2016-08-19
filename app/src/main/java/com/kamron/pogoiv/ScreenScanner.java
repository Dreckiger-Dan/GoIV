package com.kamron.pogoiv;

import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Created by v on 18.08.2016.
 */
public class ScreenScanner extends Service {
    private static final String TAG = "ScreenScanner";

    public static final int UNKNOWN_SCREEN = -1;
    public static final int POKEMON_SCREEN = 1;
    public static final int GYM_SCREEN = 2;

    private int pokemonAreaX1;
    private int pokemonAreaY1;
    private int pokemonAreaX2;
    private int pokemonAreaY2;
    private int gymAreaX1;
    private int gymAreaY1;
    private int gymAreaX2;
    private int gymAreaY2;

    private int trainerLevel;
    private String pokemonName;
    private int pokemonCP;
    private int pokemonHP;

    private MediaProjection mProjection;
    private ImageReader mImageReader;

    private TessBaseAPI tesseract;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();


        pokemonAreaX1 = Math.round(displayMetrics.widthPixels / 24);  // these values used to get "white" left of "power up"
        pokemonAreaY1 = (int) Math.round(displayMetrics.heightPixels / 1.24271845);
        pokemonAreaX2 = (int) Math.round(displayMetrics.widthPixels / 1.15942029);  // these values used to get greenish color in transfer button
        pokemonAreaY2 = (int) Math.round(displayMetrics.heightPixels / 1.11062907);

        if (!new File(getExternalFilesDir(null) + "/tessdata/eng.traineddata").exists()) {
            copyAssetFolder(getAssets(), "tessdata", getExternalFilesDir(null) + "/tessdata");
        }

        tesseract = new TessBaseAPI();
        tesseract.init(getExternalFilesDir(null) + "", "eng");
        tesseract.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789/♀♂");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tesseract.stop();
        tesseract.end();

        if (mProjection != null) {
            mProjection.stop();
            mProjection = null;
            mImageReader = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("trainerLevel")) {
            trainerLevel = intent.getIntExtra("trainerLevel", 1);
        }

        return START_STICKY;
    }

    /**
     * scan
     * Used to find out which screen is currently open.
     *
     * @return {UNKNOWN_SCREEN}
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
        if (bmp.getPixel(pokemonAreaX1, pokemonAreaY1) == Color.rgb(250, 250, 250) && bmp.getPixel(pokemonAreaX2, pokemonAreaY2) == Color.rgb(28, 135, 150)) {
            resultScreen = POKEMON_SCREEN;
        } else if (bmp.getPixel(gymAreaX1, gymAreaY1) == Color.rgb(250, 250, 250) && bmp.getPixel(gymAreaX2, gymAreaY2) == Color.rgb(28, 135, 150)) {
            resultScreen = GYM_SCREEN;
        }
        bmp.recycle();

        return resultScreen;
    }

    /**
     * scanPokemon
     * Performs OCR on an image of a pokemon and sends the pulled info to PokeFly to display.
     *
     * @param pokemonImage The image of the pokemon
     */
    public void scanPokemon(Bitmap pokemonImage) {
        double estimatedPokemonLevel = trainerLevel + 1.5;

        for (double estPokemonLevel = estimatedPokemonLevel; estPokemonLevel >= 1.0; estPokemonLevel -= 0.5) {
            //double angleInDegrees = (Data.CpM[(int) (estPokemonLevel * 2 - 2)] - 0.094) * 202.037116 / Data.CpM[trainerLevel * 2 - 2];
            //if (angleInDegrees > 1.0 && trainerLevel < 30) {
            //  angleInDegrees -= 0.5;
            //} else if (trainerLevel >= 30) {
            //   angleInDegrees += 0.5;
            //}

            //double angleInRadians = (angleInDegrees + 180) * Math.PI / 180.0;
            //int x = (int) (arcCenter + (radius * Math.cos(angleInRadians)));
            //int y = (int) (arcInitialY + (radius * Math.sin(angleInRadians)));
            //System.out.println("X: " + x + ", Y: " + y);
            int index = Data.convertLevelToIndex(estPokemonLevel);
            int x = Data.arcX[index];
            int y = Data.arcY[index];
            if (pokemonImage.getPixel(x, y) == Color.rgb(255, 255, 255)) {
                estimatedPokemonLevel = estPokemonLevel;
                break;
            }
        }

        Bitmap name = Bitmap.createBitmap(pokemonImage, displayMetrics.widthPixels / 4, (int) Math.round(displayMetrics.heightPixels / 2.22608696), (int) Math.round(displayMetrics.widthPixels / 2.057), (int) Math.round(displayMetrics.heightPixels / 18.2857143));
        name = replaceColors(name, 68, 105, 108, Color.WHITE, 200);
        tesseract.setImage(name);
        //System.out.println(tesseract.getUTF8Text());
        pokemonName = tesseract.getUTF8Text().replace(" ", "").replace("1", "l").replace("0", "o").replace("Sparky", getString(R.string.pokemon133)).replace("Rainer", getString(R.string.pokemon133)).replace("Pyro", getString(R.string.pokemon133));
        //SaveImage(name, "name");
        Bitmap hp = Bitmap.createBitmap(pokemonImage, (int) Math.round(displayMetrics.widthPixels / 2.8), (int) Math.round(displayMetrics.heightPixels / 1.8962963), (int) Math.round(displayMetrics.widthPixels / 3.5), (int) Math.round(displayMetrics.heightPixels / 34.13333333));
        hp = replaceColors(hp, 55, 66, 61, Color.WHITE, 200);
        tesseract.setImage(hp);
        //System.out.println(tesseract.getUTF8Text());
        pokemonHP = Integer.parseInt(tesseract.getUTF8Text().split("/")[1].replace("Z", "2").replace("O", "0").replace("l", "1").replaceAll("[^0-9]", ""));
        //SaveImage(hp, "hp");
        Bitmap cp = Bitmap.createBitmap(pokemonImage, (int) Math.round(displayMetrics.widthPixels / 3.0), (int) Math.round(displayMetrics.heightPixels / 15.5151515), (int) Math.round(displayMetrics.widthPixels / 3.84), (int) Math.round(displayMetrics.heightPixels / 21.333333333));
        cp = replaceColors(cp, 255, 255, 255, Color.BLACK, 1);
        tesseract.setImage(cp);
        String cpText = tesseract.getUTF8Text().replace("O", "0").replace("l", "1").replace("S", "3").replaceAll("[^0-9]", "");
        if (cpText.length() > 4) {
            cpText = cpText.substring(cpText.length() - 4, cpText.length() - 1);
        }
        //System.out.println(cpText);
        pokemonCP = Integer.parseInt(cpText);
        if (pokemonCP > 4500) {
            cpText = cpText.substring(1);
            pokemonCP = Integer.parseInt(cpText);
        }
        //SaveImage(cp, "cp");
        //System.out.println("Name: " + pokemonName);
        //System.out.println("HP: " + pokemonHP);
        //System.out.println("CP: " + pokemonCP);
        name.recycle();
        cp.recycle();
        hp.recycle();

        Intent info = new Intent("pokemon-info");
        info.putExtra("name", pokemonName);
        info.putExtra("hp", pokemonHP);
        info.putExtra("cp", pokemonCP);
        info.putExtra("level", estimatedPokemonLevel);
        LocalBroadcastManager.getInstance(ScreenScanner.this).sendBroadcast(info);
    }

    /**
     * takeScreenshot
     * Called by intent from pokefly, captures the screen and runs it through scanPokemon
     */
    public void takeScreenshot() {
        Image image = null;
        try {
            image = mImageReader.acquireLatestImage();
        } catch (Exception e) {
            Crashlytics.log("Error thrown in takeScreenshot() - acquireLatestImage()");
            Crashlytics.logException(e);
            Log.e(TAG, "Error while Scanning!", e);
            Toast.makeText(ScreenScanner.this, "Error Scanning! Please try again later!", Toast.LENGTH_SHORT).show();
        }

        if (image != null) {
            final Image.Plane[] planes = image.getPlanes();
            final ByteBuffer buffer = planes[0].getBuffer();
            int offset = 0;
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * displayMetrics.widthPixels;
            // create bitmap
            try {
                image.close();
                Bitmap bmp = getBitmap(buffer, pixelStride, rowPadding);
                scanPokemon(bmp);
                //SaveImage(bmp,"Search");
            } catch (Exception e) {
                Crashlytics.log("Exception thrown in takeScreenshot() - when creating bitmap");
                Crashlytics.logException(e);
                image.close();
            }


        }
    }

    @NonNull
    public Bitmap getBitmap(ByteBuffer buffer, int pixelStride, int rowPadding) {
        Bitmap bmp = Bitmap.createBitmap(rawDisplayMetrics.widthPixels + rowPadding / pixelStride, displayMetrics.heightPixels, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(buffer);
        return bmp;
    }

    /**
     * replaceColors
     * Replaces colors in a bitmap that are not the same as a specific color.
     *
     * @param myBitmap     The bitmap to check the colors for.
     * @param keepCr       The red color to keep
     * @param keepCg       The green color to keep
     * @param keepCb       The blue color to keep
     * @param replaceColor The color to replace mismatched colors with
     * @param similarity   The similarity buffer
     * @return Bitmap with replaced colors
     */
    private Bitmap replaceColors(Bitmap myBitmap, int keepCr, int keepCg, int keepCb, int replaceColor, int similarity) {
        int[] allpixels = new int[myBitmap.getHeight() * myBitmap.getWidth()];
        myBitmap.getPixels(allpixels, 0, myBitmap.getWidth(), 0, 0, myBitmap.getWidth(), myBitmap.getHeight());

        for (int i = 0; i < allpixels.length; i++) {
            int r = Color.red(allpixels[i]);
            int g = Color.green(allpixels[i]);
            int b = Color.blue(allpixels[i]);
            double d = Math.sqrt(Math.pow(keepCr - r, 2) + Math.pow(keepCg - g, 2) + Math.pow(keepCb - b, 2));
            if (d > similarity) {
                allpixels[i] = replaceColor;
            }
        }

        myBitmap.setPixels(allpixels, 0, myBitmap.getWidth(), 0, 0, myBitmap.getWidth(), myBitmap.getHeight());
        return myBitmap;
    }

    private static boolean copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath) {

        String[] files = new String[0];

        try {
            files = assetManager.list(fromAssetPath);
        } catch (IOException e) {
            Crashlytics.log("Exception thrown in copyAssetFolder()");
            Crashlytics.logException(e);
            Log.e(TAG, "Error while loading filenames.", e);
        }
        new File(toPath).mkdirs();
        boolean res = true;
        for (String file : files)
            if (file.contains(".")) {
                res &= copyAsset(assetManager, fromAssetPath + "/" + file, toPath + "/" + file);
            } else {
                res &= copyAssetFolder(assetManager, fromAssetPath + "/" + file, toPath + "/" + file);
            }
        return res;

    }

    private static boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) {
        try {
            InputStream in = assetManager.open(fromAssetPath);
            new File(toPath).createNewFile();
            OutputStream out = new FileOutputStream(toPath);
            copyFile(in, out);
            in.close();
            out.flush();
            out.close();
            return true;
        } catch (IOException e) {
            Crashlytics.log("Exception thrown in copyAsset()");
            Crashlytics.logException(e);
            Log.e(TAG, "Error while copying assets.", e);
            return false;
        }
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
