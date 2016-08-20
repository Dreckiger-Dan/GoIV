package com.kamron.pogoiv;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import io.fabric.sdk.android.Fabric;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private SharedPreferences sharedPref;

    private static final int OVERLAY_PERMISSION_REQ_CODE = 1234;
    private static final int WRITE_STORAGE_REQ_CODE = 1236;
    private static final int SCREEN_CAPTURE_REQ_CODE = 1235;

    private MediaProjection mProjection;
    private ImageReader mImageReader;
    private ContentObserver screenShotObserver;
    private FileObserver screenShotScanner;
    private boolean screenShotWriting= false;

    private DisplayMetrics displayMetrics;
    private DisplayMetrics rawDisplayMetrics;
    private boolean batterySaver = false;
    private String screenshotDir;
    private Uri screenshotUri;
    private BmpHelper bmpHelper;

    private boolean readyForNewScreenshot = true;

    private boolean pokeFlyRunning = false;
    private int trainerLevel;

    private int pokemonAreaX1;
    private int pokemonAreaY1;
    private int pokemonAreaX2;
    private int pokemonAreaY2;
    private int gymAreaX1;
    private int gymAreaY1;
    private int gymAreaX2;
    private int gymAreaY2;

    private int statusBarHeight;
    private int arcCenter;
    private int arcInitialY;
    private int radius;

    @TargetApi(23)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set up Crashlytics, disabled for debug builds
        Crashlytics crashlyticsKit = new Crashlytics.Builder()
                .core(new CrashlyticsCore.Builder()
                .disabled(BuildConfig.DEBUG).build())
                .build();

        // Initialize Fabric with the debug-disabled crashlytics.
        Fabric.with(this, crashlyticsKit);

        setContentView(R.layout.activity_main);

        TextView tvVersionNumber = (TextView) findViewById(R.id.version_number);
        tvVersionNumber.setText(getVersionName());

        TextView goIvInfo = (TextView) findViewById(R.id.goiv_info);
        goIvInfo.setMovementMethod(LinkMovementMethod.getInstance());

        sharedPref = getPreferences(Context.MODE_PRIVATE);
        trainerLevel = sharedPref.getInt("level", 1);
        batterySaver = sharedPref.getBoolean("batterySaver", false);
        screenshotDir = sharedPref.getString("screenshotDir","");
        screenshotUri = Uri.parse(sharedPref.getString("screenshotUri",""));

        final EditText etTrainerLevel = (EditText) findViewById(R.id.trainerLevel);
        etTrainerLevel.setText(String.valueOf(trainerLevel));

        if (!new File(getExternalFilesDir(null) + "/tessdata/eng.traineddata").exists()) {
            copyAssetFolder(getAssets(), "tessdata", getExternalFilesDir(null) + "/tessdata");
        }
        bmpHelper = new BmpHelper(getExternalFilesDir(null) + "");

        final CheckBox CheckBox_BatterySaver = (CheckBox) findViewById(R.id.checkbox_batterySaver);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            CheckBox_BatterySaver.setChecked(true);
            CheckBox_BatterySaver.setEnabled(false);
            batterySaver = true;
        } else {
            CheckBox_BatterySaver.setChecked(batterySaver);
        }

        Button launch = (Button) findViewById(R.id.start);
        launch.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (((Button) v).getText().toString().equals(getString(R.string.main_permission))) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(MainActivity.this)) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
                    }
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_STORAGE_REQ_CODE);
                    }
                } else if (((Button) v).getText().toString().equals(getString(R.string.main_start))) {
                    batterySaver = CheckBox_BatterySaver.isChecked();
                    Rect rectangle = new Rect();
                    Window window = getWindow();
                    window.getDecorView().getWindowVisibleDisplayFrame(rectangle);
                    statusBarHeight = rectangle.top;

                    // TODO same calculation as in pokefly @line 193 with difference of "- pointerHeight - statusBarHeight" this should be outsource in a method
                    arcCenter = (int) ((displayMetrics.widthPixels * 0.5));
                    arcInitialY = (int) Math.floor(displayMetrics.heightPixels / 2.803943); // - pointerHeight - statusBarHeight; // 913 - pointerHeight - statusBarHeight; //(int)Math.round(displayMetrics.heightPixels / 6.0952381) * -1; //dpToPx(113) * -1; //(int)Math.round(displayMetrics.heightPixels / 6.0952381) * -1; //-420;
                    if (displayMetrics.heightPixels == 2392 || displayMetrics.heightPixels == 800) {
                        arcInitialY--;
                    } else if (displayMetrics.heightPixels == 1920) {
                        arcInitialY++;
                    }

                    // TODO same calculation as in pokefly @line 201
                    radius = (int) Math.round(displayMetrics.heightPixels / 4.3760683); //dpToPx(157); //(int)Math.round(displayMetrics.heightPixels / 4.37606838); //(int)Math.round(displayMetrics.widthPixels / 2.46153846); //585;
                    if (displayMetrics.heightPixels == 1776 || displayMetrics.heightPixels == 960 || displayMetrics.heightPixels == 800) {
                        radius++;
                    }

                    if (isNumeric(etTrainerLevel.getText().toString())) {
                        trainerLevel = Integer.parseInt(etTrainerLevel.getText().toString());
                    } else {
                        Toast.makeText(MainActivity.this, String.format(getString(R.string.main_not_numeric), etTrainerLevel.getText().toString()), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (trainerLevel > 0 && trainerLevel <= 40) {
                        sharedPref.edit().putInt("level", trainerLevel).apply();
                        sharedPref.edit().putBoolean("batterySaver", batterySaver).apply();
                        setupArcPoints();

                        if (batterySaver) {
                            if(!screenshotDir.isEmpty()) {
                                startScreenshotService();
                            }
                            else{
                                getScreenshotDir();
                            }
                        } else {
                            startScreenService();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, getString(R.string.main_invalide_trainerlvl), Toast.LENGTH_SHORT).show();
                    }
                } else if(((Button) v).getText().toString().equals(getString(R.string.main_stop))) {
                    stopService(new Intent(MainActivity.this, pokefly.class));
                    if (mProjection != null) {
                        mProjection.stop();
                        mProjection = null;
                        mImageReader = null;
                    } else if (screenShotScanner != null) {
                        screenShotScanner.stopWatching();
                        screenShotScanner = null;
                    }
                    pokeFlyRunning = false;
                    ((Button) v).setText(getString(R.string.main_start));
                }
            }
        });

        checkPermissions(launch);


        displayMetrics = this.getResources().getDisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        rawDisplayMetrics = new DisplayMetrics();
        Display disp = windowManager.getDefaultDisplay();
        disp.getRealMetrics(rawDisplayMetrics);

        pokemonAreaX1 = Math.round(displayMetrics.widthPixels / 24);  // these values used to get "white" left of "power up"
        pokemonAreaY1 = (int) Math.round(displayMetrics.heightPixels / 1.24271845);
        pokemonAreaX2 = (int) Math.round(displayMetrics.widthPixels / 1.15942029);  // these values used to get greenish color in transfer button
        pokemonAreaY2 = (int) Math.round(displayMetrics.heightPixels / 1.11062907);

        gymAreaX1 = 0;
        gymAreaY1 = 0;
//        gymAreaX2;
//        gymAreaY2;

        //Check if language makes the pokemon name in candy second e.g. France is Bonbon pokeName
        if(Locale.getDefault().getLanguage().equals("fr")){
            bmpHelper.candyOrder = 1;
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(resetScreenshot, new IntentFilter("reset-screenshot"));
        LocalBroadcastManager.getInstance(this).registerReceiver(takeScreenshot, new IntentFilter("screenshot"));
    }

    private void getScreenshotDir(){
        new AlertDialog.Builder(this)
                .setTitle(R.string.battery_saver_setup)
                .setMessage(R.string.battery_saver_instructions)
                .setPositiveButton(R.string.setup, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        ((Button)findViewById(R.id.start)).setText(R.string.take_screenshot);
                        screenShotObserver = new ContentObserver(new Handler()) {
                            @Override
                            public void onChange(boolean selfChange, Uri uri) {
                                if(readyForNewScreenshot){
                                    final Uri fUri = uri;
                                    if(fUri.toString().contains("images")) {
                                        final String pathChange = getRealPathFromURI(MainActivity.this, fUri);
                                        if (pathChange.contains("Screenshot")) {
                                            screenshotDir = pathChange.substring(0,pathChange.lastIndexOf(File.separator));
                                            screenshotUri = fUri;
                                            getContentResolver().unregisterContentObserver(screenShotObserver);
                                            sharedPref.edit().putString("screenshotDir", screenshotDir).apply();
                                            sharedPref.edit().putString("screenshotUri", fUri.toString()).apply();
                                            ((Button)findViewById(R.id.start)).setText(R.string.main_start);
                                            new AlertDialog.Builder(MainActivity.this)
                                                    .setTitle(R.string.battery_saver_setup)
                                                    .setMessage(String.format(getString(R.string.screenshot_dir_found),screenshotDir))
                                                    .setPositiveButton(R.string.done, new DialogInterface.OnClickListener() {
                                                        public void onClick(DialogInterface dialog, int which) {
                                                            screenShotObserver = null;
                                                            getContentResolver().delete(screenshotUri, MediaStore.Files.FileColumns.DATA + "=?", new String[]{pathChange});
                                                        }
                                                    })
                                                    .show();
                                        }
                                    }
                                }
                                super.onChange(selfChange, uri);
                            }
                        };
                        getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true,screenShotObserver);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .show();
    }

    /**
     * setupArcPoints
     * Sets up the x,y coordinates of the arc using the trainer level, stores it in Data.arcX/arcY
     */
    private void setupArcPoints(){
        final int indices = Math.min((int)((trainerLevel + 1.5) * 2) - 1,79);
        Data.arcX = new int[indices];
        Data.arcY = new int[indices];

        for (double pokeLevel = 1.0; pokeLevel <= trainerLevel + 1.5; pokeLevel += 0.5) {
            double angleInDegrees = (Data.CpM[(int) (pokeLevel * 2 - 2)] - 0.094) * 202.037116 / Data.CpM[trainerLevel * 2 - 2];
            if (angleInDegrees > 1.0 && trainerLevel < 30) {
                angleInDegrees -= 0.5;
            } else if (trainerLevel >= 30) {
                angleInDegrees += 0.5;
            }

            double angleInRadians = (angleInDegrees + 180) * Math.PI / 180.0;

            int index = Data.convertLevelToIndex(pokeLevel);
            Data.arcX[index] = (int) (arcCenter + (radius * Math.cos(angleInRadians)));
            Data.arcY[index] = (int) (arcInitialY + (radius * Math.sin(angleInRadians)));
        }
    }

    /**
     * startPokeFly
     * Starts the PokeFly background service which contains overlay logic
     */
    private void startPokeyFly() {
        ((Button) findViewById(R.id.start)).setText("Stop");
        Intent PokeFly = new Intent(MainActivity.this, pokefly.class);
        PokeFly.putExtra("trainerLevel", trainerLevel);
        PokeFly.putExtra("statusBarHeight", statusBarHeight);
        PokeFly.putExtra("batterySaver", batterySaver);
        if(!screenshotDir.isEmpty()) {
            PokeFly.putExtra("screenshotUri", screenshotUri.toString());
        }
        startService(PokeFly);

        pokeFlyRunning = true;

        //openPokemonGoApp();
    }

    private boolean isNumeric(String str) {
        try {
            int number = Integer.parseInt(str);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Crashlytics.log("Exception thrown while getting version name");
            Crashlytics.logException(e);
            Log.e(TAG, "Error while getting version name", e);
        }
        return "Error while getting version name";
    }

    /**
     * checkPermissions
     * Checks to see if all runtime permissions are granted,
     * if not change button text to Grant Permissions.
     *
     * @param launch The start button to change the text of
     */
    private void checkPermissions(Button launch) {
        //Check Permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            //Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            //       Uri.parse("package:" + getPackageName()));
            //startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
            launch.setText(getString(R.string.main_permission));
            //startScreenService();
        } else if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            launch.setText(getString(R.string.main_permission));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pokeFlyRunning) {
            stopService(new Intent(MainActivity.this, pokefly.class));
            pokeFlyRunning = false;
        }
        if (mProjection != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mProjection.stop();
            }
        }
        if (screenShotObserver != null) {
            getContentResolver().unregisterContentObserver(screenShotObserver);
        }
        if (screenShotScanner != null) {
            screenShotScanner.stopWatching();
            screenShotScanner = null;
        }
        bmpHelper.destroy();
        mProjection = null;
        mImageReader = null;

        LocalBroadcastManager.getInstance(this).unregisterReceiver(resetScreenshot);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(takeScreenshot);
    }


    @TargetApi(23)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (!Settings.canDrawOverlays(this)) {
                // SYSTEM_ALERT_WINDOW permission not granted...
                ((Button) findViewById(R.id.start)).setText(getString(R.string.main_permission));
            } else if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                ((Button) findViewById(R.id.start)).setText(getString(R.string.main_start));
            }
        } else if (requestCode == SCREEN_CAPTURE_REQ_CODE) {
            if (resultCode == RESULT_OK) {
                MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                mProjection = projectionManager.getMediaProjection(resultCode, data);
                mImageReader = ImageReader.newInstance(rawDisplayMetrics.widthPixels, rawDisplayMetrics.heightPixels, PixelFormat.RGBA_8888, 2);
                mProjection.createVirtualDisplay("screen-mirror", rawDisplayMetrics.widthPixels, rawDisplayMetrics.heightPixels, rawDisplayMetrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mImageReader.getSurface(), null, null);

                startPokeyFly();
                //showNotification();
                final Handler handler = new Handler();
                final Timer timer = new Timer();
                TimerTask doAsynchronousTask = new TimerTask() {
                    @Override
                    public void run() {
                        handler.post(new Runnable() {
                            public void run() {
                                if (pokeFlyRunning) {
                                    scanPokemonScreen();
                                } else {
                                    timer.cancel();
                                }
                            }
                        });
                    }
                };
                timer.schedule(doAsynchronousTask, 0, 750);
            } else {
                ((Button) findViewById(R.id.start)).setText(getString(R.string.main_start));
            }
        }
    }

    /**
     * openPokemonGoApp
     * Runs a launch intent for Pokemon GO
     */
    private void openPokemonGoApp() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.nianticlabs.pokemongo");
        if (i != null)
            startActivity(i);
    }

    @TargetApi(23)
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == WRITE_STORAGE_REQ_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (Settings.canDrawOverlays(this) && ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    // SYSTEM_ALERT_WINDOW permission not granted...
                    ((Button) findViewById(R.id.start)).setText(getString(R.string.main_start));
                }
            }
        }
    }

    /**
     * takeScreenshot
     * Called by intent from pokefly, captures the screen and runs it through scanPokemon
     */
    private void takeScreenshot() {
        Image image = null;
        try {
            image = mImageReader.acquireLatestImage();
        } catch (Exception e) {
            Crashlytics.log("Error thrown in takeScreenshot() - acquireLatestImage()");
            Crashlytics.logException(e);
            Log.e(TAG, "Error while Scanning!", e);
            Toast.makeText(MainActivity.this, "Error Scanning! Please try again later!", Toast.LENGTH_SHORT).show();
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
                Bitmap bmp = bmpHelper.getBitmap(buffer, pixelStride, rowPadding, rawDisplayMetrics.widthPixels, displayMetrics.heightPixels);
                scanPokemon(bmp, "");
                //SaveImage(bmp,"Search");
            } catch (Exception e) {
                Crashlytics.log("Exception thrown in takeScreenshot() - when creating bitmap");
                Crashlytics.logException(e);
                image.close();
            }


        }
    }

    private void scanPokemon(Bitmap pokemonImage, String filePath) {
        BmpHelper.ScanResult scanResult =
                bmpHelper.scanPokemon(
                        pokemonImage, filePath, trainerLevel, displayMetrics.widthPixels, displayMetrics.heightPixels);

        if (scanResult == null) {
            readyForNewScreenshot = true;
        } else {
            scanResult.pokemonName = scanResult.pokemonName.replace("Sparky", getString(R.string.pokemon133)).replace("Rainer", getString(R.string.pokemon133)).replace("Pyro", getString(R.string.pokemon133));

            if (scanResult.pokemonName.toLowerCase().contains("nidora")) {
                if(bmpHelper.isNidoranFemale(pokemonImage, displayMetrics.widthPixels, displayMetrics.heightPixels)) {
                    scanResult.pokemonName = getResources().getString(R.string.pokemon029);
                } else {
                    scanResult.pokemonName = getResources().getString(R.string.pokemon032);
                }
            }

            Intent info = new Intent("pokemon-info");
            info.putExtra("name", scanResult.pokemonName);
            info.putExtra("candy", scanResult.candyName);
            info.putExtra("hp", scanResult.pokemonHP);
            info.putExtra("cp", scanResult.pokemonCP);
            info.putExtra("level", scanResult.estimatedPokemonLevel);
            if (!filePath.isEmpty()) {
                info.putExtra("screenshotDir", filePath);
            }
            LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(info);
        }

    }

    /**
     * scanPokemonScreen
     * Scans the device screen to check area1 for the white and area2 for the transfer button.
     * If both exist then the user is on the pokemon screen.
     */
    private void scanPokemonScreen() {
        //System.out.println("Checking...");
        Image image = mImageReader.acquireLatestImage();
        if (image != null) {
            final Image.Plane[] planes = image.getPlanes();
            final ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * rawDisplayMetrics.widthPixels;
            // create bitmap
            image.close();
            Bitmap bmp = bmpHelper.getBitmap(buffer, pixelStride, rowPadding, rawDisplayMetrics.widthPixels, displayMetrics.heightPixels);
            Intent showIVButton = new Intent("display-ivButton");
            if (bmp.getPixel(pokemonAreaX1, pokemonAreaY1) == Color.rgb(250, 250, 250) && bmp.getPixel(pokemonAreaX2, pokemonAreaY2) == Color.rgb(28, 135, 150)) {
                showIVButton.putExtra("show", true);
            } else if (bmp.getPixel(gymAreaX1, gymAreaY1) == Color.rgb(250, 250, 250) && bmp.getPixel(pokemonAreaX2, pokemonAreaY2) == Color.rgb(28, 135, 150)) {
            } else {
                showIVButton.putExtra("show", false);
            }
            bmp.recycle();
            LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(showIVButton);
            //bmpHelper.saveImage(bmp,"everything");
        }
    }



    /**
     * startScreenService
     * Starts the screen capture.
     */
    @TargetApi(21)
    private void startScreenService() {
        ((Button) findViewById(R.id.start)).setText("Accept Screen Capture");
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQ_CODE);
    }

    /**
     * startScreenshotService
     * Starts the screenshot service, which checks for a new screenshot to scan
     */
    private void startScreenshotService() {
        screenShotScanner = new FileObserver(screenshotDir, FileObserver.CLOSE_NOWRITE | FileObserver.CLOSE_WRITE) {
            @Override
            public void onEvent(int event, String file) {
                    if (readyForNewScreenshot && file != null) {
                        readyForNewScreenshot = false;
                        File pokemonScreenshot = new File(screenshotDir + File.separator + file);
                        scanPokemon(BitmapFactory.decodeFile(pokemonScreenshot.getAbsolutePath()),pokemonScreenshot.getAbsolutePath());
                    }
            }
        };
        screenShotScanner.startWatching();
        startPokeyFly();
    }


    private String getRealPathFromURI(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = { MediaStore.Images.Media.DATA };
            cursor = context.getContentResolver().query(contentUri,  proj, null, null, MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC");
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * takeScreenshot
     * IV Button was pressed, take screenshot and send back pokemon info.
     */
    private final BroadcastReceiver takeScreenshot = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (readyForNewScreenshot) {
                takeScreenshot();
                readyForNewScreenshot = false;
            }
        }
    };

    /**
     * resetScreenshot
     * Used to notify a new request for screenshot can be made. Needed to prevent multiple
     * intents for some devices.
     */
    private final BroadcastReceiver resetScreenshot = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            readyForNewScreenshot = true;
        }
    };


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
