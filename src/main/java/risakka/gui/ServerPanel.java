package risakka.gui;

import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.swing.*;

@Getter
@AllArgsConstructor
class ServerPanel {

    private final Integer id;
    private final String fullName;
    private final JLabel stateLabel = new JLabel("State: UNKNOWN");
    private final JToggleButton activeSwitch = new JToggleButton("ACTIVE");
    private final JToggleButton electionSwitch = new JToggleButton("Election timer: ON");
    private final JTextArea logArea = new JTextArea(17, 21);
    private final JTextArea messagesArea = new JTextArea(17, 21);

}
