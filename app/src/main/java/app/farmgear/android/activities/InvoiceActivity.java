package app.farmgear.android.activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import app.farmgear.android.R;
import app.farmgear.android.api.API;
import app.farmgear.android.threads.IssueInvoiceThread;
import app.farmgear.android.utils.NumberFormatter;
import app.farmgear.android.utils.Utils;

public class InvoiceActivity extends AppCompatActivity implements View.OnClickListener {

    private Button scanItemBtn;

    private ArrayList<String> invoiceItems;
    private TableLayout invoiceItemsTL;
    private TextView invoiceTotalTV;
    private EditText itemIDET;
    private Button addBtn;
    private EditText numberET;
    private EditText discountET;
    private EditText customerNameET;
    private TextView invoicingLocationTV;
    private Button issueInvoiceBtn;

    AlertDialog issueInvoiceStatus;

    Context context;

    private ProgressDialog loadItemDetailsDialog;
    private ProgressDialog issueInvoiceDialog;

    private RequestQueue mQueue;
    private SharedPreferences userDetails;
    private Utils utils;

    private NumberFormatter formatter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice);

        scanItemBtn = findViewById(R.id.scanItemBtn);
        invoiceItemsTL = findViewById(R.id.invoiceItemsTL);
        invoiceTotalTV = findViewById(R.id.invoiceTotalTV);
        itemIDET = findViewById(R.id.itemIDET);
        addBtn = findViewById(R.id.addBtn);
        numberET = findViewById(R.id.numberET);
        customerNameET = findViewById(R.id.customerNameET);
        discountET = findViewById(R.id.discountET);
        invoicingLocationTV = findViewById(R.id.invoicingLocationTV);
        issueInvoiceBtn = findViewById(R.id.issueInvoiceBtn);

        formatter = new NumberFormatter();

        mQueue = Volley.newRequestQueue(getApplicationContext());
        userDetails = getApplication().getSharedPreferences("user_details", getApplicationContext().MODE_PRIVATE);
        utils = new Utils();

        context = InvoiceActivity.this;

        scanItemBtn.setOnClickListener(this);
        addBtn.setOnClickListener(this);
        issueInvoiceBtn.setOnClickListener(this);

        discountET.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateInvoicePrice(getInvoicePrice()[0]);
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        invoiceItems = new ArrayList<>();

        setInvoicingLocation();
    }

    private void showMessage(final String type, final String title, final String message) {
        issueInvoiceDialog.dismiss();
        issueInvoiceStatus = new AlertDialog.Builder(context).create();
        issueInvoiceStatus.setTitle(title);
        issueInvoiceStatus.setMessage(message);
        issueInvoiceStatus.setIcon(context.getResources().getDrawable(type.equals("success") ? R.drawable.success_icon : R.drawable.failure_icon));
        issueInvoiceStatus.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        issueInvoiceStatus.show();
    }

    private void setInvoicingLocation() {
        invoicingLocationTV.setText(userDetails.getString("warehouse_name", ""));
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.scanItemBtn:
                scanCode();
                break;
            case R.id.addBtn:
                addInvoiceItem(itemIDET.getText().toString().toUpperCase());
                break;
            case R.id.issueInvoiceBtn:
                issueInvoiceDialog = new ProgressDialog(InvoiceActivity.this);
                issueInvoiceDialog.setTitle("Issuing Invoice");
                issueInvoiceDialog.setMessage("Please wait while the invoice is being issued");
                issueInvoiceDialog.setCancelable(false);
                issueInvoiceDialog.show();

                if (invoiceItemsTL.getChildCount() == 1) {
                    showMessage("failure", "Validation failure", "No items in invoice");
                    return;
                }
                if (numberET.getText().toString().length() != 9) {
                    showMessage("failure", "Validation failure", "Invalid contact number");
                    return;
                }
                if (customerNameET.getText().toString().length() == 0) {
                    showMessage("failure", "Validation failure", "Invalid customer name");
                    return;
                }
                if(!utils.isInternetAvailable(context)) {
                    showMessage("failure", "Connectivity issue", "Unable to communicate with the system. Make sure you are connected to the internet");
                    return;
                }

                final JSONArray invoiceItems = getInvoiceItems();
                if (invoiceItems == null) {
                    return;
                }

                HashMap<String, String> invoiceDetails = new HashMap<String, String>();
                invoiceDetails.put("issuer", userDetails.getString("name", ""));
                float prices[] = getInvoicePrice();
                invoiceDetails.put("price_after_discount", String.valueOf(prices[0]));
                invoiceDetails.put("customer_no", numberET.getText().toString());
                invoiceDetails.put("customer_name", customerNameET.getText().toString());
                invoiceDetails.put("discount", String.valueOf(prices[1]));
                invoiceDetails.put("price_before_discount", String.valueOf(prices[2]));
                invoiceDetails.put("items", invoiceItems.toString());

                IssueInvoiceThread issueInvoiceThread = new IssueInvoiceThread(context, issueInvoiceDialog, invoiceItemsTL, invoiceDetails);
                break;
        }
    }

    private JSONArray getInvoiceItems() {
        JSONArray invoiceArray = new JSONArray();

        if(invoiceItemsTL.getChildCount() == 1) {
            return invoiceArray;
        }

        for(int i = 1; i < invoiceItemsTL.getChildCount(); i++) {
            TableRow invoiceRow = (TableRow) invoiceItemsTL.getChildAt(i);

            JSONObject invoiceObj = new JSONObject();

            TextView itemIDTV = (TextView) invoiceRow.getChildAt(6);
            TextView quantityTV = (TextView) invoiceRow.getChildAt(3);

            try {
                invoiceObj.put("item_id", itemIDTV.getText().toString());

                int quantity = 0;

                if (quantityTV.getText().toString().length() != 0)
                    quantity = Integer.parseInt(quantityTV.getText().toString());

                if (quantity == 0) {
                    showMessage("failure", "Validation failure", "Empty items in invoice. Please double check");
                    return null;
                }

                invoiceObj.put("qty", String.valueOf(quantity));
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }

            invoiceArray.put(invoiceObj);
        }

        return invoiceArray;
    }

    private void scanCode() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setCaptureActivity(CaptureAct.class);
        integrator.setOrientationLocked(false);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("Scanning QR Code");
        integrator.initiateScan();
    }

    private void addInvoiceItem(final String itemID) {
        if (invoiceItems.contains(itemID)) {
            Toast.makeText(this, "Item already present in invoice", Toast.LENGTH_LONG).show();
            return;
        }

        loadItemDetailsDialog = new ProgressDialog(InvoiceActivity.this);
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

                    final TableRow invoiceItem = new TableRow(context);

                    Button removeItemBtn = new Button(context);
                    removeItemBtn.setText("X");

                    removeItemBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            invoiceItemsTL.removeView(invoiceItem);
                            invoiceItem.removeAllViews();
                            invoiceItems.remove(itemID);
                            updateInvoicePrice(getInvoicePrice()[0]);
                        }
                    });

                    TextView itemSystemIDTV = new TextView(context);
                    itemSystemIDTV.setText(itemObj.getString("id"));
                    itemSystemIDTV.setVisibility(View.INVISIBLE);

                    TextView itemIDTV = new TextView(context);
                    itemIDTV.setPadding(10, 0, 0, 0);
                    itemIDTV.setText(itemID);

                    TextView unitPriceTV = new TextView(context);
                    unitPriceTV.setPadding(10, 0, 0, 0);
                    unitPriceTV.setText(formatter.format(itemObj.getString("price")));

                    EditText quantityET = new EditText(context);
                    quantityET.setText("0");
                    quantityET.setInputType(InputType.TYPE_CLASS_NUMBER);

                    TextView priceTV = new TextView(context);
                    priceTV.setPadding(10, 0, 0, 0);
                    priceTV.setText("0");

                    TextView itemNameTV = new TextView(context);
                    itemNameTV.setPadding(10, 0, 0, 0);
                    itemNameTV.setText(itemObj.getString("name"));

                    quantityET.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                            TextView price = (TextView) invoiceItem.getChildAt(4);
                            if (s.length() != 0) {
                                TextView unitPriceTV = (TextView) invoiceItem.getChildAt(2);

                                float quantity = Float.parseFloat(s.toString());
                                float unitPrice = Float.parseFloat(unitPriceTV.getText().toString().replaceAll("[^\\d.]", ""));

                                price.setText(formatter.format(String.valueOf(quantity * unitPrice)));
                            } else {
                                price.setText("0");
                            }
                            updateInvoicePrice(getInvoicePrice()[0]);
                        }

                        @Override
                        public void afterTextChanged(Editable s) {

                        }
                    });

                    invoiceItem.addView(removeItemBtn);
                    invoiceItem.addView(itemIDTV);
                    invoiceItem.addView(unitPriceTV);
                    invoiceItem.addView(quantityET);
                    invoiceItem.addView(priceTV);
                    invoiceItem.addView(itemNameTV);
                    invoiceItem.addView(itemSystemIDTV);

                    invoiceItems.add(itemID);

                    invoiceItemsTL.addView(invoiceItem);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                loadItemDetailsDialog.dismiss();
                error.printStackTrace();
                Toast.makeText(getApplicationContext(), "Invalid Item ID", Toast.LENGTH_LONG).show();
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

    private float[] getInvoicePrice() {
        float[] prices = {0, 0, 0};
        prices[0] = 0;
        prices[1] = 0;
        if (discountET.getText().toString().length() != 0)
            prices[1] = Float.parseFloat(discountET.getText().toString());

        for (int i = 1; i < invoiceItemsTL.getChildCount(); i++) {
            TableRow tableRow = (TableRow) invoiceItemsTL.getChildAt(i);
            TextView rowPrice = (TextView) tableRow.getChildAt(4);

            prices[0] += Float.parseFloat(rowPrice.getText().toString().replaceAll("[^\\d.]", ""));
        }

        prices[2] = prices[0];
        if (prices[1] != 0)
            prices[0] = prices[0] * (100 - prices[1]) / 100;

        return prices;
    }

    private void updateInvoicePrice(float price) {
        invoiceTotalTV.setText("Rs. " + formatter.format(price));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if(result != null) {
            if(result.getContents() != null) {
                addInvoiceItem(result.getContents());
            } else {
                Toast.makeText(this, "No Result", Toast.LENGTH_LONG).show();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}