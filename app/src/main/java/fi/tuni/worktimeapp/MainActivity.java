package fi.tuni.worktimeapp;

import android.Manifest;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.api.services.sheets.v4.Sheets;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * @author      Joni Alanko <joni.alanko@tuni.fi>
 * @version     20190422
 * @since       1.8
 *
 * Main class for the program. Currently holds almost all functionality.
 * TODO Break this class to smaller components.
 */
public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener, View.OnClickListener, EasyPermissions.PermissionCallbacks {

    private SignInButton signInButton;
    private Button logoutButton;
    private Button start;
    private Button stop;
    private TextView time;
    private GoogleApiClient gApi;

    protected GoogleAccountCredential mCredential;
    protected ProgressDialog mProgress;
    protected TextView mOutputText;
    protected String timeData;
    protected String startTime;
    protected String endTime;
    protected String startDate;
    protected SimpleDateFormat dateFormat;
    protected DateFormat timeFormat;

    static final int REQUEST_CODE = 9001;
    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    protected static final String APP_NAME = "WorkTimeApp";
    protected static final String PREF_ACCOUNT_NAME = "accountName";
    protected static final String[] SCOPES = { SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE};

    protected Drive driveService;
    protected Sheets sheetsService;
    protected HttpTransport transport;
    protected JsonFactory jsonFactory;
    protected String spreadsheetId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logoutButton = findViewById(R.id.logoutButton);
        signInButton = findViewById(R.id.loginButton);

        signInButton.setOnClickListener(this);
        logoutButton.setOnClickListener(this);
        logoutButton.setVisibility(View.GONE);

        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build();
        gApi = new GoogleApiClient.Builder(this).enableAutoManage(this,this).addApi(Auth.GOOGLE_SIGN_IN_API,signInOptions).build();

        start = findViewById(R.id.startCounter);
        stop = findViewById(R.id.stopCounter);

        start.setVisibility(View.GONE);
        stop.setVisibility(View.GONE);

        time = findViewById(R.id.time);
        time.setVisibility(View.GONE);

        mOutputText = findViewById(R.id.testText);

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling Google Sheets API ...");

        LocalBroadcastManager.getInstance(this).registerReceiver(new myBroadcastReceiver(), new IntentFilter("Broadcast"));
        transport = AndroidHttp.newCompatibleTransport();
        jsonFactory = JacksonFactory.getDefaultInstance();

        timeFormat = new SimpleDateFormat("HH:mm:ss");
        dateFormat = new SimpleDateFormat("yyyy.MM.dd");
    }

    /**
     * Defines Start Timer-button functionality.
     *
     * @param view current activity view.
     */
    public void startCounter(View view) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        startDate = dateFormat.format(calendar.getTime());
        startTime = timeFormat.format(calendar.getTime());
        time.setText("00:00:00");
        startService(new Intent(this, TimerService.class));
        start.setVisibility(View.GONE);
        stop.setVisibility(View.VISIBLE);
    }

    /**
     * Defines Stop Timer-button functionality.
     *
     * @param view current activity view.
     */
    public void stopCounter(View view) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        endTime = timeFormat.format(calendar.getTime());
        stopService(new Intent(this, TimerService.class));
        stop.setVisibility(View.GONE);
        start.setVisibility(View.VISIBLE);

        new MakeRequestTask(mCredential).execute();
    }

    /**
     * Converts seconds to HH:mm:ss format.
     *
     * @param timeData is seconds to be converted.
     * @return converted data in String format.
     */
    public static String convertTime(long timeData) {
        int secondsLeft = (int) timeData % 3600 % 60;
        int minutes = (int) Math.floor(timeData % 3600 / 60);
        int hours = (int) Math.floor(timeData / 3600);

        String HH = hours < 10 ? "0" + hours : "" + hours;
        String MM = minutes < 10 ? "0" + minutes : "" + minutes;
        String SS = secondsLeft < 10 ? "0" + secondsLeft : "" + secondsLeft;

        return HH + ":" + MM + ":" + SS;
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.loginButton:
                signIn();
                break;
            case R.id.logoutButton:
                stopCounter(v);
                logOut();
                break;
        }
    }

    /**
     * Defines signIn-button functionality.
     */
    private void signIn() {
        Intent intent = Auth.GoogleSignInApi.getSignInIntent(gApi);
        startActivityForResult(intent,REQUEST_CODE);
    }

    /**
     * Defines logOut-button functionality.
     */
    private void logOut() {
        Auth.GoogleSignInApi.signOut(gApi).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                updateUI(false);
            }
        });
    }

    /**
     * Handles Google sign in result.
     *
     * Includes some placeholders.
     *
     * @param result GoogleSignInResult.
     */
    private void handleResult(GoogleSignInResult result) {
        if (result.isSuccess()) {
            GoogleSignInAccount account = result.getSignInAccount();
            String name = account.getDisplayName();
            String email = account.getEmail();
            String imgUrl = account.getPhotoUrl().toString();
            mCredential = GoogleAccountCredential.usingOAuth2(
                    getApplicationContext(), Arrays.asList(SCOPES))
                    .setBackOff(new ExponentialBackOff());

            driveService = new Drive.Builder(transport, jsonFactory, mCredential)
                    .setApplicationName(APP_NAME)
                    .build();

            sheetsService = new Sheets.Builder(transport, jsonFactory, mCredential)
                    .setApplicationName(APP_NAME)
                    .build();
            getResultsFromApi();
            updateUI(true);
        } else {
            updateUI(false);
        }
    }

    /**
     * Handles UI.
     *
     * @param isLogin boolean determines which elements are visible.
     */
    private void updateUI(boolean isLogin) {
        if (isLogin) {
            start.setVisibility(View.VISIBLE);
            signInButton.setVisibility(View.GONE);
            logoutButton.setVisibility(View.VISIBLE);
            time.setVisibility(View.VISIBLE);
        } else {
            time.setVisibility(View.GONE);
            start.setVisibility(View.GONE);
            stop.setVisibility(View.GONE);
            signInButton.setVisibility(View.VISIBLE);
            logoutButton.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    mOutputText.setText(R.string.requires_Google);
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
            case REQUEST_CODE:
                GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
                handleResult(result);
                break;
        }
    }

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (! isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (! isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
        } else {
            createNewSheet();
        }
    }

    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        final int connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }

    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    /**
     * Checks Google Drive for named Google Sheet.
     * If found, gets its id, else creates one and gets the id.
     */
    @SuppressLint("StaticFieldLeak")
    public void createNewSheet() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    List<File> files = driveService.files().list().setQ(
                            "name contains '"+APP_NAME+"' and mimeType = 'application/vnd.google-apps.spreadsheet'"
                    ).execute().getFiles();

                    boolean duplicateFile = false;

                    for(File file: files) {
                        if(file.getName().equals(APP_NAME)
                                && file.getMimeType()
                                .equalsIgnoreCase("application/vnd.google-apps.spreadsheet")) {
                            duplicateFile = true;
                            spreadsheetId = file.getId();
                        }
                    }
                    if(!duplicateFile) {
                        //System.out.println("CREATING FILE TO DRIVE");
                        File file = createSheet();
                        spreadsheetId = file.getId();
                        initializeSheet();
                    }

                } catch (UserRecoverableAuthIOException e) {
                    startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return null;
            }
        }.execute();
    }

    /**
     * Creates new Google Sheet.
     *
     * @return created file info.
     * @throws IOException
     */
    private File createSheet() throws IOException{
        File toBeCreated = new File();
        toBeCreated.setMimeType("application/vnd.google-apps.spreadsheet");
        toBeCreated.setName(APP_NAME);
        return driveService.files().create(toBeCreated).execute();
    }

    /**
     * Adds Column tags to the Sheet.
     */
    private void initializeSheet() {
        ValueRange valueRange = new ValueRange();
        valueRange.setValues(Collections.singletonList(Arrays.asList("Date", "Work Hours", "Start Time", "End Time")));
        try {
            sheetsService.spreadsheets().values().append(spreadsheetId, "A1:D1", valueRange).setValueInputOption("RAW").execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles sheet data reading and writing.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {

        private Exception mLastError = null;
        MakeRequestTask(GoogleAccountCredential credential) {

        }

        /**
         * Background task to call Google Sheets API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<String> doInBackground(Void... params) {
            try {
                return writeDataToApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetches a list of items in used sheet.
         *
         * @return List of items.
         * @throws IOException
         */
        private List<List<Object>> getDataFromApi(String range) throws IOException {
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();
            List<List<Object>> values = response.getValues();
            return values;
        }

        /**
         * Determines where to write in the sheet then writes there.
         *
         * @return list of items. Placeholder.
         * @throws IOException
         */
        private List<String> writeDataToApi() throws IOException {
            String range = "!A1:D1";
            List<String> results = new ArrayList<String>();

            List<List<Object>> values = getDataFromApi(range);
            String targetCell;

            if (values != null) {
                int cellNumber = values.size() + 1;
                targetCell = "A" + cellNumber + ":D" + cellNumber;
            } else {
                initializeSheet();
                targetCell = "A2:D2";
            }

            Object dateData = startDate;
            Object data = timeData;
            Object startData = startTime;
            Object endData = endTime;

            ValueRange valueRange = new ValueRange();
            valueRange.setValues(Collections.singletonList(Arrays.asList(dateData, data, startData, endData)));
            sheetsService.spreadsheets().values().append(spreadsheetId, targetCell, valueRange).setValueInputOption("RAW").execute();

            return results;
        }

        @Override
        protected void onPreExecute() {
            mOutputText.setText("");
            mProgress.show();
        }

        @Override
        protected void onPostExecute(List<String> output) {
            mProgress.hide();
            mOutputText.setText("Results can be found in your Google Drive.");
        }

        @Override
        protected void onCancelled() {
            mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    mOutputText.setText("The following error occurred:\n" + mLastError.getMessage());
                }
            } else {
                mOutputText.setText("Request cancelled.");
            }
        }
    }

    /**
     * Handles data coming from TimerService.
     */
    private class myBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            timeData = convertTime(intent.getLongExtra("time", 0));
            time.setText(timeData);
        }
    }
}
