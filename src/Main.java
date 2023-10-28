public class Main {
    public static void main(String[] args) {
       System.out.println("Starting simulator....");
//       Network network = new Network();

       Network network = new Network();
       Thread nThread = new Thread(network, "network thread");
       nThread.start();

       try {
           Packet p1 = new Packet("DATA", new double[]{0.0, 1.1}, "192.168.1.1");
           network.send(p1);
           Thread.sleep(1000);
           Packet p2 = new Packet("DATA1", new double[]{0.0, 1.1}, "192.168.1.1");
           network.send(p2);
           Thread.sleep(1000);
           Packet p3 = new Packet("DATA2", new double[]{0.0, 1.1}, "192.168.1.1");
           network.send(p3);
           Thread.sleep(1000);
           Packet p4 = new Packet("DATA3", new double[]{0.0, 1.1}, "192.168.1.1");
           network.send(p4);
           Thread.sleep(1000);
           Packet p5 = new Packet("DATA4", new double[]{0.0, 1.1}, "192.168.1.1");
           network.send(p5);
           Thread.sleep(1000);
           Packet p6 = new Packet("DATA5", new double[]{0.0, 1.1}, "192.168.1.1");
           network.send(p6);
           Thread.sleep(1000);
       } catch (Exception e) {
           e.printStackTrace();
       }

    }
}