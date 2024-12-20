package app.farmgear.android.activities;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import app.farmgear.android.R;
import app.farmgear.android.utils.NumberFormatter;
import app.farmgear.android.utils.Utils;
import app.farmgear.android.api.API;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private SharedPreferences userDetails;
    private Intent dashboard;

    private EditText username_ET;
    private EditText password_ET;

    private ProgressDialog dialog;

    private RequestQueue mQueue;

    private Utils utils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        userDetails = getSharedPreferences("user_details", MODE_PRIVATE);

        if (userDetails.contains("id")) {
            dashboard = new Intent(getApplicationContext(), DashboardActivity.class);
            startActivity(dashboard);
        }

        username_ET = findViewById(R.id.username_ET);
        password_ET = findViewById(R.id.password_ET);

        Button sign_in_BT = findViewById(R.id.sign_in_BT);

        mQueue = Volley.newRequestQueue(this);

        utils = new Utils();

        sign_in_BT.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case (R.id.sign_in_BT):
                signIn(username_ET.getText().toString(), password_ET.getText().toString());
                break;
        }
    }

    private void signIn(final String username, final String password) {

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            AlertDialog failureAlert = new AlertDialog.Builder(MainActivity.this).create();
            failureAlert.setTitle("Sign in failed");
            failureAlert.setMessage("Missing credentials. Please enter your username and password before tapping the sign in button");
            failureAlert.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            failureAlert.show();
            return;
        }

        Log.d("INTERNET", String.valueOf(utils.isInternetAvailable(getApplicationContext())));

        if (utils.isInternetAvailable(getApplicationContext()) == false) {
            AlertDialog failureAlert = new AlertDialog.Builder(MainActivity.this).create();
            failureAlert.setTitle("Sign in failed");
            failureAlert.setMessage("You are not connected to internet.");
            failureAlert.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            failureAlert.show();
            return;
        }

        dialog = new ProgressDialog(this);
        dialog.setTitle("Signin in...");
        dialog.setMessage("Validating username and password");
        dialog.setCancelable(false);
        dialog.show();

        String url = new API().getApiLink() + "/authenticate";

        StringRequest request = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                dialog.dismiss();
                try {
                    JSONObject signinRes = new JSONObject(response);

                    SharedPreferences.Editor userEditor = userDetails.edit();
                    Iterator<String> signinResIter = signinRes.keys();


                    while (signinResIter.hasNext()) {
                        String key = signinResIter.next();
                        userEditor.putString(key, signinRes.getString(key));
                    }

                    userEditor.apply();

                    dashboard = new Intent(getApplicationContext(), DashboardActivity.class);
                    startActivity(dashboard);
                    Toast.makeText(getApplicationContext(), "Signed in", Toast.LENGTH_SHORT).show();

                } catch (JSONException e) {
                    e.printStackTrace();
                    e.printStackTrace();
                    AlertDialog failureAlert = new AlertDialog.Builder(MainActivity.this).create();
                    failureAlert.setTitle("Sign in failed");
                    failureAlert.setMessage(e.toString());
                    failureAlert.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    failureAlert.show();
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                dialog.dismiss();
                error.printStackTrace();
                AlertDialog failureAlert = new AlertDialog.Builder(MainActivity.this).create();
                failureAlert.setTitle("Sign in failed");
                failureAlert.setMessage(error.toString());
                failureAlert.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                failureAlert.show();
            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<String, String>();
                params.put("username", username);
                params.put("password", password);
                return params;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();
                params.put("Content-Type", "application/x-www-form-urlencoded");
                return params;
            }
        };
        request.setRetryPolicy(new DefaultRetryPolicy(25000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        mQueue.add(request);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}
