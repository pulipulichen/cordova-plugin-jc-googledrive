package gr.jcdenton.googleDrive;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static android.app.Activity.RESULT_OK;

public class GoogleDrive extends CordovaPlugin implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "GoogleDrivePlugin";
    private static final int REQUEST_CODE_RESOLUTION = 3;
    private GoogleApiClient mGoogleApiClient;
    private String mAction = "";
    private String toLocalDest;
    private String fileid;
    private String localFPath;
    private CallbackContext mCallbackContext;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView){
        super.initialize(cordova, webView);
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(cordova.getActivity())
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        mGoogleApiClient.connect();
        Log.i(TAG,"Plugin initialized");
    }

    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        //JSONObject jobject = args.getJSONObject(0);
        mCallbackContext = callbackContext;
        mAction = action;
        if ("downloadFile".equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        toLocalDest = args.getString(0);
                        fileid = args.getString(1);
                        downloadFile(toLocalDest, fileid);
                    } catch (JSONException ex){ex.getLocalizedMessage();}
                }
            });
            return true;
        } else if("uploadFile".equals(action)){
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        localFPath = args.getString(0);
                        uploadFile(localFPath);
                    }catch(JSONException ex){ex.getLocalizedMessage();}
                }
            });
            return true;
        } else if("fileList".equals(action)){
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    fileList();
                }
            });
            return true;
        } else if("deleteFile".equals(action)){
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        fileid = args.getString(0);
                        deleteFile(fileid);
                    } catch (JSONException ex){ex.getLocalizedMessage();}
                }
            });
            return true;
        } else if("disconnect".equals(action)){
            mGoogleApiClient.disconnect();
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
            return true;
        }
        return false;
    }

    private void downloadFile(String destPath,String fileid) {
        //TODO: complete download file
        DriveId.decodeFromString(fileid).asDriveFile().getMetadata(mGoogleApiClient).setResultCallback(new ResultCallback<DriveResource.MetadataResult>() {
            @Override
            public void onResult(DriveResource.MetadataResult result) {
                if (!result.getStatus().isSuccess()) {
                    mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR,"Something went wrong with file download"));
                    return;
                }
                new Thread(){
                    @Override
                    public void run() {
                        //start the download
                    }
                }.start();
            }
        });
    }

    private void uploadFile(final String fpath) {
        Drive.DriveApi.newDriveContents(mGoogleApiClient)
                .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {

                    @Override
                    public void onResult(DriveApi.DriveContentsResult result) {
                        final DriveContents driveContents = result.getDriveContents();

                        if (!result.getStatus().isSuccess()) {
                            mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Failed to create new contents"));
                            return;
                            }

                        new Thread() {
                            @Override
                            public void run() {
                                Log.i(TAG, "New contents created.");
                                OutputStream outputStream = driveContents.getOutputStream();
                                Uri fPathURI = Uri.fromFile(new File(fpath));;
                                try{
                                    InputStream inputStream = cordova.getActivity().getContentResolver().openInputStream(fPathURI);
                                    if (inputStream != null) {
                                        byte[] data = new byte[1024];
                                        while (inputStream.read(data) != -1) {
                                            outputStream.write(data);
                                        }
                                        inputStream.close();
                                    }
                                    outputStream.close();
                                } catch (IOException e) {
                                    Log.e(TAG, e.getMessage());
                                }

                                String fname = fPathURI.getLastPathSegment();
                                //Log.i(TAG,fname);
                                MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
                                        .setMimeType("application/octet-stream").setTitle(fname).build();
                                Drive.DriveApi.getRootFolder(mGoogleApiClient)
                                        .createFile(mGoogleApiClient, metadataChangeSet, driveContents)
                                        .setResultCallback(new ResultCallback<DriveFolder.DriveFileResult>() {
                                            @Override
                                            public void onResult(DriveFolder.DriveFileResult result) {
                                                if (result.getStatus().isSuccess()) {
                                                    try {
                                                        mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, new JSONObject().put("fileId", result.getDriveFile().getDriveId())));
                                                    } catch (JSONException ex) {
                                                        Log.i(TAG, ex.getMessage());
                                                    }
                                                    Log.i(TAG, result.getDriveFile().getDriveId() + "");
                                                }
                                            }
                                        });
                            }
                        }.start();
                    }
                });
    }

    private void fileList() {
        Query query = new Query.Builder().addFilter(Filters.and(
                Filters.eq(SearchableField.MIME_TYPE, "application/octet-stream"),
                Filters.eq(SearchableField.TRASHED, false))).build();

        Drive.DriveApi.query(mGoogleApiClient, query)
                .setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
                    @Override
                    public void onResult(DriveApi.MetadataBufferResult result) {
                        if (!result.getStatus().isSuccess()) {
                            mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR,"failed to retrieve file list"));
                            return;
                        }
                        MetadataBuffer flist = result.getMetadataBuffer();
                        JSONArray response = new JSONArray();
                        for (Metadata file: flist
                             ) {
                            try {
                                response.put(new JSONObject().put("name", file.getTitle()).put("created", file.getCreatedDate().toString()).put("id", file.getDriveId()));
                            }catch (JSONException ex){}
                        }
                        JSONObject flistJSON = new JSONObject();
                        try{
                            flistJSON.put("flist", response);
                        } catch (JSONException ex){}
                        mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK,flistJSON));
                        flist.release();
                        //Log.i(TAG,flist.toString());
                    }
                });
    }

    private void deleteFile(String fileid){
        DriveId.decodeFromString(fileid).asDriveFile().getMetadata(mGoogleApiClient).setResultCallback(new ResultCallback<DriveResource.MetadataResult>() {
            @Override
            public void onResult(@NonNull DriveResource.MetadataResult result) {
                if (!result.getStatus().isSuccess()) {
                    mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR,"Something went wrong with file "));
                    return;
                }
                final Metadata metadata = result.getMetadata();
                Log.i(TAG, metadata.getTitle());
                if(metadata.isTrashable() && !metadata.isTrashed()){
                    DriveFile f = metadata.getDriveId().asDriveFile();
                    f.trash(mGoogleApiClient).setResultCallback(new ResultCallback<Status>() {
                        @Override
                        public void onResult(Status status) {
                            if (!status.isSuccess()) {
                                mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR,"failed to trash file with id "+metadata.getDriveId() ));
                                return;
                            }
                            mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_RESOLUTION && resultCode == RESULT_OK) {
            mGoogleApiClient.connect();
            if(mAction.equals("downloadFile")){
                downloadFile(toLocalDest,fileid);
            } else if(mAction.equals("uploadFile")){
                uploadFile(localFPath);
            } else if(mAction.equals("fileList")){
                fileList();
            } else if(mAction.equals("deleteFile")){
                deleteFile(fileid);
            }
        }
    }


    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Called whenever the API client fails to connect.
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution()) {
            // show the localized error dialog.
            GoogleApiAvailability.getInstance().getErrorDialog(cordova.getActivity(), result.getErrorCode(), 0).show();
            return;
        }
        try {
            Log.i(TAG,"trying to resolve issue...");
            cordova.setActivityResultCallback(this);//
            result.startResolutionForResult(cordova.getActivity(), REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "API client connected.");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "GoogleApiClient connection suspended");
    }
}