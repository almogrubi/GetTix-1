package app.com.almogrubi.idansasson.gettix.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.firebase.ui.database.SnapshotParser;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

import app.com.almogrubi.idansasson.gettix.dataservices.EventDataService;
import app.com.almogrubi.idansasson.gettix.viewholders.EventViewHolder;
import app.com.almogrubi.idansasson.gettix.R;
import app.com.almogrubi.idansasson.gettix.entities.Event;
import app.com.almogrubi.idansasson.gettix.entities.Order;
import app.com.almogrubi.idansasson.gettix.dataservices.DataUtils;
import app.com.almogrubi.idansasson.gettix.authentication.ManagementScreen;

public class EventsActivity extends ManagementScreen {

    private TextView tvYourEvents;
    private RecyclerView eventsRecyclerView;
    private LinearLayoutManager linearLayoutManager;

    // Firebase database references
    private DatabaseReference firebaseDatabaseReference;
    private DatabaseReference eventsDatabaseReference;
    private DatabaseReference producerEventsDatabaseReference;
    private DatabaseReference ordersDatabaseReference;
    private DatabaseReference orderSeatsDatabaseReference;

    private FirebaseRecyclerAdapter<Event, EventViewHolder> firebaseRecyclerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_events);

        tvYourEvents = findViewById(R.id.tv_your_events);
        eventsRecyclerView = findViewById(R.id.events_recycler_view);

        tvYourEvents.setVisibility(View.INVISIBLE);

        linearLayoutManager = new LinearLayoutManager(this);
        eventsRecyclerView.setLayoutManager(linearLayoutManager);

        // Initialization of all needed Firebase database references
        initializeDatabaseReferences();
    }

    private void initializeDatabaseReferences() {
        firebaseDatabaseReference = FirebaseDatabase.getInstance().getReference();
        eventsDatabaseReference = firebaseDatabaseReference.child("events");
        producerEventsDatabaseReference = firebaseDatabaseReference.child("producer_events");
        ordersDatabaseReference = firebaseDatabaseReference.child("orders");
        orderSeatsDatabaseReference = firebaseDatabaseReference.child("order_seats");
    }

    @Override
    protected void onSignedInInitialize(FirebaseUser user) {
        super.onSignedInInitialize(user);

        tvYourEvents.setVisibility(View.VISIBLE);

        SnapshotParser<Event> parser = new SnapshotParser<Event>() {
            @Override
            public Event parseSnapshot(DataSnapshot dataSnapshot) {
                Event event = dataSnapshot.getValue(Event.class);
                if (event != null) {
                    event.setUid(dataSnapshot.getKey());
                }
                return event;
            }
        };

        // Displaying all events produced by the signed-in user
        FirebaseRecyclerOptions<Event> options =
                new FirebaseRecyclerOptions.Builder<Event>()
                        .setQuery(producerEventsDatabaseReference.child(user.getUid()).orderByChild("date"),
                                parser)
                        .build();
        firebaseRecyclerAdapter = new FirebaseRecyclerAdapter<Event, EventViewHolder>(options) {
            @Override
            public EventViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
                LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
                return new EventViewHolder(inflater.inflate(R.layout.event_list_item, viewGroup, false));
            }

            @Override
            protected void onBindViewHolder(final EventViewHolder viewHolder,
                                            int position,
                                            final Event event) {
                viewHolder.bindEvent(event);
                viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showEventEditPopup(v, event);
                    }
                });
            }
        };

        firebaseRecyclerAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {});

        eventsRecyclerView.setAdapter(firebaseRecyclerAdapter);

        firebaseRecyclerAdapter.startListening();
    }

    private void showEventEditPopup(final View eventView, final Event event) {

        PopupMenu eventEditPopup = new PopupMenu(EventsActivity.this, eventView);
        eventEditPopup.getMenuInflater().inflate(R.menu.popup_menu_managed_event, eventEditPopup.getMenu());

        eventEditPopup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();
                final Context context = eventView.getContext();
                Intent eventEditActivity = new Intent(context, EventEditActivity.class);

                switch (itemId) {
                    case R.id.action_view_event_orders:
                        onViewEventOrders(context, event);
                        return true;
                    case R.id.action_new_event_based:
                        eventEditActivity.putExtra("editMode",
                                EventEditActivity.EventEditMode.NEW_EVENT_BASED_ON_EXISTING);
                        eventEditActivity.putExtra("eventUid", event.getUid());
                        context.startActivity(eventEditActivity);
                        return true;
                    case R.id.action_edit_event:
                        eventEditActivity.putExtra("editMode",
                                EventEditActivity.EventEditMode.EXISTING_EVENT);
                        eventEditActivity.putExtra("eventUid", event.getUid());
                        context.startActivity(eventEditActivity);
                        return true;
                    case R.id.action_delete_event:
                        onDeleteEvent(event);
                        return true;
                    default:
                        return true;
                }
            }
        });

        eventEditPopup.show();
    }

    private void onViewEventOrders(final Context context, Event event) {
        final Intent eventOrdersActivity = new Intent(context, EventOrdersActivity.class);
        eventOrdersActivity.putExtra("eventUid", event.getUid());
        eventOrdersActivity.putExtra("eventTitle", event.getTitle());
        int eventTotalTicketsNum = event.isMarkedSeats()
                ? event.getEventHall().getRows() * event.getEventHall().getColumns()
                : event.getMaxCapacity();
        eventOrdersActivity.putExtra("eventTotalTicketsNum", eventTotalTicketsNum);

        // Getting real-time leftTicketsNum value from DB
        eventsDatabaseReference.child(event.getUid()).child("leftTicketsNum")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        eventOrdersActivity.putExtra("eventLeftTicketsNum", Integer.parseInt(dataSnapshot.getValue().toString()));
                        context.startActivity(eventOrdersActivity);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {}
                });
    }

    private void onDeleteEvent(final Event event) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int choice) {
                switch (choice) {
                    case DialogInterface.BUTTON_POSITIVE:
                        handleEventOrdersRemoval(event);
                        EventDataService.RemoveEventFromDB(event, user.getUid());
                        Toast.makeText(EventsActivity.this, "המופע נמחק", Toast.LENGTH_SHORT).show();
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(EventsActivity.this);
        builder.setMessage("האם אתה בטוח שברצונך למחוק את המופע?")
                .setPositiveButton("כן, מחק", dialogClickListener)
                .setNegativeButton("לא", dialogClickListener).show();
    }

    private void handleEventOrdersRemoval(final Event event) {
        ordersDatabaseReference.child(event.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                if (dataSnapshot.exists()) {
                    ArrayList<String> customerEmailAddresses = new ArrayList<>();

                    for (DataSnapshot orderSnapshot : dataSnapshot.getChildren()) {
                        Order order = orderSnapshot.getValue(Order.class);

                        // Remove the order from DB
                        ordersDatabaseReference.child(event.getUid()).child(order.getUid()).removeValue();
                        orderSeatsDatabaseReference.child(order.getUid()).removeValue();

                        // For non-final orders, we don't need to send cancellation email
                        if (order.getStatusAsEnum() != DataUtils.OrderStatus.FINAL)
                            continue;

                        customerEmailAddresses.add(order.getCustomer().getEmail());
                    }

                    if (customerEmailAddresses.size() > 0)
                        sendOrderCancellationEmail(customerEmailAddresses, event);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        });
    }

    public void sendOrderCancellationEmail(ArrayList<String> customerAddresses, Event event) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // only email apps should handle this

        String[] addressesArray = new String[customerAddresses.size()];
        customerAddresses.toArray(addressesArray);
        intent.putExtra(Intent.EXTRA_EMAIL, addressesArray);

        intent.putExtra(Intent.EXTRA_SUBJECT, "ביטול המופע " + event.getTitle());

        String newLine = System.getProperty("line.separator");
        String body = String.format("שלום,%s%s" +
                        "הרינו להודיעך על ביטול המופע %s בתאריך %s, אליו הזמנת כרטיסים.%s" +
                        "אנא צור איתנו קשר להסדר התשלום.%s%s" +
                        "בברכה,%s" +
                        "צוות GetTix",
                newLine, newLine,
                event.getTitle(), DataUtils.convertToUiDateFormat(event.getDate()), newLine,
                newLine, newLine,
                newLine);
        intent.putExtra(Intent.EXTRA_TEXT, body);

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_manager_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_add_item) {
            Intent eventEditActivityIntent = new Intent(this, EventEditActivity.class);
            eventEditActivityIntent.putExtra("editMode",
                    EventEditActivity.EventEditMode.NEW_EVENT);
            startActivity(eventEditActivityIntent);

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firebaseRecyclerAdapter != null)
            firebaseRecyclerAdapter.startListening();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (firebaseRecyclerAdapter != null)
            firebaseRecyclerAdapter.stopListening();
    }
}
