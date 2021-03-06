package risakka.gui;

import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.swing.*;

@Getter
@AllArgsConstructor
public class ServerPanel {

    private final Integer id;
    private final String fullName;
    private final JLabel stateLabel = new JLabel("State: UNKNOWN");
    private final JLabel termLabel = new JLabel("Term: -1");
    private final JToggleButton activeSwitch = new JToggleButton("ACTIVE");
    private final JToggleButton pauseSwitch = new JToggleButton("RUNNING");
    private final JTextArea logArea = new JTextArea(17, 21);
    private final JTextArea messagesArea = new JTextArea(17, 21);

}
