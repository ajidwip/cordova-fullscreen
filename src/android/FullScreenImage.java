//
//  FullScreenImage.java
//  Copyright (c) 2014 keensoft - http://keensoft.es
//

package com.panin.fullscreenimage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Locale;

import android.annotation.SuppressLint;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;


import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.util.Base64;
import android.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.webkit.MimeTypeMap;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import android.os.Build;
import android.content.Context;
import io.github.panin.cordova.plugins.fileopenerpanin.FileProvider;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.List;



@SuppressLint("DefaultLocale")
public class FullScreenImage extends CordovaPlugin {
    private CallbackContext command;
	private static final String LOG_TAG = "FullScreenImagePlugin";

    /**
     * Executes the request.
     *
     * This method is called from the WebView thread.
     * To do a non-trivial amount of work, use:
     *     cordova.getThreadPool().execute(runnable);
     *
     * To run on the UI thread, use:
     *     cordova.getActivity().runOnUiThread(runnable);
     *
     * @param action   The action to execute.
     * @param args     The exec() arguments in JSON form.
     * @param callback The callback context used when calling
     *                 back into JavaScript.
     * @return         Whether the action was valid.
     */
    @Override
    public boolean execute (String action, JSONArray args,
                            CallbackContext callback) throws JSONException {

        this.command = callback;

        if ("showImageURL".equals(action)) {
            showImageURL(args);

            return true;
        }

        if ("showImageBase64".equals(action)) {
            showImageBase64(args);

            return true;
        }

        // Returning false results in a "MethodNotFound" error.
        return false;
    }

    private String getJSONProperty(JSONObject json, String property) throws JSONException {
        if (json.has(property)) {
            return json.getString(property);
        }
        return null;
    }

    /**
     * Show image in full screen from local resources.
     *
     * @param url     File path in local system
     */
    public void showImageURL (JSONArray args) throws JSONException {
        JSONObject json = args.getJSONObject(0);
        String url = getJSONProperty(json, "url");
        try {

            Uri path = Uri.parse(url);
            File source = new File(path.getPath());
            String filenameArray[] = url.split("\\.");
            String extension = filenameArray[filenameArray.length-1];
            if (!source.isFile()) {
                Log.d(LOG_TAG, "Not a file. Trying www/");
                InputStream inputStream = null;
                OutputStream outputStream = null;

                File pPath = getTempDirectoryPath();

                File f= new File(pPath, "output."+extension);
                f.createNewFile();
                inputStream = this.cordova.getActivity().getAssets().open("www/"+url);
                outputStream =new FileOutputStream(f);
                byte buf[]=new byte[1024];
                int len;

                while((len=inputStream.read(buf))>0)
                    outputStream.write(buf,0,len);
                outputStream.close();
                inputStream.close();

                path = Uri.fromFile(f);
            } 
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Convert the URI string to lower case to ensure compatibility with MimeTypeMap (see CB-2185).
            intent.setDataAndType(path, MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.getDefault())));
            this.cordova.getActivity().startActivity(intent);

        } catch (IOException e) {
            Log.d(LOG_TAG, "Could not create file: " + e.toString());

        }
    }


    /**
     * Show image in full screen from base64 String.
     * @param base64       Image base64 String
     * @param name              image Name to show on intent view
     */
    public void showImageBase64 (JSONArray args) throws JSONException{
        JSONObject json = args.getJSONObject(0);

        String base64Image = getJSONProperty(json, "base64");
        String name = getJSONProperty(json, "name");
        String extension = getJSONProperty(json, "type");
        File pPath = getTempDirectoryPath();

        try {

            byte[] imageAsBytes = Base64.decode(base64Image, Base64.DEFAULT);

            File filePath= new File(pPath, "output."+extension);
            filePath.createNewFile();

            FileOutputStream os = new FileOutputStream(filePath, false);
            os.write(imageAsBytes);
            os.flush();
            os.close();

            Uri path = Uri.fromFile(filePath);
            Intent intent = new Intent(Intent.ACTION_VIEW);
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            intent.setDataAndType(path, "image/*");
//            this.cordova.getActivity().startActivity(intent);


            String contentType = "image/*";

//            Uri path = Uri.fromFile(file);
//            Intent intent = new Intent(Intent.ACTION_VIEW);
            if((Build.VERSION.SDK_INT >= 23 && !contentType.equals("application/vnd.android.package-archive")) || ((Build.VERSION.SDK_INT == 24 || Build.VERSION.SDK_INT == 25) && contentType.equals("application/vnd.android.package-archive"))) {

                Context context = cordova.getActivity().getApplicationContext();
                path = FileProvider.getUriForFile(context, cordova.getActivity().getPackageName() + ".opener.provider", filePath);
                intent.setDataAndType(path, contentType);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                //intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                List<ResolveInfo> infoList = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : infoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                context.grantUriPermission(packageName, path, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            }
            else {
                intent.setDataAndType(path, contentType);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }
            this.cordova.getActivity().startActivity(intent);

        } catch (IOException e) {
            Log.d(LOG_TAG, "Could not create file: " + e.toString());
        }


    }


    /**
     * Get temporary directory for copied image
     * Refer to cordova-plugin-camera/src/android/CameraLauncher.java
     */
    private File getTempDirectoryPath() {
        File cache = null;

        // SD Card Mounted
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            cache = new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
                    "/Android/data/" + cordova.getActivity().getPackageName() + "/cache/");
        }
        // Use internal storage
        else {
            cache = cordova.getActivity().getCacheDir();
        }

        // Create the cache directory if it doesn't exist
        cache.mkdirs();
        return cache;
    }
}
