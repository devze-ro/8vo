package ro.devze.octavo;

import android.content.Context;
import android.widget.RadioButton;

/**
 * A checkable native control whose checked state is advanced only by a
 * successfully presented reader snapshot. User activation still reaches the
 * click listener, but cannot publish provisional checked accessibility state.
 */
final class PresentedProgressRadioButton extends RadioButton {
    PresentedProgressRadioButton(Context context) {
        super(context);
    }

    @Override
    public void toggle() {
        // Presentation ownership calls setChecked through RadioGroup.check().
    }
}
