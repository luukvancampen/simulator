import java.util.HashSet;
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

        Node n1 = new Node("A", 3, new double[]{2, 2}, network);
        Node n2 = new Node("B", 3, new double[]{2, 4}, network);
        Node n3 = new Node("C", 3, new double[]{2, 6}, network);
        Node n4 = new Node("D", 3, new double[]{2, 8}, network);

        Thread nodeThread1 = new Thread(n1, "A");
        Thread nodeThread2 = new Thread(n2, "B");
        Thread nodeThread3 = new Thread(n3, "C");
        Thread nodeThread4 = new Thread(n4, "D");

        nodeThread1.start();
        nodeThread2.start();
        nodeThread3.start();
        nodeThread4.start();
        submitTask(n3, "D", "HELLOOO", 0);
        submitTask(n1, "B", "GOODBEY", 300);

        while (true) {
            System.out.println(n1.getCurrent_state() + " " + n2.getCurrent_state() + " " + n3.getCurrent_state() + " " + n4.getCurrent_state());
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