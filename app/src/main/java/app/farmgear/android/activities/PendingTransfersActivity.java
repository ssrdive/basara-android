package app.farmgear.android.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.TableLayout;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import app.farmgear.android.R;
import app.farmgear.android.api.API;
import app.farmgear.android.utils.RowAdder;
import app.farmgear.android.utils.Utils;

public class PendingTransfersActivity extends AppCompatActivity {

    private SharedPreferences userDetails;
    private TextView accessLevelTV;
    Utils utils;
    private ProgressDialog loadPendingTransfers;
    private RequestQueue mQueue;
    TableLayout pendingTransfersTable;
    Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_transfers);

        context = getApplicationContext();

        utils = new Utils();

        mQueue = Volley.newRequestQueue(context);
        userDetails = getApplication().getSharedPreferences("user_details", context.MODE_PRIVATE);

        accessLevelTV = findViewById(R.id.accessLevelTV);
        pendingTransfersTable = findViewById(R.id.pendingTransfers_TL);

        if (userDetails.getString("role", "").equals("Admin")) {
            accessLevelTV.setText("Your access level is set to ADMIN. You are seeing all pending transfers");
        } else {
            accessLevelTV.setText("Your access level is set to a specific warehouse. You are seeing all pending transfers relevant to your warehouse");
        }

        loadPendingTransfers();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        loadPendingTransfers();
    }

    private void loadPendingTransfers() {
        while (pendingTransfersTable.getChildCount() > 1)
            pendingTransfersTable.removeView(pendingTransfersTable.getChildAt(pendingTransfersTable.getChildCount() - 1));

        if(utils.isInternetAvailable(context)) {
            loadPendingTransfers = new ProgressDialog(PendingTransfersActivity.this);
            loadPendingTransfers.setTitle("Loading pending transfers");
            loadPendingTransfers.setMessage("Please wait while pending transfers are loaded");
            loadPendingTransfers.setCancelable(false);
            loadPendingTransfers.show();
            String url =  new API().getApiLink() + "/transaction/inventorytransfer/" + userDetails.getString("role", "") +"/" + userDetails.getString("warehouse_id", "");
            StringRequest request = new StringRequest(Request.Method.GET, url, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    try {
                        JSONArray installments = new JSONArray(response);
                        RowAdder rowAdder = new RowAdder(context);
                        for (int i = 0; i < installments.length(); i++) {
                            JSONObject installment = installments.getJSONObject(i);
                            Iterator<String> installmentIterator = installment.keys();
                            String tableExp[] = {};
                            final LinkedHashMap<String, String> contractMap = new LinkedHashMap<String, String>();
                            while (installmentIterator.hasNext()) {
                                String key = installmentIterator.next();
                                if (!Arrays.asList(tableExp).contains(key)) {
                                    contractMap.put(key, installment.getString(key));
                                }
                            }
                            rowAdder.pendingTransfer(pendingTransfersTable, contractMap);
                        }
                        loadPendingTransfers.dismiss();
                    } catch (JSONException e) {
                        loadPendingTransfers.dismiss();
                        e.printStackTrace();
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    loadPendingTransfers.dismiss();
                    error.printStackTrace();
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
            request.setRetryPolicy(new DefaultRetryPolicy(25000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            mQueue.add(request);
        }
    }
}