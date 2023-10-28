import java.util.HashSet;
import java.util.Optional;

public class Node implements Runnable{
    private final String[] states = new String[]{"IDLE", "CONTEND", "WFRTS", "WFCTS", "WFCntend", "SendData", "WFDS", "WFData", "WFACK", "QUIET"};

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
                    System.out.println("received " + packet.get().type);
                    Packet reply = new Packet("Reply to " + packet.get().type, this.coordinate, this.id, packet.get().originID, new HashSet<>());
                    network.send(reply);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }
    }
}
