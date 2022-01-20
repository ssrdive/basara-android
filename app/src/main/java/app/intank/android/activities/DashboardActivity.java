package app.intank.android.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import app.intank.android.R;
import app.intank.android.api.API;
import app.intank.android.utils.NumberFormatter;
import app.intank.android.utils.Utils;

public class DashboardActivity extends AppCompatActivity implements View.OnClickListener {

    Button scanItemBtn;
    Button signOutBtn;

    TextView itemID;
    TextView modelID;
    TextView modelName;
    TextView categoryID;
    TextView categoryName;
    TextView pageNo;
    TextView itemNo;
    TextView foreignID;
    TextView itemName;
    TextView price;

    private RequestQueue mQueue;
    private SharedPreferences userDetails;
    private Utils utils;

    private ProgressDialog loadItemDetailsDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        mQueue = Volley.newRequestQueue(getApplicationContext());
        userDetails = getApplication().getSharedPreferences("user_details", getApplicationContext().MODE_PRIVATE);
        utils = new Utils();

        scanItemBtn = findViewById(R.id.scanItemBtn);
        signOutBtn = findViewById(R.id.signOutBtn);

        itemID = findViewById(R.id.itemID);
        modelID = findViewById(R.id.modelID);
        modelName = findViewById(R.id.modelName);
        categoryID = findViewById(R.id.categoryID);
        categoryName = findViewById(R.id.categoryName);
        pageNo = findViewById(R.id.pageNo);
        itemNo = findViewById(R.id.itemNo);
        foreignID = findViewById(R.id.foreignID);
        itemName = findViewById(R.id.itemName);
        price = findViewById(R.id.price);

        scanItemBtn.setOnClickListener(this);
        signOutBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.scanItemBtn:
                scanCode();
                break;
            case R.id.signOutBtn:
                signOut();
                break;
        }
    }

    private void scanCode() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setCaptureActivity(CaptureAct.class);
        integrator.setOrientationLocked(false);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
        integrator.setPrompt("Scanning QR Code");
        integrator.initiateScan();
    }

    private void updateItemDetails(final String itemID) {
        this.itemID.setText(itemID);

        if(utils.isInternetAvailable(this)) {
            loadItemDetailsDialog = new ProgressDialog(DashboardActivity.this);
            loadItemDetailsDialog.setTitle("Loading Item Details");
            loadItemDetailsDialog.setMessage("Please wait while item details are loaded");
            loadItemDetailsDialog.setCancelable(false);
            loadItemDetailsDialog.show();

            String url = new API().getApiLink() + "/item/" + itemID;
            StringRequest request = new StringRequest(Request.Method.GET, url, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    loadItemDetailsDialog.dismiss();
                    try {
                        JSONObject itemObj = new JSONObject(response);
                        NumberFormatter formatter = new NumberFormatter();

                        modelID.setText(itemObj.getString("model_id"));
                        modelName.setText(itemObj.getString("model_name"));
                        categoryID.setText(itemObj.getString("item_category_id"));
                        categoryName.setText(itemObj.getString("item_category_name"));
                        pageNo.setText(itemObj.getString("page_no"));
                        itemNo.setText(itemObj.getString("item_no"));
                        foreignID.setText(itemObj.getString("foreign_id"));
                        itemName.setText(itemObj.getString("item_name"));
                        price.setText("Rs " + formatter.format(itemObj.getString("price")));

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    loadItemDetailsDialog.dismiss();
                    error.printStackTrace();
                    signOut();
                }
            }) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> params = new HashMap<String, String>();
                    params.put("Authorization", "Bearer " + userDetails.getString("token", ""));
                    params.put("Content-Type", "application/x-www-form-urlencoded");
                    return params;
                }
            };
            request.setRetryPolicy(new DefaultRetryPolicy(10000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            mQueue.add(request);
        } else {
            Toast.makeText(this, "You are offline", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if(result != null) {
            if(result.getContents() != null) {
                updateItemDetails(result.getContents());
            } else {
                Toast.makeText(this, "No Result", Toast.LENGTH_LONG).show();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void signOut() {
        SharedPreferences.Editor userEditor = userDetails.edit();
        userEditor.clear();
        userEditor.commit();

        Intent mainActivity = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(mainActivity);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}
