package app.farmgear.android.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import app.farmgear.android.R;
import app.farmgear.android.api.API;
import app.farmgear.android.utils.RowAdder;
import app.farmgear.android.utils.Utils;

public class PendingTransferDetailsActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView documentID;
    private TextView created;
    private TextView fromWarehouse;
    private TextView toWarehouse;

    private EditText remarksET;

    private Button approveBtn;
    private Button provisionalBtn;
    private Button rejectBtn;

    private SharedPreferences userDetails;
    Utils utils;
    private ProgressDialog loadPendingTransfers;
    private ProgressDialog sendInventoryTransferAction;
    private RequestQueue mQueue;
    TableLayout transferItemsTable;
    Context context;

    private Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_transfer_details);

        intent = getIntent();
        context = getApplicationContext();
        utils = new Utils();

        userDetails = getApplication().getSharedPreferences("user_details", context.MODE_PRIVATE);

        mQueue = Volley.newRequestQueue(context);

        documentID = findViewById(R.id.documentID);
        created = findViewById(R.id.created);
        fromWarehouse = findViewById(R.id.fromWarehouse);
        toWarehouse = findViewById(R.id.toWarehouse);
        remarksET = findViewById(R.id.remarksET);
        approveBtn = findViewById(R.id.approveBtn);
        provisionalBtn = findViewById(R.id.provisionalBtn);
        rejectBtn = findViewById(R.id.rejectBtn);
        transferItemsTable = findViewById(R.id.transferItems_TL);

        approveBtn.setOnClickListener(this);
        provisionalBtn.setOnClickListener(this);
        rejectBtn.setOnClickListener(this);

        setDocumentDetails();
        loadTransferItems();
    }

    void setDocumentDetails() {
        documentID.setText(intent.getStringExtra("id"));
        created.setText(intent.getStringExtra("created"));
        fromWarehouse.setText(intent.getStringExtra("from"));
        toWarehouse.setText(intent.getStringExtra("to"));
    }

    private void loadTransferItems() {
        if(utils.isInternetAvailable(context)) {
            loadPendingTransfers = new ProgressDialog(PendingTransferDetailsActivity.this);
            loadPendingTransfers.setTitle("Loading transfer items");
            loadPendingTransfers.setMessage("Please wait while pending transfer items are loaded");
            loadPendingTransfers.setCancelable(false);
            loadPendingTransfers.show();
            String url =  new API().getApiLink() + "/transaction/inventorytransferitems/" + intent.getStringExtra("id");
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
                            rowAdder.pendingTransfer(transferItemsTable, contractMap);
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

            mQueue.add(request);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case (R.id.approveBtn):
                inventoryTransferAction("Approved");
                break;
            case (R.id.provisionalBtn):
                inventoryTransferAction("Provisional");
            case (R.id.rejectBtn):
                inventoryTransferAction("Rejected");
                break;
        }
    }

    private void inventoryTransferAction(final String action) {
        if(utils.isInternetAvailable(context)) {
            sendInventoryTransferAction = new ProgressDialog(PendingTransferDetailsActivity.this);
            sendInventoryTransferAction.setTitle("Sending Action");
            sendInventoryTransferAction.setMessage("Please wait while the action is being processed");
            sendInventoryTransferAction.setCancelable(false);
            sendInventoryTransferAction.show();
            String url =  new API().getApiLink() + "/transaction/inventorytransferaction";
            StringRequest request = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    sendInventoryTransferAction.dismiss();
                    Toast.makeText(context, "SUCCESS: Action processed", Toast.LENGTH_LONG).show();
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    sendInventoryTransferAction.dismiss();
                    Toast.makeText(context, "Failed: Failed to process action", Toast.LENGTH_LONG).show();
                    error.printStackTrace();
                }
            }) {
                @Override
                protected Map<String, String> getParams() {
                    Map<String, String> params = new HashMap<String, String>();
                    params.put("inventory_transfer_id", intent.getStringExtra("id"));
                    params.put("user_id", userDetails.getString("id", ""));
                    params.put("resolution", action);
                    params.put("resolution_remarks", remarksET.getText().toString());
                    return params;
                }

                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> params = new HashMap<String, String>();
                    params.put("Authorization", "Bearer " + userDetails.getString("token", ""));
                    params.put("Content-Type", "application/x-www-form-urlencoded");
                    return params;
                }
            };

            mQueue.add(request);
        }
    }
}