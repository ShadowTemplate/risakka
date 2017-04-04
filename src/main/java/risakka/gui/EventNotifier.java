package risakka.gui;

import java.util.HashMap;
import java.util.Map;
import risakka.raft.log.LogEntry;
import risakka.raft.miscellanea.ServerState;

import javax.swing.*;

public class EventNotifier {

    private final ClusterManagerGUI risakkaGUI;
    private final Map<Integer, Integer> leaderOfTerm; //pair of term and leaderId
    
    private static EventNotifier instance = null;

    private EventNotifier(ClusterManagerGUI risakkaGUI) { 
        this.risakkaGUI = risakkaGUI;
        this.leaderOfTerm = new HashMap<>();
    }
    
    public static void setInstance(ClusterManagerGUI risakkaGUI) {
        if (instance == null) {
            instance = new EventNotifier(risakkaGUI);
        }
    }

    public static EventNotifier getInstance() {
        return instance;
    }

    public void addMessage(Integer id, String message) {
        JTextArea messagesArea = risakkaGUI.getServerPanels().get(id).getMessagesArea();
        messagesArea.append(message + "\n\n");
    }

    public void updateLog(Integer id, Integer position, LogEntry entry) {
        JTextArea logArea = risakkaGUI.getServerPanels().get(id).getLogArea();
        logArea.append("[pos: " + position + ", term: " + entry.getTermNumber() + "] {client: " +
                entry.getCommand().getClientId() + "}\n" + entry.getCommand().getCommand() + "\n");
    }
    
    public void setCommittedUpTo(Integer id, Integer committedIndex) {
        JTextArea logArea = risakkaGUI.getServerPanels().get(id).getLogArea();
        //TODO colour
        logArea.append("Committed up to " + committedIndex + "\n");
    }

    public void updateState(Integer id, ServerState state, Integer term) {
        String color;
        if (state == ServerState.LEADER) {
            color = "#1a7a07";
            
            //check that no other server became leader in this term 
            assert(leaderOfTerm.get(term) == null); // Election Safety property
            leaderOfTerm.put(term, id);
            
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
