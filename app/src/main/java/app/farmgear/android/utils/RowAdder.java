package app.farmgear.android.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import app.farmgear.android.R;
import app.farmgear.android.activities.PendingTransferDetailsActivity;

public class RowAdder {
    Context context;

    public RowAdder(Context context) {
        this.context = context;
    }

    public void itemStockLine(TableLayout table, final LinkedHashMap<String, String> pendingTransfer) {
        TableRow contractRow = new TableRow(context);
        contractRow.setBackgroundColor(Color.parseColor("#f2eded"));
        contractRow.setPadding(5, 5, 5, 5);

        Iterator contractIterator = pendingTransfer.entrySet().iterator();
        while (contractIterator.hasNext()) {
            Map.Entry contractColumn = (Map.Entry) contractIterator.next();
            Button btn = new Button(context);

            TextView textView = new TextView(context);
            String columnValue = contractColumn.getValue().toString();

            textView.setText(columnValue);
            textView.setPadding(10, 10, 10, 10);
            contractRow.addView(textView);
        }

        table.addView(contractRow);
    }

    public void pendingTransfer(TableLayout table, final LinkedHashMap<String, String> pendingTransfer) {
        TableRow contractRow = new TableRow(context);
        contractRow.setBackgroundColor(Color.parseColor("#f2eded"));
        contractRow.setPadding(5, 5, 5, 5);

        Iterator contractIterator = pendingTransfer.entrySet().iterator();
        while (contractIterator.hasNext()) {
            Map.Entry contractColumn = (Map.Entry) contractIterator.next();
            Button btn = new Button(context);

            TextView textView = new TextView(context);
            String columnValue = contractColumn.getValue().toString();

            if (contractColumn.getKey().equals("id")) {
                btn.setClickable(true);
                btn.setText(columnValue);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(context, PendingTransferDetailsActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("id", pendingTransfer.get("id"));
                        intent.putExtra("created", pendingTransfer.get("created"));
                        intent.putExtra("from", pendingTransfer.get("from"));
                        intent.putExtra("to", pendingTransfer.get("to"));
                        context.startActivity(intent);
                    }
                });
            }

            if (contractColumn.getKey().equals("id")) {
                contractRow.addView(btn);
            } else {
                textView.setText(columnValue);
                textView.setPadding(10, 10, 10, 10);
                contractRow.addView(textView);
            }
        }

        table.addView(contractRow);
    }
}
