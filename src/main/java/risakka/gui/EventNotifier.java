package risakka.gui;

import lombok.AllArgsConstructor;
import risakka.raft.log.LogEntry;
import risakka.raft.miscellanea.ServerState;

import javax.swing.*;
import java.util.List;

@AllArgsConstructor
public class EventNotifier {

    private final ClusterManagerGUI risakkaGUI;

    public void addMessage(Integer id, String message) {
        JTextArea messagesArea = risakkaGUI.getServerPanels().get(id).getMessagesArea();
        messagesArea.append(message + "\n");
    }

    public void updateLog(Integer id, List<LogEntry> newEntries) {
        JTextArea logArea = risakkaGUI.getServerPanels().get(id).getLogArea();
        for (LogEntry newEntry : newEntries) {
            logArea.append(newEntry + "\n");
        }
    }

    public void updateState(Integer id, ServerState state) {
        String color;
        if (state == ServerState.LEADER) {
            color = "#00ff2a";
        } else if (state == ServerState.CANDIDATE) {
            color = "#ff0015";
        } else {
            color = "#00d8ff";
        }
        String coloredLabel = "<html>State: <b><font color=" + color + ">" + state + "</font></b></html>";
        risakkaGUI.getServerPanels().get(id).getStateLabel().setText(coloredLabel);
    }

    public void updateTerm(Integer id, Integer termNumber) {
        risakkaGUI.getServerPanels().get(id).getTermLabel().setText("Term: " + termNumber);
    }
}
