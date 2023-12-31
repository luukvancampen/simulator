import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;

public class Network implements Runnable{
    // transmission range in meters.
    final LinkedList<Packet> packets = new LinkedList<>();

    // TODO deal with broadcast of TD messages, they're for everyone (I think).

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(100);
//                System.out.println("Network contents: " + this.packets.toString());
                //TODO handle collisions here. Partially random?
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Nodes can call this method to send a packet to the network. It can't fail, so no need to return anything.
    void send(Packet packet) {
        synchronized (this.packets) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println(packet.originID + " ---> " + packet.destination + ": " + packet.type.toString());
            this.packets.add(packet);
        }
    }

    // Nodes can call this method to receive all packets destined for them. It either returns an Optional<Packet> or
    // nothing.
    Optional<Packet> receive(Node node) {
        synchronized (this.packets) {
            for (Packet packet : this.packets){
                // let a node receive a packet whenever it is in range.
                if (!packet.received.contains(node) && nodeWithinRange(packet, node) && !Objects.equals(node.id, packet.originID)) {
                    // Packet destined for node, make sure the node is added to the packets received list

                    packet.received.add(node);
                    // Return a deep copy of the object.
                    // TODO think about this, deep copy really necessary? I think so.
                    Packet transmittedPacket = packet.clone();
//                    System.out.println(node.id + " receives " + packet.type.toString() + " from " + packet.originID);
                    return Optional.of(transmittedPacket);
                }
            }
            return Optional.empty();
        }
    }

    // The following method will return true when a node is in range of the origin of a packet.
    // (Nodes each have their own transmission range)
    boolean nodeWithinRange(Packet packet, Node node) {
        double packetX = packet.originCoordinate[0];
        double packetY = packet.originCoordinate[1];

        double nodeX = node.coordinate[0];
        double nodeY = node.coordinate[1];

        double distance = Math.sqrt(Math.pow(Math.abs(packetX - nodeX), 2) + Math.pow(Math.abs(packetY - nodeY), 2));
        return distance <= node.transmissionRange;
    }
}
