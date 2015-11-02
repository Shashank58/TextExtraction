package com.cybrilla.shashank.libraryforborgs;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
    Button camera;
    private String DATA_PATH = Environment.getExternalStorageDirectory() + "/tesseract-ocr";
    private int PIC_CAPTURE = 0;
    private int CROP_IMAGE = 2;
    private TextView extractedText;
    private ImageView croppedImage;

    private TessBaseAPI baseApi;
    private int x1, y1, x2, y2;
    /**
     * Boolean that tells me how to treat a transparent pixel (Should it be black?)
     */
    private static final boolean TRASNPARENT_IS_BLACK = false;
    /**
     * This is a point that will break the space into Black or white
     * In real words, if the distance between WHITE and BLACK is D;
     * then we should be this percent far from WHITE to be in the black region.
     * Example: If this value is 0.5, the space is equally split.
     */
    private static final double SPACE_BREAKING_POINT = 13.0/30.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        copyAssets();
        baseApi = new TessBaseAPI();
        baseApi.init(DATA_PATH, "eng");

        extractedText = (TextView) findViewById(R.id.extractedText);
        croppedImage = (ImageView) findViewById(R.id.croppedImage);
        camera = (Button) findViewById(R.id.camera);

        camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(intent, PIC_CAPTURE);
                } catch (ActivityNotFoundException e) {
                    String noCamera = "Your phone doesnot support image capture";
                    Toast toast = Toast.makeText(getApplicationContext(), noCamera, Toast.LENGTH_LONG);
                    toast.show();
                }
            }
        });
    }

    //Creating tesseract-ocr directory in user's sdcard
    private void copyAssets(){
        AssetManager assetManager = getAssets();
        String[] files = null;
        String name = "/tessdata/";
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            // We can read and write the media
            // Checking file on assets subfolder
            boolean success = false;
            try {
                files = assetManager.list("Files");
                File folder = new File(DATA_PATH);
                File subFolder = new File(folder + "/tessdata");

                if (!folder.exists()) {
                    folder.mkdir();
                    subFolder.mkdir();
                    success = true;
                }
            } catch (IOException e) {
                Log.e("ERROR", "Failed to get asset file list.", e);
            }
            if (success) {
                // Analyzing all file on assets subfolder
                for (String filename : files) {
                    InputStream in = null;
                    OutputStream out = null;
                    // First: checking if there is already a target folder

                    // Moving all the files on external SD
                    try {
                        Log.e("MainActivity", "Creating again?");
                        in = assetManager.open("Files/" + filename);
                        out = new FileOutputStream(DATA_PATH + name + filename);
                        copyFile(in, out);
                    } catch (IOException e) {
                        Log.e("ERROR", "Failed to copy asset file: " + filename, e);
                    } finally {
                        // Edit 3 (after MMs comment)
                        try {
                            in.close();
                            in = null;
                            out.flush();
                            out.close();
                            out = null;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            // We can only read the media
            Log.e("Main Activity", "Change user permissions to write and read!");
        } else {
            // Something else is wrong. It may be one of many other states, but all we need
            // is to know is we can neither read nor write
            Toast toast = Toast.makeText(getApplicationContext(), "Error files couldnot be loaded!", Toast.LENGTH_LONG);
            toast.show();
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK) {
            if (requestCode == PIC_CAPTURE) {
                Uri bp = data.getData();
                performCrop(bp);
            }
            else if(requestCode == CROP_IMAGE){
                Bundle extras = data.getExtras();
                Bitmap croppedPic = extras.getParcelable("data");
                textExtraction(croppedPic);
            }
        }
    }

    private void performCrop(Uri bp){
        try {
            Intent cropIntent = new Intent("com.android.camera.action.CROP");

            cropIntent.setDataAndType(bp, "image/*");

            cropIntent.putExtra("crop", "true");
            cropIntent.putExtra("aspectX", x1);
            cropIntent.putExtra("aspectY", y1);
            cropIntent.putExtra("outputX", x2);
            cropIntent.putExtra("outputY", y2);
            cropIntent.putExtra("return-data", true);
            startActivityForResult(cropIntent, CROP_IMAGE);
        } catch (ActivityNotFoundException e){
            Toast toast = Toast.makeText(getApplicationContext(), "Your device doesnot support cropping an image", Toast.LENGTH_LONG);
            toast.show();
        }
    }

    private void textExtraction(Bitmap croppedPic){
        int width, height;
        height = croppedPic.getHeight();
        width = croppedPic.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(croppedPic, 0, 0, paint);

        baseApi.setImage(bmpGrayscale);
        baseApi.setImage(baseApi.getThresholdedImage());
        String recognizedText = baseApi.getUTF8Text();
        recognizedText = recognizedText.replaceAll("[^a-zA-Z0-9\\s]+", "");
        extractedText.setText(recognizedText);

        croppedImage.setImageBitmap(bmpGrayscale);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        baseApi.end();
    }
}
