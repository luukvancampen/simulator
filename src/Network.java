import java.util.LinkedList;
import java.util.Optional;

public class Network implements Runnable{
    // transmission range in meters.
    double range = 100;
    LinkedList<Packet> packets = new LinkedList<>();

    @Override
    public void run() {
        while (true) {
            try {
                System.out.println("Content of queue: ");
                for (Packet packet : this.packets) {
                    System.out.println(packet.type);
                }
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Nodes can call this method to send a packet to the network. It can't fail, so no need to return anything.
    void send(Packet packet) {
        this.packets.add(packet);
    }

    // Nodes can call this method to receive all packets destined for them. It either returns an Optional<Packet> or
    // nothing.
    Optional<Packet> receive(Node node) {
        for (Packet packet : this.packets){
            // Check whether the destination of a packet matches the id of the requesting node...
            if (packet.destination.equals(node.id) && !packet.received.contains(node)) {
                // Packet destined for node, make sure the node is added to the packets received list
                packet.received.add(node);
                // Return a deep copy of the object.
                // TODO think about this, deep copy really necessary? I think so.
                Packet transmittedPacket = packet.clone();
                return Optional.of(transmittedPacket);
            }
        }
        return Optional.empty();
    }
}
