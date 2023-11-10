import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

public class Main {

    LinkedList<TimerTask> tasks;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Starting simulator....");

        Network network = new Network();
        Thread nThread = new Thread(network, "network thread");
        nThread.start();

        Node n1 = new Node("A", new double[] { 2, 4 }, 2.1, network);
        Node n2 = new Node("B", new double[] { 2, 6 }, 2.1, network);
        Node n3 = new Node("C", new double[] { 4, 6 }, 2.1, network);
        Node n4 = new Node("D", new double[] { 6, 6 }, 2.1, network);
        Node n5 = new Node("E", new double[] { 2, 2 }, 2.1, network);
        Node n6 = new Node("F", new double[] { 4, 2 }, 2.1, network);
        Node n7 = new Node("G", new double[] { 6, 2 }, 2.1, network);
        Node n8 = new Node("H", new double[] { 6, 4 }, 2.1, network);

        Thread nodeThread1 = new Thread(n1, "A");
        Thread nodeThread2 = new Thread(n2, "B");
        Thread nodeThread3 = new Thread(n3, "C");
        Thread nodeThread4 = new Thread(n4, "D");
        Thread nodeThread5 = new Thread(n5, "E");
        Thread nodeThread6 = new Thread(n6, "F");
        Thread nodeThread7 = new Thread(n7, "G");
        Thread nodeThread8 = new Thread(n8, "H");

        nodeThread1.start();
        nodeThread2.start();
        nodeThread3.start();
        nodeThread4.start();
        nodeThread5.start();
        nodeThread6.start();
        nodeThread7.start();
        nodeThread8.start();

        submitTask(n1, "H", "HELLOOO", 0);

        while (true) {
            Thread.sleep(50);
        }
    }

    static void submitTask(Node node, String receiver, String data, Integer delay) {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                node.send(receiver, data);
            }
        };

        Timer timer = new Timer();
        timer.schedule(task, delay);
    }
}