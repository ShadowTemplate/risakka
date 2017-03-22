package risakka.cluster;

import akka.actor.Props;
import lombok.AllArgsConstructor;
import risakka.raft.actor.RaftServer;
import risakka.util.Conf;

import javax.swing.*;
import java.awt.*;

@AllArgsConstructor
class ClusterManagerGUI implements Runnable {

    private final ClusterManager clusterManager;

    @Override
    public void run() {
        JFrame frame = new JFrame("Cluster Manager");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel killPanel = new JPanel();
        killPanel.setLayout(new BoxLayout(killPanel, BoxLayout.PAGE_AXIS));

        JPanel restartPanel = new JPanel();
        restartPanel.setLayout(new BoxLayout(restartPanel, BoxLayout.PAGE_AXIS));

        for (int i = 0; i < 2 * Conf.SERVER_NUMBER; i++) {
            JButton button = new JButton();
            if (i < Conf.SERVER_NUMBER) {
                button.setText("KILL node " + i);
                button.setActionCommand("KILL node " + i);
                killPanel.add(button);
            } else {
                button.setText("RESTART node " + (i - Conf.SERVER_NUMBER));
                button.setActionCommand("RESTART node " + (i - Conf.SERVER_NUMBER));
                restartPanel.add(button);
            }
            button.addActionListener(e -> {
                String[] command = e.getActionCommand().split(" ");
                int nodeId = Integer.parseInt(command[2]);
                switch (command[0]) {
                    case "KILL":
                        System.out.println("Killing process " + nodeId);
                        clusterManager.getActorSystems().get(nodeId).stop(clusterManager.getActors().get(nodeId));
                        break;
                    case "RESTART":
                        System.out.println("Restarting process " + nodeId);
                        clusterManager.getActorSystems().get(nodeId).stop(clusterManager.getActors().get(nodeId));
                        clusterManager.getActors().put(nodeId, clusterManager.getActorSystems().get(nodeId).actorOf(Props.create(RaftServer.class), "server_" + nodeId));
                        break;
                    default:
                        System.out.println("Operation not available");
                }
            });

        }

        GridLayout frameLayout = new GridLayout(1, 2);
        frame.setLayout(frameLayout);
        frame.getContentPane().add(killPanel);
        frame.getContentPane().add(restartPanel);

        frame.setSize(300, 300);
        frame.setVisible(true);
    }
}
