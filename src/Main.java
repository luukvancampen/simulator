import java.util.HashSet;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting simulator....");

        Network network = new Network();
        Thread nThread = new Thread(network, "network thread");
        nThread.start();

        Node n1 = new Node("A", 3, new double[]{5, 3}, network);
        Node n2 = new Node("B", 3, new double[]{3, 5}, network);
        Node n3 = new Node("C", 3, new double[]{4, 4}, network);

        Thread nodeThread1 = new Thread(n1, "A");
        Thread nodeThread2 = new Thread(n2, "B");
        Thread nodeThread3 = new Thread(n3, "C");

        nodeThread1.start();
        nodeThread2.start();
        nodeThread3.start();

        try {
//            Packet p1 = new Packet("DATA1", new double[]{3, 4}, "node_2", "node_1", new HashSet<>());
//            Packet p2 = new Packet("DATA2", new double[]{4, 3}, "node_1", "node_2", new HashSet<>());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}