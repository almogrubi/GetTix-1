package app.com.almogrubi.idansasson.gettix;

import android.content.Intent;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import app.com.almogrubi.idansasson.gettix.entities.Event;
import app.com.almogrubi.idansasson.gettix.entities.Order;


public class NoSeatsActivity extends AppCompatActivity {

    private FirebaseDatabase firebaseDatabase;
    private DatabaseReference eventsDatabaseReference;

    private Event event;

    private TextView tvEventTitle;
    private ImageView ivPlus;
    private ImageView ivMinus;
    private TextView tvTicketsNum;
    private TextView tvFriendlyTicketsNum;
    private EditText etCouponCode;
    private Button btCheckCoupon;
    private Button btNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_no_seats);

        ActionBar actionBar = this.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        tvEventTitle = findViewById(R.id.tv_event_title);
        ivPlus = findViewById(R.id.iv_plus);
        ivMinus = findViewById(R.id.iv_minus);
        tvTicketsNum = findViewById(R.id.tv_tickets_num);
        tvFriendlyTicketsNum = findViewById(R.id.tv_friendly_tickets_num);
        etCouponCode = findViewById(R.id.et_coupon_code);
        btCheckCoupon = findViewById(R.id.bt_check_coupon);
        btNext = findViewById(R.id.bt_next);

        // Initialize all needed Firebase database references
        firebaseDatabase = FirebaseDatabase.getInstance();
        eventsDatabaseReference = firebaseDatabase.getReference().child("events");

        Intent intent = this.getIntent();
        // Lookup the event in the database and bind its data to UI
        if ((intent != null) && (intent.hasExtra("eventUid"))) {
            eventsDatabaseReference
                    .child(intent.getStringExtra("eventUid"))
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            // If we have a null result, the event was somehow not found in the database
                            if (dataSnapshot == null || !dataSnapshot.exists() || dataSnapshot.getValue() == null) {
                                abort();
                                return;
                            }

                            // If we reached here then the existing event was found, we'll bind it to UI
                            event = dataSnapshot.getValue(Event.class);
                            tvEventTitle.setText(event.getTitle());
                            updateTicketsNumUI(1);
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            abort();
                        }
                    });
        } else {
            abort();
        }

        ivPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                incrementTicketsNum();
            }
        });

        ivMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                decrementTicketsNum();
            }
        });
//
//        next.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent payActivity = new Intent(v.getContext(), PayActivity.class);
//                payActivity.putExtra("eventUid", event.getUid());
//                payActivity.putExtra("orderObject", order);
//
//                startActivity(payActivity);
//
//            }
//        }

    }

    private void abort() {
        String eventNotFoundErrorMessage = "המופע לא נמצא, נסה שנית";

        Toast.makeText(this, eventNotFoundErrorMessage, Toast.LENGTH_SHORT);
        startActivity(new Intent(this, EventDetailsActivity.class));
    }

    private void incrementTicketsNum() {
        int previousTicketsNum = Integer.parseInt(tvTicketsNum.getText().toString());

        if (previousTicketsNum < this.event.getLeftTicketsNum()) {
            updateTicketsNumUI(previousTicketsNum + 1);
        }
    }

    private void decrementTicketsNum() {
        int previousTicketsNum = Integer.parseInt(tvTicketsNum.getText().toString());

        if (previousTicketsNum > 1) {
            updateTicketsNumUI(previousTicketsNum - 1);
        }
    }

    private void updateTicketsNumUI(int ticketsNums) {
        int newTotalPrice = ticketsNums * event.getPrice();
        tvTicketsNum.setText(String.valueOf(ticketsNums));
        tvFriendlyTicketsNum.setText(String.format("רכישת %d כרטיסים: %d ₪", ticketsNums, newTotalPrice));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
