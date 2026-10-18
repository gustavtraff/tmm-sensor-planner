package se.tmm.sensorplanner.plugin;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Optional local-time entry; opening or cancelling a picker never changes the value. */
@TargetApi(26)
final class DeploymentTimeInput extends LinearLayout {
    private Instant value;
    private final TextView display;
    private final Button clear;
    private AlertDialog picker;

    DeploymentTimeInput(Context context, Instant initial) {
        super(context);
        setOrientation(VERTICAL);
        value = initial;
        display = new TextView(context);
        display.setTextSize(16);
        addView(display);
        Button choose = new Button(context);
        choose.setText("Välj datum och tid");
        choose.setOnClickListener(view -> choose());
        addView(choose);
        clear = new Button(context);
        clear.setText("Rensa utplaceringstid");
        clear.setOnClickListener(view -> { value = null; update(); });
        addView(clear);
        update();
    }

    String utcText() { return value == null ? "" : value.toString(); }

    void close() { if (picker != null) { picker.dismiss(); picker = null; } }

    private void update() {
        ZoneId zone = ZoneId.systemDefault();
        display.setText("Utplacerad (valfri): " + (value == null ? "Ej angivet" :
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX").withZone(zone).format(value))
                + "\nTidszon: " + zone.getId() + ". Sparas som UTC.");
        clear.setEnabled(value != null);
    }

    private void choose() {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime start = (value == null ? Instant.now() : value).atZone(zone);
        DatePickerDialog date = new DatePickerDialog(getContext(), (view, year, month, day) -> {
            LocalDate selected = LocalDate.of(year, month + 1, day);
            TimePickerDialog time = new TimePickerDialog(getContext(), (clock, hour, minute) -> {
                try {
                    value = selectedInstant(selected, hour, minute, zone);
                    update();
                } catch (DateTimeException invalid) {
                    picker = new AlertDialog.Builder(getContext()).setTitle("Tiden finns inte")
                            .setMessage("Klockan ställs fram vid denna tid i " + zone.getId()
                                    + ". Välj en annan tid. Det tidigare värdet är kvar.")
                            .setPositiveButton("Stäng", null).show();
                }
            }, start.getHour(), start.getMinute(), true);
            time.setTitle("Utplaceringstid · " + zone.getId());
            picker = time;
            time.show();
        }, start.getYear(), start.getMonthValue() - 1, start.getDayOfMonth());
        date.setTitle("Utplaceringsdatum · " + zone.getId());
        picker = date;
        date.show();
    }

    static Instant selectedInstant(LocalDate date, int hour, int minute, ZoneId zone) {
        LocalDateTime local = date.atTime(hour, minute);
        if (zone.getRules().getValidOffsets(local).isEmpty()) throw new DateTimeException("Clock gap");
        // On autumn clock overlap choose the first occurrence; the displayed UTC offset makes it explicit.
        return local.atZone(zone).withEarlierOffsetAtOverlap().toInstant();
    }
}
