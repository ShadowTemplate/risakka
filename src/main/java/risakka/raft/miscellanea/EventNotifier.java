package risakka.raft.miscellanea;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import risakka.raft.log.LogEntry;

import javax.swing.*;
import risakka.gui.ClusterManagerGUI;
import risakka.raft.log.StateMachineCommand;

public class EventNotifier {

    private final ClusterManagerGUI risakkaGUI;
    private final Map<Integer, Integer> leaderOfTerm; //pair of term and leaderId
    private final SequentialContainer<LogEntry> globalLog;
    private final Map<Integer, SequentialContainer<LogEntry>> logOfServer;
    
    private static EventNotifier instance = null;

    private EventNotifier(ClusterManagerGUI risakkaGUI) { 
        this.risakkaGUI = risakkaGUI;
        this.leaderOfTerm = new HashMap<>();
        this.globalLog = new SequentialContainer<>();
        
        this.logOfServer = new HashMap<>();
        for (int i = 0; i < risakkaGUI.getClusterManager().getNotResolvedConf().SERVER_NUMBER; i++) {
            logOfServer.put(i, new SequentialContainer<>());
        }
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
    
        logOfServer.get(id).set(position, entry);
        checkLogsWithSameIndexAndTerm(id, position, entry.getTermNumber());
    }
    
    public void setCommittedUpTo(Integer id, Integer startCommittedIndex, Integer endCommittedIndex, List<LogEntry> entries) {
        JTextArea logArea = risakkaGUI.getServerPanels().get(id).getLogArea();
        logArea.append("Committed up to " + endCommittedIndex + "\n");
        
        //add each committed entry in the global state, if not present 
        for (int i = startCommittedIndex; i <= endCommittedIndex; i++) {
            LogEntry entry = entries.get(i - startCommittedIndex); //get relative entry
            if(globalLog.size() < i) { //no entry in position i
                assert globalLog.size() == (i - 1); //previous entry must be already committed and stored into global log
                globalLog.set(i, new LogEntry(entry.getTermNumber(), new StateMachineCommand(entry.getCommand())));
            } else { //entry already in this position
                assert entry.equals(globalLog.get(i));
            }
        }
    }

    public void updateState(Integer id, ServerState state, Integer term, SequentialContainer<LogEntry> allEntries) {
        String color;
        if (state == ServerState.LEADER) {
            color = "#1a7a07";
            
            //check that no other server became leader in this term 
            assert leaderOfTerm.get(term) == null : "Election Safety property violated";
            if (!(leaderOfTerm.get(term) == null)) {
                JOptionPane.showMessageDialog(null, "Election Safety property violated", "Property violation", JOptionPane.ERROR_MESSAGE);
            }
            leaderOfTerm.put(term, id);
            
            //check that new leader has all previously committed entries
            checkGlobalLogPrefixOf(allEntries);
            
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
    
    private void checkGlobalLogPrefixOf(SequentialContainer<LogEntry> entries) {
        for (int i = 1; i <= globalLog.size(); i++) {
            assert globalLog.get(i).equals(entries.get(i)) : "Leader Completeness property violated";
            if (!(globalLog.get(i).equals(entries.get(i)))) {
                JOptionPane.showMessageDialog(null, "Leader Completeness property violated", "Property violation", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void checkLogsWithSameIndexAndTerm(Integer id, Integer index, Integer term) {
        SequentialContainer<LogEntry> myLog = logOfServer.get(id);
        
        for (int i = 0; i < logOfServer.size(); i++) {
            if (id != i) { //not myself
                SequentialContainer<LogEntry> otherLog = logOfServer.get(i);
                
                //if both logs contain an entry with same index and term
                if (otherLog.size() >= index && otherLog.get(index).getTermNumber().equals(term)) {
                    for (int j = 1; j <= index; j++) {
                        assert myLog.get(j).equals(otherLog.get(j)) : "Log Matching property violated";
                        if (!(myLog.get(j).equals(otherLog.get(j)))) {
                            JOptionPane.showMessageDialog(null, "Log Matching property violated", "Property violation", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            }
        }
    }
}
