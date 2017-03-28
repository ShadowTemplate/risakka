package risakka.gui;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Terminated;
import com.typesafe.config.Config;
import lombok.Getter;
import risakka.cluster.ClusterManager;
import risakka.raft.actor.RaftServer;
import risakka.raft.message.MessageToServer;
import risakka.raft.message.akka.PauseMessage;
import risakka.raft.message.akka.ResumeMessage;
import scala.concurrent.Future;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class ClusterManagerGUI implements Runnable {

    private final ClusterManager clusterManager;
    private final Map<Integer, ServerPanel> serverPanels;

    public ClusterManagerGUI(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
        this.serverPanels = new HashMap<>();
    }

    @Override
    public void run() {
        int nodesNumber = clusterManager.getActors().size();
        JFrame frame = new JFrame("Risakka Cluster Manager");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.out.println("Shutting down actor systems...");
                List<Future<Terminated>> futures = clusterManager.getActorSystems().parallelStream()
                        .map(ActorSystem::terminate).collect(Collectors.toList());

                for (Future<Terminated> future : futures) {
                    while (!future.isCompleted()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }
        });
        frame.setSize(getWindowDimension(nodesNumber));
        frame.setMaximumSize(frame.getPreferredSize());
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        Container container = frame.getContentPane();
        setComponentSize(container, getWindowDimension(nodesNumber));
        container.setLayout(new FlowLayout());

        JPanel scrollablePanel = new JPanel();
        scrollablePanel.setLayout(new GridLayout((nodesNumber - 1) / 3 + 1, 1)); // black magic here. Trust me: I like math
        JScrollPane scrollablePane = new JScrollPane(scrollablePanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollablePane.getVerticalScrollBar().setUnitIncrement(16);
        setComponentSize(scrollablePane, getScrollablePaneDimension(nodesNumber));

        for (Map.Entry<Integer, String> actorEntry : clusterManager.getActors().entrySet()) {
            ServerPanel serverPanel = new ServerPanel(actorEntry.getKey(), actorEntry.getValue());
            setActiveSwitchListener(serverPanel, actorEntry.getKey());
            setPauseSwitchListener(serverPanel, actorEntry.getKey());
            serverPanels.put(actorEntry.getKey(), serverPanel);
            scrollablePanel.add(buildServerPanel(serverPanel, frame.getBackground()));
        }

        container.add(scrollablePane);
        frame.setVisible(true);
    }

    private void setActiveSwitchListener(ServerPanel serverPanel, Integer nodeId) {
        JToggleButton activeSwitch = serverPanel.getActiveSwitch();
        activeSwitch.addItemListener(ev -> {
            if (ev.getStateChange() == ItemEvent.SELECTED) {
                System.out.println("Killing node with id " + nodeId);
                activeSwitch.setText("INACTIVE");
                // TODO CHECK KILL

                clusterManager.getActorSystems().get(nodeId).terminate();
            } else if (ev.getStateChange() == ItemEvent.DESELECTED) {
                //TODO CHECK RE-INIT HERE
                System.out.println("Reading node with id " + nodeId);
                activeSwitch.setText("ACTIVE");
                //recreate the system with the raft server (its address is the same)
                Config total = ClusterManager.resolveConfigurationForId(nodeId, clusterManager.getInitialConfig(), clusterManager.getNotResolvedConf());
                ActorSystem system = ActorSystem.create(clusterManager.getNotResolvedConf().CLUSTER_NAME, total);
                system.actorOf(Props.create(RaftServer.class, nodeId), clusterManager.getNotResolvedConf().PREFIX_NODE_NAME + nodeId);
                clusterManager.getActorSystems().set(nodeId, system);
            }
        });
    }

    private void setPauseSwitchListener(ServerPanel serverPanel, Integer nodeId) {
        JToggleButton timerSwitch = serverPanel.getPauseSwitch();
        timerSwitch.addItemListener(ev -> {
            if (ev.getStateChange() == ItemEvent.SELECTED) {
                timerSwitch.setText("PAUSED");
                sendMessage(new PauseMessage(), nodeId);
            } else if (ev.getStateChange() == ItemEvent.DESELECTED) {
                timerSwitch.setText("RUNNING");
                sendMessage(new ResumeMessage(), nodeId);
            }
        });
    }

    private void sendMessage(MessageToServer message, Integer nodeId) {
        ActorSelection actorSelection = clusterManager.getActorSystems().get(nodeId).actorSelection(clusterManager.getActors().get(nodeId));
        actorSelection.tell(message, null);
    }

    private void setComponentSize(Component component, Dimension dimension) {
        component.setSize(dimension);
        component.setPreferredSize(component.getSize());
        component.setMinimumSize(component.getPreferredSize());
        component.setMaximumSize(component.getPreferredSize());
    }

    private JPanel buildServerPanel(ServerPanel serverPanel, Color backgroundColor) {
        JPanel logContainer = new JPanel();
        logContainer.setSize(480, 400);
        logContainer.add(buildScrollableTextArea(serverPanel.getMessagesArea(), "Messages", backgroundColor));
        logContainer.add(buildScrollableTextArea(serverPanel.getLogArea(), "Persistent Log", backgroundColor));

        JPanel infoContainer = new JPanel();
        infoContainer.setLayout(new FlowLayout());
        infoContainer.add(serverPanel.getStateLabel());
        infoContainer.add(Box.createHorizontalStrut(10));
        infoContainer.add(serverPanel.getTermLabel());
        infoContainer.add(Box.createHorizontalStrut(10));
        infoContainer.add(serverPanel.getActiveSwitch());
        infoContainer.add(Box.createHorizontalStrut(10));
        infoContainer.add(serverPanel.getPauseSwitch());

        JPanel outerContainer = new JPanel();
        outerContainer.setLayout(new FlowLayout());
        setComponentSize(outerContainer, new Dimension(600, 400));
        outerContainer.add(infoContainer);
        outerContainer.add(logContainer);
        outerContainer.setBorder(BorderFactory.createTitledBorder("Node " + serverPanel.getId() + ": " + serverPanel.getFullName()));
        return outerContainer;
    }

    private JPanel buildScrollableTextArea(JTextArea textArea, String title, Color backgroundColor) {
        JPanel areaPanel = new JPanel();
        areaPanel.setLayout(new FlowLayout());
        areaPanel.setBorder(BorderFactory.createTitledBorder(title));

        textArea.setBackground(backgroundColor);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setFont(textArea.getFont().deriveFont(13f));
        textArea.addMouseListener(onRightClickClearArea(textArea));

        DefaultCaret caret = (DefaultCaret) textArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane areaScrollPane = new JScrollPane(textArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        areaScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        areaPanel.add(areaScrollPane);
        return areaPanel;
    }

    private MouseAdapter onRightClickClearArea(JTextArea textArea) {
        return new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isRightMouseButton(e)) {
                    return;
                }
                /*
                Object[] options = new String[]{"Yes", "No"};
                int res = JOptionPane.showOptionDialog(null,
                        "The operation is irreversible.\nDo you really want to clean the panel?",
                        "Cleaning panel", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options,
                        options[0]);
                if (res == JOptionPane.YES_OPTION) {
                    textArea.setText("");
                }
                */
                textArea.setText("");
            }
        };
    }

    private Dimension getWindowDimension(int nodesNumber) {
        int width = nodesNumber == 2 || nodesNumber == 4 ? 1270 : 1870;
        int height = nodesNumber < 4 ? 460 : 860;
        return new Dimension(width, height);
    }

    private Dimension getScrollablePaneDimension(int nodesNumber) {
        Dimension windowDimension = getWindowDimension(nodesNumber);
        return new Dimension(windowDimension.width - 10, windowDimension.height - 40);
    }

}
