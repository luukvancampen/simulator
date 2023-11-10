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

        Node n1 = new Node("A", new double[] { 2, 4 }, 2, network);
        Node n2 = new Node("B", new double[] { 2, 6 }, 2, network);
        Node n3 = new Node("C", new double[] { 2, 8 }, 2, network);

        Thread nodeThread1 = new Thread(n1, "A");
        Thread nodeThread2 = new Thread(n2, "B");
        Thread nodeThread3 = new Thread(n3, "C");

        nodeThread1.start();
        nodeThread2.start();
        nodeThread3.start();

        submitTask(n1, "C", "HELLOOO", 0);

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