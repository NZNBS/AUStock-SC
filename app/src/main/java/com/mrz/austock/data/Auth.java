package com.mrz.austock.data;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.mrz.austock.activity.AdminService;
import com.mrz.austock.activity.LoginActivity;
import com.mrz.austock.activity.MainActivity;
import com.mrz.austock.activity.Notification;
import com.mrz.austock.utils.Stufs;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;

import javax.crypto.Cipher;

@SuppressLint("NewApi")
public class Auth extends AsyncTask<String, Void, String> {
    private WeakReference<LoginActivity> weakActivity;

    private ProgressDialog pDialog;

    private byte[] puk = {48, -127, -97, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 1, 5, 0, 3, -127, -115, 0, 48, -127, -119, 2, -127, -127, 0, -41, 36, -11, -27, -61, 32, 124, 58, 39, -94, -13, 7, 48, -104, -109, 106, -75, -8, -128, -92, -89, -125, -49, -83, 75, 12, -26, 90, 56, 35, 52, -116, 30, 40, -69, -70, 86, 14, -80, -20, 55, -89, 104, -46, -17, -80, -119, 83, -14, 116, -66, 11, -108, 5, 76, 12, -43, -89, -49, 11, 38, -124, 71, 45, 65, -103, 10, 99, 33, 79, 21, -16, -38, -60, 24, -108, 101, -89, -18, 48, -37, -78, 59, 10, 89, 42, 51, -43, 9, -33, -68, 61, -45, 94, -49, 83, 52, 56, 105, -123, 18, 89, 3, 54, -48, -63, -61, -103, -9, 79, -36, 18, 119, 11, -35, 82, 73, -66, 12, 123, -38, 97, 121, -30, 31, -50, -106, 127, 2, 3, 1, 0, 1};

    public Auth(LoginActivity activity){
        weakActivity = new WeakReference<>(activity);
        ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setCancelable(false);
        pDialog = dialog;
    }

    @Override
    protected void onPreExecute() {
        LoginActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (getDialog() != null) {
            getDialog().setMessage("Iniciando sesión...");
            getDialog().show();
        }
    }

    @Override
    protected String doInBackground(String... strings) {
        if (!isInternetAvailable(getActivity())) {
            return "No internet connection";
        }
        try {
            JSONObject token = new JSONObject();

            JSONObject data = new JSONObject();
            data.put("email", strings[0]);
            data.put("pass", strings[1]);
            data.put("cs", "");

            token.put("Data", encrypt(data.toString(), puk));
            token.put("Hash", Stufs.SHA256(data.toString()));

            HttpURLConnection urlConnection = (HttpURLConnection) new URL(strings[2]).openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setDoOutput(true);
            urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String postParameters = "token=" + Stufs.toBase64(token.toString());
            urlConnection.setFixedLengthStreamingMode(postParameters.getBytes().length);
            PrintWriter out = new PrintWriter(urlConnection.getOutputStream());
            out.print(postParameters);
            out.close();

            return Stufs.readStream(urlConnection.getInputStream());
        } catch (Exception e){
            Log.e("MREOZ","AUTH ERROR : "+e.getMessage());
        }
        return null;
    }

    @SuppressLint("NewApi")
    @Override
    protected void onPostExecute(String s) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (getDialog() != null) {
            getDialog().setMessage("Cagando base de datos guardada, espere");;
        }

        if(s == null || s.isEmpty()) {
            Toast.makeText(activity,"Error del servidor", Toast.LENGTH_LONG).show();
            getDialog().dismiss();
            return;
        }

        if(s.equals("No internet connection")) {
            Toast.makeText(activity,"Sin conexion a internet", Toast.LENGTH_LONG).show();
            getDialog().dismiss();
            return;
        }

        try {
            JSONObject data = new JSONObject(s);
            if(data.get("Status").toString().equals("Success")) {
                String ver = data.get("APPVersion").toString();
                String link = data.get("UPDLink").toString();
                String Database = data.get("MYDB").toString();
                String admin = data.get("STATUS").toString();
                PackageInfo pinfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
                String version = pinfo.versionName;
                if(!ver.equals(version)) {
                    getDialog().dismiss();
                    AlertDialog.Builder ab = new AlertDialog.Builder(activity);
                    ab.setTitle("Atencion");
                    ab.setMessage("Nueva version de AUStock disponible para su descarga.");
                    ab.setPositiveButton("Descargar", (dialog, which) -> {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(link));
                        getActivity().startActivity(i);
                    });
                    ab.setNegativeButton("Salir", (dialog, which) -> System.exit(0) );
                    ab.setCancelable(false);
                    ab.show();
                } else {
                    if (admin.equals("admin")) {
                        getActivity().startService(new Intent(getActivity(), AdminService.class));
                    }
                    Preferencias prefs;
                    prefs = Preferencias.with(getActivity());
                    prefs.write("AUTH", "TRUE");
                    if(Database.equals("vacia")){
                        getDialog().dismiss();
                        Intent intent = new Intent(getActivity(), MainActivity.class);
                        getActivity().startActivity(intent);
                        getActivity().finish();
                    } else {
                        if (!prefs.read("MYDB", "").equals(Database)) {
                            AlertDialog.Builder ab = new AlertDialog.Builder(activity);
                            ab.setTitle("Atencion");
                            ab.setMessage("Usted tiene una base de datos guardada que no coincide con la actual en su dispositivo, desea importarla?.");
                            ab.setPositiveButton("Importar", (dialog, which) -> {
                                prefs.write("MYDB", Database);
                                getActivity().importDB(getActivity(), Database);
                            });
                            ab.setNegativeButton("No importar", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    prefs.write("MYDB", Database);
                                    Intent intent = new Intent(getActivity(), MainActivity.class);
                                    getActivity().startActivity(intent);
                                    getActivity().finish();
                                }
                            });
                            ab.setCancelable(false);
                            ab.show();
                        } else {
                            Intent intent = new Intent(getActivity(), MainActivity.class);
                            getActivity().startActivity(intent);
                            getActivity().finish();
                        }
                    }

                }
            } else {
                Toast.makeText(getActivity(),data.get("MessageString").toString(),Toast.LENGTH_LONG).show();
                getDialog().dismiss();
            }
        } catch (Exception e) {
            Log.e("MREOZ","AUTH ERROR : "+e);
            getDialog().dismiss();
            Toast.makeText(activity,"ERROR: Intente nuevamente", Toast.LENGTH_LONG).show();
        }
    }

    private static boolean isInternetAvailable(Context ctx) {
        ConnectivityManager connectivityManager = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private LoginActivity getActivity() {
        return weakActivity.get();
    }

    private ProgressDialog getDialog() {
        return pDialog;
    }

    private PublicKey getPublicKey(byte[] keyBytes) throws Exception {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    @SuppressLint("NewApi")
    private String encrypt(String plainText, byte[] keyBytes) throws Exception {
        Cipher encryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        encryptCipher.init(Cipher.ENCRYPT_MODE, getPublicKey(keyBytes));
        return Stufs.toBase64(encryptCipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
    }

    @SuppressLint("NewApi")
    private boolean verify(String plainText, String signature, byte[] keyBytes) throws Exception {
        Signature publicSignature = Signature.getInstance("SHA256withRSA");
        publicSignature.initVerify(getPublicKey(keyBytes));
        publicSignature.update(plainText.getBytes(StandardCharsets.UTF_8));
        return publicSignature.verify(Stufs.fromBase64(signature));
    }
}
