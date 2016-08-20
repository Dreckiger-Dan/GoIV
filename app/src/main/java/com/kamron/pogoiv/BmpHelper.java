package com.kamron.pogoiv;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/**
 * Created by v on 20.08.2016.
 */
public class BmpHelper {

    private static final String TAG = "BmpHelper";
    private TessBaseAPI tesseract;
    public int candyOrder = 0;

    public class ScanResult {
        public double estimatedPokemonLevel;
        public String pokemonName;
        public String candyName;
        public int pokemonHP;
        public int pokemonCP;
    }

    public BmpHelper(String datapath) {
        tesseract = new TessBaseAPI();
        tesseract.init(datapath, "eng");
        tesseract.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789/♀♂");
    }

    /**
     * scanPokemon
     * Performs OCR on an image of a pokemon and sends the pulled info to PokeFly to display.
     *
     * @param pokemonImage The image of the pokemon
     * @param filePath The screenshot path if it is a file, used to delete once checked
     */
    public ScanResult scanPokemon(Bitmap pokemonImage, String filePath, int trainerLevel, int widthPixels, int heightPixels) {
        ScanResult result = new ScanResult();
        result.estimatedPokemonLevel = trainerLevel + 1.5;

        for (double estPokemonLevel = result.estimatedPokemonLevel; estPokemonLevel >= 1.0; estPokemonLevel -= 0.5) {
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
                result.estimatedPokemonLevel = estPokemonLevel;
                break;
            }
        }

        Bitmap name = Bitmap.createBitmap(pokemonImage, widthPixels / 4, (int) Math.round(heightPixels / 2.22608696), (int) Math.round(widthPixels / 2.057), (int) Math.round(heightPixels / 18.2857143));
        name = replaceColors(name, 68, 105, 108, Color.WHITE, 200);
        tesseract.setImage(name);
        //System.out.println(tesseract.getUTF8Text());
        result.pokemonName = tesseract.getUTF8Text().replace(" ", "").replace("1", "l").replace("0", "o");


        //SaveImage(name, "name");
        // TODO : Check rectangle and color
        Bitmap candy = Bitmap.createBitmap(pokemonImage, widthPixels / 2, (int) Math.round(heightPixels / 1.3724285), (int) Math.round(widthPixels / 2.057), (int) Math.round(heightPixels / 38.4));
        candy = replaceColors(candy, 68, 105, 108, Color.WHITE, 200);
        tesseract.setImage(candy);
        //System.out.println(tesseract.getUTF8Text());
        //SaveImage(candy, "candy");
        try {
            result.candyName = tesseract.getUTF8Text().trim().replace("-", " ").split(" ")[candyOrder].replace(" ", "").replace("1", "l").replace("0", "o");
            result.candyName = new StringBuilder().append(result.candyName.substring(0, 1)).append(result.candyName.substring(1).toLowerCase()).toString();
        } catch (StringIndexOutOfBoundsException e) {
            result.candyName = result.pokemonName; //Default for not finding candy name
        }
        Bitmap hp = Bitmap.createBitmap(pokemonImage, (int) Math.round(widthPixels / 2.8), (int) Math.round(heightPixels / 1.8962963), (int) Math.round(widthPixels / 3.5), (int) Math.round(heightPixels / 34.13333333));
        hp = replaceColors(hp, 55, 66, 61, Color.WHITE, 200);
        tesseract.setImage(hp);
        //System.out.println(tesseract.getUTF8Text());
        String pokemonHPStr = tesseract.getUTF8Text();

        //Check if valid pokemon TODO find a better method of determining whether or not this is a pokemon image
        if(pokemonHPStr.contains("/")) {
            try {
                result.pokemonHP = Integer.parseInt(pokemonHPStr.split("/")[1].replace("Z", "2").replace("O", "0").replace("l", "1").replaceAll("[^0-9]", ""));
            } catch (java.lang.NumberFormatException e) {
                result.pokemonHP = 10;
            }

            //SaveImage(hp, "hp");
            Bitmap cp = Bitmap.createBitmap(pokemonImage, (int) Math.round(widthPixels / 3.0), (int) Math.round(heightPixels / 15.5151515), (int) Math.round(widthPixels / 3.84), (int) Math.round(heightPixels / 21.333333333));
            cp = replaceColors(cp, 255, 255, 255, Color.BLACK, 30);
            tesseract.setImage(cp);
            //String cpText = tesseract.getUTF8Text().replace("O", "0").replace("l", "1").replace("S", "3").replaceAll("[^0-9]", "");
            String cpText = tesseract.getUTF8Text().replace("O", "0").replace("l", "1");
            cpText = cpText.substring(2);
            if (cpText.length() > 4) {
                cpText = cpText.substring(cpText.length() - 4, cpText.length() - 1);
            }
            //System.out.println(cpText);
            try {
                result.pokemonCP = Integer.parseInt(cpText);
            } catch (java.lang.NumberFormatException e) {
                result.pokemonCP = 10;
            }

            if (result.pokemonCP > 4500) {
                cpText = cpText.substring(1);
                result.pokemonCP = Integer.parseInt(cpText);
            }
            //SaveImage(cp, "cp");
            //System.out.println("Name: " + pokemonName);
            //System.out.println("HP: " + pokemonHP);
            //System.out.println("CP: " + pokemonCP);
            name.recycle();
            candy.recycle();
            cp.recycle();
            hp.recycle();

            return result;

        } else {
            return null;
        }

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

    /**
     * Dont missgender the poor nidorans.
     *
     * Takes a subportion of the screen, and averages the color to check the average values and compares to known male / female average
     * @param pokemonImage The screenshot of the entire application
     * @return True if the nidoran is female
     */
    public boolean isNidoranFemale(Bitmap pokemonImage, int widthPixels, int heightPixels) {
        int redSum = 0;
        int greenSum = 0;
        int blueSum = 0;

        int femaleGreenLimit = 175; //if average green is over 175, its probably female
        int femaleBlueLimit = 130; //if average blue is over 130, its probably female

        boolean isFemale = true;

        Bitmap pokemon =
                Bitmap.createBitmap(
                        pokemonImage,
                        widthPixels / 3,
                        Math.round(heightPixels / 4),
                        Math.round(widthPixels / 3),
                        Math.round(heightPixels / 5));

        int[] pixelArray = new int[pokemon.getHeight() * pokemon.getWidth()];
        pokemon.getPixels(pixelArray, 0, pokemon.getWidth(), 0, 0, pokemon.getWidth(), pokemon.getHeight());

        // a loop that sums the color values of all the pixels in the image of the nidoran
        for (int pixel : pixelArray) {
            redSum += Color.red(pixel);
            blueSum += Color.green(pixel);
            greenSum += Color.blue(pixel);
        }

        int redAverage = redSum/pixelArray.length;
        int greenAverage = greenSum/pixelArray.length;
        int blueAverage = blueSum/pixelArray.length;

        //Average male nidoran has RGB value ~~ 136,165,117
        //Average female nidoran has RGB value~ 135,190,140

        if (greenAverage < femaleGreenLimit && blueAverage <femaleBlueLimit){
            isFemale = false; //if neither average is above the female limit, then it's male.
        }

        return isFemale;
    }

    @NonNull
    public Bitmap getBitmap(ByteBuffer buffer, int pixelStride, int rowPadding, int rawWidthPixels, int heightPixels) { // TODO dan
        Bitmap bmp = Bitmap.createBitmap(rawWidthPixels + rowPadding / pixelStride, heightPixels, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(buffer);
        return bmp;
    }

    /**
     * saveImage
     * Used to save the image the screen capture is captuing, used for debugging.
     *
     * @param finalBitmap The bitmap to save
     * @param name        The name of the file to save it as
     */
    private void saveImage(Bitmap finalBitmap, String name) {

        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root + "/saved_images");
        myDir.mkdirs();
        String fileName = "Image-" + name + ".jpg";
        File file = new File(myDir, fileName);
        if (file.exists()) file.delete();
        try {
            FileOutputStream out = new FileOutputStream(file);
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();

        } catch (Exception e) {
            Crashlytics.log("Exception thrown in saveImage()");
            Crashlytics.logException(e);
            Log.e(TAG, "Error while saving the image.", e);
        }
    }

    public void destroy() {
        tesseract.stop();
        tesseract.end();
    }
}
