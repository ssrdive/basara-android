package app.farmgear.android.threads;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.util.Log;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.bxl.config.editor.BXLConfigLoader;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import app.farmgear.android.R;
import app.farmgear.android.api.API;
import app.farmgear.android.utils.NumberFormatter;
import app.farmgear.android.utils.Utils;
import jpos.JposConst;
import jpos.JposException;
import jpos.POSPrinter;
import jpos.POSPrinterConst;
import jpos.config.JposEntry;

public class IssueInvoiceThread implements Runnable {
    Thread thread;
    Context context;
    Handler mainHandler;
    AlertDialog printInvoiceStatus;
    ProgressDialog issueInvoiceDialog;
    TableLayout invoiceItemsTL;
    HashMap<String, String> invoiceDetails;
    Utils utils;
    SharedPreferences userDetails;
    RequestQueue mQueue;

    public IssueInvoiceThread(Context context, ProgressDialog issueInvoiceDialog, TableLayout invoiceItemsTL, HashMap<String, String> invoiceDetails) {
        this.context = context;
        this.issueInvoiceDialog = issueInvoiceDialog;
        this.invoiceItemsTL = invoiceItemsTL;
        this.invoiceDetails = invoiceDetails;
        utils = new Utils();

        thread = new Thread(this);
        thread.start();
    }

    private void showMessage(final String type, final String title, final String message) {
        issueInvoiceDialog.dismiss();
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                printInvoiceStatus = new AlertDialog.Builder(context).create();
                printInvoiceStatus.setTitle(title);
                printInvoiceStatus.setMessage(message);
                printInvoiceStatus.setIcon(context.getResources().getDrawable(type.equals("success") ? R.drawable.success_icon : R.drawable.failure_icon));
                printInvoiceStatus.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                printInvoiceStatus.show();
            }
        };
        mainHandler.post(myRunnable);
    }

    @Override
    public void run() {
        mainHandler = new Handler(context.getMainLooper());

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> bondedDeviceSet = bluetoothAdapter.getBondedDevices();

        Iterator<BluetoothDevice> btItr = bondedDeviceSet.iterator();

        boolean paired = false;
        String MAC = null;
        while (btItr.hasNext()) {
            BluetoothDevice bt = btItr.next();
            if (bt.getName().equals(BXLConfigLoader.PRODUCT_NAME_SPP_R310)) {
                paired = true;
                MAC = bt.getAddress();
            }
        }

        final String env = new API().getEnvironment();

        if (env.equals("prod") && !paired) {
            showMessage(
                    "failure",
                    "Printer not paired",
                "Your device is not paired with the printer. Please go to bluetooth settings and make sure " + BXLConfigLoader.PRODUCT_NAME_SPP_R310 + " exists under paired devices section."
            );
            return;
        }

        BXLConfigLoader bxlConfigLoader = new BXLConfigLoader(context);
        try {
            bxlConfigLoader.openFile();
        } catch (Exception e) {
            e.printStackTrace();
            bxlConfigLoader.newFile();
        }

        try {
            for (Object entry : bxlConfigLoader.getEntries()) {
                JposEntry jposEntry = (JposEntry) entry;
                bxlConfigLoader.removeEntry(jposEntry.getLogicalName());
            }
        } catch (Exception e) {
            if (env.equals("prod")) {
                e.printStackTrace();
                showMessage(
                        "failure",
                        "Something went wrong",
                        "Please contact system administrator"
                );
                return;
            }
        }

        try {
            bxlConfigLoader.addEntry(BXLConfigLoader.PRODUCT_NAME_SPP_R310,
                    BXLConfigLoader.DEVICE_CATEGORY_POS_PRINTER,
                    BXLConfigLoader.PRODUCT_NAME_SPP_R310,
                    BXLConfigLoader.DEVICE_BUS_BLUETOOTH,
                    MAC);
            bxlConfigLoader.saveFile();
        } catch (Exception e) {
            if (env.equals("prod")) {
                e.printStackTrace();
                showMessage(
                        "failure",
                        "Something went wrong",
                        "Please contact system administrator"
                );
                return;
            }
        }

        userDetails = context.getSharedPreferences("user_details", context.MODE_PRIVATE);
        if (utils.isInternetAvailable(context)) {
            mQueue = Volley.newRequestQueue(context);
            final String url =  new API().getApiLink() + "/transaction/invoice";
            final String requestID = Utils.generateRequestID(invoiceDetails.get("customer_no"));
            StringRequest planRequest = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    try {
                        if (env.equals("prod")) {
                            POSPrinter posPrinter = new POSPrinter(context);
                            posPrinter.open(BXLConfigLoader.PRODUCT_NAME_SPP_R310);
                            posPrinter.claim(5000);
                            posPrinter.setDeviceEnabled(true);
                            posPrinter.checkHealth(JposConst.JPOS_CH_INTERNAL);
                            ByteBuffer buffer = ByteBuffer.allocate(4);
                            buffer.put((byte) POSPrinterConst.PTR_S_RECEIPT);
                            buffer.put((byte) 80);
                            buffer.put((byte) 0x01);
                            buffer.put((byte) 0x00);
                            Bitmap logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.logo_print);

                            String address = "\nFarmGear (Private) Limited\n" +
                                    "No 67/A, Sirisangabo Place, Polonnaruwa\n " +
                                    "027 222 7788\n";

                            posPrinter.printBitmap(buffer.getInt(0), logo, 300, POSPrinterConst.PTR_BM_CENTER);
                            String ESCAPE_CHARACTERS = new String(new byte[]{0x1b, 0x7c});
                            posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESCAPE_CHARACTERS + "N" + ESCAPE_CHARACTERS + "cA" + address);
                            posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESCAPE_CHARACTERS + "lA" + ESCAPE_CHARACTERS + "N"
                                    + "________________________________________________\n\n");
                            posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESCAPE_CHARACTERS + "bC" + ESCAPE_CHARACTERS + "cA" + "CASH INVOICE" + "\n\n");
                            posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESCAPE_CHARACTERS + "lA" + ESCAPE_CHARACTERS + "N"
                                    + "Date  : " + java.text.DateFormat.getDateTimeInstance().format(new Date()) + "\n");
                            posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESCAPE_CHARACTERS + "lA" + ESCAPE_CHARACTERS + "N"
                                    + "Issuer: " + invoiceDetails.get("issuer") + "\n\n");

                            posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESCAPE_CHARACTERS + "lA" + ESCAPE_CHARACTERS + "N"
                                    + "Customer : " + invoiceDetails.get("customer_name") + "\n");
                            posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESCAPE_CHARACTERS + "lA" + ESCAPE_CHARACTERS + "N"
                                    + "Contact  : " + invoiceDetails.get("customer_no") + "\n\n");

                            for(int i = 1; i < invoiceItemsTL.getChildCount(); i++) {
                                TableRow invoiceRow = (TableRow) invoiceItemsTL.getChildAt(i);

                                TextView itemIDTV = (TextView) invoiceRow.getChildAt(1);
                                TextView unitPriceTV = (TextView) invoiceRow.getChildAt(2);
                                TextView quantityTV = (TextView) invoiceRow.getChildAt(3);
                                TextView priceTV = (TextView) invoiceRow.getChildAt(4);
                                TextView itemName = (TextView) invoiceRow.getChildAt(5);

                                posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESCAPE_CHARACTERS + "lA" + ESCAPE_CHARACTERS + "N"
                                        + itemIDTV.getText().toString() +  "\n");
                                posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESCAPE_CHARACTERS + "lA" + ESCAPE_CHARACTERS + "N"
                                        + itemName.getText().toString() + "\n");
                                posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESCAPE_CHARACTERS + "lA" + ESCAPE_CHARACTERS + "N"
                                        + unitPriceTV.getText().toString() + "\tX\t"
                                        + quantityTV.getText().toString() + "\t"
                                        + priceTV.getText().toString() + "\n\n");
                            }

                            NumberFormatter numberFormatter = new NumberFormatter();
                            posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESCAPE_CHARACTERS + "lA" + ESCAPE_CHARACTERS + "N"
                                    + "Price    : " + numberFormatter.format(invoiceDetails.get("price_before_discount")) + "\n");
                            posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESCAPE_CHARACTERS + "lA" + ESCAPE_CHARACTERS + "N"
                                    + "Discount : " + (int) Double.parseDouble(invoiceDetails.get("discount")) + " %" + "\n\n");
                            posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESCAPE_CHARACTERS + "4C" + ESCAPE_CHARACTERS
                                    + "cA" + "RS " + numberFormatter.format(invoiceDetails.get("price_after_discount")) + "\n\n");
                            posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, ESCAPE_CHARACTERS + "N" + ESCAPE_CHARACTERS + "cA"
                                    + "We thank you for your purchase!\n\n");

                            ByteBuffer buffer2 = ByteBuffer.allocate(4);
                            buffer2.put((byte) POSPrinterConst.PTR_S_RECEIPT);
                            buffer2.put((byte) 20);
                            buffer2.put((byte) 0x01);
                            buffer2.put((byte) 0x00);
                            posPrinter.printNormal(POSPrinterConst.PTR_S_RECEIPT, "\n\n\n");
                            posPrinter.close();
                        }
                    } catch (JposException e) {
                        e.printStackTrace();
                        showMessage(
                                "failure",
                                "Printing error",
                                "Please check the following details\n" +
                                        "1. Printer is turned on\n" +
                                        "2. Printer is within 1m range\n" +
                                        "3. Paper roll not empty\n" +
                                        "4. Paper cover closed"
                        );
                        return;
                    }

                    StringRequest executeRequest = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            showMessage(
                                    "success",
                                    "Success",
                                    "Invoice issued"
                            );
                        }
                    }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            showMessage(
                                    "failed",
                                    "Failed",
                                    "Failed to issue invoice"
                            );
                            error.printStackTrace();
                        }
                    }) {
                        @Override
                        protected Map<String, String> getParams() {
                            Map<String, String> params = new HashMap<String, String>();
                            params.put("execution_type", "apply");
                            params.put("user_id", userDetails.getString("id", ""));
                            params.put("from_warehouse", userDetails.getString("warehouse_id", ""));
                            params.put("customer_contact", invoiceDetails.get("customer_no"));
                            params.put("customer_name", invoiceDetails.get("customer_name"));
                            params.put("discount", invoiceDetails.get("discount"));
                            params.put("items", invoiceDetails.get("items"));
                            params.put("request_id", requestID+"A");

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
                    executeRequest.setRetryPolicy(new DefaultRetryPolicy(25000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
                    mQueue.add(executeRequest);
                }

            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    showMessage(
                            "failed",
                            "Failed",
                            "Failed to issue invoice"
                    );
                    error.printStackTrace();
                }
            }) {
                @Override
                protected Map<String, String> getParams() {
                    Map<String, String> params = new HashMap<String, String>();
                    params.put("execution_type", "plan");
                    params.put("user_id", userDetails.getString("id", ""));
                    params.put("from_warehouse", userDetails.getString("warehouse_id", ""));
                    params.put("customer_contact", invoiceDetails.get("customer_no"));
                    params.put("customer_name", invoiceDetails.get("customer_name"));
                    params.put("discount", invoiceDetails.get("discount"));
                    params.put("items", invoiceDetails.get("items"));
                    params.put("request_id", requestID+"P");

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
            planRequest.setRetryPolicy(new DefaultRetryPolicy(25000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            mQueue.add(planRequest);
        }
    }
}
