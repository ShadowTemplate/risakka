package risakka;

import akka.actor.Props;
import lombok.AllArgsConstructor;
import risakka.server.util.Conf;

import javax.swing.*;
import java.awt.*;

//@AllArgsConstructor
public class ClusterManagerGUI implements Runnable {

    private final ClusterManager clusterManager;

    public ClusterManagerGUI(ClusterManager clusterManager){this.clusterManager=clusterManager;}

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
                        clusterManager.getSystem(nodeId).stop(clusterManager.getActors().get(nodeId));
                        break;
                    case "RESTART":
                        System.out.println("Restarting process " + nodeId);
                        clusterManager.getSystem(nodeId).stop(clusterManager.getActors().get(nodeId));
                        clusterManager.getActors().put(nodeId, clusterManager.getSystem(nodeId).actorOf(Props.create(FooNode.class, nodeId), "server_" + nodeId));
                        break;
                    default:
                        System.out.println("Operation not available");
                }
            });

        }

        GridLayout frameLayout = new GridLayout(1,2);
        frame.setLayout(frameLayout);
        frame.getContentPane().add(killPanel);
        frame.getContentPane().add(restartPanel);

        frame.setSize(300, 300);
        frame.setVisible(true);
    }
}
