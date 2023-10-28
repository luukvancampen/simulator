import java.util.HashSet;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting simulator....");
//       Network network = new Network();

        Network network = new Network();
        Thread nThread = new Thread(network, "network thread");
        nThread.start();

        Node n1 = new Node("node_1", 3, new double[]{5, 3}, network);
        Node n2 = new Node("node_2", 3, new double[]{3, 5}, network);

        Thread nodeThread1 = new Thread(n1, "Node 1");
        Thread nodeThread2 = new Thread(n2, "Node 2");

        nodeThread1.start();
        nodeThread2.start();

        try {
            Packet p1 = new Packet("DATA1", new double[]{3, 4}, "192.168.1.1", "node_1", new HashSet<>());
            Packet p2 = new Packet("DATA2", new double[]{4, 3}, "192.168.1.1", "node_2", new HashSet<>());
            Packet p3 = new Packet("DATA3", new double[]{3.5, 4}, "192.168.1.1", "node_1", new HashSet<>());
            Packet p4 = new Packet("OUT OF RANGE", new double[]{10, 10}, "192.168.1.1", "node_1", new HashSet<>());
            Thread.sleep(1000);
            network.send(p1);
            Thread.sleep(1000);
            network.send(p2);
            Thread.sleep(1000);
            network.send(p3);
            Thread.sleep(1000);
            network.send(p4);
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}