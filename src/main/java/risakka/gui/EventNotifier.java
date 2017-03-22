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
            logArea.append(newEntries + "\n");
        }
    }

    public void updateState(Integer id, ServerState state) {
        risakkaGUI.getServerPanels().get(id).getStateLabel().setText("State: " + state);
    }
}
