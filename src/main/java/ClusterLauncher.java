
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import risakka.util.Conf;

public class ClusterLauncher {
    
    private List<Thread> cluster;
    private final String logsDir = Conf.BASE_DIR + "/logs/";
    
    public ClusterLauncher() {        
        cluster = new ArrayList<>();
    }
    
    private class NodeLauncher implements Runnable {
        
        private final int nodeId;        
        
        public NodeLauncher(int nodeId) {
            this.nodeId = nodeId;
        }
        
        @Override
        public void run() {
            
            try {
                Runtime run = Runtime.getRuntime();
                
                Process process = run.exec("mvn exec:java -Dexec.executable=node");
                
                BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                
                String s = null;
                // read the output from the command
                while ((s = stdInput.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                    System.out.println(">>NODE " + nodeId + ": " + s);
                }

                // read any errors from the attempted command
                while ((s = stdError.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                    System.out.println(">>NODE " + nodeId + "'S ERROR: " + s);
                }
            } catch (IOException ex) {
                Logger.getLogger(ClusterLauncher.class.getName()).log(Level.SEVERE, null, ex);
            }            
            
        }
        
    }
    
    private class ClusterInterface implements Runnable {
        
        ClusterLauncher cl;
        private final String KILL = "KILL";
        private final String RESTART = "RESTART";
        
        public ClusterInterface(ClusterLauncher cl) {
            this.cl = cl;
        }
        
        @Override
        public void run() {
            JFrame frame = new JFrame("Cluster manager");
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                       
            JPanel killPanel = new JPanel();
            killPanel.setLayout(new BoxLayout(killPanel, BoxLayout.PAGE_AXIS));
            
            JPanel restartPanel = new JPanel();
            restartPanel.setLayout(new BoxLayout(restartPanel, BoxLayout.PAGE_AXIS));
            
            for (int i = 0; i < 2*Conf.SERVER_NUMBER; i++) {
                
                JButton button = new JButton();
                if (i < Conf.SERVER_NUMBER) {
                    button.setText(KILL + " node " + i);
                    button.setActionCommand(KILL + " node " + i);
                    killPanel.add(button);
                } else {
                    button.setText(RESTART + " node " + (i - Conf.SERVER_NUMBER));
                    button.setActionCommand(RESTART + " node " + (i - Conf.SERVER_NUMBER));
                    restartPanel.add(button);
                }
                               
                button.addActionListener(new ActionListener() {
                    
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        String[] command = e.getActionCommand().split(" ");
                        int nodeId = Integer.parseInt(command[2]);
                        switch (command[0]) {
                            case KILL:
                                System.out.println("Killing process " + nodeId);
                                cl.cluster.get(nodeId).interrupt();
                                break;
                            case RESTART:
                                System.out.println("Restarting process " + nodeId);
                                
                                //check if was already interrupted, otherwise interrupt it
                                if (!cl.cluster.get(nodeId).isInterrupted()) {
                                    cl.cluster.get(nodeId).interrupt();
                                }
                                
                                //launching node 
                                NodeLauncher node = cl.new NodeLauncher(nodeId);
                                Thread thread = new Thread(node);
                                thread.start();
                                cl.cluster.set(nodeId, thread);
                                break;
                            default:
                                System.out.println("Operation not available");
                        }
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
    
    public static void main(String[] args) {
        
        ClusterLauncher clusterLauncher = new ClusterLauncher();
        System.out.println("Server number: " + Conf.SERVER_NUMBER);
        
        //create directory where to store snapshots
        File f = new File(clusterLauncher.logsDir);

        if (f.exists()) { //if directory exists, delete old files
            if (f.isDirectory()) {
                clusterLauncher.deleteFolderRecursively(f);
            } else {
                f.delete();
            }
        }
        f.mkdir();
        
        for (int i = 0; i < Conf.SERVER_NUMBER; i++) {
            System.out.println("Preparing server " + i + " ");

            //create directory where to store snapshots
            File fNode = new File(clusterLauncher.logsDir + i);
            fNode.mkdir();

            //launching node i
            NodeLauncher node = clusterLauncher.new NodeLauncher(i);
            Thread thread = new Thread(node);
            thread.start();
            clusterLauncher.cluster.add(thread);
        }
        
        ClusterInterface interfacePanel = clusterLauncher.new ClusterInterface(clusterLauncher);
        Thread t = new Thread(interfacePanel);
        t.start();

    }
    
    private void deleteFolderRecursively(File path) {
        File[] files = path.listFiles();
        System.out.println("Cleaning folder:" + path.toString());
        for (File file : files) {
            if (file.isDirectory()) {
                deleteFolderRecursively(file);
            }
            file.delete();
        }
        path.delete();
    }
}
