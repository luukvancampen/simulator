import java.util.Optional;

public class Node implements Runnable{
    String id;
    double transmissionRange;
    double[] coordinate;
    Network network;

    public Node(String id, double transmissionRange, double[] coordinate, Network network) {
        this.id = id;
        this.transmissionRange = transmissionRange;
        this.coordinate = coordinate;
        this.network = network;
    }

    @Override
    public void run() {
        while(true) {
            try {
                Thread.sleep(1000);
                Optional<Packet> packet = this.network.receive(this);
                if (packet.isPresent()) {
                    System.out.println("Node " + this.id + " has received a packet: " + packet.get().type);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }
    }
}
